package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

@RegisteredStrategy(name = "PlanQEntryStrategy", type = StrategyType.ENTRY)
class PlanMVEntryStrategy : EntryStrategy {
  private val compositeStrategy =
    entryStrategy {
      // MARKET
      marketUptrend()
      marketBreadthAbove(50.0)

      // SECTOR
      // breadth > SPY breadth

      // STOCK
      priceAbove(20)
      emaAlignment(10, 20)
      adxRange(20.0, 30.0)
      atrExpanding(30.0, 60.0)
      volumeAboveAverage(1.3, 10)
    }

  override fun description() = "Plan momentum/volatility entry strategy"

  override fun test(stock: Stock, quote: StockQuote) = compositeStrategy.test(stock, quote)
}
