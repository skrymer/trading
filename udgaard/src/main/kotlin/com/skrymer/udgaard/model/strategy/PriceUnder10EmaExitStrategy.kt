package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote

class PriceUnder10EmaExitStrategy : ExitStrategy {
    /**
     * @return true when close price is under the 10 EMA at close.
     */
    override fun test(entryQuote: StockQuote?, quote: StockQuote): Boolean {
        return quote.closePrice < quote.closePriceEMA10
    }

    override fun reason(entryQuote: StockQuote?, quote: StockQuote) =
        "Price closed ${quote.closePrice} under the 10EMA ${quote.closePriceEMA10}."

    override fun description(): String {
        return "Price is under 10 EMA at close"
    }
}
