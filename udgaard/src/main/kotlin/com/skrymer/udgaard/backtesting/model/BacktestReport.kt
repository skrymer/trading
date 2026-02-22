package com.skrymer.udgaard.backtesting.model

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
  val edgeConsistency: EdgeConsistencyScore?,
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
   * Keys: "avgMarketBreadth", "spyUptrendPercent"
   */
  val marketConditionAverages: Map<String, Double>? = null,
  /**
   * Edge consistency score (0–100) measuring how consistent the strategy's
   * edge is across yearly periods. Null when fewer than 2 years of data.
   */
  val edgeConsistencyScore: EdgeConsistencyScore? = null,
  val positionSizingResult: PositionSizingResult? = null,
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

          // Calculate edge consistency per sector
          val yearlyStats = sectorTrades
            .groupBy { it.entryQuote.date.year }
            .mapValues { (_, yearTrades) ->
              val yw = yearTrades.filter { it.profit > 0 }
              val yl = yearTrades.filter { it.profit <= 0 }
              val ywr = if (yearTrades.isNotEmpty()) yw.size.toDouble() / yearTrades.size else 0.0
              val yaw = if (yw.isNotEmpty()) yw.sumOf { it.profitPercentage } / yw.size else 0.0
              val yal = if (yl.isNotEmpty()) abs(yl.sumOf { it.profitPercentage } / yl.size) else 0.0
              val ye = (yaw * ywr) - ((1.0 - ywr) * yal)
              PeriodStats(
                trades = yearTrades.size,
                winRate = ywr,
                avgProfit = yearTrades.map { it.profitPercentage }.average(),
                avgHoldingDays = yearTrades.map { it.quotes.size.toDouble() }.average(),
                edge = ye,
                exitReasons = emptyMap(),
              )
            }
          val edgeConsistency = calculateEdgeConsistency(yearlyStats)

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
            edgeConsistency = edgeConsistency,
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

private const val MAX_EXCURSION_POINTS = 5000

fun BacktestReport.toResponseDto(backtestId: String): BacktestResponseDto {
  val allTrades = trades

  return BacktestResponseDto(
    backtestId = backtestId,
    totalTrades = totalTrades,
    numberOfWinningTrades = numberOfWinningTrades,
    numberOfLosingTrades = numberOfLosingTrades,
    winRate = winRate,
    lossRate = lossRate,
    averageWinAmount = averageWinAmount,
    averageWinPercent = averageWinPercent,
    averageLossAmount = averageLossAmount,
    averageLossPercent = averageLossPercent,
    edge = edge,
    profitFactor = profitFactor,
    stockProfits = stockProfits,
    missedOpportunitiesCount = missedOpportunitiesCount,
    missedProfitPercentage = missedProfitPercentage,
    missedAverageProfitPercentage = missedAverageProfitPercentage,
    timeBasedStats = timeBasedStats,
    exitReasonAnalysis = exitReasonAnalysis,
    sectorPerformance = sectorPerformance,
    stockPerformance = stockPerformance,
    atrDrawdownStats = atrDrawdownStats,
    marketConditionAverages = marketConditionAverages,
    edgeConsistencyScore = edgeConsistencyScore,
    sectorStats = buildSectorStatsDto(sectorStats),
    equityCurveData = buildEquityCurveData(allTrades),
    excursionPoints = buildExcursionPoints(allTrades),
    excursionSummary = buildExcursionSummary(allTrades),
    dailyProfitSummary = buildDailyProfitSummary(allTrades),
    marketConditionStats = buildMarketConditionStats(allTrades),
    underlyingAssetTradeCount = allTrades.count { it.underlyingSymbol != null && it.underlyingSymbol != it.stockSymbol },
    positionSizing = positionSizingResult,
  )
}

private fun buildEquityCurveData(trades: List<Trade>): List<EquityCurvePoint> =
  trades
    .filter { it.quotes.isNotEmpty() }
    .sortedBy { it.quotes.maxByOrNull { q -> q.date }?.date }
    .map { trade ->
      val exitDate = trade.quotes.maxByOrNull { it.date }!!.date
      EquityCurvePoint(date = exitDate, profitPercentage = trade.profitPercentage)
    }

private fun buildExcursionPoints(trades: List<Trade>): List<ExcursionPoint> {
  val rawPoints =
    trades
      .filter { it.excursionMetrics != null }
      .map { trade ->
        val m = trade.excursionMetrics!!
        ExcursionPoint(
          mfe = m.maxFavorableExcursion,
          mae = m.maxAdverseExcursion,
          mfeATR = m.maxFavorableExcursionATR,
          maeATR = m.maxAdverseExcursionATR,
          mfeReached = m.mfeReached,
          profitPercentage = trade.profitPercentage,
          isWinner = trade.profitPercentage > 0,
        )
      }

  return if (rawPoints.size > MAX_EXCURSION_POINTS) {
    val step = rawPoints.size.toDouble() / MAX_EXCURSION_POINTS
    (0 until MAX_EXCURSION_POINTS).map { i -> rawPoints[(i * step).toInt()] }
  } else {
    rawPoints
  }
}

