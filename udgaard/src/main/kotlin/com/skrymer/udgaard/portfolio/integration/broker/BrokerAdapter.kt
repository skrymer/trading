package com.skrymer.udgaard.portfolio.integration.broker

import com.skrymer.udgaard.portfolio.model.CashTransactionType
import java.time.LocalDate

/**
 * Interface all broker integrations must implement.
 * Decouples broker-specific logic from core trade processing.
 */
interface BrokerAdapter {
  /**
   * Fetch trades from broker API and convert to standardized format
   * Also returns account info to minimize API calls (single request fetches both)
   *
   * @param credentials - Broker-specific credentials (token, API key, etc.)
   * @param accountId - Broker account identifier (can be empty, will be extracted from response)
   * @param startDate - Start date for trade history (optional - if null, uses broker's default)
   * @param endDate - End date for trade history (optional - if null, uses broker's default)
   * @return Combined result with trades and account info
   */
  fun fetchTrades(
    credentials: BrokerCredentials,
    accountId: String,
    startDate: LocalDate? = null,
    endDate: LocalDate? = null,
  ): BrokerDataResult

  /**
   * Test broker connection and credentials
   *
   * @param credentials - Broker-specific credentials
   * @param accountId - Broker account identifier
   * @return true if connection successful, false otherwise
   */
  fun testConnection(
    credentials: BrokerCredentials,
    accountId: String,
  ): Boolean

  /**
   * Get account information (balance, currency, etc.)
   *
   * @param credentials - Broker-specific credentials
   * @param accountId - Broker account identifier
   * @return Account information
   */
  fun getAccountInfo(
    credentials: BrokerCredentials,
    accountId: String,
  ): BrokerAccountInfo

  /**
   * Get broker type this adapter handles
   */
  fun getBrokerType(): BrokerType
}

/**
 * Broker account information
 */
data class BrokerAccountInfo(
  val accountId: String,
  val currency: String,
  val accountType: String,
  val balance: Double? = null,
)

/**
 * Combined result from broker API (trades + account info)
 * This minimizes API calls by fetching both in a single request
 */
data class BrokerDataResult(
  val trades: List<StandardizedTrade>,
  val accountInfo: BrokerAccountInfo,
  val cashTransactions: List<StandardizedCashTransaction> = emptyList(),
)

data class StandardizedCashTransaction(
  val brokerTransactionId: String,
  val type: CashTransactionType,
  val amount: Double,
  val currency: String,
  val fxRateToBase: Double?,
  val transactionDate: LocalDate,
  val description: String?,
)
