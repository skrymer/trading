package com.skrymer.udgaard.model

import java.time.LocalDate

/**
 * Represents a single holding (stock) within an ETF with its weight and trend information.
 */
data class EtfHolding(
    val stockSymbol: String,                       // "AAPL", "MSFT", etc.
    val weight: Double,                            // Percentage weight (0.0-100.0)
    val shares: Long? = null,                      // Optional: number of shares held
    val marketValue: Double? = null,               // Optional: dollar value of holding
    val asOfDate: LocalDate,                       // When this holding data was captured

    // Computed/cached trend data for this holding
    val inUptrend: Boolean = false,                // Is this holding in uptrend?
    val trend: String? = null                      // "UPTREND", "DOWNTREND", "NEUTRAL"
)
