package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.ExitCondition

/**
 * Exit condition that triggers when price is within an order block older than specified age.
 * All order blocks are calculated using ROC (Rate of Change) analysis.
 * @param orderBlockAgeInDays Minimum age of order block in days (default 120)
 */
class OrderBlockExit(
    private val orderBlockAgeInDays: Int = 120
) : ExitCondition {

    override fun shouldExit(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean {
        return stock.withinOrderBlock(quote, orderBlockAgeInDays)
    }

    override fun exitReason(): String {
        return "Quote is within an order block older than $orderBlockAgeInDays days"
    }

    override fun description(): String {
        return "Within order block (age > ${orderBlockAgeInDays}d)"
    }

    override fun getMetadata() = com.skrymer.udgaard.model.strategy.condition.ConditionMetadata(
        type = "orderBlock",
        description = description()
    )
}
