package com.skrymer.udgaard.model

import com.fasterxml.jackson.annotation.JsonBackReference
import jakarta.persistence.*
import java.time.LocalDate

/**
 * Represents a quarterly earnings announcement for a stock.
 *
 * Used by trading strategies that need to exit positions before earnings announcements
 * to avoid earnings-related volatility.
 *
 * Data sourced from AlphaVantage EARNINGS endpoint.
 */
@Entity
@Table(
  name = "earnings",
  indexes = [
    Index(name = "idx_earning_symbol", columnList = "symbol"),
    Index(name = "idx_earning_fiscal_date", columnList = "fiscalDateEnding"),
    Index(name = "idx_earning_reported_date", columnList = "reportedDate"),
  ],
)
data class Earning(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,
  /**
   * Stock symbol (e.g., AAPL)
   */
  @Column(nullable = false, length = 10)
  var symbol: String = "",
  /**
   * Fiscal period ending date (e.g., 2024-09-30)
   */
  @Column(nullable = false)
  var fiscalDateEnding: LocalDate = LocalDate.now(),
  /**
   * Date when earnings were reported to the market (e.g., 2024-10-30)
   * This is the key date for strategies that exit before earnings
   */
  @Column(nullable = true)
  var reportedDate: LocalDate? = null,
  /**
   * Reported earnings per share
   */
  @Column(nullable = true)
  var reportedEPS: Double? = null,
  /**
   * Estimated/expected earnings per share
   */
  @Column(nullable = true)
  var estimatedEPS: Double? = null,
  /**
   * Earnings surprise (reported - estimated)
   */
  @Column(nullable = true)
  var surprise: Double? = null,
  /**
   * Earnings surprise as percentage
   */
  @Column(nullable = true)
  var surprisePercentage: Double? = null,
  /**
   * Report time: "pre-market", "post-market", or null
   */
  @Column(nullable = true, length = 20)
  var reportTime: String? = null,
  /**
   * Reference to the stock this earning belongs to
   * JsonBackReference prevents circular serialization
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "stock_symbol", referencedColumnName = "symbol")
  @JsonBackReference
  var stock: Stock? = null,
) {
  /**
   * No-argument constructor for Hibernate
   */
  constructor() : this(
    id = null,
    symbol = "",
    fiscalDateEnding = LocalDate.now(),
    reportedDate = null,
    reportedEPS = null,
    estimatedEPS = null,
    surprise = null,
    surprisePercentage = null,
    reportTime = null,
    stock = null,
  )

  /**
   * Check if earnings beat estimates
   */
  fun beatEstimates(): Boolean = (surprise ?: 0.0) > 0.0

  /**
   * Check if this is a future earnings date (not yet reported)
   */
  fun isFutureEarnings(): Boolean = reportedDate?.isAfter(LocalDate.now()) ?: false

  /**
   * Check if earnings report is within N days of a given date
   *
   * @param date Date to check against
   * @param days Number of days before earnings
   * @return true if earnings are within the specified days
   */
  fun isWithinDaysOf(
    date: LocalDate,
    days: Int,
  ): Boolean {
    val earningsDate = reportedDate ?: return false
    val daysDiff =
      java.time.temporal.ChronoUnit.DAYS
        .between(date, earningsDate)
    return daysDiff in 0..days.toLong()
  }

  override fun toString(): String =
    "Earning(symbol=$symbol, fiscalDate=$fiscalDateEnding, reportedDate=$reportedDate, " +
      "reportedEPS=$reportedEPS, estimatedEPS=$estimatedEPS, surprise=$surprise)"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Earning) return false
    if (id != null && other.id != null) return id == other.id
    return symbol == other.symbol && fiscalDateEnding == other.fiscalDateEnding
  }

  override fun hashCode(): Int = id?.hashCode() ?: (symbol.hashCode() * 31 + fiscalDateEnding.hashCode())
}
