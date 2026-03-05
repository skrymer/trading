package com.skrymer.udgaard.portfolio.integration.ibkr

import com.skrymer.udgaard.portfolio.integration.broker.BrokerAccountInfo
import com.skrymer.udgaard.portfolio.integration.broker.BrokerAdapter
import com.skrymer.udgaard.portfolio.integration.broker.BrokerCredentials
import com.skrymer.udgaard.portfolio.integration.broker.BrokerDataResult
import com.skrymer.udgaard.portfolio.integration.broker.BrokerType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate

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

      // 2. Get XML response
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

      logger.info(
        "Fetched ${standardizedTrades.size} trades from IBKR Activity Flex Query " +
          "(${ibkrTrades.size - filteredTrades.size} non-execution/CASH rows filtered out)",
      )
      return BrokerDataResult(trades = standardizedTrades, accountInfo = accountInfo)
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
      // Test by sending a request without date overrides (uses template defaults)
      // This avoids issues with requesting data that might not be available yet
      flexQueryClient.sendRequest(
        token = credentials.token,
        queryId = credentials.queryId,
      )
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

  override fun getBrokerType(): BrokerType = BrokerType.IBKR

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(IBKRAdapter::class.java)
  }
}
