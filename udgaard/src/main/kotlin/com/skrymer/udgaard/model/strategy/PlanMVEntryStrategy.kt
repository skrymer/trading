package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain

@RegisteredStrategy(name = "PlanQEntryStrategy", type = StrategyType.ENTRY)
class PlanMVEntryStrategy : EntryStrategy {
  private val compositeStrategy =
    entryStrategy {
      // MARKET
      // in uptrend 10 > 20 and price over 50
      spyUptrend()
      // more than 50 precent of the stocks in the market are in a uptrend
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

  override fun test(stock: StockDomain, quote: StockQuoteDomain) = compositeStrategy.test(stock, quote)
}
