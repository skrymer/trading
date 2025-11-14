package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Entry condition that checks if the market is in an uptrend.
 * Market is considered in uptrend when bull percentage is over 10 EMA.
 */
class MarketUptrendCondition : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return quote.isMarketInUptrend()
    }

    override fun description(): String = "Market in uptrend"
}
