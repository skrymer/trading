package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Entry condition that checks if stock heatmap is rising.
 * Compares current heatmap with previous quote's heatmap.
 * Returns true if no previous quote exists (defaults to 0.0).
 */
class StockHeatmapRisingCondition : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        val previousQuote = stock.getPreviousQuote(quote)
        return quote.heatmap > (previousQuote?.heatmap ?: 0.0)
    }

    override fun description(): String = "Stock heatmap rising"
}
