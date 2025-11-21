package com.skrymer.udgaard.service

import com.skrymer.udgaard.integration.ovtlyr.OvtlyrClient
import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockInformation
import com.skrymer.udgaard.model.MarketBreadth
import com.skrymer.udgaard.model.MarketSymbol
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.valueOf
import com.skrymer.udgaard.repository.MarketBreadthRepository
import com.skrymer.udgaard.repository.StockRepository
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope


@Service
open class StockService(
  val stockRepository: StockRepository,
  val ovtlyrClient: OvtlyrClient,
  val marketBreadthRepository: MarketBreadthRepository,
  val orderBlockCalculator: OrderBlockCalculator,
  val alphaVantageClient: com.skrymer.udgaard.integration.alphavantage.AlphaVantageClient
) {

  /**
   * Loads the stock from DB if exists, else load it from Ovtlyr and save it.
   * @param symbol - the [symbol] of the stock to get
   * @param forceFetch - force fetch the stock from the ovtlyr API
   */
  @Cacheable(value = ["stocks"], key = "#symbol", unless = "#forceFetch")
  @org.springframework.cache.annotation.CacheEvict(value = ["stocks"], key = "#symbol", condition = "#forceFetch")
  open fun getStock(symbol: String, forceFetch: Boolean = false): Stock? {
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
   *
   * @param symbol - the stock symbol to fetch
   * @param spy - SPY reference data for enriching stock information
   * @return the fetched and saved stock, or null if fetch or save failed
   */
  private fun fetchStock(symbol: String, spy: OvtlyrStockInformation): Stock? {
    val logger = LoggerFactory.getLogger("StockService")
    val stockInformation = ovtlyrClient.getStockInformation(symbol) ?: return null

    return runCatching {
      val marketBreadth = marketBreadthRepository.findByIdOrNull(MarketSymbol.FULLSTOCK)
      val marketSymbol = MarketSymbol.valueOf(stockInformation.sectorSymbol)
      val sectorMarketBreadth = marketBreadthRepository.findByIdOrNull(marketSymbol)
      val stock = stockInformation.toModel(marketBreadth, sectorMarketBreadth, spy)

      // Enrich quotes with volume data from Alpha Vantage
      logger.info("Starting volume enrichment for $symbol (${stock.quotes.size} quotes to enrich)")
      val alphaQuotes = alphaVantageClient.getDailyTimeSeriesCompact(symbol)
      if (alphaQuotes != null) {
        logger.info("Enriching $symbol with volume data from Alpha Vantage (${alphaQuotes.size} Alpha quotes available)")
        logger.debug("Ovtlyr date range: ${stock.quotes.firstOrNull()?.date} to ${stock.quotes.lastOrNull()?.date}")
        logger.debug("Alpha Vantage date range: ${alphaQuotes.firstOrNull()?.date} to ${alphaQuotes.lastOrNull()?.date}")

        var matchedCount = 0
        var unmatchedCount = 0
        stock.quotes.forEach { quote ->
          val matchingAlphaQuote = alphaQuotes.find { it.date == quote.date }
          if (matchingAlphaQuote != null) {
            quote.volume = matchingAlphaQuote.volume
            matchedCount++
          } else {
            unmatchedCount++
          }
        }
        logger.info("Volume enrichment complete for $symbol: $matchedCount quotes matched, $unmatchedCount unmatched")

        // Log sample of enriched quotes
        val quotesWithVolume = stock.quotes.filter { it.volume > 0 }
        logger.info("Quotes with volume > 0: ${quotesWithVolume.size} out of ${stock.quotes.size}")
      } else {
        logger.warn("Could not fetch volume data from Alpha Vantage for $symbol, using default values")
      }

      // Calculate order blocks using ROC algorithm and add them to the stock
      val calculatedOrderBlocks = orderBlockCalculator.calculateOrderBlocks(
        quotes = stock.quotes,
        sensitivity = 28.0
      )

      // Combine Ovtlyr order blocks with calculated ones
      val allOrderBlocks = stock.orderBlocks + calculatedOrderBlocks
      val enrichedStock = Stock(
        symbol = stock.symbol,
        sectorSymbol = stock.sectorSymbol,
        quotes = stock.quotes,
        orderBlocks = allOrderBlocks,
        ovtlyrPerformance = stock.ovtlyrPerformance
      )

      return stockRepository.save(enrichedStock)
    }.getOrNull()
  }
}
