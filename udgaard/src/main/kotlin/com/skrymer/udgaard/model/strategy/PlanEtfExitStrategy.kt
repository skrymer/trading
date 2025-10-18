package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

class PlanEtfExitStrategy: ExitStrategy {
  override fun match(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote
  ) = quote.hasSellSignal()
      .or(quote.closePriceEMA10 < quote.closePriceEMA20)

  override fun reason(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote
  ): String? {
    return if(quote.hasSellSignal()) "Sell signal"
    else "10 ema has crossed under the 20 ema"
  }

  override fun description() = "Plan ETF exit strategy"

  override fun exitPrice(stock: Stock, entryQuote: StockQuote?, quote: StockQuote): Double {
    return if(quote.closePrice < 1.0) stock.getPreviousQuote(quote)?.closePrice ?: 0.0
    else quote.closePrice
  }
}