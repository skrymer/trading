package com.skrymer.udgaard.model.strategy.condition.entry

import com.skrymer.udgaard.controller.dto.ConditionEvaluationResult
import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import com.skrymer.udgaard.model.strategy.condition.entry.EntryCondition
import org.springframework.stereotype.Component

/**
 * Entry condition that checks donkey channel alignment (AS1 or AS2).
 *
 * AS1: Both market and sector donkey channel scores >= 1
 * AS2: Market donkey channel score == 2
 *
 * Entry allowed when either AS1 OR AS2 is true.
 */
@Component
class DonkeyChannelCondition : EntryCondition {
  override fun evaluate(stock: Stock, quote: StockQuote): Boolean {
    // AS1 or AS2: (market >= 1 && sector >= 1) OR market == 2
    return (quote.marketDonkeyChannelScore >= 1 && quote.sectorDonkeyChannelScore >= 1)
      .or(quote.marketDonkeyChannelScore == 2)
  }

  override fun description(): String = "Donkey channel AS1 or AS2"

  override fun getMetadata() = ConditionMetadata(
    type = "donkeyChannel",
    displayName = "Donkey Channel",
    description = "Stock is in donkey channel conditions",
    parameters = emptyList(),
    category = "Sector"
  )

  override fun evaluateWithDetails(stock: Stock, quote: StockQuote): ConditionEvaluationResult {
    val passed = evaluate(stock, quote)
    val message = if (passed) description() + " ✓" else description() + " ✗"

    return ConditionEvaluationResult(
      conditionType = "DonkeyChannelCondition",
      description = description(),
      passed = passed,
      actualValue = null,
      threshold = null,
      message = message
    )
  }
}
