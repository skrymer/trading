package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote

/**
 * An exit strategy.
 */
interface ExitStrategy {

    /**
     * @return true when exit criteria has been met.
     */
    fun match(entryQuote: StockQuote?, quote: StockQuote, previousQuote: StockQuote?  = null): Boolean

    /**
     * @return a ExitStrategyReport.
     */
    fun test(entryQuote: StockQuote?, quote: StockQuote, previousQuote: StockQuote?): ExitStrategyReport {
        return if(match(entryQuote, quote, previousQuote)) {
            ExitStrategyReport(true, reason(entryQuote, quote, previousQuote), exitPrice(entryQuote, quote, previousQuote))
        } else {
            ExitStrategyReport(false)
        }
    }

    /**
     * @return the exit reason.
     */
    fun reason(entryQuote: StockQuote?, quote: StockQuote, previousQuote: StockQuote?): String?

    /**
     * A description of this exit strategy.
     */
    fun description(): String

    /**
     * The price when the exit was hit.
     */
    fun exitPrice(entryQuote: StockQuote?, quote: StockQuote, previousQuote: StockQuote?) = quote.closePrice
}
