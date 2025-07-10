package com.skrymer.udgaard.service

import com.skrymer.udgaard.integration.ovtlyr.OvtlyrClient
import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockInformation
import com.skrymer.udgaard.model.BacktestReport
import com.skrymer.udgaard.model.MarketBreadth
import com.skrymer.udgaard.model.MarketSymbol
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.Trade
import com.skrymer.udgaard.model.strategy.EntryStrategy
import com.skrymer.udgaard.model.strategy.ExitStrategy
import com.skrymer.udgaard.model.valueOf
import com.skrymer.udgaard.repository.MarketBreadthRepository
import com.skrymer.udgaard.repository.StockRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.util.*

@Service
class StockService(
  val stockRepository: StockRepository,
  val ovtlyrClient: OvtlyrClient,
  val marketBreadthRepository: MarketBreadthRepository
) {

  /**
   * Loads the stock from DB if exists, else load it from Ovtlyr and save it.
   */
  fun getStock(symbol: String): Stock {
    return stockRepository.findById(symbol).orElseGet(java.util.function.Supplier {
      val spy: OvtlyrStockInformation? = ovtlyrClient.getStockInformation("SPY")
      fetchStock(symbol, spy)
    })
  }

  /**
   * Loads the stocks by symbol from DB if exists, else load it from Ovtlyr and save it.
   *
   * @param symbols - the stocks to load
   * @param forceFetch - force fetching stocks from ovtlyr api
   */
  fun getStocks(symbols: List<String>, forceFetch: Boolean = false): List<Stock> {
    val spy: OvtlyrStockInformation? = ovtlyrClient.getStockInformation("SPY")

    return symbols.mapNotNull {
      if(forceFetch){
        return@mapNotNull fetchStock(it, spy)
      }

      stockRepository.findById(it).orElseGet(java.util.function.Supplier {
        fetchStock(it, spy)
      })
    }
  }

  fun backtest(entryStrategy: EntryStrategy, exitStrategy: ExitStrategy, stocks: List<Stock>): BacktestReport {
    val winningTrades = ArrayList<Trade>()
    val losingTrades = ArrayList<Trade>()

    stocks.forEach { stock ->
      val quotesMatchingEntryStrategy = stock.getQuotesMatchingEntryStrategy(entryStrategy)

      quotesMatchingEntryStrategy.forEach { entryQuote ->
        val quotesMatchingExitStrategy = stock.getQuotesMatchingExitStrategy(entryQuote, exitStrategy)
        val exitQuote: StockQuote? = if (quotesMatchingExitStrategy.second.isEmpty()) {
          // When next quote did not match the exit strategy
          stock.getNextQuote(entryQuote)
        } else {
          stock.getNextQuote(quotesMatchingExitStrategy.second.last())
        }
        val profit = (exitQuote?.closePrice ?: 0.0) - entryQuote.closePrice
        val trade = Trade(stock, entryQuote, exitQuote, quotesMatchingExitStrategy.second, quotesMatchingExitStrategy.first)
        if (profit > 0) {
          winningTrades.add(trade)
        } else {
          losingTrades.add(trade)
        }
      }
    }
    return BacktestReport(winningTrades, losingTrades)
  }

  private fun fetchStock(symbol: String, spy: OvtlyrStockInformation?): Stock? {
    val stockInformation = ovtlyrClient.getStockInformation(symbol)

    if(stockInformation == null) {
      return null
    }

    val marketBreadth = marketBreadthRepository.findByIdOrNull(MarketSymbol.FULLSTOCK)
    val sectorMarketBreadth: MarketBreadth? = marketBreadthRepository
      .findByIdOrNull(MarketSymbol.valueOf(stockInformation.sectorSymbol))

    return stockRepository.save(stockInformation.toModel(marketBreadth, sectorMarketBreadth, spy!!))
  }
}
