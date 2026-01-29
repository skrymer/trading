package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain
import com.skrymer.udgaard.model.strategy.condition.entry.EntryCondition
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if the market is in an uptrend.
 * Market is considered in uptrend when bull percentage is over 10 EMA.
 */
@Component
class MarketUptrendCondition : EntryCondition {
  override fun evaluate(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): Boolean = quote.isMarketInUptrend()

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
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): ConditionEvaluationResult {
    val passed = evaluate(stock, quote)
    val bullPercentage = quote.marketBullPercentage
    val ema10 = quote.marketBullPercentage_10ema

    val message =
      if (passed) {
        "Market bull percentage %.1f%% is above 10 EMA (%.1f%%) ✓".format(bullPercentage, ema10)
      } else {
        "Market bull percentage %.1f%% is below 10 EMA (%.1f%%) ✗".format(bullPercentage, ema10)
      }

    return ConditionEvaluationResult(
      conditionType = "MarketUptrendCondition",
      description = description(),
      passed = passed,
      actualValue = "%.1f%%".format(bullPercentage),
      threshold = "> %.1f%% (10 EMA)".format(ema10),
      message = message,
    )
  }
}
