package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.entry.EntryCondition
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if SPY heatmap is rising.
 * Rising heatmap indicates increasing greed/momentum in the market.
 */
@Component
class SpyHeatmapRisingCondition : EntryCondition {
  override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
    return quote.spyHeatmap > quote.spyPreviousHeatmap
  }

  override fun description(): String = "SPY heatmap rising"

  override fun getMetadata() = ConditionMetadata(
    type = "spyHeatmapRising",
    displayName = "SPY Heatmap Rising",
    description = "SPY heatmap is increasing",
    parameters = emptyList(),
    category = "SPY"
  )

  override fun evaluateWithDetails(stock: Stock, quote: StockQuote): ConditionEvaluationResult {
    val currentHeatmap = quote.spyHeatmap
    val previousHeatmap = quote.spyPreviousHeatmap
    val passed = currentHeatmap > previousHeatmap

    val message = if (passed) {
      "SPY heatmap rose from ${"%.1f".format(previousHeatmap)} to ${"%.1f".format(currentHeatmap)} ✓"
    } else {
      "SPY heatmap did not rise (${"%.1f".format(previousHeatmap)} → ${"%.1f".format(currentHeatmap)}) ✗"
    }

    return ConditionEvaluationResult(
      conditionType = "SpyHeatmapRisingCondition",
      description = description(),
      passed = passed,
      actualValue = "${"%.1f".format(previousHeatmap)} → ${"%.1f".format(currentHeatmap)}",
      threshold = "Rising",
      message = message
    )
  }
}
