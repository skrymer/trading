package com.skrymer.udgaard.model

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Represents a user's trading portfolio
 */
@Entity
@Table(
  name = "portfolios",
  indexes = [
    Index(name = "idx_portfolio_user_id", columnList = "user_id"),
  ],
)
data class Portfolio(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,
  @Column(name = "user_id", length = 100)
  val userId: String? = null, // For future multi-user support
  @Column(length = 200)
  val name: String = "",
  @Column(name = "initial_balance")
  val initialBalance: Double = 0.0,
  @Column(name = "current_balance")
  var currentBalance: Double = 0.0,
  @Column(length = 10)
  val currency: String = "USD", // e.g., "USD", "EUR", "GBP"
  @Column(name = "created_date")
  val createdDate: LocalDateTime = LocalDateTime.now(),
  @Column(name = "last_updated")
  var lastUpdated: LocalDateTime = LocalDateTime.now(),
)
