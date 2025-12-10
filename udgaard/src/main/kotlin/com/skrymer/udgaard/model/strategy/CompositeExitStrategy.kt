package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.LogicalOperator
import com.skrymer.udgaard.model.strategy.condition.exit.ExitCondition
import org.slf4j.LoggerFactory

/**
 * A composite exit strategy that combines multiple exit conditions using logical operators.
 */
class CompositeExitStrategy(
  private val exitConditions: List<ExitCondition>,
  private val operator: LogicalOperator = LogicalOperator.OR,
  private val strategyDescription: String? = null
) : ExitStrategy {
    private val logger = LoggerFactory.getLogger(CompositeExitStrategy::class.java)

    override fun match(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Boolean {
        if (exitConditions.isEmpty()) {
            logger.warn("No exit conditions configured")
            return false
        }

        return when (operator) {
            LogicalOperator.AND -> exitConditions.all { it.shouldExit(stock, entryQuote, quote) }
            LogicalOperator.OR -> exitConditions.any { it.shouldExit(stock, entryQuote, quote) }
            LogicalOperator.NOT -> !exitConditions.first().shouldExit(stock, entryQuote, quote)
        }
    }

    override fun reason(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): String? {
        // Return the reason from the first matching exit condition
        return exitConditions
            .firstOrNull { it.shouldExit(stock, entryQuote, quote) }
            ?.exitReason()
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

        return exitConditions.joinToString(op) { it.description() }
    }

    override fun exitPrice(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Double {
        return if (quote.closePrice < 1.0) {
            stock.getPreviousQuote(quote)?.closePrice ?: 0.0
        } else {
            quote.closePrice
        }
    }

    /**
     * Returns all exit conditions in this composite strategy.
     * Used for automatic metadata extraction.
     */
    fun getConditions(): List<ExitCondition> = exitConditions
}

