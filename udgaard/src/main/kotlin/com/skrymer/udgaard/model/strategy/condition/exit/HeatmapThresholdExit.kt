package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.ExitCondition

/**
 * Exit condition based on graduated heatmap thresholds.
 *
 * Exit when heatmap reaches:
 * - Entry < 50: Exit at 63
 * - Entry 50-75: Exit at entry + 10
 * - Entry > 75: Exit at entry + 5
 */
class HeatmapThresholdExit : ExitCondition {
    override fun shouldExit(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean {
        if (entryQuote == null) return true

        val entryHeatmapValue = entryQuote.heatmap
        val currentHeatmapValue = quote.heatmap

        return when {
            entryHeatmapValue >= 50 && entryHeatmapValue <= 75 ->
                currentHeatmapValue >= (entryHeatmapValue + 10)
            entryHeatmapValue >= 75 ->
                currentHeatmapValue >= (entryHeatmapValue + 5)
            else ->
                currentHeatmapValue >= 63
        }
    }

    override fun exitReason(): String = "Heatmap reached target threshold"

    override fun description(): String = "Heatmap threshold exit"

    override fun getMetadata() = com.skrymer.udgaard.model.strategy.condition.ConditionMetadata(
        type = "heatmapThreshold",
        description = description()
    )
}
