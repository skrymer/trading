package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain

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
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): Boolean = compositeStrategy.test(stock, quote)

  override fun testWithDetails(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ) = compositeStrategy.testWithDetails(stock, quote)
}
