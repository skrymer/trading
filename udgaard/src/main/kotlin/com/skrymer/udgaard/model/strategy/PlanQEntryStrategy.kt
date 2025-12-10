package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

/**
 * Plan ETF entry strategy using composition.
 * Enters when:
 * - Stock is in uptrend
 * - Price is within value zone (< 20 EMA + 1.4 ATR)
 */
@RegisteredStrategy(name = "PlanQEntryStrategy", type = StrategyType.ENTRY)
class PlanQEntryStrategy : DetailedEntryStrategy {
  private val compositeStrategy =
    entryStrategy {
      uptrend()
      inValueZone(1.4)
    }

  override fun description() = "Vegard Plan ETF entry strategy"

  override fun test(
    stock: Stock,
    quote: StockQuote,
  ): Boolean = compositeStrategy.test(stock, quote)

  override fun testWithDetails(
    stock: Stock,
    quote: StockQuote,
  ) = compositeStrategy.testWithDetails(stock, quote)
}
