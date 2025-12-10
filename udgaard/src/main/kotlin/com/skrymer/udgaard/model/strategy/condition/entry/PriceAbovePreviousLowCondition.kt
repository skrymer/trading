package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.entry.EntryCondition
import org.springframework.stereotype.Component

/**
 * Entry condition that checks if current price is above the previous day's low.
 * This ensures we're not entering on a breakdown below prior support.
 * Returns true if no previous quote exists (defaults to 0.0).
 */
@Component
class PriceAbovePreviousLowCondition : EntryCondition {
  override fun evaluate(
    stock: Stock,
    quote: StockQuote,
  ): Boolean {
    val previousQuote = stock.getPreviousQuote(quote)
    return quote.closePrice > (previousQuote?.low ?: 0.0)
  }

  override fun description(): String = "Price above previous low"

  override fun getMetadata() =
    ConditionMetadata(
      type = "priceAbovePreviousLow",
      displayName = "Price Above Previous Low",
      description = "Current price is above previous day's low",
      parameters = emptyList(),
      category = "Stock",
    )

  override fun evaluateWithDetails(
    stock: Stock,
    quote: StockQuote,
  ): ConditionEvaluationResult {
    val passed = evaluate(stock, quote)
    val message = if (passed) description() + " ✓" else description() + " ✗"

    return ConditionEvaluationResult(
      conditionType = "PriceAbovePreviousLowCondition",
      description = description(),
      passed = passed,
      actualValue = null,
      threshold = null,
      message = message,
    )
  }
}
