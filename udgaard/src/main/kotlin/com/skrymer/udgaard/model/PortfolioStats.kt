package com.skrymer.udgaard.model

/**
 * Data transfer object containing calculated portfolio statistics
 */
data class PortfolioStats(
  val totalTrades: Int,
  val openTrades: Int,
  val closedTrades: Int,
  val ytdReturn: Double, // Year-to-date return percentage
  val annualizedReturn: Double, // CAGR (Compound Annual Growth Rate)
  val avgWin: Double, // Average winning trade percentage
  val avgLoss: Double, // Average losing trade percentage
  val winRate: Double, // Percentage of winning trades
  val provenEdge: Double, // (Win Rate × Avg Win) - (Loss Rate × Avg Loss)
  val totalProfit: Double, // Total profit in currency
  val totalProfitPercentage: Double, // Total profit as percentage of initial balance
  val largestWin: Double? = null, // Largest winning trade percentage
  val largestLoss: Double? = null, // Largest losing trade percentage
  val numberOfWins: Int = 0,
  val numberOfLosses: Int = 0,
)
