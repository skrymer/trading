package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Gap and Crap — a momentum-failure exit. A gap up of `gapPercent` (open of bar D
 * vs prior close) sets a trigger line at bar D's low. If any of the next three
 * trading bars (D+1, D+2, D+3) closes below that low, fire the exit. Past D+3
 * the candidate ages out.
 *
 * Sliding-window: every bar is potentially a Day 2 candidate, evaluated against
 * its own predecessor. Pattern is independent of trade entry.
 *
 * @param gapPercent minimum gap-up size as a percentage of Day 1's close (default 5.0)
 */
@Component
class GapAndCrapExit(
  private val gapPercent: Double = DEFAULT_GAP_PERCENT,
) : ExitCondition {
  override fun shouldExit(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): Boolean {
    if (entryQuote == null) return false
    return findFailedGap(stock, quote) != null
  }

  override fun proximity(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): ExitProximity? {
    if (entryQuote == null) return null
    val candidate = findCandidateGap(stock, quote) ?: return null
    val triggered = quote.closePrice < candidate.low
    return ExitProximity(
      conditionType = TYPE,
      proximity = if (triggered) 1.0 else 0.0,
      detail = "gap on %s low=%.2f, today close=%.2f".format(candidate.date, candidate.low, quote.closePrice),
    )
  }

  override fun exitReason(): String =
    "Gap and Crap (close below the low of a $gapPercent% gap-up bar within $FAILURE_WINDOW_DAYS trading days)"

  override fun description(): String = "Gap and Crap ($gapPercent%)"

  override fun getMetadata(): ConditionMetadata =
    ConditionMetadata(
      type = TYPE,
      displayName = "Gap and Crap",
      description =
        "Exit when price closes below the low of a recent gap-up bar within the failure window. " +
          "Catches momentum-failure patterns where a strong gap up reverses within a few days.",
      parameters =
        listOf(
          ParameterMetadata(
            name = "gapPercent",
            displayName = "Gap %",
            type = "number",
            defaultValue = DEFAULT_GAP_PERCENT,
            min = 1.0,
            max = 20.0,
          ),
        ),
      category = "Signal",
    )

  // Walks back up to FAILURE_WINDOW_DAYS bars before `quote`, returns the first
  // qualifying gap-up bar whose low is above `quote.close` (i.e. the trigger fires).
  private fun findFailedGap(
    stock: Stock,
    quote: StockQuote,
  ): StockQuote? {
    val candidate = findCandidateGap(stock, quote) ?: return null
    return candidate.takeIf { quote.closePrice < it.low }
  }

  // Returns the most recent qualifying gap-up bar within FAILURE_WINDOW_DAYS,
  // ignoring whether the trigger has fired. Used by both shouldExit (which adds
  // the close-below-low check) and proximity (which surfaces the candidate even
  // when the trigger hasn't fired yet).
  private fun findCandidateGap(
    stock: Stock,
    quote: StockQuote,
  ): StockQuote? {
    val todayIdx = stock.quotes.indexOfFirst { it.date == quote.date }
    if (todayIdx <= 0) return null
    return (1..FAILURE_WINDOW_DAYS)
      .firstNotNullOfOrNull { offset -> qualifyingGapAt(stock, todayIdx - offset) }
  }

  private fun qualifyingGapAt(
    stock: Stock,
    day2Idx: Int,
  ): StockQuote? {
    if (day2Idx <= 0) return null
    val day2 = stock.quotes[day2Idx]
    val day1Close = stock.quotes[day2Idx - 1].closePrice
    if (day1Close <= 0.0) return null
    val gapPct = ((day2.openPrice - day1Close) / day1Close) * PERCENT_SCALE
    return if (gapPct >= gapPercent) day2 else null
  }

  companion object {
    const val TYPE = "gapandcrap"
    private const val DEFAULT_GAP_PERCENT = 5.0
    private const val FAILURE_WINDOW_DAYS = 3
    private const val PERCENT_SCALE = 100.0
  }
}
