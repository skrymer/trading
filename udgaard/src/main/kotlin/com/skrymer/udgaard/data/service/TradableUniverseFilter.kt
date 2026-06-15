package com.skrymer.udgaard.data.service

import com.skrymer.udgaard.data.model.LiquidityFilterParams
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import java.time.LocalDate

/**
 * Decides whether a symbol is in the *tradable universe* on a given bar — the realistically fillable
 * opportunity set (CONTEXT.md "Trading universe", ADR 0026). Point-in-time: a name drifts in and out
 * of tradability over its life exactly as it really did, judged only on bars on or before [asOf].
 *
 * Asset-type / ETF opt-in is NOT decided here — the caller's existing `assetTypes` selection picks the
 * candidate population; this gate is purely price + liquidity + history + a fail-open market-cap floor.
 */
class TradableUniverseFilter(
  private val params: LiquidityFilterParams = LiquidityFilterParams.FROZEN,
) {
  fun isEligible(stock: Stock, asOf: LocalDate): Boolean {
    val barsThrough = stock.indexAfter(asOf) // count of bars on or before asOf, O(log n)
    // Guard the empty window (no bars on or before asOf) before any positional access — also covers a
    // degenerate future epoch with minBars <= 0.
    if (barsThrough == 0 || barsThrough < params.minBars) return false
    if (stock.quotes[barsThrough - 1].closePrice < params.minClose) return false
    if (medianDollarVolume(stock.quotes, barsThrough) < params.minMedianDollarVolume) return false
    // Fail-open cap floor (ADR 0026 Phase 2): exclude only on positive evidence the cap is below the
    // micro/small boundary. An unmeasurable cap (null — an early-era / delisted vendor coverage gap, not
    // a size signal) does NOT exclude; fillability is already carried by the price/volume/age legs.
    val marketCap = stock.marketCapAsOf(asOf)
    return marketCap == null || marketCap >= params.minMarketCap
  }

  /** Median close x volume over the trailing [params.lookbackBars] bars ending at index [barsThrough] - 1. */
  private fun medianDollarVolume(quotes: List<StockQuote>, barsThrough: Int): Double {
    val from = maxOf(0, barsThrough - params.lookbackBars)
    val sorted = quotes.subList(from, barsThrough).map { it.closePrice * it.volume }.sorted()
    val size = sorted.size
    return if (size % 2 == 1) sorted[size / 2] else (sorted[size / 2 - 1] + sorted[size / 2]) / 2.0
  }
}
