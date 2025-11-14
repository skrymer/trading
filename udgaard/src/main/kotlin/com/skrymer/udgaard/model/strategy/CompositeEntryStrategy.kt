package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.LogicalOperator
import com.skrymer.udgaard.model.strategy.condition.TradingCondition

/**
 * A composite entry strategy that combines multiple conditions using logical operators.
 * This allows for flexible composition of trading rules without creating new strategy classes.
 */
class CompositeEntryStrategy(
    private val conditions: List<TradingCondition>,
    private val operator: LogicalOperator = LogicalOperator.AND,
    private val strategyDescription: String? = null
) : EntryStrategy {

    override fun test(stock: Stock, quote: StockQuote): Boolean {
        if (conditions.isEmpty()) return false

        return when (operator) {
            LogicalOperator.AND -> conditions.all { it.evaluate(stock, quote) }
            LogicalOperator.OR -> conditions.any { it.evaluate(stock, quote) }
            LogicalOperator.NOT -> !conditions.first().evaluate(stock, quote)
        }
    }

    override fun description(): String {
        if (strategyDescription != null) {
            return strategyDescription
        }

        val op = when (operator) {
            LogicalOperator.AND -> " AND "
            LogicalOperator.OR -> " OR "
            LogicalOperator.NOT -> "NOT "
        }

        return conditions.joinToString(op) { it.description() }
    }

    /**
     * Returns all conditions in this composite strategy.
     * This is useful for backtest lifecycle management (e.g., resetting stateful conditions).
     */
    fun getConditions(): List<TradingCondition> = conditions
}
