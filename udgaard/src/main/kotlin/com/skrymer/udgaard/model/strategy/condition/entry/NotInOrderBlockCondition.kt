package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Entry condition that checks if the stock is NOT within an order block older than specified age.
 * Order blocks represent institutional supply/demand zones that can act as resistance.
 *
 * @param ageInDays Minimum age of order block to avoid (default 120 days)
 */
class NotInOrderBlockCondition(
    private val ageInDays: Int = 120
) : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return !stock.withinOrderBlock(quote, ageInDays)
    }

    override fun description(): String = "Not in order block (age > ${ageInDays}d)"

    override fun getMetadata() = com.skrymer.udgaard.model.strategy.condition.ConditionMetadata(
        type = "notInOrderBlock",
        description = description()
    )

    override fun evaluateWithDetails(stock: Stock, quote: StockQuote): ConditionEvaluationResult {
        val passed = evaluate(stock, quote)
        val message = if (passed) description() + " ✓" else description() + " ✗"
        
        return ConditionEvaluationResult(
            conditionType = "NotInOrderBlockCondition",
            description = description(),
            passed = passed,
            actualValue = null,
            threshold = null,
            message = message
        )
    }

}
