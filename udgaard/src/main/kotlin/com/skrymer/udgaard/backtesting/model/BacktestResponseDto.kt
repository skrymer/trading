package com.skrymer.udgaard.backtesting.model

import java.time.LocalDate

data class EquityCurvePoint(
  val date: LocalDate,
  val profitPercentage: Double,
)

data class ExcursionPoint(
  val mfe: Double,
  val mae: Double,
  val mfeATR: Double,
  val maeATR: Double,
  val mfeReached: Boolean,
  val profitPercentage: Double,
  val isWinner: Boolean,
)

data class ExcursionSummary(
  val totalTrades: Int,
  val avgMFE: Double,
  val avgMAE: Double,
  val avgMFEATR: Double,
  val avgMAEATR: Double,
  val profitReachRate: Double,
  val avgMFEEfficiency: Double,
  val winnerCount: Int,
  val winnerAvgMFE: Double,
  val winnerAvgMAE: Double,
  val winnerAvgFinalProfit: Double,
  val loserCount: Int,
  val loserAvgMFE: Double,
  val loserAvgMAE: Double,
  val loserAvgFinalLoss: Double,
  val loserMissedWinRate: Double,
)

data class DailyProfitSummary(
  val date: LocalDate,
  val profitPercentage: Double,
  val tradeCount: Int,
)

data class MarketConditionPoint(
  val breadth: Double,
  val profitPercentage: Double,
  val isWinner: Boolean,
  val spyInUptrend: Boolean,
)

data class MarketConditionStats(
  val scatterPoints: List<MarketConditionPoint>,
  val uptrendWinRate: Double,
  val downtrendWinRate: Double,
  val uptrendCount: Int,
  val downtrendCount: Int,
)

data class SectorStatsDto(
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
  val edgeConsistency: EdgeConsistencyScore?,
)

data class BacktestResponseDto(
  val backtestId: String,
  // Scalar metrics
  val totalTrades: Int,
  val numberOfWinningTrades: Int,
  val numberOfLosingTrades: Int,
  val winRate: Double,
  val lossRate: Double,
  val averageWinAmount: Double,
  val averageWinPercent: Double,
  val averageLossAmount: Double,
  val averageLossPercent: Double,
  val edge: Double,
  val profitFactor: Double?,
  val stockProfits: List<Pair<String, Double>>,
  // Missed trades
  val missedOpportunitiesCount: Int,
  val missedProfitPercentage: Double,
  val missedAverageProfitPercentage: Double,
  // Analytics
  val timeBasedStats: TimeBasedStats?,
  val exitReasonAnalysis: ExitReasonAnalysis?,
  val sectorPerformance: List<SectorPerformance>,
  val stockPerformance: List<StockPerformance>,
  val atrDrawdownStats: ATRDrawdownStats?,
  val marketConditionAverages: Map<String, Double>?,
  val edgeConsistencyScore: EdgeConsistencyScore?,
  val sectorStats: List<SectorStatsDto>,
  // Pre-computed chart data
  val equityCurveData: List<EquityCurvePoint>,
  val excursionPoints: List<ExcursionPoint>,
  val excursionSummary: ExcursionSummary?,
  val dailyProfitSummary: List<DailyProfitSummary>,
  val marketConditionStats: MarketConditionStats?,
  val underlyingAssetTradeCount: Int,
  val positionSizing: PositionSizingResult? = null,
)
