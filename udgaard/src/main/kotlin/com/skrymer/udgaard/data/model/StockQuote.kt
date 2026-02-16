package com.skrymer.udgaard.data.model

import java.time.LocalDate

/**
 * Domain model for StockQuote
 */
data class StockQuote(
  val symbol: String = "",
  val date: LocalDate = LocalDate.now(),
  val closePrice: Double = 0.0,
  val openPrice: Double = 0.0,
  val high: Double = 0.0,
  val low: Double = 0.0,
  var closePriceEMA10: Double = 0.0,
  var closePriceEMA20: Double = 0.0,
  var closePriceEMA5: Double = 0.0,
  var closePriceEMA50: Double = 0.0,
  var closePriceEMA100: Double = 0.0,
  var ema200: Double = 0.0,
  var trend: String? = null,
  var atr: Double = 0.0,
  var adx: Double? = null,
  val volume: Long = 0L,
  var donchianUpperBand: Double = 0.0,
) {
  fun isInUptrend() = "Uptrend" == trend

  /**
   * Get half ATR value
   */
  fun getClosePriceMinusHalfAtr() = closePrice - (atr / 2)

  override fun toString() = "Symbol: $symbol Date: $date Close price: $closePrice"
}
