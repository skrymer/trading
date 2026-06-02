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
 * Entry condition that requires the stock's market-relative strength percentile to be at or above
 * a threshold — a relative-strength gate that trades only market leaders (default ≥ 70 = stronger
 * than 70% of the market).
 *
 * Reads the pre-computed `relativeStrengthPercentile` field directly — a cross-sectional rank
 * computed in Midgaard against the whole survivorship-free universe (ADR 0009), so the condition
 * needs no peer set at evaluation time. When the percentile is unavailable (the stock lacks the
 * trailing history, the date's qualifying universe was too thin, or the date precedes the trust
 * floor), the condition fails — a stock with no market-relative rank must not pass the gate.
 *
 * Unlike the `TrailingReturn` ranker (which only *orders* same-day candidates within the
 * strategy's subset), this is an *absolute* market-wide floor applied per stock.
 *
 * @param minPercentile minimum required percentile, 0–100 (default 70.0)
 */
@Component
class RelativeStrengthPercentileCondition(
  private val minPercentile: Double = 70.0,
) : EntryCondition {
  init {
    require(minPercentile in 0.0..100.0) { "minPercentile must be in 0..100, was $minPercentile" }
  }

  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val percentile = quote.relativeStrengthPercentile ?: return false
    return percentile >= minPercentile
  }

  override fun description(): String = "Market-relative strength percentile ≥ ${"%.0f".format(minPercentile)}"

  override fun getMetadata() =
    ConditionMetadata(
      type = "relativeStrengthPercentile",
      displayName = "Relative Strength Percentile",
      description = "Stock's trailing-return percentile vs the whole market is at or above the threshold",
      parameters =
        listOf(
          ParameterMetadata(
            name = "minPercentile",
            displayName = "Min Percentile",
            type = "number",
            defaultValue = minPercentile,
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
    val percentile = quote.relativeStrengthPercentile
      ?: return ConditionEvaluationResult(
        conditionType = "RelativeStrengthPercentileCondition",
        description = description(),
        passed = false,
        actualValue = null,
        threshold = null,
        message = "Relative strength percentile not available (insufficient history or thin universe) ✗",
      )

    val passed = percentile >= minPercentile
    val mark = if (passed) "✓" else "✗"

    return ConditionEvaluationResult(
      conditionType = "RelativeStrengthPercentileCondition",
      description = description(),
      passed = passed,
      actualValue = "${"%.1f".format(percentile)} percentile",
      threshold = "≥ ${"%.0f".format(minPercentile)}",
      message = "Relative strength ${"%.1f".format(percentile)} percentile (≥ ${"%.0f".format(minPercentile)}) $mark",
    )
  }

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition =
    RelativeStrengthPercentileCondition(
      minPercentile = parameters.numberOr("minPercentile", minPercentile),
    )
}
