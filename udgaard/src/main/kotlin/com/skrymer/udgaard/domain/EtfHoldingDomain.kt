package com.skrymer.udgaard.domain

import java.time.LocalDate

/**
 * Domain model for EtfHolding (Hibernate-independent)
 * Represents a single holding (stock) within an ETF with its weight and trend information.
 */
data class EtfHoldingDomain(
  val stockSymbol: String = "",
  val weight: Double = 0.0,
  val shares: Long? = null,
  val marketValue: Double? = null,
  val asOfDate: LocalDate = LocalDate.now(),
  val inUptrend: Boolean = false,
  val trend: String? = null,
)
