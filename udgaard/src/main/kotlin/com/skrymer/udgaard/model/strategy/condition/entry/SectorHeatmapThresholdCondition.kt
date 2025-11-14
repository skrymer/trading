package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Entry condition that checks if sector heatmap is below a threshold.
 * Lower heatmap values indicate less greed/fear in the sector.
 *
 * @param threshold Maximum heatmap value (default 70.0)
 */
class SectorHeatmapThresholdCondition(
    private val threshold: Double = 70.0
) : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return quote.sectorHeatmap < threshold
    }

    override fun description(): String = "Sector heatmap < $threshold"
}
