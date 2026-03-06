package com.skrymer.udgaard.portfolio.integration.ibkr

import com.skrymer.udgaard.portfolio.integration.broker.BrokerAccountInfo
import com.skrymer.udgaard.portfolio.integration.broker.BrokerAdapter
import com.skrymer.udgaard.portfolio.integration.broker.BrokerCredentials
import com.skrymer.udgaard.portfolio.integration.broker.BrokerDataResult
import com.skrymer.udgaard.portfolio.integration.broker.BrokerType
import com.skrymer.udgaard.portfolio.integration.broker.StandardizedCashTransaction
import com.skrymer.udgaard.portfolio.model.CashTransactionType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import kotlin.math.abs

/**
 * Interactive Brokers adapter implementation
 */
@Component
class IBKRAdapter(
  private val flexQueryClient: IBKRFlexQueryClient,
  private val tradeMapper: IBKRTradeMapper,
) : BrokerAdapter {
  override fun fetchTrades(
    credentials: BrokerCredentials,
    accountId: String,
    startDate: LocalDate?,
    endDate: LocalDate?,
  ): BrokerDataResult {
    require(credentials is BrokerCredentials.IBKRCredentials) {
      "Invalid credentials type for IBKR adapter"
    }

    if (startDate != null && endDate != null) {
      logger.info("Fetching IBKR trades and account info: accountId=$accountId, dateRange=$startDate to $endDate")
    } else {
      logger.info("Fetching IBKR trades and account info: accountId=$accountId (using template defaults)")
    }

    try {
      // 1. Send request to IBKR (single API call)
      val referenceCode =
        flexQueryClient.sendRequest(
          token = credentials.token,
          queryId = credentials.queryId,
          startDate = startDate,
          endDate = endDate,
        )

      // 2. Wait for IBKR to generate the statement, then retrieve it
      Thread.sleep(STATEMENT_GENERATION_DELAY_MS)
      val xml = flexQueryClient.getStatement(credentials.token, referenceCode)

      // 3. Parse XML
      val response = flexQueryClient.parseXml(xml)
      val flexStatement = response.flexStatements.flexStatement

      // 4. Extract account info from response
      // Use functionalCurrency from FxPositions as the account's base currency (e.g. "AUD")
      // Falls back to AccountInformation.currency, then "USD"
      val baseCurrency = flexStatement.fxPositions
        ?.fxPosition
        ?.firstOrNull()
        ?.functionalCurrency
        ?: flexStatement.accountInformation?.currency
        ?: "USD"
      val accountInfo =
        BrokerAccountInfo(
          accountId = flexStatement.accountId ?: accountId,
          currency = baseCurrency,
          accountType = flexStatement.accountInformation?.accountType ?: "Individual",
          balance = null, // IBKR doesn't provide balance in trade flex query
        )

      // 5. Convert Trade elements to standardized format
      // Filter out CASH transactions and non-execution rows (SYMBOL_SUMMARY, ORDER_SUMMARY)
      val ibkrTrades = flexStatement.trades?.trade ?: emptyList()
      val filteredTrades = ibkrTrades.filter {
        it.assetCategory != "CASH" && (it.levelOfDetail == null || it.levelOfDetail == "EXECUTION")
      }
      val standardizedTrades = filteredTrades.map { tradeMapper.toStandardizedTrade(it) }

      // 6. Extract cash transactions (deposits/withdrawals only)
      val standardizedCashTxs = extractCashTransactions(flexStatement)

      logger.info(
        "Fetched ${standardizedTrades.size} trades from IBKR Activity Flex Query " +
          "(${ibkrTrades.size - filteredTrades.size} non-execution/CASH rows filtered out), " +
          "${standardizedCashTxs.size} cash transactions",
      )
      return BrokerDataResult(
        trades = standardizedTrades,
        accountInfo = accountInfo,
        cashTransactions = standardizedCashTxs,
      )
    } catch (e: IBKRApiException) {
      logger.error("Failed to fetch IBKR data", e)
      throw e
    } catch (e: Exception) {
      logger.error("Unexpected error fetching IBKR data", e)
      throw IBKRApiException("Unexpected error: ${e.message}", e)
    }
  }

  override fun testConnection(
    credentials: BrokerCredentials,
    accountId: String,
  ): Boolean {
    require(credentials is BrokerCredentials.IBKRCredentials) {
      "Invalid credentials type for IBKR adapter"
    }

    logger.info("Testing IBKR connection")

    return try {
      // Send request and retrieve the statement to avoid leaving orphaned pending statements
      // (IBKR rejects new SendRequest calls while a previous statement is still pending)
      val referenceCode = flexQueryClient.sendRequest(
        token = credentials.token,
        queryId = credentials.queryId,
      )
      Thread.sleep(STATEMENT_GENERATION_DELAY_MS)
      flexQueryClient.getStatement(credentials.token, referenceCode)
      logger.info("IBKR connection test successful")
      true
    } catch (e: Exception) {
      logger.warn("IBKR connection test failed", e)
      false
    }
  }

  override fun getAccountInfo(
    credentials: BrokerCredentials,
    accountId: String,
  ): BrokerAccountInfo {
    require(credentials is BrokerCredentials.IBKRCredentials) {
      "Invalid credentials type for IBKR adapter"
    }

    logger.info("Getting IBKR account info")

    try {
      // Fetch account info using template defaults (avoids date availability issues)
      val referenceCode =
        flexQueryClient.sendRequest(
          token = credentials.token,
          queryId = credentials.queryId,
        )

      Thread.sleep(STATEMENT_GENERATION_DELAY_MS)
      val xml = flexQueryClient.getStatement(credentials.token, referenceCode)
      val response = flexQueryClient.parseXml(xml)

      val flexStatement = response.flexStatements.flexStatement
      val accountInfo = flexStatement.accountInformation

      return BrokerAccountInfo(
        accountId = flexStatement.accountId ?: accountId,
        currency = accountInfo?.currency ?: "USD",
        accountType = accountInfo?.accountType ?: "Individual",
        balance = null, // IBKR doesn't provide balance in trade flex query
      )
    } catch (e: Exception) {
      logger.error("Failed to get IBKR account info", e)
      // Return default if account info not available
      return BrokerAccountInfo(
        accountId = accountId.ifEmpty { "UNKNOWN" },
        currency = "USD",
        accountType = "Individual",
        balance = null,
      )
    }
  }

  private fun extractCashTransactions(
    flexStatement: com.skrymer.udgaard.portfolio.integration.ibkr.dto.FlexStatement,
  ): List<StandardizedCashTransaction> =
    (flexStatement.cashTransactions?.cashTransaction ?: emptyList())
      .filter { it.type == "Deposits/Withdrawals" }
      .map { cashTx ->
        val rawAmount = cashTx.amount.toDouble()
        StandardizedCashTransaction(
          brokerTransactionId = cashTx.transactionID,
          type = if (rawAmount >= 0) CashTransactionType.DEPOSIT else CashTransactionType.WITHDRAWAL,
          amount = abs(rawAmount),
          currency = cashTx.currency ?: "USD",
          fxRateToBase = cashTx.fxRateToBase?.toDoubleOrNull(),
          transactionDate = LocalDate.parse(cashTx.reportDate),
          description = cashTx.description,
        )
      }

  override fun getBrokerType(): BrokerType = BrokerType.IBKR

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(IBKRAdapter::class.java)
    private const val STATEMENT_GENERATION_DELAY_MS = 1000L
  }
}
