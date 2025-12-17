package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import com.skrymer.udgaard.model.strategy.condition.exit.ExitCondition
import org.springframework.stereotype.Component

/**
 * Exit condition based on graduated heatmap thresholds.
 *
 * Exit when heatmap reaches:
 * - Entry < 50: Exit at 63
 * - Entry 50-75: Exit at entry + 10
 * - Entry > 75: Exit at entry + 5
 */
@Component
class HeatmapThresholdExit : ExitCondition {
  override fun shouldExit(
    stock: StockDomain,
    entryQuote: StockQuoteDomain?,
    quote: StockQuoteDomain,
  ): Boolean {
    if (entryQuote == null) return true

    val entryHeatmapValue = entryQuote.heatmap
    val currentHeatmapValue = quote.heatmap

    return when {
      entryHeatmapValue in 50.0..75.0 ->
        currentHeatmapValue >= (entryHeatmapValue + 10)

      entryHeatmapValue >= 75 ->
        currentHeatmapValue >= (entryHeatmapValue + 5)

      else ->
        currentHeatmapValue >= 63
    }
  }

  override fun exitReason(): String = "Heatmap reached target threshold"

  override fun description(): String = "Heatmap threshold exit"

  override fun getMetadata() =
    ConditionMetadata(
      type = "heatmapThreshold",
      displayName = "Heatmap Threshold",
      description = "Exit based on heatmap threshold",
      parameters = emptyList(),
      category = "Signal",
    )
}
