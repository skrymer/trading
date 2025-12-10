package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.entry.EntryCondition
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if SPY is in an uptrend.
 * SPY is considered in uptrend when 10 EMA > 20 EMA and price > 50 EMA.
 */
@Component
class SpyUptrendCondition : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
  ): Boolean = quote.spyInUptrend

  override fun description(): String = "SPY in uptrend"

  override fun getMetadata() =
    ConditionMetadata(
      type = "spyUptrend",
      displayName = "SPY in Uptrend",
      description = "SPY trend is 'Uptrend'",
      parameters = emptyList(),
      category = "SPY",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
  ): ConditionEvaluationResult {
    val passed = evaluate(stock, quote)

    val message =
      if (passed) {
        "SPY in uptrend (10 EMA > 20 EMA, price > 50 EMA) ✓"
      } else {
        "SPY not in uptrend ✗"
      }

    return ConditionEvaluationResult(
      conditionType = "SpyUptrendCondition",
      description = description(),
      passed = passed,
      actualValue = if (passed) "Uptrend" else "Not uptrend",
      threshold = "10 > 20 EMA, price > 50 EMA",
      message = message,
    )
  }
}
