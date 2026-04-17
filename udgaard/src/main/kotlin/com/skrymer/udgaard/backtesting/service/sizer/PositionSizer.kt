package com.skrymer.udgaard.backtesting.service.sizer

data class SizingContext(
  val portfolioValue: Double,
  val entryPrice: Double,
  val atr: Double,
  val symbol: String,
  val sector: String,
)

/**
 * Pluggable position sizer. Pure — does not know about leverage cap or open position count;
 * those are portfolio-level constraints applied outside the sizer (see [applyLeverageCap]).
 */
interface PositionSizer {
  val description: String

  fun calculateShares(ctx: SizingContext): Int

  /**
   * Produce a new sizer with its primary sizing parameter scaled by [multiplier].
   * Implementations clamp to a safe floor to avoid degenerate 0-share sizing in deep drawdowns.
   */
  fun scale(multiplier: Double): PositionSizer
}
