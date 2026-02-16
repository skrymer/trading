package com.skrymer.udgaard.data.model

import java.time.LocalDate

data class MarketBreadthDaily(
  val quoteDate: LocalDate,
  val breadthPercent: Double,
  val ema5: Double = 0.0,
  val ema10: Double = 0.0,
  val ema20: Double = 0.0,
  val ema50: Double = 0.0,
  val donchianUpperBand: Double = 0.0,
  val donchianLowerBand: Double = 0.0,
) {
  fun isInUptrend() = breadthPercent > ema10
}
