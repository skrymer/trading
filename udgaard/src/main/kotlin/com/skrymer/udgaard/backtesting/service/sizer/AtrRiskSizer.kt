package com.skrymer.udgaard.backtesting.service.sizer

import kotlin.math.floor

/** shares = floor(equity × risk% / (nAtr × ATR)). Risks a fixed % of equity per trade. */
data class AtrRiskSizer(
  val riskPercentage: Double,
  val nAtr: Double,
) : PositionSizer {
  override val description: String
    get() = "ATR-risk sizer: $riskPercentage% risk, $nAtr×ATR stop"

  override fun calculateShares(ctx: SizingContext): Int =
    if (ctx.atr <= 0.0 || ctx.portfolioValue <= 0.0) {
      0
    } else {
      floor(ctx.portfolioValue * (riskPercentage / 100.0) / (nAtr * ctx.atr)).toInt()
    }

  override fun scale(multiplier: Double): PositionSizer =
    copy(riskPercentage = (riskPercentage * multiplier).coerceAtLeast(MIN_RISK_PCT))

  companion object {
    const val MIN_RISK_PCT: Double = 0.1
  }
}
