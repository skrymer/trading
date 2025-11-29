package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Entry condition that checks if SPY is in an uptrend.
 * SPY is considered in uptrend when 10 EMA > 20 EMA and price > 50 EMA.
 */
class SpyUptrendCondition : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return quote.spyInUptrend
    }

    override fun description(): String = "SPY in uptrend"

    override fun getMetadata() = com.skrymer.udgaard.model.strategy.condition.ConditionMetadata(
        type = "spyUptrend",
        description = description()
    )
}
