package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

class PlanMVEntryStrategy : EntryStrategy {
  private val compositeStrategy =
    entryStrategy {
      spyUptrend()
      marketBreadthAbove(50.0)
    }

  override fun description(): String {
    TODO("Not yet implemented")
  }

  override fun test(stock: Stock, quote: StockQuote): Boolean {
    TODO("Not yet implemented")
  }
}
