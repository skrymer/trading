package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if market breadth is above a specific threshold.
 * Market breadth is measured as the percentage of stocks in uptrend.
 *
 * @param threshold The minimum market breadth percentage required (0.0 to 100.0)
 */
@Component
class MarketBreadthAboveCondition(
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
  ): Boolean = (context.getMarketBreadth(quote.date)?.breadthPercent ?: 0.0) >= threshold

  override fun description(): String = "Market breadth above ${threshold.toInt()}%"

  override fun getMetadata() =
    ConditionMetadata(
      type = "marketBreadthAbove",
      displayName = "Market Breadth Above Threshold",
      description = "Market breadth (% of stocks in uptrend) is above threshold",
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
      category = "Market",
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
    val actualBreadth = context.getMarketBreadth(quote.date)?.breadthPercent ?: 0.0

    val message =
      if (passed) {
        "Market breadth is %.1f%% (≥ %.0f%%) ✓".format(actualBreadth, threshold)
      } else {
        "Market breadth is %.1f%% (requires ≥ %.0f%%) ✗".format(actualBreadth, threshold)
      }

    return ConditionEvaluationResult(
      conditionType = "MarketBreadthAboveCondition",
      description = description(),
      passed = passed,
      actualValue = "%.1f%%".format(actualBreadth),
      threshold = "≥ %.0f%%".format(threshold),
      message = message,
    )
  }
}
