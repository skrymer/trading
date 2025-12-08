package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * Entry condition that checks if market breadth is above a specific threshold.
 * Market breadth is measured as the percentage of stocks advancing (above their 10 EMA).
 *
 * @param threshold The minimum market breadth percentage required (0.0 to 100.0)
 */
class MarketBreadthAboveCondition(private val threshold: Double) : TradingCondition {
    override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
        return quote.marketAdvancingPercent >= threshold
    }

    override fun description(): String = "Market breadth above ${threshold.toInt()}%"

    override fun getMetadata() = com.skrymer.udgaard.model.strategy.condition.ConditionMetadata(
        type = "marketBreadthAbove",
        description = description()
    )

    override fun evaluateWithDetails(stock: Stock, quote: StockQuote): ConditionEvaluationResult {
        val passed = evaluate(stock, quote)
        val actualBreadth = quote.marketAdvancingPercent

        val message = if (passed) {
            "Market breadth is %.1f%% (≥ %.0f%%) ✓".format(actualBreadth, threshold)
        } else {
            "Market breadth is %.1f%% (requires ≥ %.0f%%) ✗".format(actualBreadth, threshold)
        }

        return ConditionEvaluationResult(
            conditionType = "MarketBreadthAboveCondition",
            description = description(),
            passed = passed,
            actualValue = "%.1f%%".format(actualBreadth),
            threshold = "≥ %.0f%%".format(threshold),
            message = message
        )
    }
}
