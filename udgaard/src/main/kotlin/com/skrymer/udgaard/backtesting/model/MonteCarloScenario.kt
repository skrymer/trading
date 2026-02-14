package com.skrymer.udgaard.backtesting.model

import com.fasterxml.jackson.annotation.JsonFormat
import com.skrymer.udgaard.backtesting.model.Trade
import java.time.LocalDate

/**
 * Represents a single Monte Carlo simulation scenario with its equity curve and metrics
 */
data class MonteCarloScenario(
  /**
   * Scenario number (1 to N)
   */
  val scenarioNumber: Int,
  /**
   * Equity curve as list of cumulative returns over time
   */
  val equityCurve: List<EquityPoint>,
  /**
   * Trades in this scenario (order may be randomized)
   */
  val trades: List<Trade>,
  /**
   * Final cumulative return percentage
   */
  val totalReturnPercentage: Double,
  /**
   * Win rate for this scenario
   */
  val winRate: Double,
  /**
   * Edge for this scenario
   */
  val edge: Double,
  /**
   * Maximum drawdown percentage
   */
  val maxDrawdown: Double,
  /**
   * Number of winning trades
   */
  val winningTrades: Int,
  /**
   * Number of losing trades
   */
  val losingTrades: Int,
) {
  data class EquityPoint(
    @JsonFormat(pattern = "yyyy-MM-dd")
    val date: LocalDate,
    val cumulativeReturnPercentage: Double,
    val tradeNumber: Int,
  )
}
