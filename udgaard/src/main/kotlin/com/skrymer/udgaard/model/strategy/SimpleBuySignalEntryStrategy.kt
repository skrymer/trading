package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

/**
 * Simple entry strategy that only checks for buy signal.
 * Enters when there is a buy signal (lastBuySignal after lastSellSignal).
 */
@RegisteredStrategy(name = "SimpleBuySignal", type = StrategyType.ENTRY)
class SimpleBuySignalEntryStrategy : EntryStrategy {
  private val compositeStrategy = entryStrategy {
    buySignal(currentOnly = false)
  }

  override fun description() = "Simple buy signal entry strategy"

  override fun test(stock: Stock, quote: StockQuote): Boolean {
    return compositeStrategy.test(stock, quote)
  }
}
