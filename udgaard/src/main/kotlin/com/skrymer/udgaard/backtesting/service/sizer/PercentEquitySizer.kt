package com.skrymer.udgaard.backtesting.service.sizer

import kotlin.math.floor

/** shares = floor(equity × percent% / price). Equal-notional per position, ATR-ignorant. */
data class PercentEquitySizer(
  val percent: Double,
) : PositionSizer {
  override val description: String
    get() = "Percent-equity sizer: $percent% of equity per position"

  override fun calculateShares(ctx: SizingContext): Int =
    if (ctx.entryPrice <= 0.0 || ctx.portfolioValue <= 0.0) {
      0
    } else {
      floor(ctx.portfolioValue * (percent / 100.0) / ctx.entryPrice).toInt()
    }

  override fun scale(multiplier: Double): PositionSizer =
    copy(percent = (percent * multiplier).coerceAtLeast(MIN_PERCENT))

  companion object {
    const val MIN_PERCENT: Double = 0.5
  }
}
