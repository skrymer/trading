package com.skrymer.udgaard.model

import java.time.LocalDate

/**
 * Represents a single quote (price snapshot) for an ETF with technical indicators and trend metrics.
 */
data class EtfQuote(
    val date: LocalDate,
    val openPrice: Double,
    val closePrice: Double,
    val high: Double,
    val low: Double,
    val volume: Long,

    // Technical indicators (same as StockQuote)
    val closePriceEMA5: Double = 0.0,
    val closePriceEMA10: Double = 0.0,
    val closePriceEMA20: Double = 0.0,
    val closePriceEMA50: Double = 0.0,
    val atr: Double = 0.0,

    // ETF-specific trend metrics
    val bullishPercentage: Double = 0.0,          // % of holdings in uptrend
    val stocksInUptrend: Int = 0,                 // Count of holdings in uptrend
    val stocksInDowntrend: Int = 0,               // Count of holdings in downtrend
    val stocksInNeutral: Int = 0,                 // Count of holdings neutral
    val totalHoldings: Int = 0,                   // Total constituent stocks

    // Buy/sell signals
    val lastBuySignal: LocalDate? = null,
    val lastSellSignal: LocalDate? = null
) {
    /**
     * Check if the ETF is in an uptrend based on EMA positioning.
     * Uptrend: EMA10 > EMA20 and close price > EMA50
     */
    fun isInUptrend(): Boolean {
        return closePriceEMA10 > closePriceEMA20 &&
               closePrice > closePriceEMA50
    }

    /**
     * Check if there is an active buy signal (after the last sell signal).
     */
    fun hasBuySignal(): Boolean {
        return lastBuySignal != null &&
               (lastSellSignal == null || lastBuySignal.isAfter(lastSellSignal))
    }

    /**
     * Check if there is an active sell signal (after the last buy signal).
     */
    fun hasSellSignal(): Boolean {
        return lastSellSignal != null &&
               (lastBuySignal == null || lastSellSignal.isAfter(lastBuySignal))
    }
}
