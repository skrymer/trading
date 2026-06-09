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
  // Sector-ETF price series keyed by sector symbol (XLK, XLF, ...) then by date. Supplies the sector
  // factor for multi-factor residual rankers, the way spyQuoteMap supplies the market factor.
  val sectorEtfQuoteMap: Map<String, Map<LocalDate, StockQuote>> = emptyMap(),
  // Pre-computed leadership-gap regime series (issue #83): per date, whether the equal-weight universe
  // is leading (deploy) or a thin tape is carrying the index (cash). Empty unless a strategy uses the gate.
  val leadershipRegimeMap: Map<LocalDate, LeadershipRegimeDaily> = emptyMap(),
  // Round-trip transaction cost in basis points (commission + slippage), netted into per-share
  // Trade.profit at trade close. Default 10 = net-by-default; 0 reproduces gross perfect-fill runs.
  val costBps: Double = 10.0,
  // Idle (uninvested) cash earns the historical short rate when on (default). ADR 0016.
  val creditIdleCash: Boolean = true,
  // SGOV expense haircut (percent) subtracted once from the gross treasury yield to get the net idle rate.
  val idleCashExpensePct: Double = DEFAULT_IDLE_CASH_EXPENSE_PCT,
) {
  fun getSectorBreadth(sectorSymbol: String?, date: LocalDate): SectorBreadthDaily? =
    sectorSymbol?.let { sectorBreadthMap[it]?.get(date) }

  fun getMarketBreadth(date: LocalDate): MarketBreadthDaily? =
    marketBreadthMap[date]

  fun getSpyQuote(date: LocalDate): StockQuote? =
    spyQuoteMap[date]

  /**
   * The leadership-gap gate for [date]: `true` = deploy regime, `false` = cash. A date with no
   * pre-computed read defaults to cash — a missing regime read cannot confirm a deploy regime.
   */
  fun getLeadershipRegimeOn(date: LocalDate): Boolean =
    leadershipRegimeMap[date]?.regimeOn ?: false

  companion object {
    /** SGOV's expense ratio (~0.10%), the standard idle-cash haircut. Subtracted once (F4, ADR 0016). */
    const val DEFAULT_IDLE_CASH_EXPENSE_PCT = 0.10
    val EMPTY = BacktestContext(emptyMap(), emptyMap())
  }
}
