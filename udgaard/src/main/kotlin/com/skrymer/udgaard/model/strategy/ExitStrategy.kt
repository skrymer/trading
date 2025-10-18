package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

/**
 * An exit strategy.
 */
interface ExitStrategy {

    /**
     * @param stock - the Stock
     * @param entryQuote - the quote that matched the entry strategy.
     * @param quote - the current quote
     * @return true when exit criteria has been met.
     */
    fun match(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean

    /**
     * @return a ExitStrategyReport.
     */
    fun test(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): ExitStrategyReport {
        return if(match(stock, entryQuote, quote)) {
            ExitStrategyReport(true, reason(stock,entryQuote, quote), exitPrice(stock, entryQuote, quote))
        } else {
            ExitStrategyReport(false)
        }
    }

    /**
     * @return the exit reason.
     */
    fun reason(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): String?

    /**
     * A description of this exit strategy.
     */
    fun description(): String

    /**
     * The price when the exit was hit.
     */
    fun exitPrice(stock: Stock, entryQuote: StockQuote?, quote: StockQuote) = quote.closePrice
}
