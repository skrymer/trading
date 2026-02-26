package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if the market is in an uptrend.
 * Market is considered in uptrend when breadth percentage is over 10 EMA.
 */
@Component
class MarketUptrendCondition : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean = context.getMarketBreadth(quote.date)?.isInUptrend() ?: false

  override fun description(): String = "Market in uptrend"

  override fun getMetadata() =
    ConditionMetadata(
      type = "marketUptrend",
      displayName = "Market in Uptrend",
      description = "Market is in uptrend based on breadth",
      parameters = emptyList(),
      category = "Market",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val passed = evaluate(stock, quote, context)
    val breadth = context.getMarketBreadth(quote.date)
    val breadthPercent = breadth?.breadthPercent ?: 0.0
    val ema10 = breadth?.ema10 ?: 0.0

    val message =
      if (passed) {
        "Market breadth %.1f%% is above 10 EMA (%.1f%%) ✓".format(breadthPercent, ema10)
      } else {
        "Market breadth %.1f%% is below 10 EMA (%.1f%%) ✗".format(breadthPercent, ema10)
      }

    return ConditionEvaluationResult(
      conditionType = "MarketUptrendCondition",
      description = description(),
      passed = passed,
      actualValue = "%.1f%%".format(breadthPercent),
      threshold = "> %.1f%% (10 EMA)".format(ema10),
      message = message,
    )
  }
}
