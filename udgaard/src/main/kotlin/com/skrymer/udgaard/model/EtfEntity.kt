package com.skrymer.udgaard.model

import jakarta.persistence.*
import java.time.LocalDate

/**
 * Represents an ETF (Exchange-Traded Fund) entity with quotes, holdings, and metadata.
 * This is the main entity for ETF data persistence.
 */
@Entity
@Table(name = "etfs")
class EtfEntity {
    @Id
    @Column(length = 20)
    var symbol: String? = null                    // "SPY", "QQQ", etc.

    @Column(length = 200)
    var name: String? = null                       // "SPDR S&P 500 ETF Trust"

    @Column(length = 500)
    var description: String? = null                // "Tracks S&P 500 Index"

    @OneToMany(mappedBy = "etf", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var quotes: MutableList<EtfQuote> = mutableListOf()      // Historical price data

    @OneToMany(mappedBy = "etf", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var holdings: MutableList<EtfHolding> = mutableListOf()  // Current holdings with weights

    @Embedded
    var metadata: EtfMetadata? = null              // Expense ratio, AUM, etc.

    /**
     * Get the most recent quote for this ETF.
     * @return The latest quote or null if no quotes exist
     */
    fun getLatestQuote(): EtfQuote? {
        return quotes.maxByOrNull { it.date }
    }

    /**
     * Get the quote for a specific date.
     * @param date The date to find the quote for
     * @return The quote for the specified date, or null if not found
     */
    fun getQuoteByDate(date: LocalDate): EtfQuote? {
        return quotes.find { it.date == date }
    }

    /**
     * Get the bullish percentage (% of holdings in uptrend) for a specific date.
     * If date is null, returns the latest value.
     * @param date The date to get the bullish percentage for (null for latest)
     * @return The bullish percentage (0.0-100.0)
     */
    fun getBullishPercentage(date: LocalDate? = null): Double {
        val quote = date?.let { getQuoteByDate(it) } ?: getLatestQuote()
        return quote?.bullishPercentage ?: 0.0
    }

    /**
     * Get the number of stocks in uptrend for a specific date.
     * If date is null, returns the latest value.
     * @param date The date to get the count for (null for latest)
     * @return The count of stocks in uptrend
     */
    fun getStocksInUptrend(date: LocalDate? = null): Int {
        val quote = date?.let { getQuoteByDate(it) } ?: getLatestQuote()
        return quote?.stocksInUptrend ?: 0
    }

    /**
     * Get the number of stocks in downtrend for a specific date.
     * If date is null, returns the latest value.
     * @param date The date to get the count for (null for latest)
     * @return The count of stocks in downtrend
     */
    fun getStocksInDowntrend(date: LocalDate? = null): Int {
        val quote = date?.let { getQuoteByDate(it) } ?: getLatestQuote()
        return quote?.stocksInDowntrend ?: 0
    }

    /**
     * Get all quotes within a date range (inclusive).
     * @param fromDate Start date
     * @param toDate End date
     * @return List of quotes within the range, sorted by date ascending
     */
    fun getQuotesByDateRange(fromDate: LocalDate, toDate: LocalDate): List<EtfQuote> {
        return quotes
            .filter { it.date in fromDate..toDate }
            .sortedBy { it.date }
    }

    /**
     * Check if the ETF is currently in an uptrend based on the latest quote.
     * @return true if in uptrend, false otherwise
     */
    fun isInUptrend(): Boolean {
        return getLatestQuote()?.isInUptrend() ?: false
    }

    /**
     * Get the top holdings by weight.
     * @param limit Maximum number of holdings to return
     * @return List of holdings sorted by weight descending
     */
    fun getTopHoldings(limit: Int = 10): List<EtfHolding> {
        return holdings
            .sortedByDescending { it.weight }
            .take(limit)
    }

    /**
     * Get holdings that are currently in an uptrend.
     * @return List of holdings in uptrend
     */
    fun getHoldingsInUptrend(): List<EtfHolding> {
        return holdings.filter { it.inUptrend }
    }

    /**
     * Get the total weight of holdings in uptrend.
     * @return Sum of weights for holdings in uptrend
     */
    fun getTotalWeightInUptrend(): Double {
        return holdings.filter { it.inUptrend }.sumOf { it.weight }
    }

    override fun toString(): String {
        return "EtfEntity(symbol=$symbol, name=$name, quotes=${quotes.size}, holdings=${holdings.size})"
    }
}
