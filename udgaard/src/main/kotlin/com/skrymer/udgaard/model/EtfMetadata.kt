package com.skrymer.udgaard.model

import java.time.LocalDate

/**
 * Metadata for an ETF including expense ratio, AUM, and other descriptive information.
 */
data class EtfMetadata(
    val expenseRatio: Double? = null,              // Annual expense ratio (%)
    val aum: Double? = null,                       // Assets under management ($)
    val inceptionDate: LocalDate? = null,          // ETF launch date
    val issuer: String? = null,                    // "State Street", "Vanguard", etc.
    val exchange: String? = null,                  // "NYSE", "NASDAQ", etc.
    val currency: String = "USD",                  // Base currency
    val type: String? = null,                      // "Index", "Sector", "Leveraged", etc.
    val benchmark: String? = null,                 // "S&P 500", "NASDAQ-100", etc.
    val lastRebalanceDate: LocalDate? = null       // When holdings were last rebalanced
)
