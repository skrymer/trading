package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote

class PriceUnder10EmaExitStrategy : ExitStrategy {
    /**
     * @return true when 10 EMA close price is greater than close price
     */
    override fun test(quote: StockQuote): Boolean {
        if (quote.closePrice_EMA10 == null) {
            return true
        }

        return quote.closePrice_EMA10.doubleValue() > quote.closePrice.doubleValue()
    }

    override fun description(): String {
        return "Price is under 10 EMA at close"
    }
}
