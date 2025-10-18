package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

class TenTwentyBearishCross: ExitStrategy {
  override fun match(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote
  ) = quote.closePriceEMA10 < quote.closePriceEMA20

  override fun reason(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote
  ) = "10/20 bearish cross"

  override fun description() = "Ten twenty bearish cross exit."
}