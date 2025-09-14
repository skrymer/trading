package com.skrymer.udgaard.integration.ovtlyr

import com.skrymer.udgaard.model.MarketSymbol
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockSymbol
import com.skrymer.udgaard.service.MarketBreadthService
import com.skrymer.udgaard.service.StockService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Loads data from ovtlyr into mongo.
 */
@Component
class DataLoader(
  private val ovtlyrClient: OvtlyrClient,
  private val marketBreadthService: MarketBreadthService,
  private val stockService: StockService
) {

  fun loadData() {
    runBlocking {  loadMarkBreadthForAllSectors() }
    runBlocking { loadTopStocks(true) }
  }

  fun loadStockByMarket(marketSymbol: MarketSymbol, forceFetch: Boolean = false): List<Stock> {
    val stockSymbols = StockSymbol.entries.filter { it.market == marketSymbol }.map { it.name }
    return runBlocking { stockService.getStocks(stockSymbols, forceFetch)}
  }

  suspend fun loadTopStocks(forceFetch: Boolean = false): List<Stock> {
    return stockService.getStocks(StockSymbol.entries.map { it.symbol }, forceFetch)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private suspend fun loadMarkBreadthForAllSectors() = supervisorScope {
    val logger = LoggerFactory.getLogger("StockFetcher")
    val limited = Dispatchers.IO.limitedParallelism(10)

    MarketSymbol.entries
      .filter { it != MarketSymbol.UNK }
      .forEach {
        async(limited) {
          runCatching { loadMarketBreadth(it) }
            .onFailure { e ->logger.warn("Failed to fetch market={}: {}", it, e.message, e) }
        }
      }
  }

  private fun loadMarketBreadth(symbol: MarketSymbol) {
    marketBreadthService.getMarketBreadth(symbol, true)
  }
}
