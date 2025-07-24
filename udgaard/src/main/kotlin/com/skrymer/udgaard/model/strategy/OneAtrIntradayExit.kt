package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote

class OneAtrIntradayExit: ExitStrategy {
  override fun match(
    entryQuote: StockQuote?,
    quote: StockQuote,
    previousQuote: StockQuote?
  ) = quote.low < (entryQuote?.closePrice?.minus(entryQuote.atr) ?: 0.0)

  override fun reason(
    entryQuote: StockQuote?,
    quote: StockQuote,
    previousQuote: StockQuote?
  ) = "Intraday price hit 1 atr from entry"

  override fun description() = "One ATR from entry"

  override fun exitPrice(
    entryQuote: StockQuote?,
    quote: StockQuote,
    previousQuote: StockQuote?
  ) = entryQuote?.closePrice?.minus(entryQuote.atr) ?: 0.0
}