package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Entry condition that checks if SPY has a buy signal.
 */
class SpyBuySignalCondition : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return quote.hasSpyBuySignal()
    }

    override fun description(): String = "SPY has buy signal"

    override fun getMetadata() = com.skrymer.udgaard.model.strategy.condition.ConditionMetadata(
        type = "spyBuySignal",
        description = description()
    )

    override fun evaluateWithDetails(stock: Stock, quote: StockQuote): ConditionEvaluationResult {
        val passed = evaluate(stock, quote)

        val message = if (passed) {
            "SPY has buy signal ✓"
        } else {
            "SPY has no buy signal ✗"
        }

        return ConditionEvaluationResult(
            conditionType = "SpyBuySignalCondition",
            description = description(),
            passed = passed,
            actualValue = if (quote.hasSpyBuySignal()) "Yes" else "No",
            threshold = "Yes",
            message = message
        )
    }
}
