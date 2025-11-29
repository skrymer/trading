package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Entry condition that checks if current price is above the previous day's low.
 * This ensures we're not entering on a breakdown below prior support.
 * Returns true if no previous quote exists (defaults to 0.0).
 */
class PriceAbovePreviousLowCondition : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        val previousQuote = stock.getPreviousQuote(quote)
        return quote.closePrice > (previousQuote?.low ?: 0.0)
    }

    override fun description(): String = "Price above previous low"

    override fun getMetadata() = com.skrymer.udgaard.model.strategy.condition.ConditionMetadata(
        type = "priceAbovePreviousLow",
        description = description()
    )
}
