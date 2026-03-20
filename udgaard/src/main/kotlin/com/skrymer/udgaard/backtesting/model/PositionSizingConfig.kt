package com.skrymer.udgaard.backtesting.model

data class PositionSizingConfig(
  val startingCapital: Double = 100_000.0,
  val riskPercentage: Double = 1.5,
  val nAtr: Double = 2.0,
  val leverageRatio: Double? = null,
  val drawdownScaling: DrawdownScaling? = null,
) {
  init {
    require(startingCapital > 0.0) { "startingCapital must be positive, got $startingCapital" }
    require(riskPercentage > 0.0) { "riskPercentage must be positive, got $riskPercentage" }
    require(nAtr > 0.0) { "nAtr must be positive, got $nAtr" }
  }
}

/**
 * Drawdown-responsive risk scaling. Reduces risk per trade when the portfolio
 * is in drawdown, scaling back up automatically as equity recovers.
 *
 * Thresholds define drawdown levels (as percentages) with corresponding risk
 * multipliers. They are evaluated from the deepest threshold first.
 *
 * Example: thresholds = [DrawdownThreshold(10.0, 0.33), DrawdownThreshold(5.0, 0.67)]
 *   - Drawdown >= 10%: risk = baseRisk * 0.33
 *   - Drawdown >= 5%:  risk = baseRisk * 0.67
 *   - Drawdown < 5%:   risk = baseRisk (full)
 */
data class DrawdownScaling(
  val thresholds: List<DrawdownThreshold>,
) {
  init {
    require(thresholds.isNotEmpty()) { "drawdownScaling.thresholds must not be empty" }
  }
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
