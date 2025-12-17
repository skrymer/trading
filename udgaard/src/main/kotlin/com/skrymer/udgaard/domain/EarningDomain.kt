package com.skrymer.udgaard.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Domain model for Earning (Hibernate-independent)
 */
data class EarningDomain(
  val symbol: String = "",
  val fiscalDateEnding: LocalDate = LocalDate.now(),
  val reportedDate: LocalDate? = null,
  val reportedEPS: Double? = null,
  val estimatedEPS: Double? = null,
  val surprise: Double? = null,
  val surprisePercentage: Double? = null,
  val reportTime: String? = null,
) {
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
    val daysDiff = ChronoUnit.DAYS.between(date, earningsDate)
    return daysDiff in 0..days.toLong()
  }

  override fun toString(): String =
    "Earning(symbol=$symbol, fiscalDate=$fiscalDateEnding, reportedDate=$reportedDate, " +
      "reportedEPS=$reportedEPS, estimatedEPS=$estimatedEPS, surprise=$surprise)"
}
