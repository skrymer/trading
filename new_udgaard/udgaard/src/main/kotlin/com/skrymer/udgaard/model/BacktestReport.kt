package com.skrymer.udgaard.model

import kotlin.math.abs

class BacktestReport // TODO edge calulation
    (val winningTrades: MutableList<Trade?>, val losingTrades: MutableList<Trade?>) {
    val winRate: Double
        /**
         * Calculated as number of winning trades / total trades
         * @return the win rate
         */
        get() = (winningTrades.size.toDouble() / this.totalTrades)

    val averageWinAmount: Double
        /**
         * Calculated as totalWinningAmount / totalWins
         *
         * @return the average win amount
         */
        get() {
            val totalWinningAmount =
                winningTrades.stream().map<Double> { trade: Trade? -> trade!!.getProfit() }
                    .reduce { a: Double?, b: Double? -> a!! + b!! }.orElse(0)
            return totalWinningAmount / winningTrades.size
        }

    val lossRate: Double
        /**
         * Calculated as number of lossing trades / total trades
         * @return the loss rate
         */
        get() = (losingTrades.size.toDouble() / this.totalTrades)

    val averageLossAmount: Double
        /**
         * Calculated as the totalLossingAmount / totalLosses
         * @return the absolute average loss amount
         */
        get() {
            val totalLosingAmount =
                losingTrades.stream().map<Double> { trade: Trade? -> trade!!.getProfit() }
                    .reduce { a: Double?, b: Double? -> a!! + b!! }.orElse(0)
            return abs(totalLosingAmount / losingTrades.size)
        }

    val totalTrades: Int
        /**
         *
         * @return the number total trades
         */
        get() = winningTrades.size + losingTrades.size

    val edge: Double
        /**
         * (WinRate​ × AvgWin) − ((1−WinRate​) × AvgLoss)
         * @return - the average profit you can expect per trade.
         */
        get() = (this.winRate * this.averageWinAmount) - ((1 - this.winRate) * this.averageLossAmount)
}
