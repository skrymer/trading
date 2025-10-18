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
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope


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
  fun getAllStocks(): List<Stock>{
    return stockRepository.findAll()
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
    val trades = ArrayList<Trade>()

    stocks.forEach { stock ->
      val quotesMatchingEntryStrategy = stock.getQuotesMatchingEntryStrategy(entryStrategy, after, before)

      quotesMatchingEntryStrategy.forEach { entryQuote ->
        if(trades.find { it.containsQuote(entryQuote) } == null){
          val exitReport = stock.testExitStrategy(entryQuote, exitStrategy)

          if(exitReport.exitReason.isNotBlank()) {
            val profit = exitReport.exitPrice - entryQuote.closePrice
            val trade = Trade(stock.symbol!!, entryQuote, exitReport.quotes, exitReport.exitReason, profit, entryQuote?.date, stock.sectorSymbol ?: "")
            trades.add(trade)
          }
        }
      }
    }
    val (winningTrades, losingTrades) = trades.partition { it.profit > 0 }
    return BacktestReport(winningTrades, losingTrades)
  }

  private fun fetchStock(symbol: String, spy: OvtlyrStockInformation): Stock? {
    val stockInformation = ovtlyrClient.getStockInformation(symbol)
    val stockPerformance = ovtlyrClient.getStockPerformance(symbol)

    if(stockInformation == null) {
      return null
    }

    return runCatching {
      val marketBreadth = marketBreadthRepository.findByIdOrNull(MarketSymbol.FULLSTOCK)
      val marketSymbol = MarketSymbol.valueOf(stockInformation.sectorSymbol)
      val sectorMarketBreadth = marketBreadthRepository.findByIdOrNull(marketSymbol)
      return stockRepository.save(stockInformation.toModel(marketBreadth, sectorMarketBreadth, stockPerformance,spy))
    }.getOrNull()
  }
}
