package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.controller.dto.ParameterMetadata
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import com.skrymer.udgaard.model.strategy.condition.entry.EntryCondition
import org.springframework.stereotype.Component

/**
 * Condition that checks if heatmap is below a threshold.
 * Lower heatmap values indicate more fear in the market.
 */
@Component
class HeatmapCondition(
  private val threshold: Double = 70.0,
) : EntryCondition {
  override fun evaluate(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): Boolean = quote.heatmap < threshold

  override fun description(): String = "Heatmap < $threshold"

  override fun getMetadata() =
    ConditionMetadata(
      type = "heatmap",
      displayName = "Heatmap Below Threshold",
      description = "Stock heatmap is below the threshold",
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
      category = "Stock",
    )

  override fun evaluateWithDetails(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): ConditionEvaluationResult {
    val actualHeatmap = quote.heatmap
    val passed = actualHeatmap < threshold

    val message =
      if (passed) {
        "Heatmap ${"%.1f".format(actualHeatmap)} < ${"%.1f".format(threshold)} ✓"
      } else {
        "Heatmap ${"%.1f".format(actualHeatmap)} ≥ ${"%.1f".format(threshold)} ✗"
      }

    return ConditionEvaluationResult(
      conditionType = "HeatmapCondition",
      description = description(),
      passed = passed,
      actualValue = "%.1f".format(actualHeatmap),
      threshold = "< %.1f".format(threshold),
      message = message,
    )
  }
}
