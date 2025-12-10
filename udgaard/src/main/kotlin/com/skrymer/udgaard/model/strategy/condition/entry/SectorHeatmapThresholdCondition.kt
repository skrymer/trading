package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.controller.dto.ParameterMetadata
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.entry.EntryCondition
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if sector heatmap is below a threshold.
 * Lower heatmap values indicate less greed/fear in the sector.
 *
 * @param threshold Maximum heatmap value (default 70.0)
 */
@Component
class SectorHeatmapThresholdCondition(
  private val threshold: Double = 70.0
) : EntryCondition {
  override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
    return quote.sectorHeatmap < threshold
  }

  override fun description(): String = "Sector heatmap < $threshold"

  override fun getMetadata() = ConditionMetadata(
    type = "sectorHeatmap",
    displayName = "Sector Heatmap Below Threshold",
    description = "Sector heatmap is below the threshold",
    parameters = listOf(
      ParameterMetadata(
        name = "threshold",
        displayName = "Threshold",
        type = "number",
        defaultValue = 70.0,
        min = 0,
        max = 100
      )
    ),
    category = "Sector"
  )

  override fun evaluateWithDetails(stock: Stock, quote: StockQuote): ConditionEvaluationResult {
    val actualHeatmap = quote.sectorHeatmap
    val passed = actualHeatmap < threshold

    val message = if (passed) {
      "Sector heatmap ${"%.1f".format(actualHeatmap)} < ${"%.1f".format(threshold)} ✓"
    } else {
      "Sector heatmap ${"%.1f".format(actualHeatmap)} ≥ ${"%.1f".format(threshold)} ✗"
    }

    return ConditionEvaluationResult(
      conditionType = "SectorHeatmapThresholdCondition",
      description = description(),
      passed = passed,
      actualValue = "%.1f".format(actualHeatmap),
      threshold = "< %.1f".format(threshold),
      message = message
    )
  }
}
