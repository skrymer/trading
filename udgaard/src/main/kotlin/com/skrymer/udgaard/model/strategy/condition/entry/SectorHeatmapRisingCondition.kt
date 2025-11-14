package com.skrymer.udgaard.model.strategy.condition.entry

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
}
