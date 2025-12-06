package com.skrymer.udgaard.model.strategy.condition

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

/**
 * Represents a single trading condition that can be evaluated.
 * Conditions can be composed using logical operators to build complex strategies.
 */
interface TradingCondition {
    /**
     * Evaluates the condition for the given stock and quote.
     * @return true if the condition is met, false otherwise
     */
    fun evaluate(stock: Stock, quote: StockQuote): Boolean

    /**
     * Returns a human-readable description of the condition.
     */
    fun description(): String

    /**
     * Get metadata for this condition.
     * Used by UI to display strategy information.
     */
    fun getMetadata(): ConditionMetadata {
        return ConditionMetadata(
            type = this::class.simpleName ?: "unknown",
            description = description()
        )
    }

    /**
     * Evaluates the condition and returns detailed results.
     * Includes actual values, thresholds, and explanatory messages.
     *
     * Default implementation uses the standard evaluate() method without extra details.
     * Conditions should override this to provide richer information.
     *
     * @return Detailed evaluation result including pass/fail, actual values, and explanations
     */
    fun evaluateWithDetails(stock: Stock, quote: StockQuote): ConditionEvaluationResult {
        val passed = evaluate(stock, quote)
        return ConditionEvaluationResult(
            conditionType = this::class.simpleName ?: "Unknown",
            description = description(),
            passed = passed,
            actualValue = null,
            threshold = null,
            message = if (passed) "Condition met" else "Condition not met"
        )
    }
}
