package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain

/**
 * Project X entry strategy using composition.
 */
@RegisteredStrategy(name = "PlanEtf", type = StrategyType.ENTRY)
class ProjectXEntryStrategy : DetailedEntryStrategy {
  private val compositeStrategy =
    entryStrategy {
      // Structure (up trend)
      priceAbove(50)
      priceAbove(100)
      priceAbove(200)

      // In value zone
      inValueZone(2.5, 5)

      // at least 2 percent below 30 days old order block
      belowOrderBlock(2.0, 30)
    }

  override fun description() = "Project X entry strategy"

  override fun test(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): Boolean = compositeStrategy.test(stock, quote)

  override fun testWithDetails(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ) = compositeStrategy.testWithDetails(stock, quote)
}
