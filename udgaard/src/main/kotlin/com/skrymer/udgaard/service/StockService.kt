package com.skrymer.udgaard.service

import com.skrymer.udgaard.controller.dto.FailedStock
import com.skrymer.udgaard.controller.dto.SimpleStockInfo
import com.skrymer.udgaard.controller.dto.StockRefreshResult
import com.skrymer.udgaard.domain.BreadthDomain
import com.skrymer.udgaard.domain.OrderBlockSensitivity
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.factory.StockFactory
import com.skrymer.udgaard.integration.FundamentalDataProvider
import com.skrymer.udgaard.integration.StockProvider
import com.skrymer.udgaard.integration.TechnicalIndicatorProvider
import com.skrymer.udgaard.integration.ovtlyr.OvtlyrClient
import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockInformation
import com.skrymer.udgaard.model.BreadthSymbol
import com.skrymer.udgaard.model.SectorSymbol
import com.skrymer.udgaard.repository.jooq.BreadthJooqRepository
import com.skrymer.udgaard.repository.jooq.StockJooqRepository
import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
open class StockService(
  val stockRepository: StockJooqRepository,
  val ovtlyrClient: OvtlyrClient,
  val breadthRepository: BreadthJooqRepository,
  val orderBlockCalculator: OrderBlockCalculator,
  val stockProvider: StockProvider,
  val technicalIndicatorProvider: TechnicalIndicatorProvider,
  val fundamentalDataProvider: FundamentalDataProvider,
  val stockFactory: StockFactory,
) {
  companion object {
    private val logger: Logger = LoggerFactory.getLogger(StockService::class.java)

    // List of known indexes and ETFs that don't have earnings
    val INDEX_AND_ETF_SYMBOLS = setOf(
      // Major market indexes
      "SPY",
      "QQQ",
      "IWM",
      "DIA",
      "VTI",
      "VOO",
      "VEA",
      "VWO",
      // Sector ETFs
      "XLK",
      "XLF",
      "XLV",
      "XLE",
      "XLI",
      "XLY",
      "XLP",
      "XLB",
      "XLRE",
      "XLU",
      "XLC",
      // Leveraged ETFs
      "TQQQ",
      "SQQQ",
      "UPRO",
      "SPXU",
      "SOXL",
      "SOXS",
      "TNA",
      "TZA",
      "TECL",
      "TECS",
      "FAS",
      "FAZ",
      "ERX",
      "ERY",
      "UDOW",
      "SDOW",
      // Bond ETFs
      "TLT",
      "IEF",
      "SHY",
      "AGG",
      "BND",
      "LQD",
      "HYG",
      "JNK",
      // Commodity ETFs
      "GLD",
      "SLV",
      "USO",
      "UNG",
      "DBA",
      // International ETFs
      "EEM",
      "EFA",
      "FXI",
      "EWJ",
      "EWZ",
    )
  }

  /**
   * Check if a symbol is a known index or ETF.
   * Indexes and ETFs don't have earnings data.
   */
  private fun isIndexOrETF(symbol: String): Boolean = INDEX_AND_ETF_SYMBOLS.contains(symbol.uppercase())

  /**
   * Loads the stock from DB if exists, else load it from Ovtlyr and save it.
   * @param symbol - the [symbol] of the stock to get
   * @param forceFetch - force fetch the stock from the ovtlyr API
   */
  @Transactional(readOnly = true)
  open suspend fun getStock(
    symbol: String,
    forceFetch: Boolean = false,
    skipOvtlyrEnrichment: Boolean = false,
  ): StockDomain? {
    logger.info("Getting stock $symbol with forceFetch=$forceFetch, skipOvtlyrEnrichment=$skipOvtlyrEnrichment")

    val stock = if (forceFetch) {
      // Create single-stock context for individual fetch
      val spy = if (skipOvtlyrEnrichment) null else getSpy()
      val marketBreadth = breadthRepository.findBySymbol(BreadthSymbol.Market().toIdentifier())
      val refreshContext = RefreshContext(spy = spy, marketBreadth = marketBreadth, skipOvtlyrEnrichment = skipOvtlyrEnrichment)
      fetchStock(symbol, refreshContext, saveToDb = true)
    } else {
      stockRepository.findBySymbol(symbol) ?: run {
        val spy = if (skipOvtlyrEnrichment) null else getSpy()
        val marketBreadth = breadthRepository.findBySymbol(BreadthSymbol.Market().toIdentifier())
        val refreshContext = RefreshContext(spy = spy, marketBreadth = marketBreadth, skipOvtlyrEnrichment = skipOvtlyrEnrichment)
        fetchStock(symbol, refreshContext, saveToDb = true)
      }
    }

    // Enrich order blocks with trading days age
    stock?.let { enrichOrderBlocksWithAge(it) }

    return stock
  }

  /**
   * Enriches order blocks with trading days age based on the stock's quote history
   */
  private fun enrichOrderBlocksWithAge(stock: StockDomain) {
    val lastQuote = stock.quotes.maxByOrNull { it.date }
    if (lastQuote == null) {
      logger.warn("No quotes available for ${stock.symbol}, cannot calculate order block age")
      return
    }

    stock.orderBlocks.forEach { orderBlock ->
      val endDate = orderBlock.endDate ?: lastQuote.date
      val ageInTradingDays = stock.countTradingDaysBetween(orderBlock.startDate, endDate)
      orderBlock.ageInTradingDays = ageInTradingDays
    }
  }

  /**
   * @return all stocks currently stored in DB
   *
   * Uses explicit JOIN FETCH queries to load all collections efficiently
   * and prevent connection leaks. The three-query approach avoids Hibernate's
   * MultipleBagFetchException while ensuring all data is loaded within the transaction.
   *
   * IMPORTANT: Entities are DETACHED from the persistence context after loading
   * to prevent Hibernate from holding database connections during long-running
   * operations (like backtesting). This allows the transaction to close immediately
   * after data is loaded.
   */
  @Cacheable(value = ["stocks"], key = "'allStocks'")
  open fun getAllStocks() = stockRepository.findAll()

  /**
   * Get simple info for all stocks in the database.
   * Returns lightweight stock information without loading full quotes/order blocks.
   *
   * @return list of SimpleStockInfo with symbol, sector, quote count, and last quote date
   */
  @Transactional(readOnly = true)
  open fun getAllStocksSimple(): List<SimpleStockInfo> {
    logger.info("Getting simple info for all stocks")
    val stocks = stockRepository.findAll()
    return stocks.map { stock ->
      SimpleStockInfo(
        symbol = stock.symbol,
        sector = stock.sectorSymbol ?: "UNKNOWN",
        quoteCount = stock.quotes.size,
        orderBlockCount = stock.orderBlocks.size,
        lastQuoteDate = stock.quotes.maxOfOrNull { it.date },
        hasData = stock.quotes.isNotEmpty()
      )
    }
  }

  /**
   * Get stocks by a list of symbols (efficient repository query)
   * Returns only stocks that exist in the database
   *
   * @param symbols - list of stock symbols to fetch
   * @param forceFetch - force fetching stocks from ovtlyr api (bypasses cache)
   * @param skipOvtlyrEnrichment - skip Ovtlyr enrichment (default: false)
   * @return list of stocks matching the provided symbols (only those that exist in DB)
   */
  @Cacheable(
    value = ["stocks"],
    key = "'bySymbols:' + #symbols.toString()",
    unless = "#forceFetch or #result.isEmpty()",
  )
  @OptIn(ExperimentalCoroutinesApi::class)
  open fun getStocksBySymbols(
    symbols: List<String>,
    forceFetch: Boolean = false,
    skipOvtlyrEnrichment: Boolean = false,
  ): List<StockDomain> =
    runBlocking {
      // Sort symbols to ensure consistent cache keys (since symbols may come from a Set with no guaranteed order)
      val sortedSymbols = symbols.sorted()
      val logger = LoggerFactory.getLogger("StockFetcher")
      // Process up to 10 stocks in parallel
      // Each stock makes ~5 API calls, but rate limiting is handled transparently by provider decorators
      // The decorators use suspend functions with Mutex backpressure to enforce 5 requests/second limit
      val limited = Dispatchers.IO.limitedParallelism(10)

      if (forceFetch) {
        // Create RefreshContext with SPY and market breadth (fetched once for entire batch)
        val spy = if (skipOvtlyrEnrichment) null else getSpy()
        val marketBreadth = breadthRepository.findBySymbol(BreadthSymbol.Market().toIdentifier())
        val refreshContext = RefreshContext(spy = spy, marketBreadth = marketBreadth, skipOvtlyrEnrichment = skipOvtlyrEnrichment)

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

        if (failedStocks.isNotEmpty()) {
          logger.error(
            "STOCK FETCH FAILURE: ${failedStocks.size}/${sortedSymbols.size} stocks failed to fetch: ${failedStocks.map { it.symbol }.joinToString(", ")}",
          )
        }

        if (stocksWithoutData.isNotEmpty()) {
          logger.warn(
            "EMPTY DATA: ${stocksWithoutData.size}/${sortedSymbols.size} stocks fetched but had no quote data: ${stocksWithoutData.map { it.symbol }.joinToString(", ")}",
          )
        }

        // Batch save only stocks with quote data
        if (stocksWithData.isNotEmpty()) {
          stockRepository.batchSave(stocksWithData)
        }

        return@runBlocking stocksWithData
      }

      // Get existing stocks from repository with quotes (single query for all symbols)
      return@runBlocking stockRepository.findBySymbols(sortedSymbols)
    }

  /**
   * Refresh stocks with detailed success/failure information.
   * Returns a comprehensive result including counts, successful stocks, and failed stocks with error messages.
   *
   * @param symbols - list of stock symbols to refresh
   * @param skipOvtlyrEnrichment - skip Ovtlyr enrichment (default: false)
   * @return detailed refresh result with status, counts, and error information
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  open fun refreshStocksWithDetails(
    symbols: List<String>,
    skipOvtlyrEnrichment: Boolean = false,
  ): StockRefreshResult =
    runBlocking {
      val sortedSymbols = symbols.sorted()
      val logger = LoggerFactory.getLogger("StockRefresher")
      val limited = Dispatchers.IO.limitedParallelism(10)

      // Create RefreshContext with SPY and market breadth (fetched once for entire batch)
      val spy = if (skipOvtlyrEnrichment) null else getSpy()
      val marketBreadth = breadthRepository.findBySymbol(BreadthSymbol.Market().toIdentifier())
      val refreshContext = RefreshContext(spy = spy, marketBreadth = marketBreadth, skipOvtlyrEnrichment = skipOvtlyrEnrichment)

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
          else ->
            "${stocksWithData.size} stocks succeeded, ${allFailedStocks.size} failed (${String.format("%.1f", stocksWithData.size.toDouble() / sortedSymbols.size * 100)}% success rate)"
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

  /**
   * Fetches stock data from primary provider enriched with Ovtlyr indicators.
   * Uses StockFactory to create the stock entity with all enrichments.
   *
   * Data Pipeline:
   * - StockProvider: OHLCV (adjusted) + volume (REQUIRED)
   * - TechnicalIndicatorProvider: ATR, ADX (BOTH REQUIRED for backtesting)
   * - FundamentalDataProvider: Earnings (REQUIRED for backtesting), sector information (REQUIRED for sector breadth)
   * - Internal calculation: EMAs, Donchian channels, trend
   * - Ovtlyr enrichment: Buy/sell signals, heatmaps, sector sentiment
   *
   * Rate limiting is handled transparently by provider decorators.
   *
   * @param symbol - the stock symbol to fetch
   * @param refreshContext - optional session context with cached SPY, breadth, and sector data
   * @param saveToDb - whether to save to database immediately (default: true)
   * @return the fetched and saved stock, or null if fetch or save failed
   */
  private suspend fun fetchStock(
    symbol: String,
    refreshContext: RefreshContext? = null,
    saveToDb: Boolean = true,
  ): StockDomain? {
    val logger = LoggerFactory.getLogger("StockService")

    logger.info("Fetching $symbol")

    // Delete existing stock if it exists (only if not using batch operations)
    if (saveToDb && refreshContext == null) {
      stockRepository.findBySymbol(symbol)?.let {
        stockRepository.delete(symbol)
      }
    }

    // Fetch and process stock data
    val stock = runCatching {
      // Step 1: Get SPY reference data (from context if available, otherwise fetch)
      // When skipOvtlyrEnrichment=true, refreshContext.spy will be null intentionally - don't fetch SPY
      val spy = if (refreshContext?.skipOvtlyrEnrichment == true) {
        null
      } else {
        refreshContext?.spy ?: getSpy()
      }

      // Step 2: Fetch adjusted daily data from StockProvider (PRIMARY data source - REQUIRED)
      val stockQuotes = stockProvider.getDailyAdjustedTimeSeries(symbol)
      if (stockQuotes == null) {
        throw IllegalStateException("Could not fetch data from StockProvider")
      }

      // Step 3: Fetch ATR data (REQUIRED for strategies)
      val atrMap = technicalIndicatorProvider.getATR(symbol)
        ?: throw IllegalStateException("Could not fetch ATR data")

      // Step 3.1: Fetch ADX data (REQUIRED for trend strength conditions and backtesting)
      val adxMap = technicalIndicatorProvider.getADX(symbol)
        ?: throw IllegalStateException("Could not fetch ADX data")

      // Step 3.5: Fetch earnings history (OPTIONAL for indexes/ETFs, REQUIRED for individual stocks)
      // Indexes and ETFs don't have earnings, so we skip fetching for them
      val earnings = if (isIndexOrETF(symbol)) {
        logger.info("$symbol is an index/ETF - skipping earnings fetch")
        emptyList()
      } else {
        fundamentalDataProvider.getEarnings(symbol)
          ?: throw IllegalStateException("Could not fetch earnings data")
      }

      // Step 4: Get sector symbol (from cache if available, otherwise fetch)
      // Indexes and ETFs don't have sectors - they contain multiple sectors
      val sectorSymbol =
        refreshContext?.getSectorSymbol(symbol)?.let { cachedSectorName ->
          SectorSymbol.fromString(cachedSectorName)
        } ?: run {
          if (isIndexOrETF(symbol)) {
            logger.info("$symbol is an index/ETF - skipping sector fetch")
            null
          } else {
            val sector = fundamentalDataProvider.getSectorSymbol(symbol)
            if (sector == null) {
              logger.warn("Could not determine sector for $symbol - sector breadth will not be available")
            } else {
              // Cache sector symbol if context is available and sector is non-null
              // (ConcurrentHashMap doesn't allow null values)
              refreshContext?.cacheSectorSymbol(symbol, sector.name)
            }
            sector
          }
        }

      // Step 5: Fetch breadth data for context (from cache if available)
      val marketBreadth =
        refreshContext?.marketBreadth
          ?: breadthRepository.findBySymbol(BreadthSymbol.Market().toIdentifier())

      val sectorBreadth =
        sectorSymbol?.let { sector ->
          refreshContext?.getSectorBreadth(sector.name) ?: run {
            val breadth = breadthRepository.findBySymbol(BreadthSymbol.Sector(sector).toIdentifier())
            // Cache sector breadth if context is available and breadth is non-null
            // (ConcurrentHashMap doesn't allow null values)
            if (breadth != null) {
              refreshContext?.cacheSectorBreadth(sector.name, breadth)
            }
            breadth
          }
        }

      // Step 6: Create enriched quotes using StockFactory
      // This will: calculate EMAs, add ATR, add ADX, calculate Donchian, determine trend, enrich with Ovtlyr (if not skipped)
      val enrichedQuotes =
        stockFactory.enrichQuotes(
          symbol = symbol,
          stockQuotes = stockQuotes,
          atrMap = atrMap,
          adxMap = adxMap,
          marketBreadth = marketBreadth,
          sectorBreadth = sectorBreadth,
          spy = spy,
          skipOvtlyrEnrichment = refreshContext?.skipOvtlyrEnrichment ?: false,
        ) ?: throw IllegalStateException("Could not create enriched quotes")

      // Step 7: Calculate order blocks with multiple sensitivities (like Sonar Lab)

      // Calculate with HIGH sensitivity (28% - more blocks detected)
      val orderBlocksHigh =
        orderBlockCalculator.calculateOrderBlocks(
          quotes = enrichedQuotes,
          sensitivity = 28.0,
          sensitivityLevel = OrderBlockSensitivity.HIGH,
        )

      // Calculate with LOW sensitivity (50% - fewer, stronger blocks)
      val orderBlocksLow =
        orderBlockCalculator.calculateOrderBlocks(
          quotes = enrichedQuotes,
          sensitivity = 50.0,
          sensitivityLevel = OrderBlockSensitivity.LOW,
        )

      // Combine both sensitivity levels
      val orderBlocks = orderBlocksHigh + orderBlocksLow

      // Step 8: Create Stock entity
      stockFactory.createStock(
        symbol = symbol,
        sectorSymbol = sectorSymbol?.name,
        enrichedQuotes = enrichedQuotes,
        orderBlocks = orderBlocks,
        earnings = earnings,
      )
    }.onFailure { error ->
      // Log full exception details including stack trace
      logger.error("✗ $symbol failed: ${error.message ?: error::class.simpleName}", error)
    }.getOrNull()

    // Step 9: Save to database only if stock creation succeeded
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

  /**
   * Get market breadth data for use in backtesting.
   * @return Market breadth entity, or null if not available
   */
  @Transactional(readOnly = true)
  open fun getMarketBreadth(): BreadthDomain? = breadthRepository.findBySymbol(BreadthSymbol.Market().toIdentifier())

  /**
   * Get SPY reference data from Ovtlyr.
   * Cached for 30 minutes to reduce API calls during batch stock refreshes.
   *
   * @return SPY stock information from Ovtlyr
   */
  @Cacheable(value = ["spy"], key = "'SPY'")
  open fun getSpy(): OvtlyrStockInformation {
    logger.info("Fetching SPY reference data from Ovtlyr (cache miss)")
    val spy: OvtlyrStockInformation? = ovtlyrClient.getStockInformation("SPY")
    checkNotNull(spy) { "Failed to fetch SPY reference data" }
    return spy
  }
}
