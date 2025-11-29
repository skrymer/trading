package com.skrymer.udgaard.model.strategy.condition

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
}
