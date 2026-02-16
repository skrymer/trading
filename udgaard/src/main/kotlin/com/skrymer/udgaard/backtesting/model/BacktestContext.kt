package com.skrymer.udgaard.backtesting.model

import com.skrymer.udgaard.data.model.MarketBreadthDaily
import com.skrymer.udgaard.data.model.SectorBreadthDaily
import java.time.LocalDate

/**
 * Holds pre-loaded breadth data for use during backtesting.
 * Passed through to strategy conditions that need sector/market breadth context,
 * avoiding the need to store redundant breadth fields on every StockQuote.
 */
data class BacktestContext(
  val sectorBreadthMap: Map<String, Map<LocalDate, SectorBreadthDaily>>,
  val marketBreadthMap: Map<LocalDate, MarketBreadthDaily>,
) {
  fun getSectorBreadth(sectorSymbol: String?, date: LocalDate): SectorBreadthDaily? =
    sectorSymbol?.let { sectorBreadthMap[it]?.get(date) }

  fun getMarketBreadth(date: LocalDate): MarketBreadthDaily? =
    marketBreadthMap[date]

  companion object {
    val EMPTY = BacktestContext(emptyMap(), emptyMap())
  }
}
