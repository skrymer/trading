package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Entry condition that checks if sector heatmap is rising.
 * Rising heatmap indicates increasing greed/momentum in the sector.
 */
class SectorHeatmapRisingCondition : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return quote.sectorIsGettingGreedier()
    }

    override fun description(): String = "Sector heatmap rising"

    override fun getMetadata() = com.skrymer.udgaard.model.strategy.condition.ConditionMetadata(
        type = "sectorHeatmapRising",
        description = description()
    )

    override fun evaluateWithDetails(stock: Stock, quote: StockQuote): ConditionEvaluationResult {
        val currentHeatmap = quote.sectorHeatmap
        val previousHeatmap = quote.previousSectorHeatmap
        val passed = currentHeatmap > previousHeatmap

        val message = if (passed) {
            "Sector heatmap rose from ${"%.1f".format(previousHeatmap)} to ${"%.1f".format(currentHeatmap)} ✓"
        } else {
            "Sector heatmap did not rise (${"%.1f".format(previousHeatmap)} → ${"%.1f".format(currentHeatmap)}) ✗"
        }

        return ConditionEvaluationResult(
            conditionType = "SectorHeatmapRisingCondition",
            description = description(),
            passed = passed,
            actualValue = "${"%.1f".format(previousHeatmap)} → ${"%.1f".format(currentHeatmap)}",
            threshold = "Rising",
            message = message
        )
    }
}
