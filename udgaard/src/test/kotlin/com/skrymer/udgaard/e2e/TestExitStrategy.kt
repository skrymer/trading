package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.strategy.ExitStrategy
import com.skrymer.udgaard.backtesting.strategy.RegisteredStrategy
import com.skrymer.udgaard.backtesting.strategy.StrategyType
import com.skrymer.udgaard.backtesting.strategy.exitStrategy
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

/**
 * Test exit strategy covering ALL data types in the database:
 * - Stock quotes: stop loss (ATR-based)
 * - Market breadth: deteriorating (bearish EMA inversion)
 * - Sector breadth: bull percentage below threshold
 * - Earnings: exit before earnings announcement
 * - Order blocks: price entering bearish order block
 */
@RegisteredStrategy(name = "TestExit", type = StrategyType.EXIT)
class TestExitStrategy : ExitStrategy {
  private val compositeStrategy =
    exitStrategy {
      // Stock quote conditions (ATR-based stop loss)
      stopLoss(2.0)

      // Market breadth (from market_breadth_daily table)
      marketBreadthDeteriorating()

      // Sector breadth (from sector_breadth_daily table)
      sectorBreadthBelow(15.0)

      // Earnings (from earnings table)
      exitBeforeEarnings(1)

      // Order blocks (from order_blocks table)
      bearishOrderBlock(120)
    }

  override fun description() = "Test exit strategy covering all data types"

  override fun match(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): Boolean = compositeStrategy.match(stock, entryQuote, quote)

  override fun match(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean = compositeStrategy.match(stock, entryQuote, quote, context)

  override fun reason(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
  ): String? = compositeStrategy.reason(stock, entryQuote, quote)

  override fun reason(
    stock: Stock,
    entryQuote: StockQuote?,
    quote: StockQuote,
    context: BacktestContext,
  ): String? = compositeStrategy.reason(stock, entryQuote, quote, context)
}
