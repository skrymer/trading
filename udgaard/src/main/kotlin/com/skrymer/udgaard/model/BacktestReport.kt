package com.skrymer.udgaard.model

import java.time.LocalDate
import kotlin.math.abs

/**
 * Statistics for a specific sector
 */
data class SectorStats(
  val sector: String,
  val totalTrades: Int,
  val winningTrades: Int,
  val losingTrades: Int,
  val winRate: Double,
  val edge: Double,
  val averageWinPercent: Double,
  val averageLossPercent: Double,
  val totalProfitPercentage: Double,
  val maxDrawdown: Double,
  val trades: List<Trade>,
)

/**
 * Performance statistics for a specific stock
 */
data class StockPerformance(
  val symbol: String,
  val trades: Int,
  val winRate: Double,
  val avgProfit: Double,
  val avgHoldingDays: Double,
  val totalProfitPercentage: Double,
  val edge: Double,
  /**
   * Profit Factor = Gross Profit / Gross Loss
   * A value > 1.0 indicates profitable performance for this stock
   * A value of 2.0 means the stock made $2 for every $1 lost
   * Null if there are no losing trades
   */
  val profitFactor: Double?,
  /**
   * Maximum drawdown percentage for this stock
   * The peak-to-trough decline in cumulative returns
   */
  val maxDrawdown: Double,
)

