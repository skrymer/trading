package com.skrymer.udgaard.e2e

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.skrymer.udgaard.data.integration.midgaard.MidgaardClient
import com.skrymer.udgaard.portfolio.controller.CashTransactionSummary
import com.skrymer.udgaard.portfolio.integration.ibkr.IBKRFlexQueryClient
import com.skrymer.udgaard.portfolio.integration.ibkr.dto.FlexQueryResponse
import com.skrymer.udgaard.portfolio.model.CashTransaction
import com.skrymer.udgaard.portfolio.model.PositionStats
import com.skrymer.udgaard.portfolio.service.CreateFromBrokerResult
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.util.ResourceUtils
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Suppress("KotlinRedundantDiagnosticSuppress")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CashTransactionE2ETest : AbstractIntegrationTest() {
  @MockitoBean
  private lateinit var flexQueryClient: IBKRFlexQueryClient

  @MockitoBean
  private lateinit var midgaardClient: MidgaardClient

  @Autowired
  private lateinit var dsl: DSLContext

  @BeforeEach
  fun setup() {
    dsl.deleteFrom(DSL.table("cash_transactions")).execute()
    dsl.deleteFrom(DSL.table("forex_disposals")).execute()
    dsl.deleteFrom(DSL.table("forex_lots")).execute()
    dsl.deleteFrom(DSL.table("executions")).execute()
    dsl.deleteFrom(DSL.table("positions")).execute()
    dsl.deleteFrom(DSL.table("portfolios")).execute()

    val xml = ResourceUtils.getFile("classpath:ibkr-test-trades-cash-tx.xml").readText()
    val parsedResponse = XmlMapper().readValue(xml, FlexQueryResponse::class.java)

    whenever(flexQueryClient.sendRequest(any(), any(), anyOrNull(), anyOrNull()))
      .thenReturn("TEST_REF_CASH")
    whenever(flexQueryClient.getStatement(any(), any()))
      .thenReturn(xml)
    whenever(flexQueryClient.parseXml(any()))
      .thenReturn(parsedResponse)

    whenever(midgaardClient.getHistoricalExchangeRate(any(), any(), any()))
      .thenReturn(1.55)
    whenever(midgaardClient.getExchangeRate(any(), any()))
      .thenReturn(1.48)
  }

  @Test
  fun `import should create cash transactions from IBKR data`() {
    val result = importPortfolio()
    val transactions = getCashTransactions(result.portfolio.id!!)

    // Only Deposits/Withdrawals should be imported (not Dividends)
    assertEquals(2, transactions.size, "Should import 2 cash transactions (deposit + withdrawal)")

    val deposit = transactions.first { it.type.name == "DEPOSIT" }
    assertEquals(10000.0, deposit.amount, 0.01)
    assertEquals("USD", deposit.currency)
    assertEquals("Electronic Fund Deposit", deposit.description)

    val withdrawal = transactions.first { it.type.name == "WITHDRAWAL" }
    assertEquals(2000.0, withdrawal.amount, 0.01)
    assertEquals("USD", withdrawal.currency)
    assertEquals("Electronic Fund Withdrawal", withdrawal.description)
  }

  @Test
  fun `cash transactions should update portfolio balance`() {
    val result = importPortfolio()

    // TQQQ: buy 100 @ $50, sell 100 @ $55 = $500 realized P&L
    // Commissions stored as negative: -1.00 + -1.00 = -2.00
    // Net cash flow: $10,000 deposit - $2,000 withdrawal = $8,000
    // Balance = initialBalance + totalRealizedPnl + totalCommissions + netCashFlow
    //         = 50,000 + 500 + (-2) + 8,000 = 58,498
    val portfolio = getPortfolio(result.portfolio.id!!)
    assertEquals(58498.0, portfolio["currentBalance"] as Double, 1.0)
  }

  @Test
  fun `cash transactions endpoint should return all transactions`() {
    val result = importPortfolio()
    val transactions = getCashTransactions(result.portfolio.id!!)

    assertEquals(2, transactions.size)
    transactions.forEach { tx ->
      assertNotNull(tx.id)
      assertEquals(result.portfolio.id, tx.portfolioId)
      assertTrue(tx.amount > 0, "Amount should be positive")
    }
  }

  @Test
  fun `cash transactions summary should return correct totals`() {
    val result = importPortfolio()
    val summary = getCashTransactionSummary(result.portfolio.id!!)

    assertEquals(10000.0, summary.totalDeposits, 0.01)
    assertEquals(2000.0, summary.totalWithdrawals, 0.01)
    assertEquals(8000.0, summary.netCashFlow, 0.01)
  }

  @Test
  fun `stats should include totalDeposits and totalWithdrawals`() {
    val result = importPortfolio()
    val stats = getStats(result.portfolio.id!!)

    assertEquals(10000.0, stats.totalDeposits, 0.01)
    assertEquals(2000.0, stats.totalWithdrawals, 0.01)
  }

  @Test
  fun `sync should dedup cash transactions by brokerTransactionId`() {
    val result = importPortfolio()

    // Sync again (same data)
    syncPortfolio(result.portfolio.id!!)

    val transactions = getCashTransactions(result.portfolio.id)
    assertEquals(2, transactions.size, "Should not duplicate cash transactions on re-sync")
  }

  @Test
  fun `cash transactions should store fxRateToBase`() {
    val result = importPortfolio()
    val transactions = getCashTransactions(result.portfolio.id!!)

    val deposit = transactions.first { it.type.name == "DEPOSIT" }
    assertNotNull(deposit.fxRateToBase, "Deposit should have FX rate")
    assertEquals(1.55, deposit.fxRateToBase, 0.001)

    val withdrawal = transactions.first { it.type.name == "WITHDRAWAL" }
    assertNotNull(withdrawal.fxRateToBase, "Withdrawal should have FX rate")
    assertEquals(1.48, withdrawal.fxRateToBase, 0.001)
  }

  // -- Helper methods --

  private fun importPortfolio(): CreateFromBrokerResult {
    val request = mapOf(
      "name" to "Test Cash Tx Portfolio",
      "broker" to "IBKR",
      "credentials" to mapOf(
        "token" to "test-token",
        "queryId" to "test-query-id",
      ),
      "startDate" to "2026-01-01",
      "currency" to "USD",
      "initialBalance" to 50000.0,
    )

    val response = restTemplate.exchange(
      "/api/portfolio/import",
      HttpMethod.POST,
      HttpEntity(request, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
      CreateFromBrokerResult::class.java,
    )

    assertEquals(HttpStatus.CREATED, response.statusCode, "Import should succeed")
    return response.body!!
  }

  private fun syncPortfolio(portfolioId: Long) {
    val request = mapOf(
      "credentials" to mapOf(
        "token" to "test-token",
        "queryId" to "test-query-id",
      ),
    )

    restTemplate.exchange(
      "/api/portfolio/$portfolioId/sync",
      HttpMethod.POST,
      HttpEntity(request, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
      Map::class.java,
    )
  }

  private fun getPortfolio(portfolioId: Long): Map<*, *> {
    val response = restTemplate.exchange(
      "/api/portfolio/$portfolioId",
      HttpMethod.GET,
      null,
      Map::class.java,
    )
    assertEquals(HttpStatus.OK, response.statusCode)
    return response.body!!
  }

  private fun getCashTransactions(portfolioId: Long): List<CashTransaction> {
    val response = restTemplate.exchange(
      "/api/portfolio/$portfolioId/cash-transactions",
      HttpMethod.GET,
      null,
      object : ParameterizedTypeReference<List<CashTransaction>>() {},
    )
    assertEquals(HttpStatus.OK, response.statusCode)
    return response.body!!
  }

  private fun getCashTransactionSummary(portfolioId: Long): CashTransactionSummary {
    val response = restTemplate.exchange(
      "/api/portfolio/$portfolioId/cash-transactions/summary",
      HttpMethod.GET,
      null,
      CashTransactionSummary::class.java,
    )
    assertEquals(HttpStatus.OK, response.statusCode)
    return response.body!!
  }

  private fun getStats(portfolioId: Long): PositionStats {
    val response = restTemplate.exchange(
      "/api/positions/$portfolioId/stats",
      HttpMethod.GET,
      null,
      PositionStats::class.java,
    )
    assertEquals(HttpStatus.OK, response.statusCode)
    return response.body!!
  }
}
