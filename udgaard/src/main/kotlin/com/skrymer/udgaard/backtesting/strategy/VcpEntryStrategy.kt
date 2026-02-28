package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.dto.EntrySignalDetails
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

/**
 * Volatility Contraction Pattern (VCP) entry strategy inspired by Mark Minervini,
 * using bearish order blocks as resistance levels.
 *
 * Entry when ALL of the following conditions are met:
 *
 * MARKET:
 * - Market in uptrend (breadth EMA alignment)
 *
 * SECTOR:
 * - Sector in uptrend
 *
 * STOCK:
 * - Stock is in uptrend (Minervini trend template)
 * - Volatility contracted (price range ≤ 3.5× ATR over 10 days)
 * - Price above bearish order block (1 consecutive day)
 * - Price near Donchian high (breakout within 3%)
 * - Volume above 20-day average (1.2× surge on breakout)
 * - Minimum price $10 (filter penny stocks)
 */
@RegisteredStrategy(name = "Vcp", type = StrategyType.ENTRY)
class VcpEntryStrategy : DetailedEntryStrategy {
  private val compositeStrategy =
    entryStrategy {
      // MARKET
      marketUptrend()

      // SECTOR
      sectorUptrend()

      // STOCK
      uptrend()
      volatilityContracted(lookbackDays = 10, maxAtrMultiple = 3.5)
      aboveBearishOrderBlock(consecutiveDays = 1, ageInDays = 0)
      priceNearDonchianHigh(maxDistancePercent = 3.0)
      volumeAboveAverage(multiplier = 1.2, lookbackDays = 20)
      minimumPrice(10.0)
    }

  override fun description() = "VCP entry strategy (Volatility Contraction Pattern with order block resistance)"

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
