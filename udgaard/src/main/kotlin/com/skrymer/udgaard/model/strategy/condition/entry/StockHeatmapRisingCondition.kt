package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.entry.EntryCondition
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if stock heatmap is rising.
 * Compares current heatmap with previous quote's heatmap.
 * Returns true if no previous quote exists (defaults to 0.0).
 */
@Component
class StockHeatmapRisingCondition : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
  ): Boolean {
    val previousQuote = stock.getPreviousQuote(quote)
    return quote.heatmap > (previousQuote?.heatmap ?: 0.0)
  }

  override fun description(): String = "Stock heatmap rising"

  override fun getMetadata() =
    ConditionMetadata(
      type = "stockHeatmapRising",
      displayName = "Stock Heatmap Rising",
      description = "Stock heatmap is increasing compared to previous day",
      parameters = emptyList(),
      category = "Stock",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
  ): ConditionEvaluationResult {
    val previousQuote = stock.getPreviousQuote(quote)
    val currentHeatmap = quote.heatmap
    val previousHeatmap = previousQuote?.heatmap ?: 0.0
    val passed = currentHeatmap > previousHeatmap

    val message =
      if (passed) {
        "Stock heatmap rose from ${"%.1f".format(previousHeatmap)} to ${"%.1f".format(currentHeatmap)} ✓"
      } else {
        "Stock heatmap did not rise (${"%.1f".format(previousHeatmap)} → ${"%.1f".format(currentHeatmap)}) ✗"
      }

    return ConditionEvaluationResult(
      conditionType = "StockHeatmapRisingCondition",
      description = description(),
      passed = passed,
      actualValue = "${"%.1f".format(previousHeatmap)} → ${"%.1f".format(currentHeatmap)}",
      threshold = "Rising",
      message = message,
    )
  }
}
