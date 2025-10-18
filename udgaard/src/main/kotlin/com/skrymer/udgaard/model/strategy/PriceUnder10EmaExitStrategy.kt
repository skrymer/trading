package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

class PriceUnder10EmaExitStrategy : ExitStrategy {
    /**
     * @return true when close price is under the 10 EMA at close.
     */
    override fun match(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean {
        return quote.closePrice < quote.closePriceEMA10
    }

    override fun reason(stock: Stock, entryQuote: StockQuote?, quote: StockQuote) =
        "Price closed under the 10 EMA."

    override fun description(): String {
        return "Price is under 10 EMA at close"
    }
}
