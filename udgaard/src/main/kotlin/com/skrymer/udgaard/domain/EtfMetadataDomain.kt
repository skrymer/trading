package com.skrymer.udgaard.domain

import java.time.LocalDate

/**
 * Domain model for EtfMetadata (Hibernate-independent)
 * Metadata for an ETF including expense ratio, AUM, and other descriptive information.
 */
data class EtfMetadataDomain(
  val expenseRatio: Double? = null,
  val aum: Double? = null,
  val inceptionDate: LocalDate? = null,
  val issuer: String? = null,
  val exchange: String? = null,
  val currency: String = "USD",
  val type: String? = null,
  val benchmark: String? = null,
  val lastRebalanceDate: LocalDate? = null,
)
