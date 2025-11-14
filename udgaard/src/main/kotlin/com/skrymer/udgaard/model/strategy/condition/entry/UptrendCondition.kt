package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Condition that checks if the stock is in an uptrend.
 */
class UptrendCondition : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return quote.isInUptrend()
    }

    override fun description(): String = "Stock is in uptrend"
}
