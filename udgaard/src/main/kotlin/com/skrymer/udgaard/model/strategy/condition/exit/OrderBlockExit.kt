package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.ExitCondition

/**
 * Exit condition that triggers when price is within an order block older than specified age.
 * @param orderBlockAgeInDays Minimum age of order block in days (default 120)
 */
class OrderBlockExit(
    private val orderBlockAgeInDays: Int = 120
) : ExitCondition {

    override fun shouldExit(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean {
        return stock.withinOrderBlock(quote, orderBlockAgeInDays)
    }

    override fun exitReason(): String =
        "Quote is within an order block older than $orderBlockAgeInDays days"

    override fun description(): String =
        "Within order block (age > ${orderBlockAgeInDays}d)"
}
