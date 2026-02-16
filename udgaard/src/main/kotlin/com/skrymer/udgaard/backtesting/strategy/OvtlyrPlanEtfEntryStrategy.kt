package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

/**
 * Plan ETF entry strategy using composition - OPTIMIZED VERSION.
 *
 * Enters when:
 * - Stock is in uptrend
 * - Price is within value zone (< 20 EMA + 1.5 ATR)
 * - Price is at least 2% below an order block older than 30 days
 * - Market in uptrend
 */
@RegisteredStrategy(name = "OvtlyrPlanEtf", type = StrategyType.ENTRY)
class OvtlyrPlanEtfEntryStrategy : DetailedEntryStrategy {
  private val compositeStrategy =
    entryStrategy {
      uptrend()
      marketUptrend()
      inValueZone(atrMultiplier = 1.5, emaPeriod = 20)
      belowOrderBlock(percentBelow = 2.0, ageInDays = 30)
    }

  override fun description() = "Ovtlyr Plan ETF entry strategy"

  override fun test(
    stock: Stock,
    quote: StockQuote,
  ): Boolean = compositeStrategy.test(stock, quote)

  override fun testWithDetails(
    stock: Stock,
    quote: StockQuote,
  ) = compositeStrategy.testWithDetails(stock, quote)
}
