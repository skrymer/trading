package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

/**
 * Simple entry strategy that only checks for buy signal.
 * Enters when there is a buy signal (lastBuySignal after lastSellSignal).
 */
@RegisteredStrategy(name = "SimpleBuySignal", type = StrategyType.ENTRY)
class SimpleBuySignalEntryStrategy : DetailedEntryStrategy {
  private val compositeStrategy =
    entryStrategy {
      buySignal(daysOld = -1) // Accept any buy signal age
    }

  override fun description() = "Simple buy signal entry strategy"

  override fun test(
    stock: Stock,
    quote: StockQuote,
  ): Boolean = compositeStrategy.test(stock, quote)

  override fun testWithDetails(
    stock: Stock,
    quote: StockQuote,
  ) = compositeStrategy.testWithDetails(stock, quote)
}
