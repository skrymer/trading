package com.skrymer.udgaard.portfolio.model

import com.skrymer.udgaard.portfolio.integration.broker.BrokerType
import java.time.LocalDateTime

/**
 * Domain model for Portfolio (Hibernate-independent)
 * Represents a user's trading portfolio
 */
data class Portfolio(
  val id: Long? = null,
  val userId: String? = null,
  val name: String = "",
  val initialBalance: Double = 0.0,
  val currentBalance: Double = 0.0,
  val currency: String = "USD",
  val createdDate: LocalDateTime = LocalDateTime.now(),
  val lastUpdated: LocalDateTime = LocalDateTime.now(),
  val broker: BrokerType = BrokerType.MANUAL,
  val brokerAccountId: String? = null,
  val brokerConfig: Map<String, String> = emptyMap(),
  val lastSyncDate: LocalDateTime? = null,
)
