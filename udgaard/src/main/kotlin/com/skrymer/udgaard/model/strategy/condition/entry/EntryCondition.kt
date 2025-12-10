package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

/**
 * Represents a single entry condition that can be evaluated.
 * Conditions can be composed using logical operators to build complex strategies.
 *
 * All implementations must be annotated with @Component to be auto-discovered by Spring.
 */
interface EntryCondition {
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
     * Get metadata for this condition including parameters and UI information.
     * This metadata is used by the API to expose conditions to the frontend.
     *
     * All implementations MUST override this method to provide:
     * - type: Unique identifier (e.g., "atrExpanding")
     * - displayName: User-friendly name (e.g., "ATR Expanding")
     * - description: Detailed explanation
     * - parameters: List of configurable parameters with metadata
     * - category: Grouping category (e.g., "Stock", "Market", "Volatility")
     *
     * @return Condition metadata for UI consumption
     */
    fun getMetadata(): ConditionMetadata

    /**
     * Evaluates the condition and returns detailed results.
     * Includes actual values, thresholds, and explanatory messages.
     *
     * All conditions must implement this to provide meaningful diagnostic information.
     *
     * @return Detailed evaluation result including pass/fail, actual values, and explanations
     */
    fun evaluateWithDetails(stock: Stock, quote: StockQuote): ConditionEvaluationResult
}