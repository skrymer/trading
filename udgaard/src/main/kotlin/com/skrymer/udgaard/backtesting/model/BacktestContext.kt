package com.skrymer.udgaard.backtesting.model

import com.skrymer.udgaard.data.model.MarketBreadthDaily
import com.skrymer.udgaard.data.model.SectorBreadthDaily
import com.skrymer.udgaard.data.model.StockQuote
import java.time.LocalDate

/**
 * Holds pre-loaded breadth and market data for use during backtesting.
 * Passed through to strategy conditions that need sector/market breadth context
 * or SPY price data, avoiding the need to store redundant fields on every StockQuote.
 */
data class BacktestContext(
  val sectorBreadthMap: Map<String, Map<LocalDate, SectorBreadthDaily>>,
  val marketBreadthMap: Map<LocalDate, MarketBreadthDaily>,
  val spyQuoteMap: Map<LocalDate, StockQuote> = emptyMap(),
) {
  fun getSectorBreadth(sectorSymbol: String?, date: LocalDate): SectorBreadthDaily? =
    sectorSymbol?.let { sectorBreadthMap[it]?.get(date) }

  fun getMarketBreadth(date: LocalDate): MarketBreadthDaily? =
    marketBreadthMap[date]

  fun getSpyQuote(date: LocalDate): StockQuote? =
    spyQuoteMap[date]

  companion object {
    val EMPTY = BacktestContext(emptyMap(), emptyMap())
  }
}
