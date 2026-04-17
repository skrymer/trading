package com.skrymer.udgaard.backtesting.service.sizer

import kotlin.math.floor

/**
 * shares = floor(equity × targetVolPct% / (kAtr × ATR)). Equal-vol-contribution sizer.
 *
 * Distinct from [AtrRiskSizer]: that caps loss at a fixed-stop event (multi-ATR move);
 * this caps contribution to daily portfolio vol regardless of where the stop sits.
 */
data class VolatilityTargetSizer(
  val targetVolPct: Double,
  val kAtr: Double = 1.0,
) : PositionSizer {
  override val description: String
    get() = "Vol-target sizer: $targetVolPct% vol contribution, k=$kAtr×ATR"

  override fun calculateShares(ctx: SizingContext): Int {
    if (ctx.atr <= 0.0 || ctx.portfolioValue <= 0.0 || ctx.entryPrice <= 0.0) return 0
    val dollarVolPerShare = kAtr * ctx.atr
    val targetDollarVol = ctx.portfolioValue * (targetVolPct / 100.0)
    return floor(targetDollarVol / dollarVolPerShare).toInt()
  }

  override fun scale(multiplier: Double): PositionSizer =
    copy(targetVolPct = (targetVolPct * multiplier).coerceAtLeast(MIN_TARGET_VOL_PCT))

  companion object {
    const val MIN_TARGET_VOL_PCT: Double = 0.05
  }
}
