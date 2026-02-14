package com.skrymer.udgaard.portfolio.dto

import com.skrymer.udgaard.portfolio.integration.broker.BrokerCredentials
import com.skrymer.udgaard.portfolio.integration.broker.BrokerType
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Request to create portfolio from broker data
 */
data class CreatePortfolioFromBrokerRequest(
  val name: String,
  val broker: BrokerType,
  val credentials: Map<String, String>,
  val startDate: LocalDate? = null,
  val currency: String = "USD",
  val initialBalance: Double? = null,
)

/**
 * Request to sync portfolio with broker
 */
data class SyncPortfolioRequest(
  val credentials: Map<String, String>,
)

/**
 * Request to test broker connection
 */
data class TestBrokerConnectionRequest(
  val broker: BrokerType,
  val credentials: Map<String, String>,
)

/**
 * Response for test broker connection
 */
data class TestBrokerConnectionResponse(
  val success: Boolean,
  val message: String,
)

/**
 * Helper extension to convert Map credentials to BrokerCredentials
 */
fun Map<String, String>.toBrokerCredentials(broker: BrokerType): BrokerCredentials =
  when (broker) {
    BrokerType.IBKR ->
      BrokerCredentials.IBKRCredentials(
        token = this["token"] ?: throw IllegalArgumentException("Missing 'token' for IBKR"),
        queryId = this["queryId"] ?: throw IllegalArgumentException("Missing 'queryId' for IBKR"),
      )
    BrokerType.MANUAL -> throw IllegalArgumentException("MANUAL portfolios don't use credentials")
  }

/**
 * Result from creating portfolio from broker
 */
data class CreateFromBrokerResult(
  val portfolio: com.skrymer.udgaard.portfolio.model.Portfolio,
  val tradesImported: Int,
  val tradesUpdated: Int,
  val errors: List<String>,
)

/**
 * Result from syncing portfolio with broker
 */
data class PortfolioSyncResult(
  val tradesImported: Int,
  val tradesUpdated: Int,
  val lastSyncDate: LocalDateTime,
  val errors: List<String>,
)

/**
 * Error response for broker operations
 */
data class BrokerErrorResponse(
  val error: String,
  val message: String,
  val timestamp: LocalDateTime = LocalDateTime.now(),
)
