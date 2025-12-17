package com.skrymer.udgaard.domain

import java.time.LocalDate

/**
 * Domain model for EtfQuote (Hibernate-independent)
 * Represents a single quote (price snapshot) for an ETF with technical indicators and trend metrics.
 */
data class EtfQuoteDomain(
  val date: LocalDate = LocalDate.now(),
  val openPrice: Double = 0.0,
  val closePrice: Double = 0.0,
  val high: Double = 0.0,
  val low: Double = 0.0,
  val volume: Long = 0,
  val closePriceEMA5: Double = 0.0,
  val closePriceEMA10: Double = 0.0,
  val closePriceEMA20: Double = 0.0,
  val closePriceEMA50: Double = 0.0,
  val atr: Double = 0.0,
  val bullishPercentage: Double = 0.0,
  val stocksInUptrend: Int = 0,
  val stocksInDowntrend: Int = 0,
  val stocksInNeutral: Int = 0,
  val totalHoldings: Int = 0,
  val lastBuySignal: LocalDate? = null,
  val lastSellSignal: LocalDate? = null,
) {
  /**
   * Check if the ETF is in an uptrend based on EMA positioning.
   * Uptrend: EMA10 > EMA20 and close price > EMA50
   */
  fun isInUptrend(): Boolean =
    closePriceEMA10 > closePriceEMA20 &&
      closePrice > closePriceEMA50

  /**
   * Check if there is an active buy signal (after the last sell signal).
   */
  fun hasBuySignal(): Boolean =
    lastBuySignal != null &&
      (lastSellSignal == null || lastBuySignal.isAfter(lastSellSignal))

  /**
   * Check if there is an active sell signal (after the last buy signal).
   */
  fun hasSellSignal(): Boolean =
    lastSellSignal != null &&
      (lastBuySignal == null || lastSellSignal.isAfter(lastBuySignal))
}
