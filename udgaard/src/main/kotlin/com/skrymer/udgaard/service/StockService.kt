package com.skrymer.udgaard.service

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
  }

  /**
   * Loads the stock from DB if exists, else load it from Ovtlyr and save it.
   * @param symbol - the [symbol] of the stock to get
   * @param forceFetch - force fetch the stock from the ovtlyr API
   */
  @Transactional(readOnly = true)
  open suspend fun getStock(
    symbol: String,
    forceFetch: Boolean = false,
  ): StockDomain? {
    logger.info("Getting stock $symbol with forceFetch=$forceFetch")

    return if (forceFetch) {
      fetchStock(symbol, getSpy())
    } else {
      stockRepository.findBySymbol(symbol) ?: fetchStock(symbol, getSpy())
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
   * Get stocks by a list of symbols (efficient repository query)
   * Returns only stocks that exist in the database
   *
   * @param symbols - list of stock symbols to fetch
   * @param forceFetch - force fetching stocks from ovtlyr api (bypasses cache)
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
        // Fetch SPY ONCE before parallel processing to avoid redundant API calls
        val spy = getSpy()
        logger.info("Fetched SPY reference data once for batch of ${sortedSymbols.size} stocks")

        // Force fetch all symbols from API
        return@runBlocking sortedSymbols
          .map { symbol ->
            async(limited) {
              runCatching {
                fetchStock(symbol, spy)
              }.onFailure { e ->
                logger.warn("Failed to force fetch symbol={}: {}", symbol, e.message, e)
              }.getOrNull()
            }
          }.awaitAll()
          .filterNotNull()
      }

      // Get existing stocks from repository with quotes (single query for all symbols)
      return@runBlocking stockRepository.findBySymbols(sortedSymbols)
    }

  /**
   * Fetches stock data from primary provider enriched with Ovtlyr indicators.
   * Uses StockFactory to create the stock entity with all enrichments.
   *
   * Data Pipeline:
   * - StockProvider: OHLCV (adjusted) + volume
   * - TechnicalIndicatorProvider: ATR, ADX
   * - FundamentalDataProvider: Earnings, sector information
   * - Internal calculation: EMAs, Donchian channels, trend
   * - Ovtlyr enrichment: Buy/sell signals, heatmaps, sector sentiment
   *
   * Rate limiting is handled transparently by provider decorators.
   *
   * @param symbol - the stock symbol to fetch
   * @param spy - SPY reference data for enriching stock information
   * @return the fetched and saved stock, or null if fetch or save failed
   */
  private suspend fun fetchStock(
    symbol: String,
    spy: OvtlyrStockInformation,
  ): StockDomain? {
    val logger = LoggerFactory.getLogger("StockService")

    return runCatching {
      // Step 1: Delete existing stock if it exists (cascade delete will remove quotes)
      stockRepository.findBySymbol(symbol)?.let {
        logger.info("Deleting existing stock $symbol before refreshing")
        stockRepository.delete(symbol)
      }

      // Step 2: Fetch adjusted daily data from StockProvider (PRIMARY data source - REQUIRED)
      logger.info("Fetching adjusted daily data from StockProvider for $symbol")
      val stockQuotes = stockProvider.getDailyAdjustedTimeSeries(symbol)
      if (stockQuotes == null) {
        logger.error("FAILED: Could not fetch data from StockProvider for $symbol")
        return null
      }
      logger.info("Fetched ${stockQuotes.size} quotes from StockProvider for $symbol")

      // Step 3: Fetch ATR data (REQUIRED for strategies)
      logger.info("Fetching ATR data for $symbol")
      val atrMap = technicalIndicatorProvider.getATR(symbol)
      if (atrMap == null) {
        logger.error("FAILED: Could not fetch ATR data for $symbol")
        return null
      }
      logger.info("Fetched ${atrMap.size} ATR values for $symbol")

      // Step 3.1: Fetch ADX data (optional, for trend strength conditions)
      logger.info("Fetching ADX data for $symbol")
      val adxMap = technicalIndicatorProvider.getADX(symbol)
      if (adxMap != null) {
        logger.info("Fetched ${adxMap.size} ADX values for $symbol")
      } else {
        logger.warn("Could not fetch ADX data for $symbol - ADX conditions will not work")
      }

      // Step 3.5: Fetch earnings history (for exit-before-earnings strategies)
      logger.info("Fetching earnings history for $symbol")
      val earnings = fundamentalDataProvider.getEarnings(symbol) ?: emptyList()
      logger.info("Fetched ${earnings.size} quarterly earnings for $symbol")

      // Step 4: Get sector symbol (needed for sector breadth lookup)
      logger.info("Fetching sector symbol for $symbol")
      val sectorSymbol = fundamentalDataProvider.getSectorSymbol(symbol)
      if (sectorSymbol == null) {
        logger.warn("Could not determine sector for $symbol - sector breadth will not be available")
      }

      // Step 5: Fetch breadth data for context
      val marketBreadth = breadthRepository.findBySymbol(BreadthSymbol.Market().toIdentifier())
      val sectorBreadth =
        sectorSymbol?.let {
          breadthRepository.findBySymbol(BreadthSymbol.Sector(it).toIdentifier())
        }

      // Step 6: Create enriched quotes using StockFactory
      // This will: calculate EMAs, add ATR, add ADX, calculate Donchian, determine trend, enrich with Ovtlyr
      logger.info("Creating enriched quotes for $symbol")
      val enrichedQuotes =
        stockFactory.enrichQuotes(
          symbol = symbol,
          stockQuotes = stockQuotes,
          atrMap = atrMap,
          adxMap = adxMap,
          marketBreadth = marketBreadth,
          sectorBreadth = sectorBreadth,
          spy = spy,
        )

      if (enrichedQuotes == null) {
        logger.error("FAILED: Could not create enriched quotes for $symbol (Ovtlyr enrichment failed)")
        return null
      }

      // Step 7: Calculate order blocks with multiple sensitivities (like Sonar Lab)
      logger.info("Calculating order blocks for $symbol")

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
      logger.info(
        "Found ${orderBlocksHigh.size} order blocks (HIGH sensitivity) + ${orderBlocksLow.size} (LOW sensitivity) = ${orderBlocks.size} total",
      )

      // Step 8: Create Stock entity
      logger.info("Creating Stock entity for $symbol")
      val stock =
        stockFactory.createStock(
          symbol = symbol,
          sectorSymbol = sectorSymbol?.name,
          enrichedQuotes = enrichedQuotes,
          orderBlocks = orderBlocks,
          earnings = earnings,
        )

      // Step 9: Save and return
      logger.info("Saving stock $symbol to database")
      stockRepository.save(stock)
    }.onFailure { action -> Companion.logger.error("Could not fetch stock $symbol", action) }
      .getOrNull()
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
