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
 * - Price is at least 2% below an order block older than 30 days
 */
@RegisteredStrategy(name = "VegardPlanEtf", type = StrategyType.ENTRY)
class VegardPlanEtfEntryStrategy: EntryStrategy {
  private val compositeStrategy = entryStrategy {
    uptrend()
    inValueZone(1.4)
  }

  override fun description() = "Vegard Plan ETF entry strategy"

  override fun test(stock: Stock, quote: StockQuote): Boolean {
    return compositeStrategy.test(stock, quote)
  }
}