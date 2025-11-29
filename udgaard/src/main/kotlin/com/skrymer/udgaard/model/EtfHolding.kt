package com.skrymer.udgaard.model

import jakarta.persistence.*
import java.time.LocalDate

/**
 * Represents a single holding (stock) within an ETF with its weight and trend information.
 */
@Entity
@Table(
    name = "etf_holdings",
    indexes = [
        Index(name = "idx_etf_holding_etf", columnList = "etf_symbol"),
        Index(name = "idx_etf_holding_stock", columnList = "stock_symbol")
    ]
)
data class EtfHolding(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etf_symbol", referencedColumnName = "symbol")
    val etf: EtfEntity? = null,

    @Column(name = "stock_symbol", length = 20)
    val stockSymbol: String = "",                  // "AAPL", "MSFT", etc.

    val weight: Double = 0.0,                      // Percentage weight (0.0-100.0)

    val shares: Long? = null,                      // Optional: number of shares held

    @Column(name = "market_value")
    val marketValue: Double? = null,               // Optional: dollar value of holding

    @Column(name = "as_of_date")
    val asOfDate: LocalDate = LocalDate.now(),     // When this holding data was captured

    // Computed/cached trend data for this holding
    @Column(name = "in_uptrend")
    val inUptrend: Boolean = false,                // Is this holding in uptrend?

    @Column(length = 20)
    val trend: String? = null                      // "UPTREND", "DOWNTREND", "NEUTRAL"
)
