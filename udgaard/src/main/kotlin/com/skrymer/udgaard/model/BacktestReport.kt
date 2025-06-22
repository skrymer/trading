package com.skrymer.udgaard.model

import kotlin.math.abs

class BacktestReport(val winningTrades: List<Trade>, val losingTrades: List<Trade>) {
    /**
     * Calculated as number of winning trades / total trades
     * @return the win rate
     */
    val winRate: Double
        get() = (winningTrades.size.toDouble() / this.totalTrades)

    /**
     * Calculated as totalWinningAmount / totalWins
     *
     * @return the average win amount
     */
    val averageWinAmount: Double
        get() {
            val totalWinningAmount = winningTrades.sumOf { it.calculatePercentageProfit() }
            return totalWinningAmount / winningTrades.size
        }

    /**
     * Calculated as number of lossing trades / total trades
     * @return the loss rate
     */
    val lossRate: Double
        get() = (losingTrades.size.toDouble() / this.totalTrades)

    /**
     * Calculated as the totalLossingAmount / totalLosses
     * @return the absolute average loss amount
     */
    val averageLossAmount: Double
        get() {
            val totalLosingAmount = losingTrades.sumOf { it.calculatePercentageProfit() }
            return abs(totalLosingAmount / losingTrades.size)
        }

    /**
     *
     * @return the number total trades
     */
    val totalTrades: Int
        get() = winningTrades.size + losingTrades.size

    /**
     * (WinRate × AvgWin) − ((1−WinRate) × AvgLoss)
     * @return - the average profit you can expect per trade.
     */
    val edge: Double
        get() = (this.winRate * this.averageWinAmount) - ((1 - this.winRate) * this.averageLossAmount)

    /**
     * The number of winning trades
     */
    fun numberOfWinningTrades() = winningTrades.size

    /**
     * The number of losing trades
     */
    fun numberOfLosingTrades() = losingTrades.size

    /**
     *
     */
    fun mostProfitable(): Stock {
        return winningTrades.maxBy { it.calculateProfit() }.stock
    }

}
