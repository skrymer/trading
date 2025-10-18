package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

class BelowPriorDaysLowExitStrategy: ExitStrategy {
  override fun match(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote
  ) = quote.closePrice < ( stock.getPreviousQuote(quote)?.low ?: 0.0)

  override fun reason(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ) = "Price is below previous day low"

  override fun description() = ""
}