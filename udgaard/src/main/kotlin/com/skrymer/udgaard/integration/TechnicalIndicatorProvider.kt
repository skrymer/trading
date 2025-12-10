package com.skrymer.udgaard.integration

import java.time.LocalDate

/**
 * Interface for technical indicator data providers
 *
 * Implementations should provide calculated technical indicators
 * commonly used in trading strategies (ATR, EMA, MACD, RSI, etc.)
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
    fun getATR(
        symbol: String,
        interval: String = "daily",
        timePeriod: Int = 14
    ): Map<LocalDate, Double>?
}
