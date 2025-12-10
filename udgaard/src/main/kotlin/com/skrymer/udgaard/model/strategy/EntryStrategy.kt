package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

interface EntryStrategy {
  fun description(): String

  fun test(
    stock: Stock,
    quote: StockQuote,
  ): Boolean
}
