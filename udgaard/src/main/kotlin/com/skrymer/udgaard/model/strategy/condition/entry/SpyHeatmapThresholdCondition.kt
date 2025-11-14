package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Entry condition that checks if SPY heatmap is below a threshold.
 * Lower heatmap values indicate less greed/fear in the market.
 *
 * @param threshold Maximum heatmap value (default 70.0)
 */
class SpyHeatmapThresholdCondition(
    private val threshold: Double = 70.0
) : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return quote.spyHeatmap < threshold
    }

    override fun description(): String = "SPY heatmap < $threshold"
}
