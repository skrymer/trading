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
            val totalWinningAmount = winningTrades.sumOf { it.profit }
            return totalWinningAmount / winningTrades.size
        }

    /**
     * The average
     */
    val averageWinPercent: Double
        get() {
            val totalWinPercentage = winningTrades.sumOf { it.profitPercentage }
            return totalWinPercentage / winningTrades.size
        }

    /**
     * Calculated as number of losing trades / total trades
     * @return the loss rate
     */
    val lossRate: Double
        get() = (losingTrades.size.toDouble() / this.totalTrades)

    /**
     * Calculated as the totalLossAmount / totalLosses
     * @return the absolute average loss amount
     */
    val averageLossAmount: Double
        get() {
            val totalLossAmount = losingTrades.sumOf { it.profit }
            return abs(totalLossAmount / losingTrades.size)
        }

    /**
     * The average
     */
    val averageLossPercent: Double
        get() {
            val totalLossPercentage = losingTrades.sumOf { it.profitPercentage }
            return totalLossPercentage / losingTrades.size
        }
    /**
     *
     * @return the number total trades
     */
    val totalTrades: Int
        get() = winningTrades.size + losingTrades.size

    /**
     * (AvgWin × WinRate) − ((1−WinRate) × AvgLoss)
     * @return - the average profit percentage you can expect per trade.
     */
    val edge: Double
        get() = (this.averageWinPercent * this.winRate) - ((1 - this.winRate) * this.averageLossPercent)

    /**
     * The number of winning trades
     */
    val numberOfWinningTrades: Int
        get() = winningTrades.size

    /**
     * The number of losing trades
     */
    val numberOfLosingTrades: Int
        get() = losingTrades.size

    /**
     * Profitable stocks with their profits (based on stock price).
     */
    val stockProfits: List<Pair<Stock, Double>>
        get() = (winningTrades + losingTrades)
            .groupBy { it.stock }
            .map { map -> Pair(map.key, map.value.sumOf { it.profit }) }
            .sortedByDescending { it.second }

    /**
     * All trades, winning and losing.
     */
    val trades: List<Trade>
        get() = (winningTrades + losingTrades).sortedBy { it.entryQuote.date }
}
