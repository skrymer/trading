package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.dto.RefreshProgress
import com.skrymer.udgaard.data.dto.RefreshTask
import com.skrymer.udgaard.data.dto.RefreshType
import com.skrymer.udgaard.data.integration.StockProvider
import com.skrymer.udgaard.data.integration.midgaard.MidgaardClient
import com.skrymer.udgaard.data.model.OrderBlockSensitivity
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
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
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

@Service
class StockIngestionService(
  private val stockRepository: StockJooqRepository,
  private val stockProvider: StockProvider,
  private val technicalIndicatorService: TechnicalIndicatorService,
  private val orderBlockCalculator: OrderBlockCalculator,
  private val midgaardClient: MidgaardClient,
  private val sectorBreadthService: SectorBreadthService,
  private val marketBreadthService: MarketBreadthService,
  private val marketBreadthRepository: MarketBreadthRepository,
) {
  private val logger = LoggerFactory.getLogger(StockIngestionService::class.java)
  private val refreshQueue = ConcurrentLinkedQueue<RefreshTask>()

  @Volatile
  private var isProcessing = false

  private val currentProgress = AtomicReference(RefreshProgress())

  @Volatile
  private var processingJob: Job? = null

  @Volatile
  private var _lastRefreshedAt: LocalDateTime? = null
  val lastRefreshedAt: LocalDateTime? get() = _lastRefreshedAt

  @PostConstruct
  fun initLastRefreshedAt() {
    _lastRefreshedAt = stockRepository.getLatestQuoteTimestamp()
  }

  // ── Public API ─────────────────────────────────────────────────────

  /**
   * Refresh a single stock: delete existing → fetch from Midgaard → save to DB.
   */
  fun refreshStock(symbol: String): Stock? {
    stockRepository.findBySymbol(symbol)?.let { stockRepository.delete(symbol) }
    val stock = fetchAndBuildStock(symbol) ?: return null
    return saveStock(stock)
  }

  /**
   * Queue stock symbols for async batch refresh with progress tracking.
   */
  fun queueStockRefresh(symbols: List<String>) {
    symbols.forEach { refreshQueue.add(RefreshTask(type = RefreshType.STOCK, identifier = it)) }
    currentProgress.set(RefreshProgress(total = refreshQueue.size))
    logger.info("Queued ${symbols.size} stocks for refresh. Total queue size: ${refreshQueue.size}")
    startProcessing()
  }

  fun getProgress(): RefreshProgress = currentProgress.get()

  fun clearQueue() {
    logger.info("Clearing refresh queue")
    refreshQueue.clear()
    currentProgress.set(RefreshProgress())
    processingJob?.cancel()
    isProcessing = false
  }

  // ── Stock fetch pipeline ───────────────────────────────────────────

  /**
   * Fetch quotes from Midgaard, enrich with trend, and calculate order blocks.
   * Returns a fully built Stock entity, or null on failure.
   */
  internal fun fetchAndBuildStock(symbol: String): Stock? =
    runCatching {
      logger.info("Fetching $symbol")
      val quotes = fetchQuotes(symbol)
      val enrichedQuotes = enrichWithTrend(quotes, symbol)
      val orderBlocks = calculateOrderBlocks(enrichedQuotes)
      val sortedQuotes = enrichedQuotes.sortedBy { it.date }
      Stock(
        symbol = symbol,
        sectorSymbol = midgaardClient.getSymbolInfo(symbol)?.sectorSymbol,
        quotes = sortedQuotes,
        orderBlocks = orderBlocks.toMutableList(),
        listingDate = sortedQuotes.firstOrNull()?.date,
        delistingDate = sortedQuotes.lastOrNull()?.date?.let { lastDate ->
          val cutoff = LocalDate.now().minusDays(90)
          if (lastDate.isBefore(cutoff)) lastDate else null
        },
      )
    }.onFailure { error ->
      logger.error("✗ $symbol failed: ${error.message ?: error::class.simpleName}", error)
    }.getOrNull()

  private fun fetchQuotes(symbol: String): List<StockQuote> =
    stockProvider.getDailyAdjustedTimeSeries(symbol)
      ?: throw IllegalStateException("No data from Midgaard for $symbol")

  private fun enrichWithTrend(quotes: List<StockQuote>, symbol: String): List<StockQuote> =
    technicalIndicatorService.enrichWithIndicators(quotes, symbol)

  private fun calculateOrderBlocks(quotes: List<StockQuote>) =
    orderBlockCalculator.calculateOrderBlocks(
      quotes = quotes,
      sensitivity = 28.0,
      sensitivityLevel = OrderBlockSensitivity.HIGH,
    ) + orderBlockCalculator.calculateOrderBlocks(
      quotes = quotes,
      sensitivity = 50.0,
      sensitivityLevel = OrderBlockSensitivity.LOW,
    )

  private fun saveStock(stock: Stock): Stock? =
    try {
      stockRepository.save(stock)
      logger.info("✓ ${stock.symbol} completed")
      stock
    } catch (e: Exception) {
      logger.error("✗ ${stock.symbol} database save failed: ${e.message ?: e::class.simpleName}", e)
      null
    }

  // ── Batch processing ───────────────────────────────────────────────

  /**
   * Fetch a batch of stocks in parallel: delete existing → fetch → batch save.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  private fun batchRefreshStocks(symbols: List<String>): List<Stock> =
    runBlocking {
      val sorted = symbols.sorted()
      val limited = Dispatchers.IO.limitedParallelism(20)

      stockRepository.batchDelete(sorted)

      val results = sorted
        .map { symbol -> async(limited) { symbol to fetchAndBuildStock(symbol) } }
        .awaitAll()

      val stocks = results.mapNotNull { it.second }
      val stocksWithData = stocks.filter { it.quotes.isNotEmpty() }

      logBatchFailures(results, sorted.size)

      if (stocksWithData.isNotEmpty()) {
        val totalQuotes = stocksWithData.sumOf { it.quotes.size }
        logger.info("Saving ${stocksWithData.size} stocks ($totalQuotes quotes) to database")
        stockRepository.batchInsert(stocksWithData)
        logger.info("Batch save complete")
      }

      stocksWithData
    }

  private fun logBatchFailures(results: List<Pair<String, Stock?>>, totalCount: Int) {
    val failed = results.filter { it.second == null }.map { it.first }
    val empty = results.mapNotNull { it.second }.filter { it.quotes.isEmpty() }

    if (failed.isNotEmpty()) {
      logger.error(
        "STOCK FETCH FAILURE: ${failed.size}/$totalCount " +
          "stocks failed to fetch: ${failed.joinToString(", ")}",
      )
    }
    if (empty.isNotEmpty()) {
      logger.warn(
        "EMPTY DATA: ${empty.size}/$totalCount stocks fetched " +
          "but had no quote data: ${empty.map { it.symbol }.joinToString(", ")}",
      )
    }
  }

  // ── Queue processing ───────────────────────────────────────────────

  @Synchronized
  private fun startProcessing() {
    if (isProcessing) {
      logger.info("Already processing, skipping startProcessing()")
      return
    }

    isProcessing = true

    processingJob = CoroutineScope(Dispatchers.IO).launch {
      val errorDetails = mutableListOf<String>()
      var lastProgressLog = 0

      while (refreshQueue.isNotEmpty()) {
        val batch = pollBatch()
        if (batch.isEmpty()) break

        try {
          val symbols = batch.map { it.identifier }
          logger.info("Starting batch: ${symbols.size} stocks")

          val stocks = batchRefreshStocks(symbols)
          val failedSymbols = symbols.filterNot { s -> stocks.any { it.symbol == s } }

          failedSymbols.forEach { errorDetails.add("$it: fetch failed") }
          updateProgress(succeeded = stocks.size, failed = failedSymbols.size, stocks = stocks, failedSymbols = failedSymbols)
        } catch (e: Exception) {
          batch.forEach { errorDetails.add("${it.identifier}: Batch failure - ${e.message}") }
          updateProgress(succeeded = 0, failed = batch.size, stocks = emptyList(), failedSymbols = batch.map { it.identifier })
        }

        lastProgressLog = logProgressIfNeeded(lastProgressLog)
      }

      isProcessing = false
      refreshBreadth()
      logCompletionSummary(errorDetails)
      _lastRefreshedAt = LocalDateTime.now()
      currentProgress.set(RefreshProgress())
    }
  }

  private fun pollBatch(size: Int = 20): List<RefreshTask> {
    val tasks = mutableListOf<RefreshTask>()
    repeat(size) { refreshQueue.poll()?.let { tasks.add(it) } }
    return tasks
  }

  private fun updateProgress(
    succeeded: Int,
    failed: Int,
    stocks: List<Stock>,
    failedSymbols: List<String>,
  ) {
    currentProgress.updateAndGet { prev ->
      prev.copy(
        completed = prev.completed + succeeded,
        failed = prev.failed + failed,
        lastSuccess = stocks.lastOrNull()?.symbol ?: prev.lastSuccess,
        lastError = if (failedSymbols.isNotEmpty()) {
          "Failed symbols: ${failedSymbols.joinToString(", ")}"
        } else {
          prev.lastError
        },
      )
    }
  }

  private fun logProgressIfNeeded(lastProgressLog: Int): Int {
    val progress = currentProgress.get()
    val progressCompleted = progress.completed + progress.failed
    if (progressCompleted - lastProgressLog >= 10 || progressCompleted == progress.total) {
      val percentage = String.format("%.1f", progressCompleted.toDouble() / progress.total * 100)
      logger.info("Progress: $progressCompleted/${progress.total} completed ($percentage%)")
      return progressCompleted
    }
    return lastProgressLog
  }

  private fun refreshBreadth() {
    try {
      marketBreadthRepository.refreshBreadthDaily()
    } catch (e: Exception) {
      logger.error("Failed to refresh market breadth daily: ${e.message}")
    }
    try {
      sectorBreadthService.refreshSectorBreadth()
    } catch (e: Exception) {
      logger.error("Failed to refresh sector breadth: ${e.message}")
    }
    try {
      marketBreadthService.refreshMarketBreadth()
    } catch (e: Exception) {
      logger.error("Failed to refresh market breadth EMAs: ${e.message}")
    }
  }

  private fun logCompletionSummary(errorDetails: List<String>) {
    val progress = currentProgress.get()
    val totalProcessed = progress.completed + progress.failed
    val successRate = if (totalProcessed > 0) {
      String.format("%.1f", progress.completed.toDouble() / totalProcessed * 100)
    } else {
      "0.0"
    }

    logger.info("")
    logger.info("=== Refresh Complete ===")
    logger.info(
      "Total: $totalProcessed | Succeeded: ${progress.completed} " +
        "| Failed: ${progress.failed} ($successRate% success)",
    )

    if (errorDetails.isNotEmpty()) {
      logger.info("")
      logger.info("Failed items (${errorDetails.size}):")
      errorDetails.forEach { logger.info("  - $it") }
    }
    logger.info("========================")
    logger.info("")
  }
}
