package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.service.numberOr
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that requires the close to be within a given percentage of the
 * 52-week high — Minervini's Trend Template criterion "within 25% of the 52-week high",
 * which keeps entries near the top of the base rather than chasing broken leaders.
 *
 * Reads the pre-computed `high52Week` field directly. A price at or above the high passes
 * (zero or negative distance). When the high is unavailable (fewer than 52 weeks of
 * history), the condition fails — a stock without a full window must not pass the gate.
 *
 * @param maxPercentBelowHigh maximum allowed distance below the 52-week high, in percent (default 25.0)
 */
@Component
class PercentFrom52WeekHighCondition(
  private val maxPercentBelowHigh: Double = 25.0,
) : EntryCondition {
  init {
    require(maxPercentBelowHigh >= 0.0) { "maxPercentBelowHigh must be >= 0, was $maxPercentBelowHigh" }
  }

  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val percentBelow = percentBelowHigh(quote) ?: return false
    return percentBelow <= maxPercentBelowHigh
  }

  override fun description(): String = "Price within ${"%.0f".format(maxPercentBelowHigh)}% of 52-week high"

  override fun getMetadata() =
    ConditionMetadata(
      type = "percentFrom52WeekHigh",
      displayName = "Percent From 52-Week High",
      description = "Close is within the given percentage of the 52-week high",
      parameters =
        listOf(
          ParameterMetadata(
            name = "maxPercentBelowHigh",
            displayName = "Max % Below High",
            type = "number",
            defaultValue = maxPercentBelowHigh,
            min = 0.0,
            max = 100.0,
          ),
        ),
      category = "Trend",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val percentBelow = percentBelowHigh(quote)
      ?: return ConditionEvaluationResult(
        conditionType = "PercentFrom52WeekHighCondition",
        description = description(),
        passed = false,
        actualValue = null,
        threshold = null,
        message = "52-week high not available (insufficient history) ✗",
      )

    val passed = percentBelow <= maxPercentBelowHigh
    val mark = if (passed) "✓" else "✗"

    return ConditionEvaluationResult(
      conditionType = "PercentFrom52WeekHighCondition",
      description = description(),
      passed = passed,
      actualValue = "${"%.1f".format(percentBelow)}% below high",
      threshold = "≤ ${"%.0f".format(maxPercentBelowHigh)}%",
      message = "${"%.1f".format(percentBelow)}% below 52-week high (≤ ${"%.0f".format(maxPercentBelowHigh)}%) $mark",
    )
  }

  /** Percent the close sits below the 52-week high (negative when at/above it), or null when unavailable. */
  private fun percentBelowHigh(quote: StockQuote): Double? {
    val high = quote.high52Week ?: return null
    if (high <= 0.0) return null
    return (high - quote.closePrice) / high * 100.0
  }

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition =
    PercentFrom52WeekHighCondition(
      maxPercentBelowHigh = parameters.numberOr("maxPercentBelowHigh", maxPercentBelowHigh),
    )
}
