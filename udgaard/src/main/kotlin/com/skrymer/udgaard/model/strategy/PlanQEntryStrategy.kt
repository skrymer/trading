package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain

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
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): Boolean = compositeStrategy.test(stock, quote)

  override fun testWithDetails(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ) = compositeStrategy.testWithDetails(stock, quote)
}
