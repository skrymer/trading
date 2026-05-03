package com.skrymer.udgaard.backtesting.model

import com.skrymer.udgaard.backtesting.model.BacktestReport

/**
 * Request for Monte Carlo simulation
 */
data class MonteCarloRequest(
  /**
   * The backtest result to run simulation on
   */
  val backtestResult: BacktestReport,
  /**
   * Type of Monte Carlo technique to use
   */
  val techniqueType: MonteCarloTechniqueType,
  /**
   * Number of simulation iterations to run
   */
  val iterations: Int = 10000,
  /**
   * Random seed for reproducibility (optional)
   */
  val seed: Long? = null,
  /**
   * Whether to include equity curves for all scenarios (can be large)
   * If false, only percentile curves will be included
   */
  val includeAllEquityCurves: Boolean = false,
  val positionSizing: PositionSizingConfig? = null,
  /**
   * Drawdown thresholds in percent units (e.g. [20.0, 25.0, 30.0]). When non-null,
   * MonteCarloStatistics.drawdownThresholdProbabilities is populated.
   */
  val drawdownThresholds: List<Double>? = null,
  /**
   * Fixed block size for circular block bootstrap. null/1 → IID (current behaviour);
   * >= 2 enables block bootstrap. Only meaningful for BOOTSTRAP_RESAMPLING; silently
   * ignored on other techniques.
   */
  val blockSize: Int? = null,
)
