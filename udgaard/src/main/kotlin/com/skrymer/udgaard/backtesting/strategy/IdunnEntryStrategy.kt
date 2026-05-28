package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.dto.EntrySignalDetails
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

/**
 * Idunn — pullback-to-EMA20 reversal entry in a market uptrend.
 *
 * Entry: `marketUptrend` AND at least 2 of 3 pullback-reversal sub-conditions
 * (price within 1.5×ATR of EMA20, higher low vs 10 trading days ago, EMA20 rising).
 */
@RegisteredStrategy(name = "IdunnEntryStrategy", type = StrategyType.ENTRY)
class IdunnEntryStrategy : DetailedEntryStrategy {
  private val compositeStrategy =
    entryStrategy {
      marketUptrend()
      pullback2of3(atrMultiple = 1.5, lookbackDays = 10, minSubConditions = 2)
    }

  override fun description() = "Idunn — pullback-to-EMA20 reversal in market uptrend"

  override fun test(stock: Stock, quote: StockQuote, context: BacktestContext) =
    compositeStrategy.test(stock, quote, context)

  override fun testWithDetails(stock: Stock, quote: StockQuote, context: BacktestContext): EntrySignalDetails =
    compositeStrategy.testWithDetails(stock, quote, context)
}
