package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.dto.EntrySignalDetails
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.strategy.condition.LogicalOperator
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EntryCondition
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

/**
 * A composite entry strategy that combines multiple conditions using logical operators.
 * This allows for flexible composition of trading rules without creating new strategy classes.
 */
class CompositeEntryStrategy(
  private val conditions: List<EntryCondition>,
  private val operator: LogicalOperator = LogicalOperator.AND,
  private val strategyDescription: String? = null,
) : DetailedEntryStrategy {
  override fun test(
    stock: Stock,
    quote: StockQuote,
  ): Boolean {
    if (conditions.isEmpty()) return false

    return when (operator) {
      LogicalOperator.AND -> conditions.all { it.evaluate(stock, quote) }
      LogicalOperator.OR -> conditions.any { it.evaluate(stock, quote) }
      LogicalOperator.NOT -> !conditions.first().evaluate(stock, quote)
    }
  }

  override fun test(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    if (conditions.isEmpty()) return false

    return when (operator) {
      LogicalOperator.AND -> conditions.all { it.evaluate(stock, quote, context) }
      LogicalOperator.OR -> conditions.any { it.evaluate(stock, quote, context) }
      LogicalOperator.NOT -> !conditions.first().evaluate(stock, quote, context)
    }
  }

  override fun description(): String {
    if (strategyDescription != null) {
      return strategyDescription
    }

    val op =
      when (operator) {
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
  fun getConditions(): List<EntryCondition> = conditions

  /**
   * Evaluates the strategy and returns detailed condition results.
   * Useful for understanding why an entry signal triggered or failed.
   *
   * @return Detailed entry signal information including all condition results
   */
  override fun testWithDetails(
    stock: Stock,
    quote: StockQuote,
  ): EntrySignalDetails {
    val conditionResults = conditions.map { it.evaluateWithDetails(stock, quote) }

    val allConditionsMet =
      when (operator) {
        LogicalOperator.AND -> conditionResults.all { it.passed }
        LogicalOperator.OR -> conditionResults.any { it.passed }
        LogicalOperator.NOT -> !conditionResults.first().passed
      }

    return EntrySignalDetails(
      strategyName = this::class.simpleName ?: "CompositeEntryStrategy",
      strategyDescription = description(),
      conditions = conditionResults,
      allConditionsMet = allConditionsMet,
    )
  }

  override fun testWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): EntrySignalDetails {
    val conditionResults = conditions.map { it.evaluateWithDetails(stock, quote, context) }

    val allConditionsMet =
      when (operator) {
        LogicalOperator.AND -> conditionResults.all { it.passed }
        LogicalOperator.OR -> conditionResults.any { it.passed }
        LogicalOperator.NOT -> !conditionResults.first().passed
      }

    return EntrySignalDetails(
      strategyName = this::class.simpleName ?: "CompositeEntryStrategy",
      strategyDescription = description(),
      conditions = conditionResults,
      allConditionsMet = allConditionsMet,
    )
  }
}
