package com.skrymer.udgaard.integration

import java.time.LocalDate

/**
 * Interface for technical indicator data providers
 *
 * Implementations should provide calculated technical indicators
 * commonly used in trading strategies (ATR, EMA, MACD, RSI, etc.)
 *
 * All methods are suspend functions to support rate limiting with true backpressure.
 */
interface TechnicalIndicatorProvider {
  /**
   * Get ATR (Average True Range) technical indicator values
   *
   * ATR measures market volatility by decomposing the entire range of an asset price
   * for a given period. Commonly used for position sizing and stop-loss placement.
   *
   * @param symbol Stock symbol (e.g., "AAPL", "QQQ", "TQQQ")
   * @param interval Time interval: "daily", "weekly", "monthly"
   * @param timePeriod Number of data points used to calculate ATR (typical: 14)
   * @return Map of date to ATR value, or null if unavailable
   */
  suspend fun getATR(
    symbol: String,
    interval: String = "daily",
    timePeriod: Int = 14,
    minDate: LocalDate = LocalDate.of(2020, 1, 1),
  ): Map<LocalDate, Double>?

  /**
   * Get ADX (Average Directional Index) technical indicator values
   *
   * ADX measures the strength of a trend (not the direction).
   * Values range from 0 to 100:
   * - 0-25: Absent or weak trend
   * - 25-50: Strong trend
   * - 50-75: Very strong trend
   * - 75-100: Extremely strong trend
   *
   * Commonly used to identify trending markets vs ranging markets.
   * Higher values indicate stronger trends (either up or down).
   *
   * @param symbol Stock symbol (e.g., "AAPL", "QQQ", "TQQQ")
   * @param interval Time interval: "daily", "weekly", "monthly"
   * @param timePeriod Number of data points used to calculate ADX (typical: 14)
   * @return Map of date to ADX value, or null if unavailable
   */
  suspend fun getADX(
    symbol: String,
    interval: String = "daily",
    timePeriod: Int = 14,
    minDate: LocalDate = LocalDate.of(2020, 1, 1),
  ): Map<LocalDate, Double>?
}
