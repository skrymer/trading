package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component
import kotlin.math.abs
import kotlin.math.max

/**
 * Pullback-to-EMA20 reversal pattern requiring at least `minSubConditions` of 3 sub-conditions:
 *
 * 1. **In-zone**: `|close − closeEMA20| <= atrMultiple × ATR` — price within N ATRs of the 20-EMA
 * 2. **Higher low**: today's low > the low `lookbackDays` trading days ago — no fresh breakdown
 * 3. **EMA rising**: today's `closeEMA20` > previous bar's `closeEMA20` — local trend intact
 *
 * Returns false if ATR is non-positive, if fewer than `lookbackDays + 1` quotes of history are
 * available (need today + N prior bars), or if no previous quote exists.
 *
 * `quotesInRange(from, to)` is inclusive on both ends, so today's bar sits at `ref.size - 1` and
 * the bar `N` trading days ago is at `ref.size - 1 - N`.
 */
@Component
class Pullback2of3Condition(
  private val atrMultiple: Double = 1.5,
  private val lookbackDays: Int = 10,
  private val minSubConditions: Int = 2,
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean = computeOutcome(stock, quote).passed

  override fun description(): String =
    "Pullback $minSubConditions-of-3 (in-zone $atrMultiple×ATR / higher-low ${lookbackDays}d / EMA20 rising)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "pullback2of3",
      displayName = "Pullback 2-of-3 reversal pattern",
      description = "At least N of 3 sub-conditions: in-zone (|close - EMA20| <= atrMultiple x ATR), " +
        "higher low vs lookbackDays trading days ago, EMA20 rising.",
      parameters = listOf(
        ParameterMetadata(
          name = "atrMultiple",
          displayName = "ATR multiple",
          type = "number",
          defaultValue = atrMultiple,
          min = 0.1,
          max = 10.0,
        ),
        ParameterMetadata(
          name = "lookbackDays",
          displayName = "Lookback days",
          type = "number",
          defaultValue = lookbackDays,
          min = 1,
          max = 100,
        ),
        ParameterMetadata(
          name = "minSubConditions",
          displayName = "Min sub-conditions",
          type = "number",
          defaultValue = minSubConditions,
          min = 1,
          max = 3,
        ),
      ),
      category = "Stock",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val outcome = computeOutcome(stock, quote)
    return ConditionEvaluationResult(
      conditionType = "Pullback2of3Condition",
      description = description(),
      passed = outcome.passed,
      actualValue = null,
      threshold = ">= $minSubConditions of 3",
      message = outcome.message,
    )
  }

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition =
    Pullback2of3Condition(
      atrMultiple = (parameters["atrMultiple"] as? Number)?.toDouble() ?: atrMultiple,
      lookbackDays = (parameters["lookbackDays"] as? Number)?.toInt() ?: lookbackDays,
      minSubConditions = (parameters["minSubConditions"] as? Number)?.toInt() ?: minSubConditions,
    )

  private data class Outcome(
    val passed: Boolean,
    val message: String
  )

  /**
   * Compute the sub-condition outcomes and the overall pass/fail in one pass. Shared by
   * `evaluate` (hot path) and `evaluateWithDetails` (diagnostic) — there's no place for
   * the two paths to drift apart on the lookback math.
   *
   * Hot-path note: allocates one `Outcome` per evaluation but the inner counter avoids
   * the List + lambda allocation that a `listOf().count { it }` would do.
   */
  private fun computeOutcome(stock: Stock, quote: StockQuote): Outcome {
    if (quote.atr <= 0.0) return Outcome(false, "ATR non-positive — cannot evaluate")
    val prev = stock.getPreviousQuote(quote) ?: return Outcome(false, "No previous quote available")

    val bufferDays = max(LOOKBACK_BASE_CALENDAR_BUFFER, lookbackDays * CALENDAR_PER_TRADING_DAY_MULT + LOOKBACK_HOLIDAY_SLACK)
    val ref = stock.quotesInRange(quote.date.minusDays(bufferDays.toLong()), quote.date)
    // Need today + lookbackDays prior bars, i.e. ref.size >= lookbackDays + 1.
    if (ref.size < lookbackDays + 1) {
      return Outcome(false, "Insufficient history: have ${ref.size}, need >= ${lookbackDays + 1}")
    }

    val inZone = abs(quote.closePrice - quote.closePriceEMA20) <= atrMultiple * quote.atr
    val higherLow = quote.low > ref[ref.size - 1 - lookbackDays].low
    val emaRising = quote.closePriceEMA20 > prev.closePriceEMA20

    val count = (if (inZone) 1 else 0) + (if (higherLow) 1 else 0) + (if (emaRising) 1 else 0)
    val passed = count >= minSubConditions
    val message = "Sub-conditions: in-zone=$inZone, higher-low=$higherLow, EMA-rising=$emaRising " +
      "(passed $count of 3, need >= $minSubConditions)"
    return Outcome(passed, message)
  }

  companion object {
    // Trading days are ~5/7 of calendar days. 2× factor + slack covers a year of US holidays
    // even for the metadata-advertised max lookbackDays = 100.
    private const val CALENDAR_PER_TRADING_DAY_MULT = 2
    private const val LOOKBACK_HOLIDAY_SLACK = 10
    private const val LOOKBACK_BASE_CALENDAR_BUFFER = 20
  }
}
