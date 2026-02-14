package com.skrymer.udgaard.backtesting.model

/**
 * Aggregated statistics from Monte Carlo simulation
 */
data class MonteCarloStatistics(
  /**
   * Mean total return percentage across all scenarios
   */
  val meanReturnPercentage: Double,
  /**
   * Median total return percentage
   */
  val medianReturnPercentage: Double,
  /**
   * Standard deviation of returns
   */
  val stdDevReturnPercentage: Double,
  /**
   * Percentile values for total return
   */
  val returnPercentiles: Percentiles,
  /**
   * Mean maximum drawdown percentage
   */
  val meanMaxDrawdown: Double,
  /**
   * Median maximum drawdown percentage
   */
  val medianMaxDrawdown: Double,
  /**
   * Percentile values for max drawdown
   */
  val drawdownPercentiles: Percentiles,
  /**
   * Mean win rate across scenarios
   */
  val meanWinRate: Double,
  /**
   * Median win rate
   */
  val medianWinRate: Double,
  /**
   * Percentile values for win rate
   */
  val winRatePercentiles: Percentiles,
  /**
   * Mean edge across scenarios
   */
  val meanEdge: Double,
  /**
   * Median edge
   */
  val medianEdge: Double,
  /**
   * Percentile values for edge
   */
  val edgePercentiles: Percentiles,
  /**
   * Probability that strategy is profitable (% of scenarios with positive return)
   */
  val probabilityOfProfit: Double,
  /**
   * 95% confidence interval for total return
   */
  val returnConfidenceInterval95: ConfidenceInterval,
  /**
   * 95% confidence interval for max drawdown
   */
  val drawdownConfidenceInterval95: ConfidenceInterval,
  /**
   * Best case scenario (95th percentile return)
   */
  val bestCaseReturnPercentage: Double,
  /**
   * Worst case scenario (5th percentile return)
   */
  val worstCaseReturnPercentage: Double,
) {
  data class Percentiles(
    val p5: Double,
    val p25: Double,
    val p50: Double,
    val p75: Double,
    val p95: Double,
  )

  data class ConfidenceInterval(
    val lower: Double,
    val upper: Double,
  )
}
