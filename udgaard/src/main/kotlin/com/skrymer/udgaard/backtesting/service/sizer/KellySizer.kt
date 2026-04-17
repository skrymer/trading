package com.skrymer.udgaard.backtesting.service.sizer

import kotlin.math.floor

/**
 * Kelly-criterion sizing. Classical Kelly: f* = W - (1-W)/R where W is win rate and R is avgWin/avgLoss.
 * The Kelly fraction represents the fraction of equity to allocate to a single bet under 100% downside.
 *
 * Implementation: shares = floor(equity × f × fractionMultiplier / entryPrice).
 * This is notional-based; the ATR stop does NOT reduce per-trade risk further. Using f atop
 * an ATR stop would over-size by the ratio of 100%/stopLoss% (typically 7-12×).
 *
 * Default fractionMultiplier is 0.25 (quarter-Kelly). Trader-supplied W and R estimates have
 * wide confidence intervals from typical 500-trade backtests; full Kelly (f × 1.0) is almost
 * always too aggressive.
 *
 * Negative-EV guard: if kellyFraction <= 0 (trader has no edge), size is 0 — trade is skipped.
 */
data class KellySizer(
  val winRate: Double,
  val winLossRatio: Double,
  val fractionMultiplier: Double = DEFAULT_FRACTION_MULTIPLIER,
) : PositionSizer {
  private val kellyFraction: Double = (winRate - (1.0 - winRate) / winLossRatio).coerceAtLeast(0.0)

  override val description: String
    get() = "Kelly sizer: f=${"%.3f".format(kellyFraction)} (W=$winRate, R=$winLossRatio), multiplier=$fractionMultiplier"

  override fun calculateShares(ctx: SizingContext): Int {
    if (ctx.portfolioValue <= 0.0 || ctx.entryPrice <= 0.0 || kellyFraction <= 0.0) return 0
    val allocation = ctx.portfolioValue * kellyFraction * fractionMultiplier
    return floor(allocation / ctx.entryPrice).toInt()
  }

  /**
   * Scale the fractional-Kelly multiplier, not the underlying kellyFraction. This preserves
   * the negative-EV guard (kellyFraction <= 0 → skip trade) and matches the intent of drawdown
   * scaling: reduce bet size, not change edge estimate.
   */
  override fun scale(multiplier: Double): PositionSizer =
    copy(fractionMultiplier = (fractionMultiplier * multiplier).coerceAtLeast(MIN_FRACTION_MULTIPLIER))

  companion object {
    const val DEFAULT_FRACTION_MULTIPLIER: Double = 0.25
    const val MIN_FRACTION_MULTIPLIER: Double = 0.01
  }
}
