package com.skrymer.udgaard.backtesting.dto

import com.skrymer.udgaard.backtesting.model.MonteCarloTechniqueType

/**
 * Request DTO for Monte Carlo simulation.
 * Uses backtestId to retrieve trades from the result store instead of
 * sending the full trade list over the wire.
 */
data class MonteCarloRequestDto(
  /**
   * ID of the cached backtest result to run simulation on
   */
  val backtestId: String,
  /**
   * Type of Monte Carlo technique to use
   */
  val technique: MonteCarloTechniqueType = MonteCarloTechniqueType.TRADE_SHUFFLING,
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
)
