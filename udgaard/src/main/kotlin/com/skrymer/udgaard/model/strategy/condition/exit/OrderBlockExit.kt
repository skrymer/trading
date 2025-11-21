package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.model.OrderBlockSource
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.ExitCondition

/**
 * Exit condition that triggers when price is within an order block older than specified age.
 * @param orderBlockAgeInDays Minimum age of order block in days (default 120)
 * @param source Order block source to consider: "CALCULATED", "OVTLYR", or "ALL" (default CALCULATED)
 */
class OrderBlockExit(
    private val orderBlockAgeInDays: Int = 120,
    private val source: String = "CALCULATED"
) : ExitCondition {

    override fun shouldExit(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean {
        val orderBlockSource = when (source.uppercase()) {
            "CALCULATED" -> OrderBlockSource.CALCULATED
            "OVTLYR" -> OrderBlockSource.OVTLYR
            "ALL" -> null  // null means all sources
            else -> null
        }
        return stock.withinOrderBlock(quote, orderBlockAgeInDays, orderBlockSource)
    }

    override fun exitReason(): String {
        val sourceText = when (source.uppercase()) {
            "CALCULATED" -> " (calculated)"
            "OVTLYR" -> " (Ovtlyr)"
            else -> ""
        }
        return "Quote is within an order block$sourceText older than $orderBlockAgeInDays days"
    }

    override fun description(): String {
        val sourceText = when (source.uppercase()) {
            "CALCULATED" -> " calc"
            "OVTLYR" -> " Ovtlyr"
            else -> ""
        }
        return "Within order block$sourceText (age > ${orderBlockAgeInDays}d)"
    }
}
