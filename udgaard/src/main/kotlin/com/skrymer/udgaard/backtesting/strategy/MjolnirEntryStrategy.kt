package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.dto.EntrySignalDetails
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

@RegisteredStrategy(name = "MjolnirEntryStrategy", type = StrategyType.ENTRY)
class MjolnirEntryStrategy : DetailedEntryStrategy {
  private val compositeStrategy =
    entryStrategy {
      marketBreadthTrending(30.0)
      adxRange(20.0, 40.0)
      atrExpanding(30.0, 60.0)
      volumeAboveAverage(1.3, 10)
      consecutiveHigherHighsInValueZone(2, 2.0, 20)
      emaSpread(10, 20, 1.0)
    }

  override fun description() = "Plan momentum/volatility entry strategy"

  override fun test(stock: Stock, quote: StockQuote, context: BacktestContext) =
    compositeStrategy.test(stock, quote, context)

  override fun testWithDetails(stock: Stock, quote: StockQuote, context: BacktestContext): EntrySignalDetails =
    compositeStrategy.testWithDetails(stock, quote, context)
}
