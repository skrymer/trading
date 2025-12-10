package com.skrymer.udgaard.model.montecarlo

import com.skrymer.udgaard.model.BacktestReport
import com.skrymer.udgaard.model.Trade
import kotlin.math.abs
import kotlin.random.Random

/**
 * Bootstrap Resampling Monte Carlo technique.
 * Randomly samples trades WITH replacement to create new scenarios.
 * Some trades may appear multiple times, others not at all.
 * This tests the consistency of edge across different combinations of actual trades.
 */
class BootstrapResamplingTechnique : MonteCarloTechnique {
  override fun generateScenarios(
    backtestResult: BacktestReport,
    iterations: Int,
    seed: Long?,
  ): List<MonteCarloScenario> {
    val random = seed?.let { Random(it) } ?: Random.Default
    val allTrades = backtestResult.trades

    if (allTrades.isEmpty()) {
      return emptyList()
    }

    val numberOfTrades = allTrades.size

    return (1..iterations).map { iteration ->
      // Sample trades with replacement
      val resampledTrades =
        (1..numberOfTrades).map {
          allTrades.random(random)
        }

      // Calculate equity curve and metrics for this scenario
      createScenario(iteration, resampledTrades)
    }
  }

  private fun createScenario(
    scenarioNumber: Int,
    trades: List<Trade>,
  ): MonteCarloScenario {
    val equityCurve = mutableListOf<MonteCarloScenario.EquityPoint>()
    var multiplier = 1.0 // Start with 1.0 for compounding

    trades.forEachIndexed { index, trade ->
      // Compound the returns properly: (1 + r1) * (1 + r2) * ... - 1
      multiplier *= (1.0 + trade.profitPercentage / 100.0)
      val cumulativeReturn = (multiplier - 1.0) * 100.0

      val exitDate =
        trade.quotes
          .maxByOrNull { it.date ?: throw IllegalStateException("Trade has quote with null date") }
          ?.date
          ?: throw IllegalStateException("Trade has no quotes")

      equityCurve.add(
        MonteCarloScenario.EquityPoint(
          date = exitDate,
          cumulativeReturnPercentage = cumulativeReturn,
          tradeNumber = index + 1,
        ),
      )
    }

    val winningTrades = trades.filter { it.profitPercentage > 0 }
    val losingTrades = trades.filter { it.profitPercentage <= 0 }

    val winRate = winningTrades.size.toDouble() / trades.size
    val averageWinPercent =
      if (winningTrades.isNotEmpty()) {
        winningTrades.sumOf { it.profitPercentage } / winningTrades.size
      } else {
        0.0
      }

    val averageLossPercent =
      if (losingTrades.isNotEmpty()) {
        abs(losingTrades.sumOf { it.profitPercentage } / losingTrades.size)
      } else {
        0.0
      }

    val edge = (averageWinPercent * winRate) - ((1.0 - winRate) * averageLossPercent)
    val maxDrawdown = calculateMaxDrawdown(equityCurve)

    // Final cumulative return is the last point in equity curve
    val totalReturn =
      if (equityCurve.isNotEmpty()) {
        equityCurve.last().cumulativeReturnPercentage
      } else {
        0.0
      }

    return MonteCarloScenario(
      scenarioNumber = scenarioNumber,
      equityCurve = equityCurve,
      trades = trades,
      totalReturnPercentage = totalReturn,
      winRate = winRate,
      edge = edge,
      maxDrawdown = maxDrawdown,
      winningTrades = winningTrades.size,
      losingTrades = losingTrades.size,
    )
  }

  private fun calculateMaxDrawdown(equityCurve: List<MonteCarloScenario.EquityPoint>): Double {
    if (equityCurve.isEmpty()) return 0.0

    var maxDrawdown = 0.0
    var peakBalance = 1.0 + equityCurve.first().cumulativeReturnPercentage / 100.0 // Convert % to balance multiplier

    equityCurve.forEach { point ->
      val currentBalance = 1.0 + point.cumulativeReturnPercentage / 100.0

      if (currentBalance > peakBalance) {
        peakBalance = currentBalance
      }

      // Drawdown as percentage: (peak - current) / peak * 100
      val drawdown = ((peakBalance - currentBalance) / peakBalance) * 100.0
      if (drawdown > maxDrawdown) {
        maxDrawdown = drawdown
      }
    }

    return maxDrawdown
  }

  override fun name(): String = "Bootstrap Resampling"

  override fun description(): String =
    "Randomly samples trades with replacement to test edge consistency. " +
      "Creates new scenarios where some trades may appear multiple times and others not at all. " +
      "Tests if the strategy has consistent edge across different trade combinations."
}
