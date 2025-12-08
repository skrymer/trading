package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Entry condition that checks if SPY is in an uptrend.
 * SPY is considered in uptrend when 10 EMA > 20 EMA and price > 50 EMA.
 */
class SpyUptrendCondition : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return quote.spyInUptrend
    }

    override fun description(): String = "SPY in uptrend"

    override fun getMetadata() = com.skrymer.udgaard.model.strategy.condition.ConditionMetadata(
        type = "spyUptrend",
        description = description()
    )

    override fun evaluateWithDetails(stock: Stock, quote: StockQuote): ConditionEvaluationResult {
        val passed = evaluate(stock, quote)

        val message = if (passed) {
            "SPY in uptrend (10 EMA > 20 EMA, price > 50 EMA) ✓"
        } else {
            "SPY not in uptrend ✗"
        }

        return ConditionEvaluationResult(
            conditionType = "SpyUptrendCondition",
            description = description(),
            passed = passed,
            actualValue = if (passed) "Uptrend" else "Not uptrend",
            threshold = "10 > 20 EMA, price > 50 EMA",
            message = message
        )
    }
}