private fun buildExcursionSummary(trades: List<Trade>): ExcursionSummary? {
  val tradesWithExcursions = trades.filter { it.excursionMetrics != null }
  if (tradesWithExcursions.isEmpty()) return null

  val winners = tradesWithExcursions.filter { it.profitPercentage > 0 }
  val losers = tradesWithExcursions.filter { it.profitPercentage <= 0 }

  val avgMFEEfficiency =
    if (winners.isNotEmpty()) {
      winners
        .map { t ->
          val mfe = t.excursionMetrics!!.maxFavorableExcursion
          if (mfe > 0) (t.profitPercentage / mfe) * 100 else 0.0
        }.average()
    } else {
      0.0
    }

  val profitReachRate =
    tradesWithExcursions.count { it.excursionMetrics!!.mfeReached }.toDouble() /
      tradesWithExcursions.size * 100

  return ExcursionSummary(
    totalTrades = tradesWithExcursions.size,
    avgMFE = tradesWithExcursions.map { it.excursionMetrics!!.maxFavorableExcursion }.average(),
    avgMAE = tradesWithExcursions.map { it.excursionMetrics!!.maxAdverseExcursion }.average(),
    avgMFEATR = tradesWithExcursions.map { it.excursionMetrics!!.maxFavorableExcursionATR }.average(),
    avgMAEATR = tradesWithExcursions.map { it.excursionMetrics!!.maxAdverseExcursionATR }.average(),
    profitReachRate = profitReachRate,
    avgMFEEfficiency = avgMFEEfficiency,
    winnerCount = winners.size,
    winnerAvgMFE = winners.avgOrZero { it.excursionMetrics!!.maxFavorableExcursion },
    winnerAvgMAE = winners.avgOrZero { it.excursionMetrics!!.maxAdverseExcursion },
    winnerAvgFinalProfit = winners.avgOrZero { it.profitPercentage },
    loserCount = losers.size,
    loserAvgMFE = losers.avgOrZero { it.excursionMetrics!!.maxFavorableExcursion },
    loserAvgMAE = losers.avgOrZero { it.excursionMetrics!!.maxAdverseExcursion },
    loserAvgFinalLoss = losers.avgOrZero { it.profitPercentage },
    loserMissedWinRate =
      if (losers.isNotEmpty()) {
        (losers.count { it.excursionMetrics!!.mfeReached }.toDouble() / losers.size) * 100
      } else {
        0.0
      },
  )
}

private fun List<Trade>.avgOrZero(selector: (Trade) -> Double): Double =
  if (isNotEmpty()) map(selector).average() else 0.0

private fun buildDailyProfitSummary(trades: List<Trade>): List<DailyProfitSummary> =
  trades
    .groupBy { it.entryQuote.date!! }
    .entries
    .map { (date, dateTrades) ->
      DailyProfitSummary(
        date = date,
        profitPercentage = dateTrades.sumOf { it.profitPercentage },
        tradeCount = dateTrades.size,
      )
    }.sortedBy { it.date }

private fun buildSectorStatsDto(sectorStats: List<SectorStats>): List<SectorStatsDto> =
  sectorStats.map {
    SectorStatsDto(
      sector = it.sector,
      totalTrades = it.totalTrades,
      winningTrades = it.winningTrades,
      losingTrades = it.losingTrades,
      winRate = it.winRate,
      edge = it.edge,
      averageWinPercent = it.averageWinPercent,
      averageLossPercent = it.averageLossPercent,
      totalProfitPercentage = it.totalProfitPercentage,
      maxDrawdown = it.maxDrawdown,
      edgeConsistency = it.edgeConsistency,
    )
  }

private fun buildMarketConditionStats(trades: List<Trade>): MarketConditionStats? {
  val tradesWithMarketData = trades.filter { it.marketConditionAtEntry != null }
  if (tradesWithMarketData.isEmpty()) return null

  val scatterPoints =
    tradesWithMarketData
      .filter { it.marketConditionAtEntry?.marketBreadthBullPercent != null }
      .map { trade ->
        val mc = trade.marketConditionAtEntry!!
        MarketConditionPoint(
          breadth = mc.marketBreadthBullPercent!!,
          profitPercentage = trade.profitPercentage,
          isWinner = trade.profit > 0,
          spyInUptrend = mc.spyInUptrend,
        )
      }

  val uptrendTrades = tradesWithMarketData.filter { it.marketConditionAtEntry!!.spyInUptrend }
  val downtrendTrades = tradesWithMarketData.filter { !it.marketConditionAtEntry!!.spyInUptrend }

  fun calcWinRate(list: List<Trade>): Double =
    if (list.isEmpty()) 0.0 else (list.count { it.profit > 0 }.toDouble() / list.size) * 100

  return MarketConditionStats(
    scatterPoints = scatterPoints,
    uptrendWinRate = calcWinRate(uptrendTrades),
    downtrendWinRate = calcWinRate(downtrendTrades),
    uptrendCount = uptrendTrades.size,
    downtrendCount = downtrendTrades.size,
  )
}
