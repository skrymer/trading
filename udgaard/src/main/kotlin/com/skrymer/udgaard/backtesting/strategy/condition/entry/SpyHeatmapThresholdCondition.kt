package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EntryCondition
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if SPY heatmap is below a threshold.
 * Lower heatmap values indicate less greed/fear in the market.
 *
 * @param threshold Maximum heatmap value (default 70.0)
 */
@Component
class SpyHeatmapThresholdCondition(
  private val threshold: Double = 70.0,
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
  ): Boolean = quote.spyHeatmap < threshold

  override fun description(): String = "SPY heatmap < $threshold"

  override fun getMetadata() =
    ConditionMetadata(
      type = "spyHeatmap",
      displayName = "SPY Heatmap Below Threshold",
      description = "SPY heatmap is below the threshold",
      parameters =
        listOf(
          ParameterMetadata(
            name = "threshold",
            displayName = "Threshold",
            type = "number",
            defaultValue = 70.0,
            min = 0,
            max = 100,
          ),
        ),
      category = "SPY",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
  ): ConditionEvaluationResult {
    val actualHeatmap = quote.spyHeatmap
    val passed = actualHeatmap < threshold

    val message =
      if (passed) {
        "SPY heatmap ${"%.1f".format(actualHeatmap)} < ${"%.1f".format(threshold)} ✓"
      } else {
        "SPY heatmap ${"%.1f".format(actualHeatmap)} ≥ ${"%.1f".format(threshold)} ✗"
      }

    return ConditionEvaluationResult(
      conditionType = "SpyHeatmapThresholdCondition",
      description = description(),
      passed = passed,
      actualValue = "%.1f".format(actualHeatmap),
      threshold = "< %.1f".format(threshold),
      message = message,
    )
  }
}
