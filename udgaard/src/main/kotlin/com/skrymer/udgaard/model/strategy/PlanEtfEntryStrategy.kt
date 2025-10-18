package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

class PlanEtfEntryStrategy: EntryStrategy {
  override fun description() = "Plan ETF entry strategy"

  override fun test(
    stock: Stock,
    quote: StockQuote
  ) =
    quote.isInUptrend()
      .and(quote.hasBuySignal())
      .and(quote.heatmap < 70)
      .and(quote.closePrice < ((quote.atr * 2) + quote.closePriceEMA20))
}