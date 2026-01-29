package com.skrymer.udgaard.domain

import java.time.LocalDate

/**
 * Domain model for BreadthQuote
 * A single breadth data point for a specific date.
 */
data class BreadthQuoteDomain(
  val symbol: String = "",
  val quoteDate: LocalDate = LocalDate.now(),
  val numberOfStocksWithABuySignal: Int = 0,
  val numberOfStocksWithASellSignal: Int = 0,
  val numberOfStocksInUptrend: Int = 0,
  val numberOfStocksInNeutral: Int = 0,
  val numberOfStocksInDowntrend: Int = 0,
  val bullStocksPercentage: Double = 0.0,
  val ema_5: Double = 0.0,
  val ema_10: Double = 0.0,
  val ema_20: Double = 0.0,
  val ema_50: Double = 0.0,
  val heatmap: Double = 0.0,
  val previousHeatmap: Double = 0.0,
  val donchianUpperBand: Double = 0.0,
  val previousDonchianUpperBand: Double = 0.0,
  val donchianLowerBand: Double = 0.0,
  val previousDonchianLowerBand: Double = 0.0,
  val donkeyChannelScore: Int = 0,
) {
  /**
   * @return true if percentage of bullish stocks are higher than the 10ema.
   */
  fun isInUptrend() = bullStocksPercentage > ema_10
}
