package com.skrymer.udgaard.integration.ovtlyr

import com.skrymer.udgaard.model.MarketSymbol
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockSymbol
import com.skrymer.udgaard.repository.MarketBreadthRepository
import com.skrymer.udgaard.service.StockService
import org.springframework.stereotype.Component

/**
 * Loads data from ovtlyr into mongo.
 */
@Component
class DataLoader(
  private val ovtlyrClient: OvtlyrClient,
  private val marketBreadthRepository: MarketBreadthRepository,
  private val stockService: StockService
) {

  fun loadData() {
    loadMarkBreadthForAllSectors()
//    loadTopStocks()
  }

  fun loadStockByMarket(marketSymbol: MarketSymbol, forceFetch: Boolean = false): List<Stock> {
    val stockSymbols = StockSymbol.entries.filter { it.market == marketSymbol }.map { it.name }
    return stockService.getStocks(stockSymbols, forceFetch)
  }

  fun loadTopStocks(): List<Stock> {
    return stockService.getStocks(StockSymbol.entries.map { it.name })
  }

  private fun loadMarkBreadthForAllSectors() =
    MarketSymbol.entries
      .filter { it != MarketSymbol.UNK }
      .forEach { loadMarketBreadth(it) }

  private fun loadMarketBreadth(symbol: MarketSymbol) {
    val response = try{
      ovtlyrClient.getMarketBreadth(symbol.name)
    } catch (e: Exception){
      throw CouldNotLoadMarketBreadth("Could not load market breadth for market ${symbol.description}", e)
    }

    if (response != null) {
      marketBreadthRepository.save(response.toModel())
      println(response)
    } else {
      throw CouldNotLoadMarketBreadth("Could not load market breadth for market ${symbol.description}")
    }
  }
}

class CouldNotLoadMarketBreadth(message: String, cause: Throwable? = null): RuntimeException(message, cause)
