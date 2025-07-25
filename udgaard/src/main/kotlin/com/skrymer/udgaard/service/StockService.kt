package com.skrymer.udgaard.service

import com.skrymer.udgaard.integration.ovtlyr.OvtlyrClient
import com.skrymer.udgaard.integration.ovtlyr.dto.OvtlyrStockInformation
import com.skrymer.udgaard.model.BacktestReport
import com.skrymer.udgaard.model.MarketBreadth
import com.skrymer.udgaard.model.MarketSymbol
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.Trade
import com.skrymer.udgaard.model.strategy.EntryStrategy
import com.skrymer.udgaard.model.strategy.ExitStrategy
import com.skrymer.udgaard.model.valueOf
import com.skrymer.udgaard.repository.MarketBreadthRepository
import com.skrymer.udgaard.repository.StockRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.*

@Service
class StockService(
  val stockRepository: StockRepository,
  val ovtlyrClient: OvtlyrClient,
  val marketBreadthRepository: MarketBreadthRepository
) {

  /**
   * Loads the stock from DB if exists, else load it from Ovtlyr and save it.
   * @param symbol - the [symbol] of the stock to get
   * @param forceFetch - force fetch the stock from the ovtlyr API
   */
  fun getStock(symbol: String, forceFetch: Boolean = false): Stock? {
    if(forceFetch){
      val spy: OvtlyrStockInformation? = ovtlyrClient.getStockInformation("SPY")
      return fetchStock(symbol, spy)
    }

    return stockRepository.findById(symbol).orElseGet {
      val spy: OvtlyrStockInformation? = ovtlyrClient.getStockInformation("SPY")
      fetchStock(symbol, spy)
    }
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

  /**
   * Run backtest for the given [entryStrategy] and [exitStrategy] using the [stocks] given
   * @param entryStrategy - the entry strategy
   * @param exitStrategy - the exit strategy
   * @param stocks - the stocks to generate the report for
   * @return a backtest report
   *
   */
  fun backtest(
    entryStrategy: EntryStrategy,
    exitStrategy: ExitStrategy,
    stocks: List<Stock>,
    after: LocalDate,
    before: LocalDate
  ): BacktestReport {
    val winningTrades = ArrayList<Trade>()
    val losingTrades = ArrayList<Trade>()

    stocks.forEach { stock ->
      val quotesMatchingEntryStrategy = stock.getQuotesMatchingEntryStrategy(entryStrategy, after, before)

      quotesMatchingEntryStrategy.forEach { entryQuote ->
        if((winningTrades + losingTrades).find { it.containsQuote(entryQuote) } == null){
          val exitReport = stock.testExitStrategy(entryQuote, exitStrategy)
          val profit = exitReport.exitPrice - entryQuote.closePrice
          val trade = Trade(stock, entryQuote, exitReport.quotes, exitReport.exitReason, profit)

          if (profit > 0) {
            winningTrades.add(trade)
          } else {
            losingTrades.add(trade)
          }
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
