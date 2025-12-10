package com.skrymer.udgaard.model

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.LocalDate

/**
 * Metadata for an ETF including expense ratio, AUM, and other descriptive information.
 */
@Embeddable
data class EtfMetadata(
  @Column(name = "expense_ratio")
  val expenseRatio: Double? = null, // Annual expense ratio (%)
  val aum: Double? = null, // Assets under management ($)
  @Column(name = "inception_date")
  val inceptionDate: LocalDate? = null, // ETF launch date
  @Column(length = 100)
  val issuer: String? = null, // "State Street", "Vanguard", etc.
  @Column(length = 50)
  val exchange: String? = null, // "NYSE", "NASDAQ", etc.
  @Column(length = 10)
  val currency: String = "USD", // Base currency
  @Column(length = 50)
  val type: String? = null, // "Index", "Sector", "Leveraged", etc.
  @Column(length = 100)
  val benchmark: String? = null, // "S&P 500", "NASDAQ-100", etc.
  @Column(name = "last_rebalance_date")
  val lastRebalanceDate: LocalDate? = null, // When holdings were last rebalanced
)
