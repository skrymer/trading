package com.skrymer.udgaard.backtesting.dto

import com.skrymer.udgaard.backtesting.model.MonteCarloTechniqueType
import com.skrymer.udgaard.backtesting.model.PositionSizingConfig

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
  val positionSizing: PositionSizingConfig? = null,
  /**
   * Drawdown thresholds (percent units, e.g. [20.0, 25.0, 30.0]) for which the response
   * will return P(maxDD > threshold) and CVaR. Omit/null = response field stays null.
   * Each value must be in (0.0, 100.0).
   */
  val drawdownThresholds: List<Double>? = null,
  /**
   * Fixed block size for circular block bootstrap. null/1 → IID (preserves current behaviour);
   * 2..[MAX_BLOCK_SIZE] enables block bootstrap. Only meaningful for BOOTSTRAP_RESAMPLING; silently
   * ignored on other techniques (mirrors how `drawdownThresholds` is silently ignored on
   * BOOTSTRAP_RESAMPLING). Values > N (number of trades) are clamped to N at the technique level.
   */
  val blockSize: Int? = null,
) {
  init {
    drawdownThresholds?.let { list ->
      require(list.isNotEmpty()) { "drawdownThresholds must not be empty when provided" }
      require(list.all { it > 0.0 && it < 100.0 }) {
        "drawdownThresholds must each be in (0.0, 100.0) percent units, got $list"
      }
    }
    blockSize?.let {
      require(it in 1..MAX_BLOCK_SIZE) {
        "blockSize must be in 1..$MAX_BLOCK_SIZE, got $it"
      }
    }
  }

  companion object {
    // Comfortably above the largest realistic backtest trade count and well below Int.MAX_VALUE,
    // so an obviously-wrong input (e.g. 1_000_000 instead of 1_000) is surfaced rather than silently
    // clamped to N at the technique layer with a misleading "Block Bootstrap" technique name.
    const val MAX_BLOCK_SIZE: Int = 10_000
  }
}
