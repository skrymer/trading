package com.skrymer.udgaard.domain

import java.time.LocalDateTime

/**
 * Domain model for Portfolio (Hibernate-independent)
 * Represents a user's trading portfolio
 */
data class PortfolioDomain(
  val id: Long? = null,
  val userId: String? = null,
  val name: String = "",
  val initialBalance: Double = 0.0,
  val currentBalance: Double = 0.0,
  val currency: String = "USD",
  val createdDate: LocalDateTime = LocalDateTime.now(),
  val lastUpdated: LocalDateTime = LocalDateTime.now(),
)
