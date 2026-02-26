package com.skrymer.udgaard.backtesting.strategy.condition.entry

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationResult
import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if SPY's price is in an uptrend.
 * Uses SPY's EMA-based trend determination: (EMA5 > EMA10 > EMA20) AND (Price > EMA50).
 * This is different from MarketUptrendCondition which checks breadth % > breadth EMA10.
 */
@Component
class SpyPriceUptrendCondition : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean = context.getSpyQuote(quote.date)?.isInUptrend() ?: false

  override fun description(): String = "SPY price in uptrend"

  override fun getMetadata() =
    ConditionMetadata(
      type = "spyPriceUptrend",
      displayName = "SPY Price in Uptrend",
      description = "SPY price is in uptrend based on EMA alignment (EMA5 > EMA10 > EMA20, Price > EMA50)",
      parameters = emptyList(),
      category = "Market",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): ConditionEvaluationResult {
    val passed = evaluate(stock, quote, context)
    val spyQuote = context.getSpyQuote(quote.date)
    val trend = spyQuote?.trend ?: "N/A"
    val close = spyQuote?.closePrice ?: 0.0

    val message =
      if (passed) {
        "SPY price %.2f is in %s ✓".format(close, trend)
      } else {
        "SPY price %.2f is in %s ✗".format(close, trend)
      }

    return ConditionEvaluationResult(
      conditionType = "SpyPriceUptrendCondition",
      description = description(),
      passed = passed,
      actualValue = trend,
      threshold = "Uptrend",
      message = message,
    )
  }
}
