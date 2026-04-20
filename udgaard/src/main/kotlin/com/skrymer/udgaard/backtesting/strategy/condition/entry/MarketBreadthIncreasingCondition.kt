package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.MarketBreadthDaily
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that passes when market breadth has strictly increased
 * for N consecutive trading-day readings up to and including quote.date.
 *
 * Weekend/holiday gaps are handled by iterating the breadth map directly —
 * we take the most recent N+1 readings at or before quote.date, regardless
 * of calendar spacing.
 *
 * @param days Number of consecutive daily up-moves required (>= 1).
 */
@Component
class MarketBreadthIncreasingCondition(
  private val days: Int = 3,
) : EntryCondition {
  init {
    require(days >= 1) { "days must be >= 1, got $days" }
  }

  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val recent = recentReadings(context, quote) ?: return false
    return recent.zipWithNext().all { (prev, curr) -> curr.breadthPercent > prev.breadthPercent }
  }

  private fun recentReadings(context: BacktestContext, quote: StockQuote): List<MarketBreadthDaily>? {
    val sorted = context.marketBreadthMap
      .filterKeys { !it.isAfter(quote.date) }
      .toSortedMap()
      .values
      .toList()
    if (sorted.size < days + 1) return null
    return sorted.takeLast(days + 1)
  }

  override fun description(): String = "Market breadth increasing for $days consecutive days"

  override fun getMetadata() =
    ConditionMetadata(
      type = "marketBreadthIncreasing",
      displayName = "Market Breadth Increasing",
      description = "Market breadth strictly increases over N consecutive trading days",
      parameters =
        listOf(
          ParameterMetadata(
            name = "days",
            displayName = "Days",
            type = "number",
            defaultValue = 3,
            min = 1,
            max = 20,
          ),
        ),
      category = "Market",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val recent = recentReadings(context, quote)
    val passed = recent != null &&
      recent.zipWithNext().all { (prev, curr) -> curr.breadthPercent > prev.breadthPercent }

    val streak = recent?.let { countTrailingUpMoves(it) } ?: 0
    val series = recent?.joinToString(" → ") { "%.1f".format(it.breadthPercent) } ?: "insufficient history"

    val message =
      if (passed) {
        "Market breadth increased for $days days ($series) ✓"
      } else {
        "Market breadth has not increased for $days consecutive days — observed streak: $streak ($series)"
      }

    return ConditionEvaluationResult(
      conditionType = "MarketBreadthIncreasingCondition",
      description = description(),
      passed = passed,
      actualValue = "$streak days",
      threshold = ">= $days days",
      message = message,
    )
  }

  private fun countTrailingUpMoves(readings: List<MarketBreadthDaily>): Int {
    var count = 0
    for (i in readings.size - 1 downTo 1) {
      if (readings[i].breadthPercent > readings[i - 1].breadthPercent) count++ else break
    }
    return count
  }
}
