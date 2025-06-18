package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote
import java.util.function.Predicate

/**
 * An exit strategy.
 */
interface ExitStrategy {

    /**
     * @return true when exit criteria has been met.
     */
    fun test(entryQuote: StockQuote, quote: StockQuote): Boolean

    /**
     * @return the outcome and the exit reason.
     */
    fun testAndExitReason(entryQuote: StockQuote, quote: StockQuote): Pair<Boolean, String?> {
        return if(test(entryQuote, quote)) {
            Pair(true, reason(entryQuote, quote))
        } else {
            Pair(false, null)
        }
    }

    /**
     * @return the exit reason.
     */
    fun reason(entryQuote: StockQuote, quote: StockQuote): String?

    /**
     * A description of this exit strategy.
     */
    fun description(): String
}
