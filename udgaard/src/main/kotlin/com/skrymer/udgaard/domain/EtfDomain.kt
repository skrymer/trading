package com.skrymer.udgaard.domain

import java.time.LocalDate

/**
 * Domain model for ETF (Hibernate-independent)
 * Represents an ETF with quotes, holdings, and metadata.
 */
data class EtfDomain(
  val symbol: String = "",
  val name: String = "",
  val description: String = "",
  val quotes: List<EtfQuoteDomain> = emptyList(),
  val holdings: List<EtfHoldingDomain> = emptyList(),
  val metadata: EtfMetadataDomain? = null,
) {
  /**
   * Get the most recent quote for this ETF.
   * @return The latest quote or null if no quotes exist
   */
  fun getLatestQuote(): EtfQuoteDomain? = quotes.maxByOrNull { it.date }

  /**
   * Get the quote for a specific date.
   * @param date The date to find the quote for
   * @return The quote for the specified date, or null if not found
   */
  fun getQuoteByDate(date: LocalDate): EtfQuoteDomain? = quotes.find { it.date == date }

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
   * @return List of quotes within the date range, sorted by date
   */
  fun getQuotesInRange(
    fromDate: LocalDate,
    toDate: LocalDate,
  ): List<EtfQuoteDomain> =
    quotes
      .filter { it.date >= fromDate && it.date <= toDate }
      .sortedBy { it.date }
}
