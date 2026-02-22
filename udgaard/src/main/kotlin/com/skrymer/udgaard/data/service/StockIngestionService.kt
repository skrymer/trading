package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.dto.FailedStock
import com.skrymer.udgaard.data.dto.RefreshProgress
import com.skrymer.udgaard.data.dto.RefreshTask
import com.skrymer.udgaard.data.dto.RefreshType
import com.skrymer.udgaard.data.dto.StockRefreshResult
import com.skrymer.udgaard.data.factory.StockFactory
import com.skrymer.udgaard.data.integration.FundamentalDataProvider
import com.skrymer.udgaard.data.integration.StockProvider
import com.skrymer.udgaard.data.integration.TechnicalIndicatorProvider
import com.skrymer.udgaard.data.model.OrderBlockSensitivity
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.concurrent.ConcurrentLinkedQueue

@Service
class StockIngestionService(
  private val stockRepository: StockJooqRepository,
  private val stockProvider: StockProvider,
  private val technicalIndicatorProvider: TechnicalIndicatorProvider,
  private val fundamentalDataProvider: FundamentalDataProvider,
  private val stockFactory: StockFactory,
  private val orderBlockCalculator: OrderBlockCalculator,
  private val symbolService: SymbolService,
  private val sectorBreadthService: SectorBreadthService,
  private val marketBreadthService: MarketBreadthService,
  private val marketBreadthRepository: MarketBreadthRepository,
) {
  private val logger = LoggerFactory.getLogger(StockIngestionService::class.java)
  private val refreshQueue = ConcurrentLinkedQueue<RefreshTask>()

  @Volatile
  private var isProcessing = false

  private var currentProgress = RefreshProgress()
  private var processingJob: Job? = null

  // Minimum date for data filtering (shared across the current refresh session)
  @Volatile
  private var minDate: LocalDate = LocalDate.of(2016, 1, 1)

  /**
   * Check if a symbol is a non-stock asset (ETF, index, leveraged ETF, etc.).
   * Non-stock assets don't have earnings data or individual sector assignments.
   */
  private fun isNonStock(symbol: String): Boolean = symbolService.isNonStock(symbol)

  // ── Single-stock refresh ───────────────────────────────────────────

  /**
   * Refresh a single stock: delete existing data, fetch from APIs, save to DB.
   *
   * @param symbol stock symbol to refresh
   * @param minDate earliest date for data (default 2016-01-01)
   * @return the refreshed Stock, or null if fetch failed
   */
  suspend fun refreshStock(
    symbol: String,
    minDate: LocalDate = LocalDate.of(2016, 1, 1),
  ): Stock? {
    val refreshContext = RefreshContext(minDate = minDate)
    return fetchStock(symbol, refreshContext, saveToDb = true)
  }

  // ── Multi-stock refresh (with detailed results) ────────────────────

  /**
   * Refresh stocks with detailed success/failure information.
   * Returns a comprehensive result including counts, successful stocks, and failed stocks with error messages.
   *
   * @param symbols list of stock symbols to refresh
   * @param minDate earliest date for data (default 2016-01-01)
   * @return detailed refresh result with status, counts, and error information
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  fun refreshStocks(
    symbols: List<String>,
    minDate: LocalDate = LocalDate.of(2016, 1, 1),
  ): StockRefreshResult =
    runBlocking {
      val sortedSymbols = symbols.sorted()
      val limited = Dispatchers.IO.limitedParallelism(10)

      val refreshContext = RefreshContext(minDate = minDate)

      // Batch delete existing stocks before fetching (reduces DB operations)
      stockRepository.batchDelete(sortedSymbols)

      // Force fetch all symbols from API (WITHOUT saving to DB yet)
      val results =
        sortedSymbols
          .map { symbol ->
            async(limited) {
              val result =
                runCatching {
                  fetchStock(symbol, refreshContext, saveToDb = false)
                }
              val stock = result.getOrNull()
              val error = result.exceptionOrNull()?.message ?: result.exceptionOrNull()?.toString()
              Triple(symbol, stock, error)
            }
          }.awaitAll()

      val fetchedStocks = results.mapNotNull { it.second }

      // Filter out stocks with no quote data (API returned successfully but data was empty)
      val stocksWithData = fetchedStocks.filter { it.quotes.isNotEmpty() }
      val stocksWithoutData = fetchedStocks.filter { it.quotes.isEmpty() }

      val failedStocks =
        results
          .filter { it.second == null }
          .map { FailedStock(symbol = it.first, error = it.third ?: "Unknown error") }

      // Add stocks with no data to the failed list
      val allFailedStocks = failedStocks + stocksWithoutData.map {
        FailedStock(symbol = it.symbol, error = "No quote data returned from API")
      }

      // Batch save only stocks with quote data
      if (stocksWithData.isNotEmpty()) {
        stockRepository.batchSave(stocksWithData)
      }

      // Determine status
      val status =
        when {
          allFailedStocks.isEmpty() -> "success"
          stocksWithData.isEmpty() -> "failure"
          else -> "partial_success"
        }

      val message =
        when (status) {
          "success" -> "${stocksWithData.size} stocks refreshed successfully"
          "failure" -> "All ${allFailedStocks.size} stocks failed to refresh"
          else -> {
            val successRate = String.format(
              "%.1f",
              stocksWithData.size.toDouble() / sortedSymbols.size * 100,
            )
            "${stocksWithData.size} stocks succeeded, " +
              "${allFailedStocks.size} failed ($successRate% success rate)"
          }
        }

      return@runBlocking StockRefreshResult(
        status = status,
        total = sortedSymbols.size,
        succeeded = stocksWithData.size,
        failed = allFailedStocks.size,
        successfulStocks = stocksWithData.map { it.symbol },
        failedStocks = allFailedStocks,
        message = message,
      )
    }

  // ── Batch refresh (parallel fetch, used by queue processing) ───────

  /**
   * Fetch a batch of stocks in parallel: delete existing → fetch from APIs → save to DB.
   * Used internally by startProcessing() for queue-based batch refresh.
   *
   * @param symbols list of stock symbols to fetch
   * @param minDate earliest date for data
   * @return list of successfully fetched stocks
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun batchRefreshStocks(
    symbols: List<String>,
    minDate: LocalDate,
  ): List<Stock> =
    runBlocking {
      val sortedSymbols = symbols.sorted()
      val limited = Dispatchers.IO.limitedParallelism(10)
      val refreshContext = RefreshContext(minDate = minDate)

      // Batch delete existing stocks before fetching
      stockRepository.batchDelete(sortedSymbols)

      // Force fetch all symbols from API (WITHOUT saving to DB yet)
      val results =
        sortedSymbols
          .map { symbol ->
            async(limited) {
              val result =
                runCatching {
                  fetchStock(symbol, refreshContext, saveToDb = false)
                }
              val stock = result.getOrNull()
              val error = result.exceptionOrNull()?.message ?: result.exceptionOrNull()?.toString()
              Triple(symbol, stock, error)
            }
          }.awaitAll()

      val fetchedStocks = results.mapNotNull { it.second }

      // Filter out stocks with no quote data
      val stocksWithData = fetchedStocks.filter { it.quotes.isNotEmpty() }
      val stocksWithoutData = fetchedStocks.filter { it.quotes.isEmpty() }

      val failedStocks =
        results
          .filter { it.second == null }
          .map { it.first }

      if (failedStocks.isNotEmpty()) {
        logger.error(
          "STOCK FETCH FAILURE: ${failedStocks.size}/${sortedSymbols.size} " +
            "stocks failed to fetch: ${failedStocks.joinToString(", ")}",
        )
      }

      if (stocksWithoutData.isNotEmpty()) {
        logger.warn(
          "EMPTY DATA: ${stocksWithoutData.size}/${sortedSymbols.size} stocks fetched " +
            "but had no quote data: ${stocksWithoutData.map { it.symbol }.joinToString(", ")}",
        )
      }

      // Batch save only stocks with quote data
      if (stocksWithData.isNotEmpty()) {
        stockRepository.batchSave(stocksWithData)
      }

      return@runBlocking stocksWithData
    }

  // ── Queue-based refresh  ──────────────────

  /**
   * Queue stock symbols for refresh
   */
  fun queueStockRefresh(
    symbols: List<String>,
    minDate: LocalDate = LocalDate.of(2016, 1, 1),
  ) {
    this.minDate = minDate
    symbols.forEach { symbol ->
      refreshQueue.add(
        RefreshTask(
          type = RefreshType.STOCK,
          identifier = symbol,
        ),
      )
    }
    currentProgress = RefreshProgress(total = refreshQueue.size)
    logger.info("Queued ${symbols.size} stocks for refresh. Total queue size: ${refreshQueue.size}")
    startProcessing()
  }

  /**
   * Start processing the refresh queue with parallel batch processing
   */
  @Synchronized
  private fun startProcessing() {
    if (isProcessing) {
      logger.info("Already processing, skipping startProcessing()")
      return
    }

    isProcessing = true

    processingJob =
      CoroutineScope(Dispatchers.IO).launch {
        val errorDetails = mutableListOf<String>()
        var lastProgressLog = 0

        while (refreshQueue.isNotEmpty()) {
          val stockTasks = mutableListOf<RefreshTask>()

          // Poll up to 10 tasks for batch processing (reduced to avoid OOM)
          repeat(10) {
            refreshQueue.poll()?.let { task ->
              stockTasks.add(task)
            }
          }

          if (stockTasks.isEmpty()) break

          try {
            val symbols = stockTasks.map { it.identifier }
            logger.info("Starting batch: ${symbols.size} stocks")

            // Use batchRefreshStocks instead of StockService
            val stocks = batchRefreshStocks(symbols, minDate)

            // Update progress for successful stocks
            val successfulSymbols = stocks.map { it.symbol }.toSet()
            val failedSymbols = symbols.filterNot { it in successfulSymbols }

            // Collect error details for summary
            failedSymbols.forEach { symbol ->
              errorDetails.add("$symbol: Unknown error (not fetched)")
            }

            synchronized(currentProgress) {
              currentProgress =
                currentProgress.copy(
                  completed = currentProgress.completed + stocks.size,
                  failed = currentProgress.failed + failedSymbols.size,
                  lastSuccess = stocks.lastOrNull()?.symbol ?: currentProgress.lastSuccess,
                  lastError =
                    if (failedSymbols.isNotEmpty()) {
                      "Failed symbols: ${failedSymbols.joinToString(", ")}"
                    } else {
                      currentProgress.lastError
                    },
                )
            }

            // Log progress every 10 stocks or when reaching certain percentages
            val progressCompleted = currentProgress.completed + currentProgress.failed
            if (progressCompleted - lastProgressLog >= 10 || progressCompleted == currentProgress.total) {
              val percentage = String.format("%.1f", progressCompleted.toDouble() / currentProgress.total * 100)
              logger.info("Progress: $progressCompleted/${currentProgress.total} completed ($percentage%)")
              lastProgressLog = progressCompleted
            }
          } catch (e: Exception) {
            // Collect batch error details
            stockTasks.forEach { task ->
              errorDetails.add("${task.identifier}: Batch failure - ${e.message}")
            }

            synchronized(currentProgress) {
              currentProgress =
                currentProgress.copy(
                  failed = currentProgress.failed + stockTasks.size,
                  lastError = "Batch failure: ${e.message}",
                )
            }
          }
        }

        isProcessing = false

        // Recalculate market breadth from updated stock data
        try {
          marketBreadthRepository.refreshBreadthDaily()
        } catch (e: Exception) {
          logger.error("Failed to refresh market breadth daily: ${e.message}")
        }

        // Recalculate sector breadth from updated stock data
        try {
          sectorBreadthService.refreshSectorBreadth()
        } catch (e: Exception) {
          logger.error("Failed to refresh sector breadth: ${e.message}")
        }

        // Enrich market breadth with EMAs
        try {
          marketBreadthService.refreshMarketBreadth()
        } catch (e: Exception) {
          logger.error("Failed to refresh market breadth EMAs: ${e.message}")
        }

        // Log completion summary
        val totalProcessed = currentProgress.completed + currentProgress.failed
        val successRate = if (totalProcessed > 0) {
          String.format("%.1f", currentProgress.completed.toDouble() / totalProcessed * 100)
        } else {
          "0.0"
        }

        logger.info("")
        logger.info("=== Refresh Complete ===")
        logger.info(
          "Total: $totalProcessed | Succeeded: ${currentProgress.completed} " +
            "| Failed: ${currentProgress.failed} ($successRate% success)",
        )

        if (errorDetails.isNotEmpty()) {
          logger.info("")
          logger.info("Failed items (${errorDetails.size}):")
          errorDetails.forEach { error ->
            logger.info("  - $error")
          }
        }
        logger.info("========================")
        logger.info("")

        // Reset progress after completion
        currentProgress = RefreshProgress()
      }
  }

  /**
   * Get current refresh progress
   */
  fun getProgress(): RefreshProgress = currentProgress

  /**
   * Clear the refresh queue and reset progress
   */
  fun clearQueue() {
    logger.info("Clearing refresh queue")
    refreshQueue.clear()
    currentProgress = RefreshProgress()
    processingJob?.cancel()
    isProcessing = false
  }

  // ── Internal: fetch pipeline ───────────────────────────────────────

  /**
   * Fetches stock data from primary provider with calculated technical indicators.
   * Uses StockFactory to create the stock entity with all enrichments.
   *
   * Data Pipeline:
   * - StockProvider: OHLCV (adjusted) + volume (REQUIRED)
   * - TechnicalIndicatorProvider: ATR, ADX (BOTH REQUIRED for backtesting)
   * - FundamentalDataProvider: Earnings (REQUIRED for backtesting), sector information (REQUIRED for sector breadth)
   * - Internal calculation: EMAs, Donchian channels, trend
   *
   * Rate limiting is handled transparently by provider decorators.
   *
   * @param symbol the stock symbol to fetch
   * @param refreshContext optional session context with cached sector data
   * @param saveToDb whether to save to database immediately (default: true)
   * @return the fetched and saved stock, or null if fetch or save failed
   */
  private suspend fun fetchStock(
    symbol: String,
    refreshContext: RefreshContext? = null,
    saveToDb: Boolean = true,
  ): Stock? {
    logger.info("Fetching $symbol")

    // Delete existing stock if it exists (only if not using batch operations)
    if (saveToDb && refreshContext == null) {
      stockRepository.findBySymbol(symbol)?.let {
        stockRepository.delete(symbol)
      }
    }

    // Fetch and process stock data
    val stock = runCatching {
      // Step 1: Fetch adjusted daily data from StockProvider (PRIMARY data source - REQUIRED)
      val minDate = refreshContext?.minDate ?: LocalDate.of(2016, 1, 1)
      val stockQuotes = stockProvider.getDailyAdjustedTimeSeries(symbol, minDate = minDate)
        ?: throw IllegalStateException("Could not fetch data from StockProvider")

      // Step 2: Fetch ATR data (REQUIRED for strategies)
      val atrMap = technicalIndicatorProvider.getATR(symbol, minDate = minDate)
        ?: throw IllegalStateException("Could not fetch ATR data")

      // Step 2.1: Fetch ADX data (REQUIRED for trend strength conditions and backtesting)
      val adxMap = technicalIndicatorProvider.getADX(symbol, minDate = minDate)
        ?: throw IllegalStateException("Could not fetch ADX data")

      // Step 2.5: Fetch earnings history (OPTIONAL for indexes/ETFs, REQUIRED for individual stocks)
      val earnings = if (isNonStock(symbol)) {
        logger.info("$symbol is a non-stock asset - skipping earnings fetch")
        emptyList()
      } else {
        fundamentalDataProvider.getEarnings(symbol)
          ?: throw IllegalStateException("Could not fetch earnings data")
      }

      // Step 3: Get company info (sector + market cap) from cache or API
      val companyInfo =
        refreshContext?.getCachedCompanyInfo(symbol) ?: run {
          if (isNonStock(symbol)) {
            logger.info("$symbol is a non-stock asset - skipping company info fetch")
            null
          } else {
            val info = fundamentalDataProvider.getCompanyInfo(symbol)
            if (info == null) {
              logger.warn("Could not fetch company info for $symbol - sector breadth will not be available")
            } else {
              refreshContext?.cacheCompanyInfo(symbol, info)
            }
            info
          }
        }
      val sectorSymbol = companyInfo?.sectorSymbol
      val marketCap = companyInfo?.marketCap

      // Step 4: Create enriched quotes using StockFactory
      val enrichedQuotes =
        stockFactory.enrichQuotes(
          symbol = symbol,
          stockQuotes = stockQuotes,
          atrMap = atrMap,
          adxMap = adxMap,
        ) ?: throw IllegalStateException("Could not create enriched quotes")

      // Step 5: Calculate order blocks with multiple sensitivities
      val orderBlocksHigh =
        orderBlockCalculator.calculateOrderBlocks(
          quotes = enrichedQuotes,
          sensitivity = 28.0,
          sensitivityLevel = OrderBlockSensitivity.HIGH,
        )

      val orderBlocksLow =
        orderBlockCalculator.calculateOrderBlocks(
          quotes = enrichedQuotes,
          sensitivity = 50.0,
          sensitivityLevel = OrderBlockSensitivity.LOW,
        )

      val orderBlocks = orderBlocksHigh + orderBlocksLow

      // Step 6: Create Stock entity
      stockFactory.createStock(
        symbol = symbol,
        sectorSymbol = sectorSymbol?.name,
        marketCap = marketCap,
        enrichedQuotes = enrichedQuotes,
        orderBlocks = orderBlocks,
        earnings = earnings,
      )
    }.onFailure { error ->
      logger.error("✗ $symbol failed: ${error.message ?: error::class.simpleName}", error)
    }.getOrNull()

    // Step 7: Save to database only if stock creation succeeded
    if (stock != null && saveToDb) {
      try {
        stockRepository.save(stock)
        logger.info("✓ $symbol completed")
      } catch (e: Exception) {
        logger.error("✗ $symbol database save failed: ${e.message ?: e::class.simpleName}", e)
        return null
      }
    }

    return stock
  }
}
