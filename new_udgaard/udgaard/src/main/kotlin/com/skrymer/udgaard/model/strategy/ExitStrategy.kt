package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote
import java.util.function.Predicate

interface ExitStrategy : Predicate<StockQuote> {
    fun description(): String
}
