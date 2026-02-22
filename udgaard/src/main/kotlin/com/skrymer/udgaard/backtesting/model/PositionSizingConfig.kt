package com.skrymer.udgaard.backtesting.model

data class PositionSizingConfig(
  val startingCapital: Double = 100_000.0,
  val riskPercentage: Double = 1.5,
  val nAtr: Double = 2.0,
  val leverageRatio: Double? = null,
)
