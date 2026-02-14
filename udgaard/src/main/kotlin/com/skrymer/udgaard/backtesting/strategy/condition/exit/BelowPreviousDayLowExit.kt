package com.skrymer.udgaard.backtesting.strategy.condition.exit

import com.skrymer.udgaard.backtesting.dto.ConditionMetadata
import com.skrymer.udgaard.backtesting.strategy.condition.exit.ExitCondition
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Exit condition that triggers when price closes below previous day's low.
 * This helps protect against sharp reversals.
 */
@Component
class BelowPreviousDayLowExit : ExitCondition {
  override fun shouldExit(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): Boolean {
    val previousQuote = stock.getPreviousQuote(quote)
    return quote.closePrice < (previousQuote?.low ?: 0.0)
  }

  override fun exitReason(): String = "Price closed below previous day's low"

  override fun description(): String = "Below previous day low"

  override fun getMetadata() =
    ConditionMetadata(
      type = "belowPreviousDayLow",
      displayName = "Below Previous Day Low",
      description = "Exit when price closes below previous day's low",
      parameters = emptyList(),
      category = "StopLoss",
    )
}
