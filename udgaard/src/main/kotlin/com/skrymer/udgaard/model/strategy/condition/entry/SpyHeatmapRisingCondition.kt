package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Entry condition that checks if SPY heatmap is rising.
 * Rising heatmap indicates increasing greed/momentum in the market.
 */
class SpyHeatmapRisingCondition : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return quote.spyHeatmap > quote.spyPreviousHeatmap
    }

    override fun description(): String = "SPY heatmap rising"

    override fun getMetadata() = com.skrymer.udgaard.model.strategy.condition.ConditionMetadata(
        type = "spyHeatmapRising",
        description = description()
    )
}
