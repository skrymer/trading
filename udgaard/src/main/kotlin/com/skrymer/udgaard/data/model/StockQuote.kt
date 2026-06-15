package com.skrymer.udgaard.data.model

import java.time.LocalDate

/**
 * Domain model for StockQuote
 */
data class StockQuote(
  val symbol: String = "",
  val date: LocalDate = LocalDate.now(),
  val closePrice: Double = 0.0,
  // The un-adjusted provider close. [closePrice] is EODHD `adjusted_close` (split AND dividend adjusted);
  // absolute-level products like market cap must use this raw close, not [closePrice] (ADR 0027 landmine).
  // Null on bars predating the raw-close re-store — a name with no raw close has no point-in-time cap.
  val rawClose: Double? = null,
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
  var sma50: Double? = null,
  var sma150: Double? = null,
  var sma200: Double? = null,
  var high52Week: Double? = null,
  var low52Week: Double? = null,
  var relativeStrengthPercentile: Double? = null,
  var qualityPercentile: Double? = null,
) {
  fun isInUptrend() = "Uptrend" == trend

  /**
   * Get half ATR value
   */
  fun getClosePriceMinusHalfAtr() = closePrice - (atr / 2)

  override fun toString() = "Symbol: $symbol Date: $date Close price: $closePrice"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is StockQuote) return false
    return symbol == other.symbol && date == other.date
  }

  override fun hashCode(): Int = 31 * symbol.hashCode() + date.hashCode()
}
