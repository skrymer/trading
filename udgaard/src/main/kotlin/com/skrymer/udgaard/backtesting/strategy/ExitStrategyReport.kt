package com.skrymer.udgaard.backtesting.strategy

data class ExitStrategyReport(
  val match: Boolean = false,
  val exitReason: String? = null,
  val exitPrice: Double = 0.0,
)
