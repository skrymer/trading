package com.skrymer.udgaard.domain

/**
 * Position statistics for a portfolio
 */
data class PositionStats(
  val totalTrades: Int,
  val openTrades: Int,
  val closedTrades: Int,
  val ytdReturn: Double,
  val annualizedReturn: Double,
  val avgWin: Double,
  val avgLoss: Double,
  val winRate: Double,
  val provenEdge: Double,
  val totalProfit: Double,
  val totalProfitPercentage: Double,
  val largestWin: Double? = null,
  val largestLoss: Double? = null,
  val numberOfWins: Int = 0,
  val numberOfLosses: Int = 0,
)

/**
 * Position with all its executions
 */
data class PositionWithExecutions(
  val position: PositionDomain,
  val executions: List<ExecutionDomain>,
)

/**
 * Result from broker import operation
 */
data class ImportResult(
  val positionsCreated: Int,
  val newPositions: Int = 0,
  val executionsCreated: Int,
  val rollsDetected: Int,
)
