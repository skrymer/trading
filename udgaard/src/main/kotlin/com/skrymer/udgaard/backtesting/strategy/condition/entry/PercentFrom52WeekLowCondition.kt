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
 * Entry condition that requires the close to be at least a given percentage above the
 * 52-week low — Minervini's Trend Template criterion "at least 30% above the 52-week low",
 * which excludes names that have not yet lifted clear of their basing range.
 *
 * Reads the pre-computed `low52Week` field directly. When the low is unavailable (fewer
 * than 52 weeks of history), the condition fails — a stock without a full window must not
 * pass the gate.
 *
 * @param minPercentAboveLow minimum required distance above the 52-week low, in percent (default 30.0)
 */
@Component
class PercentFrom52WeekLowCondition(
  private val minPercentAboveLow: Double = 30.0,
) : EntryCondition {
  init {
    require(minPercentAboveLow >= 0.0) { "minPercentAboveLow must be >= 0, was $minPercentAboveLow" }
  }

  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val percentAbove = percentAboveLow(quote) ?: return false
    return percentAbove >= minPercentAboveLow
  }

  override fun description(): String = "Price at least ${"%.0f".format(minPercentAboveLow)}% above 52-week low"

  override fun getMetadata() =
    ConditionMetadata(
      type = "percentFrom52WeekLow",
      displayName = "Percent From 52-Week Low",
      description = "Close is at least the given percentage above the 52-week low",
      parameters =
        listOf(
          ParameterMetadata(
            name = "minPercentAboveLow",
            displayName = "Min % Above Low",
            type = "number",
            defaultValue = minPercentAboveLow,
            min = 0.0,
            max = 1000.0,
          ),
        ),
      category = "Trend",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val percentAbove = percentAboveLow(quote)
      ?: return ConditionEvaluationResult(
        conditionType = "PercentFrom52WeekLowCondition",
        description = description(),
        passed = false,
        actualValue = null,
        threshold = null,
        message = "52-week low not available (insufficient history) ✗",
      )

    val passed = percentAbove >= minPercentAboveLow
    val mark = if (passed) "✓" else "✗"

    return ConditionEvaluationResult(
      conditionType = "PercentFrom52WeekLowCondition",
      description = description(),
      passed = passed,
      actualValue = "${"%.1f".format(percentAbove)}% above low",
      threshold = "≥ ${"%.0f".format(minPercentAboveLow)}%",
      message = "${"%.1f".format(percentAbove)}% above 52-week low (≥ ${"%.0f".format(minPercentAboveLow)}%) $mark",
    )
  }

  /** Percent the close sits above the 52-week low, or null when unavailable. */
  private fun percentAboveLow(quote: StockQuote): Double? {
    val low = quote.low52Week ?: return null
    if (low <= 0.0) return null
    return (quote.closePrice - low) / low * 100.0
  }

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition =
    PercentFrom52WeekLowCondition(
      minPercentAboveLow = parameters.numberOr("minPercentAboveLow", minPercentAboveLow),
    )
}
