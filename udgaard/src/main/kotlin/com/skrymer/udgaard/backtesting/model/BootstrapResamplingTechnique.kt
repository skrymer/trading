package com.skrymer.udgaard.backtesting.model

import kotlin.random.Random

/**
 * Bootstrap Resampling Monte Carlo technique.
 * Randomly samples trades WITH replacement to create new scenarios.
 * Some trades may appear multiple times, others not at all.
 * This tests the consistency of edge across different combinations of actual trades.
 */
class BootstrapResamplingTechnique : MonteCarloTechnique() {
  override fun generateScenarios(
    backtestResult: BacktestReport,
    iterations: Int,
    seed: Long?,
    positionSizing: PositionSizingConfig?,
  ): List<MonteCarloScenario> {
    val allTrades = backtestResult.trades
    if (allTrades.isEmpty()) return emptyList()

    val numberOfTrades = allTrades.size
    val baseSeed = seed ?: System.nanoTime()

    return (1..iterations)
      .toList()
      .parallelStream()
      .map { iteration ->
        val random = Random(baseSeed + iteration)
        val resampledTrades = (1..numberOfTrades).map { allTrades.random(random) }

        if (positionSizing != null) {
          createScenarioWithSizing(iteration, resampledTrades, positionSizing)
        } else {
          createScenario(iteration, resampledTrades)
        }
      }.toList()
  }

  override fun name(): String = "Bootstrap Resampling"

  override fun description(): String =
    "Randomly samples trades with replacement to test edge consistency. " +
      "Creates new scenarios where some trades may appear multiple times and others not at all. " +
      "Tests if the strategy has consistent edge across different trade combinations."
}
