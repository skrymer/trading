package com.skrymer.udgaard.model.montecarlo

/**
 * Result of Monte Carlo simulation
 */
data class MonteCarloResult(
  /**
   * Technique used for this simulation
   */
  val technique: String,
  /**
   * Number of iterations run
   */
  val iterations: Int,
  /**
   * Aggregated statistics from all scenarios
   */
  val statistics: MonteCarloStatistics,
  /**
   * All scenarios (only if includeAllEquityCurves was true, otherwise empty)
   */
  val scenarios: List<MonteCarloScenario> = emptyList(),
  /**
   * Percentile equity curves for visualization (5th, 25th, 50th, 75th, 95th)
   * These are always included regardless of includeAllEquityCurves setting
   */
  val percentileEquityCurves: PercentileEquityCurves,
  /**
   * Original backtest result for comparison
   */
  val originalReturnPercentage: Double,
  /**
   * Original backtest edge
   */
  val originalEdge: Double,
  /**
   * Original backtest win rate
   */
  val originalWinRate: Double,
  /**
   * Execution time in milliseconds
   */
  val executionTimeMs: Long,
) {
  data class PercentileEquityCurves(
    val p5: List<MonteCarloScenario.EquityPoint>,
    val p25: List<MonteCarloScenario.EquityPoint>,
    val p50: List<MonteCarloScenario.EquityPoint>,
    val p75: List<MonteCarloScenario.EquityPoint>,
    val p95: List<MonteCarloScenario.EquityPoint>,
  )
}
