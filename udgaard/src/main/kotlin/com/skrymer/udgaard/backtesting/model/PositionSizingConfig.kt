package com.skrymer.udgaard.backtesting.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.skrymer.udgaard.backtesting.service.sizer.PositionSizer
import com.skrymer.udgaard.backtesting.service.sizer.SizerConfig

data class PositionSizingConfig(
  val startingCapital: Double = 100_000.0,
  val sizer: SizerConfig,
  val leverageRatio: Double? = null,
  val drawdownScaling: DrawdownScaling? = null,
) {
  init {
    require(startingCapital > 0.0) { "startingCapital must be positive, got $startingCapital" }
    require(startingCapital <= 1_000_000_000.0) { "startingCapital must be <= 1B, got $startingCapital" }
    if (leverageRatio != null) {
      require(leverageRatio in 0.1..100.0) { "leverageRatio must be in 0.1..100, got $leverageRatio" }
    }
  }

  /** Cached base sizer. Avoids reallocating on every call site in hot paths. */
  @get:JsonIgnore
  val baseSizer: PositionSizer by lazy { sizer.toSizer() }
}

/**
 * Drawdown-responsive risk scaling. Reduces per-trade size when the portfolio
 * is in drawdown, scaling back up automatically as equity recovers.
 *
 * Thresholds define drawdown levels (as percentages) with corresponding size
 * multipliers. They are evaluated from the deepest threshold first.
 *
 * Example: thresholds = [DrawdownThreshold(10.0, 0.33), DrawdownThreshold(5.0, 0.67)]
 *   - Drawdown >= 10%: size = baseSize * 0.33
 *   - Drawdown >= 5%:  size = baseSize * 0.67
 *   - Drawdown < 5%:   size = baseSize (full)
 *
 * The multiplier is applied to the sizer via PositionSizer.scale — each sizer decides
 * which of its parameters gets scaled.
 */
data class DrawdownScaling(
  val thresholds: List<DrawdownThreshold>,
) {
  init {
    require(thresholds.isNotEmpty()) { "drawdownScaling.thresholds must not be empty" }
  }

  /** Thresholds pre-sorted deepest-first for O(n) scan in [findMatch]. */
  @com.fasterxml.jackson.annotation.JsonIgnore
  val sortedThresholds: List<DrawdownThreshold> = thresholds.sortedByDescending { it.drawdownPercent }

  fun findMatch(drawdownPct: Double): DrawdownThreshold? =
    sortedThresholds.firstOrNull { drawdownPct >= it.drawdownPercent }
}

data class DrawdownThreshold(
  val drawdownPercent: Double,
  val riskMultiplier: Double,
) {
  init {
    require(drawdownPercent > 0.0) { "drawdownPercent must be positive, got $drawdownPercent" }
    require(riskMultiplier in 0.0..1.0) { "riskMultiplier must be between 0.0 and 1.0, got $riskMultiplier" }
  }
}
