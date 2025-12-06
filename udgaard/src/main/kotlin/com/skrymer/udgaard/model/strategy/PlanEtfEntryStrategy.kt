package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

/**
 * Plan ETF entry strategy using composition.
 * Enters when:
 * - Stock is in uptrend
 * - Has buy signal
 * - Heatmap < 70
 * - Price is within value zone (< 20 EMA + 2 ATR)
 * - Price is at least 2% below an order block older than 15 days
 */
@RegisteredStrategy(name = "PlanEtf", type = StrategyType.ENTRY)
class PlanEtfEntryStrategy: DetailedEntryStrategy {
  private val compositeStrategy = entryStrategy {
    uptrend()
    buySignal(daysOld = -1)  // Accept any buy signal age
    heatmap(70)
    inValueZone(2.0)
    belowOrderBlock(percentBelow = 2.0, ageInDays = 15)
  }

  override fun description() = "Plan ETF entry strategy"

  override fun test(stock: Stock, quote: StockQuote): Boolean {
    return compositeStrategy.test(stock, quote)
  }

  override fun testWithDetails(stock: Stock, quote: StockQuote) = compositeStrategy.testWithDetails(stock, quote)
}