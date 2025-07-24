package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote
import java.util.function.Predicate

interface EntryStrategy {
    fun description(): String

    fun test(quote: StockQuote, previousQuote: StockQuote? = null): Boolean
}
