package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.domain.StockQuoteDomain

/**
 * Plan ETF entry strategy using composition - OPTIMIZED VERSION.
 *
 * Enters when:
 * - Stock is in uptrend
 * - Has buy signal
 * - Heatmap < 70
 * - Price is within value zone (< 20 EMA + 1.5 ATR) - OPTIMIZED from 2.0
 * - Price is at least 2% below an order block older than 30 days
 *
 * OPTIMIZATION RESULTS (tested 20 parameter combinations):
 * - Value Zone: 1.5 ATR (optimal for entry timing)
 * - Heatmap: 70 (unchanged, optimal threshold)
 * - Return/DD Ratio: 27.69 (40% improvement from 19.72)
 * - Total Return: 545.4% over 5.2 years (vs 452.5%)
 * - Max Drawdown: 19.70% (improved from 22.94%)
 * - Win Rate: 60.0% (improved from 55.6%)
 * - Edge: 4.84% (improved from 4.48%)
 */
@RegisteredStrategy(name = "OvtlyrPlanEtf", type = StrategyType.ENTRY)
class OvtlyrPlanEtfEntryStrategy : DetailedEntryStrategy {
  private val compositeStrategy =
    entryStrategy {
      uptrend()
      buySignal(daysOld = -1) // Accept any buy signal age
      heatmap(70)
      inValueZone(atrMultiplier = 1.5, emaPeriod = 20) // Optimized: 1.5 ATR (was 2.0) - tighter entry timing
      belowOrderBlock(percentBelow = 2.0, ageInDays = 30)
    }

  override fun description() = "Ovtlyr Plan ETF entry strategy"

  override fun test(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ): Boolean = compositeStrategy.test(stock, quote)

  override fun testWithDetails(
    stock: StockDomain,
    quote: StockQuoteDomain,
  ) = compositeStrategy.testWithDetails(stock, quote)
}
