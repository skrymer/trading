package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.SectorBreadthDaily
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that passes when the configured sector's breadth has strictly
 * increased for N consecutive trading-day readings up to and including quote.date.
 *
 * Unlike SectorBreadthAboveCondition, this condition evaluates a sector configured
 * on the condition itself, not the candidate stock's own sector. This makes it
 * usable as a macro filter — e.g., require XLK (Technology) to be in a rising-breadth
 * regime regardless of which stock is being evaluated.
 *
 * @param days Number of consecutive daily up-moves required (>= 1).
 * @param sectorSymbol Sector ETF symbol (e.g., "XLK", "XLF").
 */
@Component
class SectorBreadthIncreasingCondition(
  private val days: Int = 3,
  private val sectorSymbol: String = "XLK",
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val recent = recentReadings(context, quote) ?: return false
    return recent.zipWithNext().all { (prev, curr) -> curr.bullPercentage > prev.bullPercentage }
  }

  private fun recentReadings(context: BacktestContext, quote: StockQuote): List<SectorBreadthDaily>? {
    val sectorMap = context.sectorBreadthMap[sectorSymbol] ?: return null
    val sorted = sectorMap
      .filterKeys { !it.isAfter(quote.date) }
      .toSortedMap()
      .values
      .toList()
    if (sorted.size < days + 1) return null
    return sorted.takeLast(days + 1)
  }

  override fun description(): String = "$sectorSymbol sector breadth increasing for $days consecutive days"

  override fun getMetadata() =
    ConditionMetadata(
      type = "sectorBreadthIncreasing",
      displayName = "Sector Breadth Increasing",
      description = "Configured sector's breadth strictly increases over N consecutive trading days",
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
          ParameterMetadata(
            name = "sectorSymbol",
            displayName = "Sector Symbol",
            type = "string",
            defaultValue = "XLK",
          ),
        ),
      category = "Sector",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val recent = recentReadings(context, quote)
    val passed = recent != null &&
      recent.zipWithNext().all { (prev, curr) -> curr.bullPercentage > prev.bullPercentage }

    val streak = recent?.let { countTrailingUpMoves(it) } ?: 0
    val series = recent?.joinToString(" → ") { "%.1f".format(it.bullPercentage) } ?: "insufficient history"

    val message =
      if (passed) {
        "$sectorSymbol sector breadth increased for $days days ($series) ✓"
      } else {
        "$sectorSymbol sector breadth has not increased for $days consecutive days — observed streak: $streak ($series)"
      }

    return ConditionEvaluationResult(
      conditionType = "SectorBreadthIncreasingCondition",
      description = description(),
      passed = passed,
      actualValue = "$streak days",
      threshold = ">= $days days",
      message = message,
    )
  }

  private fun countTrailingUpMoves(readings: List<SectorBreadthDaily>): Int {
    var count = 0
    for (i in readings.size - 1 downTo 1) {
      if (readings[i].bullPercentage > readings[i - 1].bullPercentage) count++ else break
    }
    return count
  }
}
