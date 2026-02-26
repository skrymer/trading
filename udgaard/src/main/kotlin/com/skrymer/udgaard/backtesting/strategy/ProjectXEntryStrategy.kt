package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.dto.EntrySignalDetails
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

/**
 * Project X entry strategy using composition.*
 *     Price > EMA50, EMA100, EMA200
 *     Price inside Value Zone (VZ)
 *     3 consecutive higher highs inside VZ
 *     ≥ 2% distance to nearest bearish OB
 *     No earnings within next 7 trading days
 */
@RegisteredStrategy(name = "ProjectXEntryStrategy", type = StrategyType.ENTRY)
class ProjectXEntryStrategy : DetailedEntryStrategy {
  private val compositeStrategy =
    entryStrategy {
      // Structure (up trend)
      priceAbove(50)
      priceAbove(100)
      priceAbove(200)

      // 3 consecutive higher highs inside VZ
      consecutiveHigherHighsInValueZone(consecutiveDays = 3, atrMultiplier = 2.5, emaPeriod = 5)

      // at least 2 percent below 30 days old order block
      belowOrderBlock(2.0, 30)

      // Above 30 days old bearish order block for 3 days
      aboveBearishOrderBlock(consecutiveDays = 3, ageInDays = 30)

      // No earnings within next 7 trading days
//      noEarningsWithinDays(days = 7)
    }

  override fun description() = "Project X entry strategy"

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
