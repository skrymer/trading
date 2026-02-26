package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

interface EntryStrategy {
  fun description(): String

  fun test(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean
}
