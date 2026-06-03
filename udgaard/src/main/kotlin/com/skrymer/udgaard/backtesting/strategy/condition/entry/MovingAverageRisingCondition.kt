package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.service.intOr
import com.skrymer.udgaard.backtesting.service.stringOr
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that checks a moving average is rising — higher today than it was
 * `lookbackBars` trading bars ago. A trend filter for "the long-term average is sloping up
 * for at least a month", which the defaults (SMA200, 30 bars) encode.
 *
 * Both MA values are read from pre-computed quote fields — today's and the bar
 * `lookbackBars` ago. If either is unavailable (insufficient history), or there aren't
 * enough bars to look back over, the condition fails. Only past/current bars are read,
 * so there is no lookahead.
 *
 * @param maType "SMA" (default) or "EMA"
 * @param period MA period to test for rising (default 200)
 * @param lookbackBars trading bars to look back when comparing (default 30 ≈ one month)
 */
@Component
class MovingAverageRisingCondition(
  private val maType: String = "SMA",
  private val period: Int = 200,
  private val lookbackBars: Int = 30,
) : EntryCondition {
  init {
    val supported = supportedMaPeriods(maType)
    require(supported.isNotEmpty()) { "Unsupported MA type '$maType' (use SMA or EMA)" }
    require(period in supported) { "$maType does not provide period $period (supported: ${supported.sorted()})" }
    require(lookbackBars >= 1) { "lookbackBars must be >= 1, was $lookbackBars" }
  }

  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val current = quote.movingAverage(maType, period) ?: return false
    val past = movingAverageBarsAgo(stock, quote) ?: return false
    return current > past
  }

  override fun description(): String = "$maType$period rising over $lookbackBars bars"

  override fun getMetadata() =
    ConditionMetadata(
      type = "movingAverageRising",
      displayName = "Moving Average Rising",
      description = "Moving average is higher than it was N bars ago (trend slope is up)",
      parameters =
        listOf(
          ParameterMetadata(
            name = "maType",
            displayName = "MA Type",
            type = "string",
            defaultValue = maType,
            options = listOf("SMA", "EMA"),
          ),
          ParameterMetadata(
            name = "period",
            displayName = "MA Period",
            type = "number",
            defaultValue = period,
            min = 5,
            max = 200,
          ),
          ParameterMetadata(
            name = "lookbackBars",
            displayName = "Lookback Bars",
            type = "number",
            defaultValue = lookbackBars,
            min = 1,
            max = 250,
          ),
        ),
      category = "Trend",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val current = quote.movingAverage(maType, period)
    val past = movingAverageBarsAgo(stock, quote)

    if (current == null || past == null) {
      return ConditionEvaluationResult(
        conditionType = "MovingAverageRisingCondition",
        description = description(),
        passed = false,
        actualValue = null,
        threshold = null,
        message = "Moving average history not available (insufficient history) ✗",
      )
    }

    val passed = current > past
    val mark = if (passed) "✓" else "✗"
    val message =
      "$maType$period ${"%.2f".format(current)} vs ${"%.2f".format(past)} ($lookbackBars bars ago) $mark"

    return ConditionEvaluationResult(
      conditionType = "MovingAverageRisingCondition",
      description = description(),
      passed = passed,
      actualValue = "%.2f vs %.2f".format(current, past),
      threshold = "now > $lookbackBars bars ago",
      message = message,
    )
  }

  /**
   * The MA value `lookbackBars` trading bars before the current quote, or null when there
   * isn't enough history or the prior MA field is unavailable.
   */
  private fun movingAverageBarsAgo(
    stock: Stock,
    quote: StockQuote,
  ): Double? {
    val window = stock.quotesInRange(quote.date.minusDays(lookbackBars * 2L + 10), quote.date)
    if (window.size <= lookbackBars) return null
    return window[window.size - 1 - lookbackBars].movingAverage(maType, period)
  }

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition =
    MovingAverageRisingCondition(
      maType = parameters.stringOr("maType", maType),
      period = parameters.intOr("period", period),
      lookbackBars = parameters.intOr("lookbackBars", lookbackBars),
    )
}
