package com.skrymer.udgaard.backtesting.model

import java.time.LocalDate

/**
 * Walk-forward window configuration. All sizes are in months; the controller
 * resolves year-based request fields to months before constructing this.
 */
data class WalkForwardConfig(
  val inSampleMonths: Int,
  val outOfSampleMonths: Int,
  val stepMonths: Int,
  val startDate: LocalDate,
  val endDate: LocalDate,
)

data class WalkForwardWindow(
  val inSampleStart: LocalDate,
  val inSampleEnd: LocalDate,
  val outOfSampleStart: LocalDate,
  val outOfSampleEnd: LocalDate,
  val derivedSectorRanking: List<String>,
  val inSampleEdge: Double,
  val outOfSampleEdge: Double,
  val inSampleTrades: Int,
  val outOfSampleTrades: Int,
  val inSampleWinRate: Double,
  val outOfSampleWinRate: Double,
  // Regime tagging derived from MarketBreadthDaily.isInUptrend() (breadthPercent > ema10).
  // Default 0.0 when the window range has no breadth rows; analyst inspects date range to disambiguate.
  val inSampleBreadthUptrendPercent: Double,
  val inSampleBreadthAvg: Double,
  val outOfSampleBreadthUptrendPercent: Double,
  val outOfSampleBreadthAvg: Double,
)

data class WalkForwardResult(
  val windows: List<WalkForwardWindow>,
  val aggregateOosEdge: Double,
  val aggregateOosTrades: Int,
  val aggregateOosWinRate: Double,
  val walkForwardEfficiency: Double,
)
