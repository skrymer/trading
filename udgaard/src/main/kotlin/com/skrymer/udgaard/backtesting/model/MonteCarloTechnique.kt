package com.skrymer.udgaard.backtesting.model

import com.skrymer.udgaard.backtesting.model.BacktestReport

/**
 * Interface for Monte Carlo simulation techniques.
 * Implementations can use different randomization strategies to test trading strategy robustness.
 */
interface MonteCarloTechnique {
  /**
   * Generate multiple randomized scenarios from a backtest result.
   * @param backtestResult The original backtest result to randomize
   * @param iterations Number of scenarios to generate
   * @param seed Random seed for reproducibility (optional)
   * @return List of randomized scenarios
   */
  fun generateScenarios(
    backtestResult: BacktestReport,
    iterations: Int,
    seed: Long? = null,
  ): List<MonteCarloScenario>

  /**
   * Name of the technique
   */
  fun name(): String

  /**
   * Description of what this technique does
   */
  fun description(): String
}
