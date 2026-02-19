package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if sector breadth is accelerating.
 * Sector's ema5 is rising relative to ema20, meaning participation is
 * increasing faster than the broader trend. Good for catching sector rotation early.
 *
 * Logic: ema5 > ema20 AND (ema5 - ema20) > threshold
 *
 * @param threshold Minimum spread between ema5 and ema20 (percentage points)
 */
@Component
class SectorBreadthAcceleratingCondition(
  private val threshold: Double = 5.0,
) : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
  ): Boolean = evaluate(stock, quote, BacktestContext.EMPTY)

  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val breadth = context.getSectorBreadth(stock.sectorSymbol, quote.date) ?: return false
    val spread = breadth.ema5 - breadth.ema20
    return breadth.ema5 > breadth.ema20 && spread > threshold
  }

  override fun description(): String =
    "Sector breadth accelerating (EMA5-EMA20 spread > ${threshold.toInt()}pp)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "sectorBreadthAccelerating",
      displayName = "Sector Breadth Accelerating",
      description = "Sector breadth EMA5 is rising faster than EMA20 by more than threshold",
      parameters =
        listOf(
          ParameterMetadata(
            name = "threshold",
            displayName = "Minimum Spread (pp)",
            type = "number",
            defaultValue = 5.0,
            min = 0,
            max = 50,
          ),
        ),
      category = "Sector",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
  ): ConditionEvaluationResult = evaluateWithDetails(stock, quote, BacktestContext.EMPTY)

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val passed = evaluate(stock, quote, context)
    val breadth = context.getSectorBreadth(stock.sectorSymbol, quote.date)
    val ema5 = breadth?.ema5 ?: 0.0
    val ema20 = breadth?.ema20 ?: 0.0
    val spread = ema5 - ema20

    val message =
      if (passed) {
        "Sector breadth accelerating: EMA5-EMA20 spread %.1fpp > %.1fpp".format(spread, threshold)
      } else {
        "Sector breadth not accelerating: EMA5-EMA20 spread %.1fpp (requires > %.1fpp)".format(spread, threshold)
      }

    return ConditionEvaluationResult(
      conditionType = "SectorBreadthAcceleratingCondition",
      description = description(),
      passed = passed,
      actualValue = "%.1fpp spread".format(spread),
      threshold = "> %.1fpp".format(threshold),
      message = message,
    )
  }
}
