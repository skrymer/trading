package com.skrymer.udgaard.model.strategy.condition.entry

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
}
