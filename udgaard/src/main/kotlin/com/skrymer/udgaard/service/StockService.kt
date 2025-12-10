package com.skrymer.udgaard.service

import com.skrymer.udgaard.factory.StockFactory
import com.skrymer.udgaard.integration.FundamentalDataProvider
import com.skrymer.udgaard.integration.StockProvider
import com.skrymer.udgaard.integration.TechnicalIndicatorProvider
import com.skrymer.udgaard.integration.ovtlyr.OvtlyrClient
import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockInformation
import com.skrymer.udgaard.model.BreadthSymbol
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.repository.BreadthRepository
import com.skrymer.udgaard.repository.StockRepository
import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import jakarta.persistence.EntityManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
open class StockService(
  val stockRepository: StockRepository,
  val ovtlyrClient: OvtlyrClient,
  val breadthRepository: BreadthRepository,
  val orderBlockCalculator: OrderBlockCalculator,
  val stockProvider: StockProvider,
  val technicalIndicatorProvider: TechnicalIndicatorProvider,
  val fundamentalDataProvider: FundamentalDataProvider,
  val stockFactory: StockFactory,
  val entityManager: EntityManager,
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
  open fun getStock(
    symbol: String,
    forceFetch: Boolean = false,
  ): Stock? {
    logger.info("Getting stock $symbol with forceFetch=$forceFetch")

    val stock =
      if (forceFetch) {
        fetchStock(symbol, getSpy())
      } else {
        stockRepository
          .findById(symbol)
          .orElseGet {
            fetchStock(symbol, getSpy())
          }?.also {
            // Force loading of orderBlocks and earnings to ensure they're in cache
            it.orderBlocks.size
            it.earnings.size
          }
      }

    // Detach the entity to prevent holding connections
    stock?.let { entityManager.detach(it) }

    return stock
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
  @Transactional(readOnly = true)
  open fun getAllStocks(): List<Stock> {
    // Query 1: Load stocks with quotes
    val stocks = stockRepository.findAllWithQuotes()

    if (stocks.isEmpty()) {
      return emptyList()
    }

    // Query 2: Fetch order blocks for all stocks
    stockRepository.fetchOrderBlocks(stocks)

    // Query 3: Fetch earnings for all stocks
    stockRepository.fetchEarnings(stocks)

    // CRITICAL: Detach all entities from the Hibernate session
    // This closes the transaction and releases the database connection
    // before the entities are used in long-running operations (backtesting)
    entityManager.clear() // Detaches ALL entities in the persistence context

    return stocks
  }

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
  ): List<Stock> =
    runBlocking {
      // Sort symbols to ensure consistent cache keys (since symbols may come from a Set with no guaranteed order)
      val sortedSymbols = symbols.sorted()
      val logger = LoggerFactory.getLogger("StockFetcher")
      val limited = Dispatchers.IO.limitedParallelism(10)

      if (forceFetch) {
        // Force fetch all symbols from API
        return@runBlocking sortedSymbols
          .map { symbol ->
            async(limited) {
              runCatching {
                fetchStock(symbol, getSpy())
              }.onFailure { e ->
                logger.warn("Failed to force fetch symbol={}: {}", symbol, e.message, e)
              }.getOrNull()
            }
          }.awaitAll()
          .filterNotNull()
      }

      // Get existing stocks from repository with quotes (single query for all symbols)
      return@runBlocking stockRepository.findAllBySymbolIn(sortedSymbols)
    }

  /**
   * Fetches stock data from AlphaVantage (primary) enriched with Ovtlyr indicators.
   * Uses StockFactory to create the stock entity with all enrichments.
   *
   * NEW ARCHITECTURE:
   * - AlphaVantage provides: OHLCV (adjusted) + volume + ATR
   * - We calculate: EMAs, Donchian channels, trend
   * - Ovtlyr provides: Buy/sell signals, heatmaps, sector sentiment
   *
   * @param symbol - the stock symbol to fetch
   * @param spy - SPY reference data for enriching stock information
   * @return the fetched and saved stock, or null if fetch or save failed
   */
  private fun fetchStock(
    symbol: String,
    spy: OvtlyrStockInformation,
  ): Stock? {
    val logger = LoggerFactory.getLogger("StockService")

    return runCatching {
      // Step 1: Delete existing stock if it exists (cascade delete will remove quotes)
      stockRepository.findById(symbol).ifPresent { existingStock ->
        logger.info("Deleting existing stock $symbol before refreshing")
        stockRepository.delete(existingStock)
        stockRepository.flush() // Ensure delete is committed before insert
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

      // Step 4: Get sector symbol from AlphaVantage (needed for sector breadth lookup)
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
          sensitivityLevel = com.skrymer.udgaard.model.OrderBlockSensitivity.HIGH,
        )

      // Calculate with LOW sensitivity (50% - fewer, stronger blocks)
      val orderBlocksLow =
        orderBlockCalculator.calculateOrderBlocks(
          quotes = enrichedQuotes,
          sensitivity = 50.0,
          sensitivityLevel = com.skrymer.udgaard.model.OrderBlockSensitivity.LOW,
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
   * Recalculate order blocks for a stock without re-fetching data.
   *
   * This is much faster than refresh=true as it:
   * 1. Loads existing quotes from database
   * 2. Recalculates order blocks with current algorithm
   * 3. Updates the stock with new order blocks
   *
   * Use this when you've updated the order block algorithm or want to test
   * different sensitivity values without waiting for fresh data fetch.
   *
   * @param symbol Stock symbol to recalculate
   * @return Updated stock with new order blocks, or null if not found
   */
  @CacheEvict(value = ["stocks"], key = "#symbol")
  open fun recalculateOrderBlocks(symbol: String): Stock? {
    logger.info("Recalculating order blocks for $symbol from existing data")

    // Load stock with quotes from database
    val stock = stockRepository.findById(symbol).orElse(null)
    if (stock == null) {
      logger.error("Stock not found: $symbol")
      return null
    }

    if (stock.quotes.isEmpty()) {
      logger.warn("No quotes found for $symbol, cannot calculate order blocks")
      return stock
    }

    // Recalculate order blocks with both sensitivities
    val orderBlocksHigh =
      orderBlockCalculator.calculateOrderBlocks(
        quotes = stock.quotes.sortedBy { it.date },
        sensitivity = 28.0,
        sensitivityLevel = com.skrymer.udgaard.model.OrderBlockSensitivity.HIGH,
      )

    val orderBlocksLow =
      orderBlockCalculator.calculateOrderBlocks(
        quotes = stock.quotes.sortedBy { it.date },
        sensitivity = 50.0,
        sensitivityLevel = com.skrymer.udgaard.model.OrderBlockSensitivity.LOW,
      )

    // Create new order blocks list
    val newOrderBlocks = (orderBlocksHigh + orderBlocksLow).toMutableList()

    // Explicitly clear existing order blocks (orphanRemoval = true will delete from DB)
    stock.orderBlocks.clear()
    stockRepository.flush() // Force orphan removal to execute before adding new blocks

    // Add new order blocks and set stock reference
    stock.orderBlocks.addAll(newOrderBlocks)
    stock.orderBlocks.forEach { it.stock = stock }

    logger.info(
      "Recalculated ${newOrderBlocks.size} order blocks for $symbol (${orderBlocksHigh.size} HIGH + ${orderBlocksLow.size} LOW)",
    )

    // Save and return
    return stockRepository.save(stock)
  }

  /**
   * Recalculate order blocks for all stocks in the database.
   *
   * WARNING: This can take a while with many stocks.
   * Processes stocks sequentially to avoid overwhelming the database.
   *
   * @return Map with summary: updatedCount, failedCount, totalBlocks
   */
  @CacheEvict(value = ["stocks"], allEntries = true)
  open fun recalculateAllOrderBlocks(): Map<String, Any> {
    logger.info("Recalculating order blocks for ALL stocks in database")

    val allStocks = stockRepository.findAll()
    var updatedCount = 0
    var failedCount = 0
    var totalBlocks = 0

    allStocks.forEach { stock ->
      try {
        val symbol = stock.symbol ?: "UNKNOWN"
        logger.info("Recalculating order blocks for $symbol (${updatedCount + 1}/${allStocks.size})")

        if (stock.quotes.isEmpty()) {
          logger.warn("Skipping $symbol: no quotes")
          failedCount++
          return@forEach
        }

        // Recalculate with both sensitivities
        val orderBlocksHigh =
          orderBlockCalculator.calculateOrderBlocks(
            quotes = stock.quotes.sortedBy { it.date },
            sensitivity = 28.0,
            sensitivityLevel = com.skrymer.udgaard.model.OrderBlockSensitivity.HIGH,
          )

        val orderBlocksLow =
          orderBlockCalculator.calculateOrderBlocks(
            quotes = stock.quotes.sortedBy { it.date },
            sensitivity = 50.0,
            sensitivityLevel = com.skrymer.udgaard.model.OrderBlockSensitivity.LOW,
          )

        // Create new order blocks list
        val newOrderBlocks = (orderBlocksHigh + orderBlocksLow).toMutableList()

        // Explicitly clear existing order blocks (orphanRemoval = true will delete from DB)
        stock.orderBlocks.clear()
        stockRepository.flush() // Force orphan removal to execute before adding new blocks

        // Add new order blocks and set stock reference
        stock.orderBlocks.addAll(newOrderBlocks)
        stock.orderBlocks.forEach { it.stock = stock }

        stockRepository.save(stock)

        updatedCount++
        totalBlocks += newOrderBlocks.size
        logger.info("Updated $symbol: ${newOrderBlocks.size} blocks")
      } catch (e: Exception) {
        logger.error("Failed to recalculate order blocks for ${stock.symbol}: ${e.message}", e)
        failedCount++
      }
    }

    logger.info("Recalculation complete: $updatedCount updated, $failedCount failed, $totalBlocks total blocks")

    return mapOf(
      "updatedCount" to updatedCount,
      "failedCount" to failedCount,
      "totalBlocks" to totalBlocks,
      "totalStocks" to allStocks.size,
    )
  }

  /**
   * Get market breadth data for use in backtesting.
   * @return Market breadth entity, or null if not available
   */
  @Transactional(readOnly = true)
  open fun getMarketBreadth(): com.skrymer.udgaard.model.Breadth? {
    val breadth = breadthRepository.findBySymbol(com.skrymer.udgaard.model.BreadthSymbol.Market().toIdentifier())

    // Detach the entity to prevent holding connections
    breadth?.let {
      // Force load quotes collection before detaching
      it.quotes.size
      entityManager.detach(it)
    }

    return breadth
  }

  private fun getSpy(): OvtlyrStockInformation {
    val spy: OvtlyrStockInformation? = ovtlyrClient.getStockInformation("SPY")
    checkNotNull(spy) { "Failed to fetch SPY reference data" }
    return spy
  }
}
