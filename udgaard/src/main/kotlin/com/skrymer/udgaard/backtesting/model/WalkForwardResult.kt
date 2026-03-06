package com.skrymer.udgaard.backtesting.model

import java.time.LocalDate

data class WalkForwardConfig(
  val inSampleYears: Int = 5,
  val outOfSampleYears: Int = 1,
  val stepYears: Int = 1,
  val startDate: LocalDate,
  val endDate: LocalDate,
)

data class WalkForwardWindow(
  val inSampleStart: LocalDate,
  val inSampleEnd: LocalDate,
  val outOfSampleStart: LocalDate,
  val outOfSampleEnd: LocalDate,
  val derivedSectorRanking: List<String>,
  val inSampleEdge: Double,
  val outOfSampleEdge: Double,
  val inSampleTrades: Int,
  val outOfSampleTrades: Int,
  val inSampleWinRate: Double,
  val outOfSampleWinRate: Double,
)

data class WalkForwardResult(
  val windows: List<WalkForwardWindow>,
  val aggregateOosEdge: Double,
  val aggregateOosTrades: Int,
  val aggregateOosWinRate: Double,
  val walkForwardEfficiency: Double,
)
