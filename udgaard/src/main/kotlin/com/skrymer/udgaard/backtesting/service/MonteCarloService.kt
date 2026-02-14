package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.*
import org.springframework.stereotype.Service
import kotlin.math.sqrt

/**
 * Service for running Monte Carlo simulations on backtest results
 */
@Service
class MonteCarloService {
  private val techniques =
    mapOf(
      MonteCarloTechniqueType.TRADE_SHUFFLING to TradeShufflingTechnique(),
      MonteCarloTechniqueType.BOOTSTRAP_RESAMPLING to BootstrapResamplingTechnique(),
    )

  /**
   * Run Monte Carlo simulation
   */
  fun runSimulation(request: MonteCarloRequest): MonteCarloResult {
    val startTime = System.currentTimeMillis()

    // Get the technique
    val technique =
      techniques[request.techniqueType]
        ?: throw IllegalArgumentException("Technique ${request.techniqueType} not implemented")

    // Generate scenarios
    val scenarios =
      technique.generateScenarios(
        backtestResult = request.backtestResult,
        iterations = request.iterations,
        seed = request.seed,
      )

    if (scenarios.isEmpty()) {
      throw IllegalStateException("No scenarios generated - backtest may have no trades")
    }

    // Calculate statistics
    val statistics = calculateStatistics(scenarios)

    // Extract percentile equity curves
    val percentileEquityCurves = extractPercentileEquityCurves(scenarios)

    // Get original backtest metrics for comparison
    // Calculate compounded return: (1 + r1) * (1 + r2) * ... - 1
    val originalReturn =
      request.backtestResult.trades
        .fold(1.0) { multiplier, trade -> multiplier * (1.0 + trade.profitPercentage / 100.0) }
        .let { (it - 1.0) * 100.0 }
    val originalEdge = request.backtestResult.edge
    val originalWinRate = request.backtestResult.winRate

    val executionTime = System.currentTimeMillis() - startTime

    return MonteCarloResult(
      technique = technique.name(),
      iterations = request.iterations,
      statistics = statistics,
      scenarios = if (request.includeAllEquityCurves) scenarios else emptyList(),
      percentileEquityCurves = percentileEquityCurves,
      originalReturnPercentage = originalReturn,
      originalEdge = originalEdge,
      originalWinRate = originalWinRate,
      executionTimeMs = executionTime,
    )
  }

  private fun calculateStatistics(scenarios: List<MonteCarloScenario>): MonteCarloStatistics {
    // Extract metrics from all scenarios
    val returns = scenarios.map { it.totalReturnPercentage }.sorted()
    val drawdowns = scenarios.map { it.maxDrawdown }.sorted()
    val winRates = scenarios.map { it.winRate }.sorted()
    val edges = scenarios.map { it.edge }.sorted()

    // Calculate percentiles
    val returnPercentiles = calculatePercentiles(returns)
    val drawdownPercentiles = calculatePercentiles(drawdowns)
    val winRatePercentiles = calculatePercentiles(winRates)
    val edgePercentiles = calculatePercentiles(edges)

    // Calculate means
    val meanReturn = returns.average()
    val meanDrawdown = drawdowns.average()
    val meanWinRate = winRates.average()
    val meanEdge = edges.average()

    // Calculate medians
    val medianReturn = returns[returns.size / 2]
    val medianDrawdown = drawdowns[drawdowns.size / 2]
    val medianWinRate = winRates[winRates.size / 2]
    val medianEdge = edges[edges.size / 2]

    // Calculate standard deviations
    val stdDevReturn = calculateStdDev(returns, meanReturn)

    // Calculate confidence intervals (95%)
    val returnCI =
      MonteCarloStatistics.ConfidenceInterval(
        lower = returnPercentiles.p5,
        upper = returnPercentiles.p95,
      )
    val drawdownCI =
      MonteCarloStatistics.ConfidenceInterval(
        lower = drawdownPercentiles.p5,
        upper = drawdownPercentiles.p95,
      )

    // Calculate probability of profit
    val profitableScenarios = scenarios.count { it.totalReturnPercentage > 0 }
    val probabilityOfProfit = profitableScenarios.toDouble() / scenarios.size * 100.0

    return MonteCarloStatistics(
      meanReturnPercentage = meanReturn,
      medianReturnPercentage = medianReturn,
      stdDevReturnPercentage = stdDevReturn,
      returnPercentiles = returnPercentiles,
      meanMaxDrawdown = meanDrawdown,
      medianMaxDrawdown = medianDrawdown,
      drawdownPercentiles = drawdownPercentiles,
      meanWinRate = meanWinRate,
      medianWinRate = medianWinRate,
      winRatePercentiles = winRatePercentiles,
      meanEdge = meanEdge,
      medianEdge = medianEdge,
      edgePercentiles = edgePercentiles,
      probabilityOfProfit = probabilityOfProfit,
      returnConfidenceInterval95 = returnCI,
      drawdownConfidenceInterval95 = drawdownCI,
      bestCaseReturnPercentage = returnPercentiles.p95,
      worstCaseReturnPercentage = returnPercentiles.p5,
    )
  }

  private fun calculatePercentiles(sortedValues: List<Double>): MonteCarloStatistics.Percentiles =
    MonteCarloStatistics.Percentiles(
      p5 = percentile(sortedValues, 5.0),
      p25 = percentile(sortedValues, 25.0),
      p50 = percentile(sortedValues, 50.0),
      p75 = percentile(sortedValues, 75.0),
      p95 = percentile(sortedValues, 95.0),
    )

  private fun percentile(
    sortedValues: List<Double>,
    percentile: Double,
  ): Double {
    val index = (percentile / 100.0 * (sortedValues.size - 1)).toInt()
    return sortedValues[index]
  }

  private fun calculateStdDev(
    values: List<Double>,
    mean: Double,
  ): Double {
    val variance = values.map { (it - mean) * (it - mean) }.average()
    return sqrt(variance)
  }

  private fun extractPercentileEquityCurves(scenarios: List<MonteCarloScenario>): MonteCarloResult.PercentileEquityCurves {
    // Find the scenario closest to each percentile
    val sortedByReturn = scenarios.sortedBy { it.totalReturnPercentage }

    val p5Scenario = sortedByReturn[(0.05 * scenarios.size).toInt()]
    val p25Scenario = sortedByReturn[(0.25 * scenarios.size).toInt()]
    val p50Scenario = sortedByReturn[(0.50 * scenarios.size).toInt()]
    val p75Scenario = sortedByReturn[(0.75 * scenarios.size).toInt()]
    val p95Scenario = sortedByReturn[(0.95 * scenarios.size).toInt()]

    return MonteCarloResult.PercentileEquityCurves(
      p5 = p5Scenario.equityCurve,
      p25 = p25Scenario.equityCurve,
      p50 = p50Scenario.equityCurve,
      p75 = p75Scenario.equityCurve,
      p95 = p95Scenario.equityCurve,
    )
  }
}
