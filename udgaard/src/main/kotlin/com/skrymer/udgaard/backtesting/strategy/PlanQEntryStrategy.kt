package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.dto.EntrySignalDetails
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

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
      inValueZone(atrMultiplier = 1.4, emaPeriod = 20)
    }

  override fun description() = "Vegard Plan ETF entry strategy"

  override fun test(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean = compositeStrategy.test(stock, quote, context)

  override fun testWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): EntrySignalDetails = compositeStrategy.testWithDetails(stock, quote, context)
}
