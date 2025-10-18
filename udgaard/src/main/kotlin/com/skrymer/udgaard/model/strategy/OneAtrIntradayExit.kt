package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

class OneAtrIntradayExit: ExitStrategy {
  override fun match(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote
  ) = quote.low < (entryQuote?.closePrice?.minus(entryQuote.atr) ?: 0.0)

  override fun reason(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote
  ) = "Intraday price hit 1 atr from entry"

  override fun description() = "One ATR from entry"

  override fun exitPrice(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote
  ) = entryQuote?.closePrice?.minus(entryQuote.atr) ?: 0.0
}