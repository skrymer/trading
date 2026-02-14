package com.skrymer.udgaard.backtesting.dto

import com.skrymer.udgaard.backtesting.model.MonteCarloTechniqueType
import com.skrymer.udgaard.backtesting.model.Trade

/**
 * Request DTO for Monte Carlo simulation
 */
data class MonteCarloRequestDto(
  /**
   * The trades from the backtest to run simulation on
   */
  val trades: List<Trade>,
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
