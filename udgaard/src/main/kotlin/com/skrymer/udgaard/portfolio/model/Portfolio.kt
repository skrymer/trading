package com.skrymer.udgaard.portfolio.model

import com.skrymer.udgaard.portfolio.integration.broker.BrokerType
import java.time.LocalDateTime

data class Portfolio(
  val id: Long? = null,
  val userId: String? = null,
  val name: String = "",
  val initialBalance: Double = 0.0,
  val currentBalance: Double = 0.0,
  val currency: String = "USD",
  val baseCurrency: String = "USD",
  val createdDate: LocalDateTime = LocalDateTime.now(),
  val lastUpdated: LocalDateTime = LocalDateTime.now(),
  val broker: BrokerType = BrokerType.MANUAL,
  val brokerAccountId: String? = null,
  val brokerConfig: Map<String, String> = emptyMap(),
  val lastSyncDate: LocalDateTime? = null,
  val initialFxRate: Double? = null,
) {
  fun withBalanceUpdated(newBalance: Double): Portfolio =
    copy(currentBalance = newBalance, lastUpdated = LocalDateTime.now())

  fun withSyncCompleted(syncedAt: LocalDateTime): Portfolio =
    copy(lastSyncDate = syncedAt, lastUpdated = syncedAt)

  companion object {
    fun create(
      name: String,
      initialBalance: Double,
      currency: String,
      userId: String? = null,
    ): Portfolio {
      require(name.isNotBlank()) { "name must not be blank" }
      require(currency.isNotBlank()) { "currency must not be blank" }
      require(initialBalance >= 0.0) { "initialBalance must be non-negative" }
      val now = LocalDateTime.now()
      return Portfolio(
        id = null,
        userId = userId,
        name = name,
        initialBalance = initialBalance,
        currentBalance = initialBalance,
        currency = currency,
        baseCurrency = currency,
        createdDate = now,
        lastUpdated = now,
      )
    }
  }
}
