package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.dto.EntrySignalDetails
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.strategy.condition.LogicalOperator
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EntryCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EntryConditionGroup
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
  init {
    require(operator != LogicalOperator.NOT || conditions.size == 1) {
      "NOT operator requires exactly one entry condition, but ${conditions.size} were provided"
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
   * Returns the flattened leaf conditions of this composite strategy, recursing into any
   * nested [EntryConditionGroup]s. This is useful for backtest lifecycle management (e.g.,
   * resetting stateful conditions) — a stateful condition nested inside a group must still
   * be reachable here or it won't get reset between backtests.
   */
  fun getConditions(): List<EntryCondition> =
    conditions.flatMap { if (it is EntryConditionGroup) it.leaves() else listOf(it) }

  /**
   * Evaluates the strategy and returns detailed condition results.
   * Useful for understanding why an entry signal triggered or failed.
   *
   * @return Detailed entry signal information including all condition results
   */
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
