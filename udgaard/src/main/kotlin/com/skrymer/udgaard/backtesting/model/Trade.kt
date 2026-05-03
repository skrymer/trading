package com.skrymer.udgaard.backtesting.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.skrymer.udgaard.data.model.StockQuote
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Represents a trade with an entry, the quotes while the trade was on and an exit.
 * @param stockSymbol - the stock symbol the trade was for.
 * @param underlyingSymbol - optional underlying symbol used for strategy evaluation (null = same as stockSymbol).
 * @param entryQuote - the stock quote on the day of entry.
 * @param quotes - the stock quotes included in the trade, excluding entry and exit.
 * @param exitReason - the reason for exiting the trade.
 * @param startDate - the start date of the trade.
 * @param sector - the sector of the stock.
 */
class Trade(
  var stockSymbol: String,
  var underlyingSymbol: String? = null,
  var entryQuote: StockQuote,
  var quotes: List<StockQuote>,
  var exitReason: String,
  var profit: Double = 0.0,
  var startDate: LocalDate?,
  var sector: String,
) {
  // The four runtime-only fields below are populated by the engine and consumed within
  // the same request. `@Transient` blocks Java serialization; `@get:JsonIgnore` blocks
  // Jackson — both needed because Kotlin's `@Transient` does not affect Jackson's
  // property discovery, and BacktestReport is persisted as JSONB.

  @Transient @get:JsonIgnore
  var marketConditionAtEntry: MarketConditionSnapshot? = null

  @Transient @get:JsonIgnore
  var excursionMetrics: ExcursionMetrics? = null

  @Transient @get:JsonIgnore
  var missedReason: String? = null

  @Transient @get:JsonIgnore
  var entryContext: EntryDecisionContext? = null

  /**
   * Calculate the profit percentage of this trade: (profit/entry close price) * 100
   * @return
   */
  val profitPercentage: Double
    get() = (profit / entryQuote.closePrice) * 100.0

  /**
   * The number of days the trade lasted
   */
  val tradingDays: Long
    get() {
      if (quotes.isEmpty()) return 0L
      return ChronoUnit.DAYS.between(entryQuote.date, quotes.last().date)
    }

  fun containsQuote(stockQuote: StockQuote) = quotes.contains(stockQuote)

  override fun toString(): String = "Start date $startDate"

  companion object {
    const val MISSED_INSUFFICIENT_CAPITAL = "insufficient capital"

    /**
     * Tags trades that were force-closed because the trading symbol delisted
     * before the entry strategy's natural exit. Without this, the trade would
     * silently disappear from `BacktestService.createTradeFromEntry()` —
     * causing pre-2010 backtests to under-report real losses from late-cycle
     * delistings.
     */
    const val EXIT_REASON_DELISTED = "delisted"
  }
}

/**
 * Snapshot of budget state at the moment a trade selection decision was made.
 * Enables post-hoc validation of capital-aware selection logic.
 */
data class EntryDecisionContext(
  val cashAtDecision: Double,
  val openNotionalAtDecision: Double,
  val openPositionCount: Int,
  val cohortSize: Int,
  val rankInCohort: Int,
  val availableSlots: Int,
  val sharesReserved: Int,
) {
  /** True if a slot was available but the sizer/leverage returned 0 shares (vs slot-limit skip). */
  fun isCapitalSkip(): Boolean = availableSlots > 0 && sharesReserved == 0
}
