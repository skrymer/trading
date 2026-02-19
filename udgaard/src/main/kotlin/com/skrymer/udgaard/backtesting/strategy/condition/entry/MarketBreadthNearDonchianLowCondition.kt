package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.dto.ParameterMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if market breadth is near its Donchian lower band.
 * Contrarian/mean-reversion signal: market is washed out and due for a bounce.
 *
 * Logic: breadthPercent <= donchianLowerBand + (donchianUpperBand - donchianLowerBand) * percentile
 *
 * @param percentile How close to the Donchian low (0.0-1.0). Default 0.10 = bottom 10% of range.
 */
@Component
class MarketBreadthNearDonchianLowCondition(
  private val percentile: Double = 0.10,
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
    val breadth = context.getMarketBreadth(quote.date) ?: return false
    val range = breadth.donchianUpperBand - breadth.donchianLowerBand
    if (range <= 0) return false
    val threshold = breadth.donchianLowerBand + range * percentile
    return breadth.breadthPercent <= threshold
  }

  override fun description(): String =
    "Market breadth near Donchian low (bottom ${(percentile * 100).toInt()}%)"

  override fun getMetadata() =
    ConditionMetadata(
      type = "marketBreadthNearDonchianLow",
      displayName = "Market Breadth Near Donchian Low",
      description = "Market breadth is in the lower percentile of its Donchian channel (mean reversion)",
      parameters =
        listOf(
          ParameterMetadata(
            name = "percentile",
            displayName = "Percentile",
            type = "number",
            defaultValue = 0.10,
            min = 0.0,
            max = 1.0,
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
    val breadth = context.getMarketBreadth(quote.date)
    val breadthPct = breadth?.breadthPercent ?: 0.0
    val upper = breadth?.donchianUpperBand ?: 0.0
    val lower = breadth?.donchianLowerBand ?: 0.0
    val range = upper - lower
    val threshold = if (range > 0) lower + range * percentile else 0.0

    val message =
      if (passed) {
        "Market breadth %.1f%% near Donchian low (threshold %.1f%%, range %.1f-%.1f)".format(
          breadthPct,
          threshold,
          lower,
          upper,
        )
      } else {
        "Market breadth %.1f%% above Donchian low zone (threshold %.1f%%, range %.1f-%.1f)".format(
          breadthPct,
          threshold,
          lower,
          upper,
        )
      }

    return ConditionEvaluationResult(
      conditionType = "MarketBreadthNearDonchianLowCondition",
      description = description(),
      passed = passed,
      actualValue = "%.1f%%".format(breadthPct),
      threshold = "<= %.1f%% (bottom ${(percentile * 100).toInt()}%%)".format(threshold),
      message = message,
    )
  }
}
