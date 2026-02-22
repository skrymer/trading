package com.skrymer.udgaard.backtesting.model

import java.time.LocalDate

data class PositionSizingResult(
  val startingCapital: Double,
  val finalCapital: Double,
  val totalReturnPct: Double,
  val maxDrawdownPct: Double,
  val maxDrawdownDollars: Double,
  val peakCapital: Double,
  val trades: List<PositionSizedTrade>,
  val equityCurve: List<PortfolioEquityPoint>,
)

data class PositionSizedTrade(
  val symbol: String,
  val entryDate: LocalDate,
  val exitDate: LocalDate,
  val shares: Int,
  val entryPrice: Double,
  val exitPrice: Double,
  val dollarProfit: Double,
  val portfolioValueAtEntry: Double,
  val portfolioReturnPct: Double,
)

data class PortfolioEquityPoint(
  val date: LocalDate,
  val portfolioValue: Double,
)
