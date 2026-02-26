package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.strategy.EntryStrategy
import com.skrymer.udgaard.backtesting.strategy.RegisteredStrategy
import com.skrymer.udgaard.backtesting.strategy.StrategyType
import com.skrymer.udgaard.backtesting.strategy.entryStrategy
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

/**
 * Test entry strategy covering ALL data types in the database:
 * - Stock quotes: uptrend (EMA alignment + price > EMA50)
 * - Market breadth: breadth percent above threshold
 * - Sector breadth: sector bull percentage above threshold
 * - Earnings: no earnings within 7 trading days
 * - Order blocks: not within a bearish order block
 */
@RegisteredStrategy(name = "TestEntry", type = StrategyType.ENTRY)
class TestEntryStrategy : EntryStrategy {
  private val compositeStrategy =
    entryStrategy {
      // Stock quote conditions
      uptrend()
      minimumPrice(5.0)

      // Market breadth (from market_breadth_daily table)
      marketBreadthAbove(40.0)

      // Sector breadth (from sector_breadth_daily table)
      sectorBreadthAbove(20.0)

      // Earnings (from earnings table)
      noEarningsWithinDays(7)

      // Order blocks (from order_blocks table)
      notInOrderBlock(120)
    }

  override fun description() = "Test entry strategy covering all data types"

  override fun test(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean = compositeStrategy.test(stock, quote, context)
}
