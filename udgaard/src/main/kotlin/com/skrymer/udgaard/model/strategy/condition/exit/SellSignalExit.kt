package com.skrymer.udgaard.model.strategy.condition.exit

import com.skrymer.udgaard.controller.dto.ConditionMetadata
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote
import org.springframework.stereotype.Component

/**
 * Exit condition that triggers on a sell signal.
 */
@Component
class SellSignalExit : ExitCondition {
  override fun shouldExit(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): Boolean = quote.hasSellSignal()

  override fun exitReason(): String = "Sell signal"

  override fun description(): String = "Sell signal"

  override fun getMetadata() =
    ConditionMetadata(
      type = "sellSignal",
      displayName = "Sell Signal",
      description = "Exit when sell signal appears",
      parameters = emptyList(),
      category = "Signal",
    )
}
