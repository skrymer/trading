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
 * Entry condition that checks if market breadth EMAs are bullishly aligned.
 * Configurable EMA periods to check. Available periods: 5, 10, 20, 50.
 *
 * Examples:
 * - (5, 10, 20) = full triple alignment (strictest)
 * - (5, 20) = fast above slow (looser momentum check)
 * - (5, 10) = short-term momentum only
 *
 * @param emaPeriods EMA periods to check in descending speed order. Each must be > the next.
 */
@Component
class MarketBreadthEmaAlignmentCondition(
  private val emaPeriods: List<Int> = listOf(5, 10, 20),
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val breadth = context.getMarketBreadth(quote.date) ?: return false
    val values = emaPeriods.map { getEma(breadth, it) }
    return values.zipWithNext().all { (fast, slow) -> fast > slow }
  }

  override fun description(): String =
    "Market breadth EMA alignment (${emaPeriods.joinToString(" > ") { "EMA$it" }})"

  override fun getMetadata() =
    ConditionMetadata(
      type = "marketBreadthEmaAlignment",
      displayName = "Market Breadth EMA Alignment",
      description = "Market breadth EMAs are bullishly stacked in order",
      parameters =
        listOf(
          ParameterMetadata(
            name = "emaPeriods",
            displayName = "EMA Periods",
            type = "string",
            defaultValue = "5,10,20",
          ),
        ),
      category = "Market",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val passed = evaluate(stock, quote, context)
    val breadth = context.getMarketBreadth(quote.date)
    val values = emaPeriods.map { period -> period to (breadth?.let { getEma(it, period) } ?: 0.0) }
    val valuesStr = values.joinToString(", ") { "EMA${it.first}=%.1f".format(it.second) }
    val thresholdStr = emaPeriods.joinToString(" > ") { "EMA$it" }

    val message =
      if (passed) {
        "Market breadth EMAs aligned: $valuesStr"
      } else {
        "Market breadth EMAs not aligned: $valuesStr"
      }

    return ConditionEvaluationResult(
      conditionType = "MarketBreadthEmaAlignmentCondition",
      description = description(),
      passed = passed,
      actualValue = valuesStr,
      threshold = thresholdStr,
      message = message,
    )
  }

  companion object {
    fun getEma(breadth: MarketBreadthDaily, period: Int): Double =
      when (period) {
        5 -> breadth.ema5
        10 -> breadth.ema10
        20 -> breadth.ema20
        50 -> breadth.ema50
        else -> throw IllegalArgumentException("Unsupported EMA period: $period. Use 5, 10, 20, or 50.")
      }
  }
}
