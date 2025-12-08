package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockQuote

/**
 * Plan Alpha entry strategy using composition.
 *
 * Entry when ALL of the following conditions are met:
 *
 * MARKET (SPY):
 * - SPY has a buy signal
 * - SPY is in uptrend (10 > 20, price > 50)
 * - Market stocks bull % over 10 EMA
 * - Market breadth above 35% (absolute threshold)
 * - SPY heatmap < 70
 * - SPY heatmap is rising
 *
 * SECTOR:
 * - Sector bull % is over 10 EMA (in uptrend)
 * - Sector heatmap is rising
 * - Sector heatmap < 70
 * - Donkey channel AS1 or AS2
 * - Sector heatmap > SPY heatmap
 *
 * STOCK:
 * - Has current buy signal
 * - Close price > 10 EMA
 * - Stock is in uptrend
 * - Stock heatmap is rising
 * - Price above previous low
 * - NOT within order block older than 120 days
 */
@RegisteredStrategy(name = "PlanAlpha", type = StrategyType.ENTRY)
class PlanAlphaEntryStrategy: DetailedEntryStrategy {
  private val compositeStrategy = entryStrategy {
    // MARKET (SPY)
    spyBuySignal()
    spyUptrend()
    marketUptrend()
    spyHeatmap(70)
    spyHeatmapRising()

    // SECTOR
    sectorUptrend()
    sectorHeatmapRising()
    sectorHeatmap(70)
    donkeyChannel()
    sectorHeatmapGreaterThanSpy()

    // STOCK
    uptrend()
    buySignal(daysOld = 5)  // Buy signal must be ≤ 5 day old
    stockHeatmapRising()
    belowOrderBlock(2.0, 120)
    priceAbove(10)
  }

  override fun description() = "Plan Alpha entry strategy"

  override fun test(stock: Stock, quote: StockQuote): Boolean {
    return compositeStrategy.test(stock, quote)
  }

  override fun testWithDetails(stock: Stock, quote: StockQuote) = compositeStrategy.testWithDetails(stock, quote)
}