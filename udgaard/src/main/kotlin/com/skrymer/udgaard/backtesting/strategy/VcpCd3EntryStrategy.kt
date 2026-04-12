package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.dto.EntrySignalDetails
import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote

/**
 * High-conviction VCP entry strategy requiring 3 consecutive days above bearish order block.
 *
 * Filters out weaker breakouts by requiring price to hold above resistance for 3 days,
 * producing fewer but higher-quality signals compared to VcpEntryStrategy (consecutiveDays=1).
 *
 * Intended as the primary tier in a tiered scanning approach:
 * 1. Fill positions with VcpCd3 signals first (high conviction)
 * 2. Fill remaining slots with Vcp signals (broader net)
 */
@RegisteredStrategy(name = "VcpCd3", type = StrategyType.ENTRY)
class VcpCd3EntryStrategy : DetailedEntryStrategy {
  private val compositeStrategy =
    entryStrategy {
      // MARKET
      marketUptrend()

      // SECTOR
      sectorUptrend()

      // STOCK
      uptrend()
      volatilityContracted(lookbackDays = 10, maxAtrMultiple = 3.5)
      aboveBearishOrderBlock(consecutiveDays = 3, ageInDays = 0)
      priceNearDonchianHigh(maxDistancePercent = 3.0)
      volumeAboveAverage(multiplier = 1.2, lookbackDays = 20)
      minimumPrice(10.0)
    }

  override fun description() = "VCP CD3 entry strategy (high-conviction, 3-day OB confirmation)"

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

  override fun preferredRanker(): StockRanker = SectorEdgeRanker(
    listOf("XLC", "XLI", "XLK", "XLY", "XLV", "XLF", "XLE", "XLU", "XLP", "XLB", "XLRE"),
  )
}
