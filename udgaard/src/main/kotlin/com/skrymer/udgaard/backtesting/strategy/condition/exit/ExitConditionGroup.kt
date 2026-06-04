package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.strategy.condition.LogicalOperator
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

/**
 * A nested AND/OR/NOT group of exit conditions, itself an [ExitCondition] (Composite
 * pattern). Lets a custom exit strategy express an arbitrary boolean tree that a flat
 * single-operator condition list cannot.
 *
 * Built by [com.skrymer.udgaard.backtesting.service.DynamicStrategyBuilder] from nested
 * config; never registered with [com.skrymer.udgaard.backtesting.service.ConditionRegistry],
 * so it carries no discoverable wire-type.
 */
class ExitConditionGroup(
  private val operator: LogicalOperator,
  val members: List<ExitCondition>,
) : ExitCondition {
  init {
    require(members.isNotEmpty()) { "A condition group requires at least one member" }
    require(operator != LogicalOperator.NOT || members.size == 1) {
      "NOT operator requires exactly one member, but ${members.size} were provided"
    }
  }

  // Under OR, the child that triggered the exit — so exitReason() can name it. Thread-local
  // because a backtest evaluates many positions concurrently.
  private val lastMatched = ThreadLocal<ExitCondition?>()

  override fun shouldExit(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): Boolean = combine { it.shouldExit(stock, entryQuote, quote) }

  override fun shouldExit(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean = combine { it.shouldExit(stock, entryQuote, quote, context) }

  private fun combine(test: (ExitCondition) -> Boolean): Boolean {
    lastMatched.set(null)
    return when (operator) {
      LogicalOperator.AND -> members.all(test)
      LogicalOperator.OR -> {
        val matched = members.firstOrNull(test)
        lastMatched.set(matched)
        matched != null
      }
      LogicalOperator.NOT -> !test(members.first())
    }
  }

  override fun exitReason(): String = lastMatched.get()?.exitReason() ?: description()

  override fun description(): String {
    val joiner =
      when (operator) {
        LogicalOperator.AND -> " AND "
        LogicalOperator.OR -> " OR "
        LogicalOperator.NOT -> "NOT "
      }
    return "(" + members.joinToString(joiner) { it.description() } + ")"
  }

  override fun getMetadata(): ConditionMetadata =
    throw UnsupportedOperationException("Condition groups are not registrable conditions")

  override fun evaluateWithDetails(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): ConditionEvaluationResult {
    val childResults = members.map { it.evaluateWithDetails(stock, entryQuote, quote) }
    val passed =
      when (operator) {
        LogicalOperator.AND -> childResults.all { it.passed }
        LogicalOperator.OR -> childResults.any { it.passed }
        LogicalOperator.NOT -> !childResults.first().passed
      }
    return ConditionEvaluationResult(
      conditionType = "ConditionGroup",
      description = description(),
      passed = passed,
      message = childResults.joinToString("; ") { "${it.description}: ${if (it.passed) "PASS" else "FAIL"}" },
    )
  }

  override fun parseConfig(parameters: Map<String, Any>): ExitCondition = this

  /**
   * All leaf conditions reachable through this group, recursing into nested groups.
   * Used so stateful conditions inside a group still get reset between backtests.
   */
  fun leaves(): List<ExitCondition> =
    members.flatMap { if (it is ExitConditionGroup) it.leaves() else listOf(it) }

  /**
   * The proximity warning chips of this group's leaf conditions, so a leaf moved into a
   * group keeps the near-exit warning it had as a top-level condition. Silent under NOT,
   * where proximity is ill-defined (the composite triggers when the inner result is false).
   */
  fun leafProximities(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): List<ExitProximity> {
    if (operator == LogicalOperator.NOT) return emptyList()
    return members.flatMap {
      if (it is ExitConditionGroup) {
        it.leafProximities(stock, entryQuote, quote)
      } else {
        listOfNotNull(it.proximity(stock, entryQuote, quote))
      }
    }
  }
}
