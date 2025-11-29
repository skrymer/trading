package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

/**
 * Plan ETF entry strategy using composition.
 * Enters when:
 * - Stock is in uptrend
 * - Price is within value zone (< 20 EMA + 1.4 ATR)
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

  /**
   * Get the underlying composite strategy for metadata extraction.
   */
  fun getCompositeStrategy(): CompositeEntryStrategy = compositeStrategy
}