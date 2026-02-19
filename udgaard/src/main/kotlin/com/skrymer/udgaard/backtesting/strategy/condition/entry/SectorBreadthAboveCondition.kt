package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if sector breadth is above a specific threshold.
 * Like marketBreadthAbove but for the stock's sector.
 *
 * @param threshold The minimum sector bull percentage required (0.0 to 100.0)
 */
@Component
class SectorBreadthAboveCondition(
  private val threshold: Double = 50.0,
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
    val bullPct = context.getSectorBreadth(stock.sectorSymbol, quote.date)?.bullPercentage ?: return false
    return bullPct >= threshold
  }

  override fun description(): String = "Sector breadth above ${threshold.toInt()}%"

  override fun getMetadata() =
    ConditionMetadata(
      type = "sectorBreadthAbove",
      displayName = "Sector Breadth Above Threshold",
      description = "Sector breadth (% of stocks in uptrend) is above threshold",
      parameters =
        listOf(
          ParameterMetadata(
            name = "threshold",
            displayName = "Threshold",
            type = "number",
            defaultValue = 50.0,
            min = 0,
            max = 100,
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
    val bullPct = context.getSectorBreadth(stock.sectorSymbol, quote.date)?.bullPercentage ?: 0.0

    val message =
      if (passed) {
        "Sector breadth %.1f%% is above threshold %.0f%%".format(bullPct, threshold)
      } else {
        "Sector breadth %.1f%% is below threshold %.0f%%".format(bullPct, threshold)
      }

    return ConditionEvaluationResult(
      conditionType = "SectorBreadthAboveCondition",
      description = description(),
      passed = passed,
      actualValue = "%.1f%%".format(bullPct),
      threshold = ">= %.0f%%".format(threshold),
      message = message,
    )
  }
}
