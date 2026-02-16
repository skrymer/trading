package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.dto.EntrySignalDetails
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

/**
 * Plan Alpha entry strategy using composition.
 *
 * Entry when ALL of the following conditions are met:
 *
 * MARKET:
 * - Market in uptrend (breadth EMA alignment)
 * - Market breadth above 50%
 *
 * SECTOR:
 * - Sector bull % is over 10 EMA (in uptrend)
 * - Sector breadth > Market breadth
 *
 * STOCK:
 * - Close price > 10 EMA
 * - Stock is in uptrend
 * - Price above previous low
 * - NOT within order block older than 120 days
 */
@RegisteredStrategy(name = "PlanAlpha", type = StrategyType.ENTRY)
class PlanAlphaEntryStrategy : DetailedEntryStrategy {
  private val compositeStrategy =
    entryStrategy {
      // MARKET
      marketUptrend()
      marketBreadthAbove(50.0)

      // SECTOR
      sectorUptrend()
      sectorBreadthGreaterThanSpy()

      // STOCK
      uptrend()
      belowOrderBlock(percentBelow = 2.0, ageInDays = 0)
      priceAbove(10)
    }

  override fun description() = "Plan Alpha entry strategy"

  override fun test(
    stock: Stock,
    quote: StockQuote,
  ): Boolean = compositeStrategy.test(stock, quote)

  override fun test(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean = compositeStrategy.test(stock, quote, context)

  override fun testWithDetails(
    stock: Stock,
    quote: StockQuote,
  ): EntrySignalDetails = compositeStrategy.testWithDetails(stock, quote)

  override fun testWithDetails(
    stock: Stock,
    quote: StockQuote,
    context: BacktestContext,
  ): EntrySignalDetails = compositeStrategy.testWithDetails(stock, quote, context)
}
