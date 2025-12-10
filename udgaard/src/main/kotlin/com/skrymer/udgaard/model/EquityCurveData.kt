package com.skrymer.udgaard.model

import java.time.LocalDate

/**
 * Represents the equity curve data for portfolio visualization
 */
data class EquityCurveData(
  val dataPoints: List<EquityDataPoint>,
)

data class EquityDataPoint(
  val date: LocalDate,
  val balance: Double,
  val returnPercentage: Double, // Return percentage from initial balance
)
