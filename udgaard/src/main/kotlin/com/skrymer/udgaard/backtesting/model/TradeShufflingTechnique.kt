package com.skrymer.udgaard.backtesting.model

import kotlin.random.Random

/**
 * Trade Shuffling Monte Carlo technique.
 * Randomly reorders trades while keeping the same trades to test if edge holds
 * regardless of trade sequence. This helps identify if positive results are due to
 * a lucky sequence or genuine strategy edge.
 *
 * Win rate and edge are invariant across shuffles (same trades, different order),
 * so they are computed once and reused.
 */
class TradeShufflingTechnique : MonteCarloTechnique() {
  override fun generateScenarios(
    backtestResult: BacktestReport,
    iterations: Int,
    seed: Long?,
    positionSizing: PositionSizingConfig?,
  ): List<MonteCarloScenario> {
    val allTrades = backtestResult.trades
    if (allTrades.isEmpty()) return emptyList()

    val baseSeed = seed ?: System.nanoTime()
    val (winRate, edge) = computeWinRateAndEdge(allTrades)

    return (1..iterations)
      .toList()
      .parallelStream()
      .map { iteration ->
        val random = Random(baseSeed + iteration)
        val shuffledTrades = allTrades.shuffled(random)

        val equityCurve = if (positionSizing != null) {
          buildSizedEquityCurve(shuffledTrades, positionSizing)
        } else {
          buildEquityCurve(shuffledTrades)
        }

        buildScenarioWithPrecomputedStats(iteration, shuffledTrades, equityCurve, winRate, edge)
      }.toList()
  }

  override fun name(): String = "Trade Shuffling"

  override fun description(): String =
    "Randomly reorders trades to test if strategy edge holds regardless of trade sequence. " +
      "Helps identify if positive results are due to lucky timing or genuine strategy edge."
}
