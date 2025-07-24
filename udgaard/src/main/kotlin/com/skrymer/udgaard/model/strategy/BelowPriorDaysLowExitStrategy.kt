package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote

class BelowPriorDaysLowExitStrategy: ExitStrategy {
  override fun match(
    entryQuote: StockQuote?,
    quote: StockQuote,
    previousQuote: StockQuote?
  ) = quote.closePrice < (previousQuote?.low ?: 0.0)

  override fun reason(
    entryQuote: StockQuote?,
    quote: StockQuote,
    previousQuote: StockQuote?
  ) = "Price is below previous day low"

  override fun description() = ""
}