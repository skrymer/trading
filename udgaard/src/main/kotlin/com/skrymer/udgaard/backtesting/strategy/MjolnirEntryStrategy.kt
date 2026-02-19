package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

@RegisteredStrategy(name = "MjolnirEntryStrategy", type = StrategyType.ENTRY)
class MjolnirEntryStrategy : EntryStrategy {
  private val compositeStrategy =
    entryStrategy {
      marketBreadthTrending(30.0)
      emaAlignment(10, 20)
      adxRange(20.0, 30.0)
      atrExpanding(30.0, 60.0)
      volumeAboveAverage(1.3, 10)
      consecutiveHigherHighsInValueZone(2, 2.0, 20)
      emaSpread(10, 20, 1.0)
    }

  override fun description() = "Plan momentum/volatility entry strategy"

  override fun test(stock: Stock, quote: StockQuote) = compositeStrategy.test(stock, quote)

  override fun test(stock: Stock, quote: StockQuote, context: BacktestContext) =
    compositeStrategy.test(stock, quote, context)
}
