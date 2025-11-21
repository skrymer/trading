package com.skrymer.udgaard.model.montecarlo

import com.skrymer.udgaard.model.Trade
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
    val losingTrades: Int
) {
    data class EquityPoint(
        val date: LocalDate,
        val cumulativeReturnPercentage: Double,
        val tradeNumber: Int
    )
}
