package com.skrymer.udgaard.model

import jakarta.persistence.*
import java.time.LocalDate

/**
 * Represents a single quote (price snapshot) for an ETF with technical indicators and trend metrics.
 */
@Entity
@Table(
    name = "etf_quotes",
    indexes = [
        Index(name = "idx_etf_quote_symbol_date", columnList = "etf_symbol, quote_date", unique = true),
        Index(name = "idx_etf_quote_date", columnList = "quote_date")
    ]
)
data class EtfQuote(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etf_symbol", referencedColumnName = "symbol")
    val etf: EtfEntity? = null,

    @Column(name = "quote_date", nullable = false)
    val date: LocalDate = LocalDate.now(),

    @Column(name = "open_price")
    val openPrice: Double = 0.0,

    @Column(name = "close_price")
    val closePrice: Double = 0.0,

    @Column(name = "high_price")
    val high: Double = 0.0,

    @Column(name = "low_price")
    val low: Double = 0.0,

    val volume: Long = 0,

    // Technical indicators (same as StockQuote)
    @Column(name = "close_price_ema5")
    val closePriceEMA5: Double = 0.0,

    @Column(name = "close_price_ema10")
    val closePriceEMA10: Double = 0.0,

    @Column(name = "close_price_ema20")
    val closePriceEMA20: Double = 0.0,

    @Column(name = "close_price_ema50")
    val closePriceEMA50: Double = 0.0,

    val atr: Double = 0.0,

    // ETF-specific trend metrics
    @Column(name = "bullish_percentage")
    val bullishPercentage: Double = 0.0,          // % of holdings in uptrend

    @Column(name = "stocks_in_uptrend")
    val stocksInUptrend: Int = 0,                 // Count of holdings in uptrend

    @Column(name = "stocks_in_downtrend")
    val stocksInDowntrend: Int = 0,               // Count of holdings in downtrend

    @Column(name = "stocks_in_neutral")
    val stocksInNeutral: Int = 0,                 // Count of holdings neutral

    @Column(name = "total_holdings")
    val totalHoldings: Int = 0,                   // Total constituent stocks

    // Buy/sell signals
    @Column(name = "last_buy_signal")
    val lastBuySignal: LocalDate? = null,

    @Column(name = "last_sell_signal")
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
