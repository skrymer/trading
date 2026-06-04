package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.strategy.condition.LogicalOperator
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

/**
 * A nested AND/OR/NOT group of entry conditions, itself an [EntryCondition] (Composite
 * pattern). Lets a custom strategy express an arbitrary boolean tree — e.g.
 * `(A AND B) OR (C AND D)` — that a flat single-operator condition list cannot.
 *
 * Built by [com.skrymer.udgaard.backtesting.service.DynamicStrategyBuilder] from nested
 * config; never registered with [com.skrymer.udgaard.backtesting.service.ConditionRegistry],
 * so it carries no discoverable wire-type.
 */
class EntryConditionGroup(
  private val operator: LogicalOperator,
  val members: List<EntryCondition>,
) : EntryCondition {
  init {
    require(members.isNotEmpty()) { "A condition group requires at least one member" }
    require(operator != LogicalOperator.NOT || members.size == 1) {
      "NOT operator requires exactly one member, but ${members.size} were provided"
    }
  }

  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean =
    when (operator) {
      LogicalOperator.AND -> members.all { it.evaluate(stock, quote, context) }
      LogicalOperator.OR -> members.any { it.evaluate(stock, quote, context) }
      LogicalOperator.NOT -> !members.first().evaluate(stock, quote, context)
    }

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
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val childResults = members.map { it.evaluateWithDetails(stock, quote, context) }
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

  override fun parseConfig(parameters: Map<String, Any>): EntryCondition = this

  /**
   * All leaf conditions reachable through this group, recursing into nested groups.
   * Used so stateful conditions inside a group still get reset between backtests.
   */
  fun leaves(): List<EntryCondition> =
    members.flatMap { if (it is EntryConditionGroup) it.leaves() else listOf(it) }
}
