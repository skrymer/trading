package com.skrymer.udgaard.model.strategy

data class ExitStrategyReport(
  val match: Boolean = false,
  val exitReason: String? = null,
  val exitPrice: Double = 0.0
)