class BacktestReport(
  val winningTrades: List<Trade>,
  val losingTrades: List<Trade>,
  val missedTrades: List<Trade> = emptyList(),
  /**
   * Performance broken down by time periods (year, quarter, month).
   */
  val timeBasedStats: TimeBasedStats? = null,
  /**
   * Exit reason analysis with stats per reason and per year.
   */
  val exitReasonAnalysis: ExitReasonAnalysis? = null,
  /**
   * Performance broken down by sector.
   */
  val sectorPerformance: List<SectorPerformance> = emptyList(),
  /**
   * Performance broken down by stock symbol.
   * Sorted by edge (descending), showing top performing stocks.
   */
  val stockPerformance: List<StockPerformance> = emptyList(),
  /**
   * ATR drawdown statistics for winning trades.
   * Shows how much adverse movement winners endure before becoming profitable.
   */
  val atrDrawdownStats: ATRDrawdownStats? = null,
  /**
   * Average market conditions at trade entry.
   * Keys: "avgSpyHeatmap", "avgMarketBreadth", "spyUptrendPercent"
   */
  val marketConditionAverages: Map<String, Double>? = null,
) {
  /**
   * Calculated as (number of winning trades / total trades)
   * @return the win rate
   */
  val winRate: Double
    get() = (winningTrades.size.toDouble() / this.totalTrades)

  /**
   * Calculated as totalWinningAmount / totalWins
   * @return the average win amount
   */
  val averageWinAmount: Double
    get() {
      if (winningTrades.isEmpty()) return 0.0
      val totalWinningAmount = winningTrades.sumOf { it.profit }
      return totalWinningAmount / winningTrades.size
    }

  /**
   * The average win percentage.
   */
  val averageWinPercent: Double
    get() {
      if (winningTrades.isEmpty()) return 0.0
      val totalWinPercentage = winningTrades.sumOf { it.profitPercentage }
      return totalWinPercentage / winningTrades.size
    }

  /**
   * Calculated as (number of losing trades / total trades)
   * @return the loss rate
   */
  val lossRate: Double
    get() = (losingTrades.size.toDouble() / this.totalTrades)

  /**
   * Calculated as the (totalLossAmount / totalLosses)
   * @return the absolute average loss amount
   */
  val averageLossAmount: Double
    get() {
      if (losingTrades.isEmpty()) return 0.0
      val totalLossAmount = losingTrades.sumOf { it.profit }
      return abs(totalLossAmount / losingTrades.size)
    }

  /**
   * The average loss percentage
   */
  val averageLossPercent: Double
    get() {
      if (losingTrades.isEmpty()) return 0.0
      val totalLossPercentage = losingTrades.sumOf { it.profitPercentage }
      return abs(totalLossPercentage / losingTrades.size)
    }

  /**
   *
   * @return the number of total trades
   */
  val totalTrades: Int
    get() = winningTrades.size + losingTrades.size

  /**
   * (AvgWinPercentage × WinRate) − ((1−WinRate) × AvgLossPercentage)
   * @return - the average percentage gain to expect per trade.
   */
  val edge: Double
    get() = (this.averageWinPercent * this.winRate) - ((1.0 - this.winRate) * this.averageLossPercent)

  /**
   * Profit Factor = Gross Profit / Gross Loss
   * A value > 1.0 indicates a profitable strategy
   * A value of 2.0 means the strategy makes $2 for every $1 lost
   * @return the profit factor, or null if there are no losing trades
   */
  val profitFactor: Double?
    get() {
      if (losingTrades.isEmpty()) return null
      val grossProfit = winningTrades.sumOf { it.profit }
      val grossLoss = abs(losingTrades.sumOf { it.profit })
      return if (grossLoss == 0.0) 0.0 else grossProfit / grossLoss
    }

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
   * Profitable stocks with their profit percentage (based on stock price).
   */
  val stockProfits: List<Pair<String, Double>>
    get() =
      (winningTrades + losingTrades)
        .groupBy { it.stockSymbol }
        .map { map -> Pair(map.key, map.value.sumOf { it.profitPercentage }) }
        .sortedByDescending { it.second }

  /**
   * All trades, winning and losing.
   */
  val trades: List<Trade>
    get() = (winningTrades + losingTrades).sortedBy { it.entryQuote.date }

  /**
   * Exit reason grouped by count
   */
  val exitReasonCount: Map<String, Int>
    get() = trades.groupingBy { it.exitReason }.eachCount()

  /**
   * Trades grouped by date
   */
  val tradesGroupedByDate: List<ReportEntry>
    get() =
      trades
        .groupBy { it.entryQuote.date!! }
        .entries
        .map {
          ReportEntry(
            date = it.key,
            profitPercentage =
              it.value.sumOf { trade ->
                trade.profitPercentage
              },
            trades = it.value,
          )
        }

  /**
   * Number of missed opportunities due to position limits
   */
  val missedOpportunitiesCount: Int
    get() = missedTrades.size

  /**
   * Total potential profit from missed trades
   */
  val missedProfitPercentage: Double
    get() = if (missedTrades.isEmpty()) 0.0 else missedTrades.sumOf { it.profitPercentage }

  /**
   * Average profit percentage of missed trades
   */
  val missedAverageProfitPercentage: Double
    get() = if (missedTrades.isEmpty()) 0.0 else missedProfitPercentage / missedTrades.size

  /**
   * Statistics grouped by sector
   */
  val sectorStats: List<SectorStats>
    get() {
      val allTrades = trades
      if (allTrades.isEmpty()) return emptyList()

      return allTrades
        .groupBy { it.sector }
        .map { (sector, sectorTrades) ->
          val sectorWinningTrades = sectorTrades.filter { it.profit > 0 }
          val sectorLosingTrades = sectorTrades.filter { it.profit <= 0 }

          val sectorWinRate =
            if (sectorTrades.isNotEmpty()) {
              sectorWinningTrades.size.toDouble() / sectorTrades.size
            } else {
              0.0
            }

          val sectorAvgWinPercent =
            if (sectorWinningTrades.isNotEmpty()) {
              sectorWinningTrades.sumOf { it.profitPercentage } / sectorWinningTrades.size
            } else {
              0.0
            }

          val sectorAvgLossPercent =
            if (sectorLosingTrades.isNotEmpty()) {
              abs(sectorLosingTrades.sumOf { it.profitPercentage } / sectorLosingTrades.size)
            } else {
              0.0
            }

          val sectorEdge = (sectorAvgWinPercent * sectorWinRate) - ((1.0 - sectorWinRate) * sectorAvgLossPercent)

          val totalProfitPercentage = sectorTrades.sumOf { it.profitPercentage }

          // Calculate max drawdown for this sector
          val maxDrawdown = calculateMaxDrawdown(sectorTrades)

          SectorStats(
            sector = sector,
            totalTrades = sectorTrades.size,
            winningTrades = sectorWinningTrades.size,
            losingTrades = sectorLosingTrades.size,
            winRate = sectorWinRate,
            edge = sectorEdge,
            averageWinPercent = sectorAvgWinPercent,
            averageLossPercent = sectorAvgLossPercent,
            totalProfitPercentage = totalProfitPercentage,
            maxDrawdown = maxDrawdown,
            trades = sectorTrades,
          )
        }.sortedByDescending { it.edge }
    }

  /**
   * Calculate maximum drawdown for a list of trades
   * Drawdown is the peak-to-trough decline in cumulative returns
   */
  private fun calculateMaxDrawdown(trades: List<Trade>): Double {
    if (trades.isEmpty()) return 0.0

    val sortedTrades = trades.sortedBy { it.entryQuote.date }
    var peak = 0.0
    var maxDrawdown = 0.0
    var cumulativeReturn = 0.0

    sortedTrades.forEach { trade ->
      cumulativeReturn += trade.profitPercentage

      if (cumulativeReturn > peak) {
        peak = cumulativeReturn
      }

      val drawdown = peak - cumulativeReturn
      if (drawdown > maxDrawdown) {
        maxDrawdown = drawdown
      }
    }

    return maxDrawdown
  }

  data class ReportEntry(
    val date: LocalDate,
    val profitPercentage: Double,
    val trades: List<Trade>,
  )
}
