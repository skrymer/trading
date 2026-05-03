package com.skrymer.udgaard.backtesting.model

/**
 * Per-threshold drawdown risk: probability the simulated max drawdown exceeded
 * `drawdownPercent`, plus the conditional expectation (CVaR) of the drawdowns
 * that did exceed it.
 *
 * Probability and CVaR answer different questions:
 *  - probability = P(maxDD > drawdownPercent)              "how likely is it bad?"
 *  - expectedDrawdownGivenExceeded = E[maxDD | maxDD > drawdownPercent]   "given it's bad, how bad?"
 *
 * Both measured in percent (e.g. drawdownPercent = 25.0 means 25% drawdown).
 * `drawdownPercent` mirrors the field name on PositionSizingConfig.DrawdownThreshold.
 */
data class DrawdownThresholdProbability(
  val drawdownPercent: Double,
  val probability: Double,
  // CVaR — null when zero iterations exceeded the threshold (no exceedances to average)
  val expectedDrawdownGivenExceeded: Double?,
)
