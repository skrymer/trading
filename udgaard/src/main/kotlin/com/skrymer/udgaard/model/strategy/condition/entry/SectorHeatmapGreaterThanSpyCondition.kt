package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Entry condition that checks if sector heatmap is greater than SPY heatmap.
 * This indicates the sector is showing more strength/momentum than the overall market.
 */
class SectorHeatmapGreaterThanSpyCondition : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return quote.sectorHeatmap > quote.spyHeatmap
    }

    override fun description(): String = "Sector heatmap > SPY heatmap"

    override fun getMetadata() = com.skrymer.udgaard.model.strategy.condition.ConditionMetadata(
        type = "sectorHeatmapGreaterThanSpy",
        description = description()
    )

    override fun evaluateWithDetails(stock: Stock, quote: StockQuote): ConditionEvaluationResult {
        val sectorHeatmap = quote.sectorHeatmap
        val spyHeatmap = quote.spyHeatmap
        val passed = sectorHeatmap > spyHeatmap

        val message = if (passed) {
            "Sector heatmap ${"%.1f".format(sectorHeatmap)} > SPY heatmap ${"%.1f".format(spyHeatmap)} ✓"
        } else {
            "Sector heatmap ${"%.1f".format(sectorHeatmap)} ≤ SPY heatmap ${"%.1f".format(spyHeatmap)} ✗"
        }

        return ConditionEvaluationResult(
            conditionType = "SectorHeatmapGreaterThanSpyCondition",
            description = description(),
            passed = passed,
            actualValue = "Sector: ${"%.1f".format(sectorHeatmap)}, SPY: ${"%.1f".format(spyHeatmap)}",
            threshold = "Sector > SPY",
            message = message
        )
    }
}
