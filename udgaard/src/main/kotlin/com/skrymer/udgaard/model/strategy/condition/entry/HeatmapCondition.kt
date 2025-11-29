package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Condition that checks if heatmap is below a threshold.
 * Lower heatmap values indicate more fear in the market.
 */
class HeatmapCondition(private val threshold: Double) : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return quote.heatmap < threshold
    }

    override fun description(): String = "Heatmap < $threshold"

    override fun getMetadata() = com.skrymer.udgaard.model.strategy.condition.ConditionMetadata(
        type = "heatmap",
        description = description()
    )
}
