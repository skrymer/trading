package com.skrymer.udgaard.backtesting.model

import com.skrymer.udgaard.backtesting.service.PositionSizingService
import kotlin.math.abs
import kotlin.math.max

/**
 * Base class for Monte Carlo simulation techniques.
 * Provides shared scenario building, equity curve computation, and drawdown calculation.
 * Subclasses only need to implement trade selection logic in [generateScenarios].
 */
abstract class MonteCarloTechnique {
  abstract fun generateScenarios(
    backtestResult: BacktestReport,
    iterations: Int,
    seed: Long? = null,
    positionSizing: PositionSizingConfig? = null,
  ): List<MonteCarloScenario>

  abstract fun name(): String

  abstract fun description(): String

  protected fun createScenario(
    scenarioNumber: Int,
    trades: List<Trade>,
  ): MonteCarloScenario {
    val equityCurve = buildEquityCurve(trades)
    return buildScenario(scenarioNumber, trades, equityCurve)
  }

  protected fun createScenarioWithSizing(
    scenarioNumber: Int,
    trades: List<Trade>,
    config: PositionSizingConfig,
  ): MonteCarloScenario {
    val equityCurve = buildSizedEquityCurve(trades, config)
    return buildScenario(scenarioNumber, trades, equityCurve)
  }

  protected fun buildScenarioWithPrecomputedStats(
    scenarioNumber: Int,
    trades: List<Trade>,
    equityCurve: List<MonteCarloScenario.EquityPoint>,
    winRate: Double,
    edge: Double,
  ): MonteCarloScenario {
    val maxDrawdown = calculateMaxDrawdown(equityCurve)
    val totalReturn = equityCurve.lastOrNull()?.cumulativeReturnPercentage ?: 0.0
    val winCount = trades.count { it.profitPercentage > 0 }

    return MonteCarloScenario(
      scenarioNumber = scenarioNumber,
      equityCurve = equityCurve,
      trades = trades,
      totalReturnPercentage = totalReturn,
      winRate = winRate,
      edge = edge,
      maxDrawdown = maxDrawdown,
      winningTrades = winCount,
      losingTrades = trades.size - winCount,
    )
  }

  protected fun computeWinRateAndEdge(trades: List<Trade>): Pair<Double, Double> {
    val winners = trades.filter { it.profitPercentage > 0 }
    val losers = trades.filter { it.profitPercentage <= 0 }
    val winRate = winners.size.toDouble() / trades.size
    val avgWin = if (winners.isNotEmpty()) winners.sumOf { it.profitPercentage } / winners.size else 0.0
    val avgLoss = if (losers.isNotEmpty()) abs(losers.sumOf { it.profitPercentage } / losers.size) else 0.0
    val edge = (avgWin * winRate) - ((1.0 - winRate) * avgLoss)
    return winRate to edge
  }

  protected fun buildEquityCurve(trades: List<Trade>): List<MonteCarloScenario.EquityPoint> {
    val equityCurve = mutableListOf<MonteCarloScenario.EquityPoint>()
    var multiplier = 1.0

    trades.forEachIndexed { index, trade ->
      multiplier *= (1.0 + trade.profitPercentage / 100.0)
      val cumulativeReturn = (multiplier - 1.0) * 100.0
      val exitDate = trade.quotes.lastOrNull()?.date
        ?: throw IllegalStateException("Trade has no quotes")

      equityCurve.add(
        MonteCarloScenario.EquityPoint(
          date = exitDate,
          cumulativeReturnPercentage = cumulativeReturn,
          tradeNumber = index + 1,
        ),
      )
    }
    return equityCurve
  }

  protected fun buildSizedEquityCurve(
    trades: List<Trade>,
    config: PositionSizingConfig,
  ): List<MonteCarloScenario.EquityPoint> {
    val equityCurve = mutableListOf<MonteCarloScenario.EquityPoint>()
    var portfolioValue = config.startingCapital
    var peakValue = config.startingCapital

    trades.forEachIndexed { index, trade ->
      val shares = PositionSizingService.calculateShares(portfolioValue, trade.entryQuote.atr, config)
      val dollarProfit = shares * trade.profit
      portfolioValue += dollarProfit
      peakValue = max(peakValue, portfolioValue)
      val cumulativeReturn = ((portfolioValue - config.startingCapital) / config.startingCapital) * 100.0
      val exitDate = trade.quotes.lastOrNull()?.date
        ?: throw IllegalStateException("Trade has no quotes")

      equityCurve.add(
        MonteCarloScenario.EquityPoint(
          date = exitDate,
          cumulativeReturnPercentage = cumulativeReturn,
          tradeNumber = index + 1,
        ),
      )
    }
    return equityCurve
  }

  private fun buildScenario(
    scenarioNumber: Int,
    trades: List<Trade>,
    equityCurve: List<MonteCarloScenario.EquityPoint>,
  ): MonteCarloScenario {
    val (winRate, edge) = computeWinRateAndEdge(trades)
    return buildScenarioWithPrecomputedStats(scenarioNumber, trades, equityCurve, winRate, edge)
  }

  protected fun calculateMaxDrawdown(equityCurve: List<MonteCarloScenario.EquityPoint>): Double {
    if (equityCurve.isEmpty()) return 0.0

    var maxDrawdown = 0.0
    var peakBalance = 1.0 + equityCurve.first().cumulativeReturnPercentage / 100.0

    equityCurve.forEach { point ->
      val currentBalance = 1.0 + point.cumulativeReturnPercentage / 100.0
      if (currentBalance > peakBalance) {
        peakBalance = currentBalance
      }
      val drawdown = ((peakBalance - currentBalance) / peakBalance) * 100.0
      if (drawdown > maxDrawdown) {
        maxDrawdown = drawdown
      }
    }
    return maxDrawdown
  }
}
