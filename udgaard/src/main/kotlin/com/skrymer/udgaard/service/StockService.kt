package com.skrymer.udgaard.service

import com.skrymer.udgaard.factory.StockFactory
import com.skrymer.udgaard.integration.ovtlyr.OvtlyrClient
import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockInformation
import com.skrymer.udgaard.model.BreadthSymbol
import com.skrymer.udgaard.model.SectorSymbol
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.repository.BreadthRepository
import com.skrymer.udgaard.repository.StockRepository
import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import kotlin.math.log


@Service
open class StockService(
  val stockRepository: StockRepository,
  val ovtlyrClient: OvtlyrClient,
  val breadthRepository: BreadthRepository,
  val orderBlockCalculator: OrderBlockCalculator,
  val alphaVantageClient: com.skrymer.udgaard.integration.alphavantage.AlphaVantageClient,
  val stockFactory: StockFactory
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(StockService::class.java)
    }

  /**
   * Loads the stock from DB if exists, else load it from Ovtlyr and save it.
   * @param symbol - the [symbol] of the stock to get
   * @param forceFetch - force fetch the stock from the ovtlyr API
   */
  @Cacheable(value = ["stocks"], key = "#symbol", condition = "!#forceFetch")
  @CacheEvict(value = ["stocks"], key = "#symbol", condition = "#forceFetch")
  open fun getStock(symbol: String, forceFetch: Boolean = false): Stock? {
    logger.info("Getting stock $symbol with forceFetch=$forceFetch")
    if(forceFetch){
      val spy: OvtlyrStockInformation? = ovtlyrClient.getStockInformation("SPY")
      checkNotNull(spy)
      return fetchStock(symbol, spy)
    }

    return stockRepository.findById(symbol).orElseGet {
      val spy: OvtlyrStockInformation? = ovtlyrClient.getStockInformation("SPY")
      checkNotNull(spy)
      fetchStock(symbol, spy)
    }
  }

  /**
   * Loads the stocks by symbol from DB if exists, else load it from Ovtlyr and save it.
   *
   * @param symbols - the stocks to load
   * @param forceFetch - force fetching stocks from ovtlyr api
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  suspend fun getStocks(symbols: List<String>, forceFetch: Boolean = false): List<Stock> = supervisorScope {
    val logger = LoggerFactory.getLogger("StockFetcher")
    val limited = Dispatchers.IO.limitedParallelism(10)

    val spy: OvtlyrStockInformation? = ovtlyrClient.getStockInformation("SPY")
    checkNotNull(spy)

    symbols.map { symbol ->
      async(limited) {
        runCatching {
          if (forceFetch) {
            fetchStock(symbol, spy)
          } else {
            stockRepository.findById(symbol).orElseGet { fetchStock(symbol, spy) }
          }
        }.onFailure { e ->
          logger.warn("Failed to fetch symbol={}: {}", symbol, e.message, e)
        }.getOrNull()
      }
    }
    .awaitAll()
    .filterNotNull()
  }

  /**
   * @return all stocks currently stored in DB
   */
  @Cacheable(value = ["stocks"], key = "'allStocks'")
  open fun getAllStocks(): List<Stock>{
    return stockRepository.findAll()
  }

  /**
   * Get stocks by a list of symbols (efficient repository query)
   * Returns only stocks that exist in the database
   *
   * @param symbols - list of stock symbols to fetch
   * @param forceFetch - force fetching stocks from ovtlyr api (bypasses cache)
   * @return list of stocks matching the provided symbols (only those that exist in DB)
   */
  @Cacheable(value = ["stocks"], key = "'bySymbols:' + #symbols.toString()", unless = "#forceFetch")
  @OptIn(ExperimentalCoroutinesApi::class)
  open fun getStocksBySymbols(symbols: List<String>, forceFetch: Boolean = false): List<Stock> = runBlocking {
    // Sort symbols to ensure consistent cache keys (since symbols may come from a Set with no guaranteed order)
    val sortedSymbols = symbols.sorted()

    val logger = LoggerFactory.getLogger("StockFetcher")
    val limited = Dispatchers.IO.limitedParallelism(10)

    if (forceFetch) {
      // Force fetch all symbols from API
      val spy: OvtlyrStockInformation? = ovtlyrClient.getStockInformation("SPY")
      checkNotNull(spy) { "Failed to fetch SPY reference data" }

      return@runBlocking sortedSymbols.map { symbol ->
        async(limited) {
          runCatching {
            fetchStock(symbol, spy)
          }.onFailure { e ->
            logger.warn("Failed to force fetch symbol={}: {}", symbol, e.message, e)
          }.getOrNull()
        }
      }
      .awaitAll()
      .filterNotNull()
    }

    // Get existing stocks from repository
    val existingStocks = stockRepository.findBySymbolIn(sortedSymbols)

    return@runBlocking existingStocks
  }

  /**
   * Fetches stock data from Ovtlyr API and saves it to the database.
   * Uses StockFactory to create the stock entity with all enrichments.
   *
   * @param symbol - the stock symbol to fetch
   * @param spy - SPY reference data for enriching stock information
   * @return the fetched and saved stock, or null if fetch or save failed
   */
  private fun fetchStock(symbol: String, spy: OvtlyrStockInformation): Stock? {
    val logger = LoggerFactory.getLogger("StockService")

    // Step 1: Fetch stock information from Ovtlyr
    val stockInformation = ovtlyrClient.getStockInformation(symbol) ?: return null

    return runCatching {
      // Step 2: Fetch breadth data for context
      val marketBreadth = breadthRepository.findByIdOrNull(BreadthSymbol.Market().toIdentifier())
      val sectorSymbol = SectorSymbol.fromString(stockInformation.sectorSymbol)
      val sectorBreadth = sectorSymbol?.let {
        breadthRepository.findByIdOrNull(BreadthSymbol.Sector(it).toIdentifier())
      }

      // Step 3: Enrich with AlphaVantage volume data
      logger.info("Starting volume enrichment for $symbol")
      val alphaQuotes = alphaVantageClient.getDailyTimeSeries(symbol)
      if (alphaQuotes == null) {
        logger.warn("Could not fetch volume data from Alpha Vantage for $symbol, using default values")
      }

      // Step 3b: Enrich with AlphaVantage ATR data
      logger.info("Starting ATR enrichment for $symbol")
      val alphaATR = alphaVantageClient.getATR(symbol)
      if (alphaATR == null) {
        logger.warn("Could not fetch ATR data from Alpha Vantage for $symbol, using calculated ATR")
      } else {
        logger.info("Fetched ${alphaATR.size} ATR values from Alpha Vantage for $symbol")
      }

      // Step 4: Create quotes for order block calculation
      val quotes = stockFactory.createQuotes(
        stockInformation = stockInformation,
        marketBreadth = marketBreadth,
        sectorBreadth = sectorBreadth,
        spy = spy,
        alphaQuotes = alphaQuotes,
        alphaATR = alphaATR
      )

      // Step 5: Calculate order blocks based on quotes
      val calculatedOrderBlocks = orderBlockCalculator.calculateOrderBlocks(
        quotes = quotes,
        sensitivity = 28.0
      )

      // Step 6: Use factory to create complete stock with all enrichments
      val enrichedStock = stockFactory.createStock(
        stockInformation = stockInformation,
        marketBreadth = marketBreadth,
        sectorBreadth = sectorBreadth,
        spy = spy,
        alphaQuotes = alphaQuotes,
        calculatedOrderBlocks = calculatedOrderBlocks,
        alphaATR = alphaATR
      )

      // Step 7: Save and return
      stockRepository.save(enrichedStock)
    }.getOrNull()
  }
}
