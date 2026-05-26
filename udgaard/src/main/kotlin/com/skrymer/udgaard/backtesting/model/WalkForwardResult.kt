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
  // OOS-segment CAGR and max drawdown — the per-window inputs for a Calmar-based
  // walk-forward objective. Null when the run is un-sized (no daily equity curve).
  val outOfSampleCagr: Double?,
  val outOfSampleMaxDrawdownPct: Double?,
  // Per-window OOS risk-adjusted metrics computed from the window's position-sized equity
  // curve via RiskMetricsService.compute. Null when the run is un-sized (no daily curve).
  val outOfSampleRiskMetrics: RiskMetrics?,
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
  // Aggregate OOS metrics computed from the STITCHED daily-return series across all
  // OOS windows. Captures cross-window drawdowns (which per-window-max-DD cannot).
  // Per ADR-0005 (Walk-forward aggregation methodology). Null when no window contributed
  // a sized equity curve of at least two points (typically: un-sized run with no
  // PositionSizingConfig).
  val aggregateOosRiskMetrics: RiskMetrics?,
  val aggregateOosCagr: Double?,
  val aggregateOosMaxDrawdownPct: Double?,
)
