package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.ExitCondition

/**
 * Exit condition that triggers when heatmap is declining (getting more fearful).
 * This helps lock in profits before sentiment shifts too negative.
 */
class HeatmapDecliningExit : ExitCondition {
    override fun shouldExit(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean {
        return quote.isGettingMoreFearful()
    }

    override fun exitReason(): String = "Heatmap is declining (buyers getting fearful)"

    override fun description(): String = "Heatmap declining"
}
