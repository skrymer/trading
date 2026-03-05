package com.skrymer.udgaard.e2e

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.skrymer.udgaard.data.integration.midgaard.MidgaardClient
import com.skrymer.udgaard.portfolio.integration.broker.BrokerType
import com.skrymer.udgaard.portfolio.integration.ibkr.IBKRFlexQueryClient
import com.skrymer.udgaard.portfolio.integration.ibkr.dto.FlexQueryResponse
import com.skrymer.udgaard.portfolio.model.InstrumentType
import com.skrymer.udgaard.portfolio.model.OptionType
import com.skrymer.udgaard.portfolio.model.Position
import com.skrymer.udgaard.portfolio.model.PositionStats
import com.skrymer.udgaard.portfolio.model.PositionStatus
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
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IBKRBrokerImportE2ETest : AbstractIntegrationTest() {
  @MockitoBean
  private lateinit var flexQueryClient: IBKRFlexQueryClient

  @MockitoBean
  private lateinit var midgaardClient: MidgaardClient

  @Autowired
  private lateinit var dsl: DSLContext

  @BeforeEach
  fun setup() {
    // Clean portfolio tables (reverse FK order) to avoid broker_trade_id conflicts between tests
    dsl.deleteFrom(DSL.table("executions")).execute()
    dsl.deleteFrom(DSL.table("positions")).execute()
    dsl.deleteFrom(DSL.table("portfolios")).execute()

    val xml = ResourceUtils.getFile("classpath:ibkr-test-trades.xml").readText()
    val parsedResponse = XmlMapper().readValue(xml, FlexQueryResponse::class.java)

    whenever(flexQueryClient.sendRequest(any(), any(), anyOrNull(), anyOrNull()))
      .thenReturn("TEST_REF_123")
    whenever(flexQueryClient.getStatement(any(), any()))
      .thenReturn(xml)
    whenever(flexQueryClient.parseXml(any()))
      .thenReturn(parsedResponse)
  }

  @Test
  fun `import from IBKR should create portfolio with correct broker info`() {
    val importResult = importPortfolio()

    assertNotNull(importResult.portfolio.id)
    assertEquals("Test IBKR Portfolio", importResult.portfolio.name)
    assertEquals(BrokerType.IBKR, importResult.portfolio.broker)
    assertEquals("U12345678", importResult.portfolio.brokerAccountId)
    assertEquals("USD", importResult.portfolio.currency)
    assertEquals(25000.0, importResult.portfolio.initialBalance)
    assertTrue(importResult.tradesImported > 0, "Should have imported positions")
  }

  @Test
  fun `import should filter out CASH and SYMBOL_SUMMARY trades`() {
    val importResult = importPortfolio()
    val positions = getPositions(importResult.portfolio.id!!)

    val symbols = positions.map { it.symbol }.toSet()
    assertTrue("AUD.USD" !in symbols, "CASH trade should be filtered out")

    // Only TQQQ, NEM roll chain, and EGO should produce positions
    assertEquals(3, positions.size, "Expected 3 positions (TQQQ, NEM roll chain, EGO)")
  }

  @Test
  fun `import should correctly create stock position with partial closes`() {
    val importResult = importPortfolio()
    val positions = getPositions(importResult.portfolio.id!!)

    val tqqq = positions.first { it.symbol == "TQQQ" }

    assertEquals(InstrumentType.STOCK, tqqq.instrumentType)
    assertEquals(PositionStatus.CLOSED, tqqq.status)
    assertEquals(0, tqqq.currentQuantity, "All shares should be sold")
    assertEquals(53.73, tqqq.averageEntryPrice, 0.01)
    assertNotNull(tqqq.closedDate, "Closed position should have close date")
  }

  @Test
  fun `import should correctly create simple option position`() {
    val importResult = importPortfolio()
    val positions = getPositions(importResult.portfolio.id!!)

    val ego = positions.first { it.underlyingSymbol == "EGO" || it.symbol.contains("EGO") }

    assertEquals(InstrumentType.OPTION, ego.instrumentType)
    assertEquals(OptionType.CALL, ego.optionType)
    assertEquals(35.0, ego.strikePrice)
    assertEquals(PositionStatus.CLOSED, ego.status)
    assertEquals(0, ego.currentQuantity)
  }

  @Test
  fun `import should detect NEM option roll chain`() {
    val importResult = importPortfolio()

    assertTrue(
      importResult.rollsDetected > 0,
      "Should detect at least 1 roll in NEM chain (105 -> 110 -> 115)",
    )
  }

  @Test
  fun `NEM roll chain should produce single closed position with correct final strike`() {
    val importResult = importPortfolio()
    val positions = getPositions(importResult.portfolio.id!!)

    val nemPositions = positions.filter {
      it.underlyingSymbol == "NEM" || it.symbol.contains("NEM")
    }

    assertEquals(1, nemPositions.size, "NEM roll chain should be a single position")

    val nem = nemPositions.first()
    assertEquals(InstrumentType.OPTION, nem.instrumentType)
    assertEquals(OptionType.CALL, nem.optionType)
    assertEquals(PositionStatus.CLOSED, nem.status)
    assertEquals(0, nem.currentQuantity)

    // Final strike should be 115 (last leg of the chain)
    assertEquals(115.0, nem.strikePrice, "Strike should be updated to final roll target")
  }

  @Test
  fun `import should create correct number of executions for roll chain`() {
    val importResult = importPortfolio()
    val positions = getPositions(importResult.portfolio.id!!)
    val nem = positions.first { it.underlyingSymbol == "NEM" || it.symbol.contains("NEM") }

    val response = restTemplate.exchange(
      "/api/positions/${importResult.portfolio.id}/${nem.id}",
      HttpMethod.GET,
      null,
      Map::class.java,
    )

    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!

    @Suppress("UNCHECKED_CAST")
    val executions = body["executions"] as List<Map<String, Any>>

    // NEM chain: 3 legs with aggregated executions
    // Leg 1: buy 2 (1 exec) + sell 2 aggregated (1 exec) = 2
    // Leg 2: buy 2 aggregated (1 exec) + sell 2 (1 exec) = 2
    // Leg 3: buy 2 (1 exec) + sell 2 aggregated (1 exec) = 2
    // Total: 6 executions
    assertEquals(6, executions.size, "NEM roll chain should have 6 executions (3 buys + 3 sells)")
  }

  @Test
  fun `import should create correct number of executions for stock position`() {
    val importResult = importPortfolio()
    val positions = getPositions(importResult.portfolio.id!!)
    val tqqq = positions.first { it.symbol == "TQQQ" }

    val response = restTemplate.exchange(
      "/api/positions/${importResult.portfolio.id}/${tqqq.id}",
      HttpMethod.GET,
      null,
      Map::class.java,
    )

    assertEquals(HttpStatus.OK, response.statusCode)

    @Suppress("UNCHECKED_CAST")
    val executions = response.body!!["executions"] as List<Map<String, Any>>

    // TQQQ: 1 buy (10 shares) + 2 sells (5+5 shares) = 3 executions
    assertEquals(3, executions.size, "TQQQ should have 3 executions (1 buy + 2 sells)")
  }

  @Test
  fun `portfolio balance should be set from request`() {
    val importResult = importPortfolio(initialBalance = 50000.0)
    assertEquals(50000.0, importResult.portfolio.initialBalance)
  }

  @Test
  fun `rolled position avgEntryPrice should reflect current leg not blended average`() {
    val importResult = importPortfolio()
    val positions = getPositions(importResult.portfolio.id!!)
    val nem = positions.first { it.underlyingSymbol == "NEM" || it.symbol.contains("NEM") }

    // NEM roll chain: $105 (buy@9.55) -> $110 (buy@12.25) -> $115 (buy@11.19)
    // Current (last) leg entry price is 11.19
    // A blended average across all 3 legs would be (2*9.55 + 2*12.25 + 2*11.19) / 6 ≈ 10.997
    assertEquals(
      11.19,
      nem.averageEntryPrice,
      0.01,
      "avgEntryPrice should reflect current leg (11.19), not blended across all roll legs (~11.00)",
    )
  }

  @Test
  fun `create and sync should use consistent endDate offset`() {
    // Capture the endDate passed to sendRequest during import (create flow)
    val capturedEndDates = mutableListOf<LocalDate?>()
    whenever(flexQueryClient.sendRequest(any(), any(), anyOrNull(), anyOrNull()))
      .thenAnswer { invocation ->
        capturedEndDates.add(invocation.getArgument<LocalDate?>(3))
        "TEST_REF_123"
      }

    importPortfolio()
    val createEndDate = capturedEndDates.last()

    // BrokerIntegrationService.createPortfolioFromBroker uses now().minusDays(2)
    // BrokerIntegrationService.syncPortfolio uses now().minusDays(1)
    // These should be consistent to avoid missing a day of trades
    val expectedEndDate = LocalDate.now().minusDays(1)
    assertEquals(
      expectedEndDate,
      createEndDate,
      "Create endDate ($createEndDate) should use same offset as sync (now()-1 = $expectedEndDate)",
    )
  }

  @Test
  fun `option position totalCost should include multiplier`() {
    val importResult = importPortfolio()
    val positions = getPositions(importResult.portfolio.id!!)

    val ego = positions.first { it.underlyingSymbol == "EGO" || it.symbol.contains("EGO") }

    // EGO: BUY 3 @ $7.18, multiplier=100
    // totalCost should be 3 * 7.18 * 100 = 2154.00 (not 21.54 without multiplier)
    assertEquals(2154.0, ego.totalCost, 0.01, "Option totalCost should include multiplier")
  }

  @Test
  fun `stock position totalCost should not be affected by multiplier fix`() {
    val importResult = importPortfolio()
    val positions = getPositions(importResult.portfolio.id!!)

    val tqqq = positions.first { it.symbol == "TQQQ" }

    // TQQQ: BUY 10 @ $53.73, multiplier=1
    // totalCost = 10 * 53.73 = 537.30
    assertEquals(537.30, tqqq.totalCost, 0.01, "Stock totalCost should be quantity * price")
  }

  @Test
  fun `portfolio stats should have correct avgWin and provenEdge`() {
    val importResult = importPortfolio()
    val stats = getStats(importResult.portfolio.id!!)

    // All 3 positions are wins:
    // TQQQ: 20.50/537.30 = 3.81%, NEM: 2802/6598 = 42.47%, EGO: 75/2154 = 3.48%
    // avgWin ≈ 16.59%
    assertTrue(stats.avgWin in 10.0..25.0, "avgWin should be ~16.6%, got ${stats.avgWin}")
    assertTrue(
      stats.provenEdge < 100.0,
      "provenEdge should be reasonable (not inflated by missing multiplier), got ${stats.provenEdge}",
    )
    assertTrue(stats.provenEdge > 0.0, "provenEdge should be positive (all wins)")
    assertEquals(100.0, stats.winRate, 0.01, "All 3 positions are wins")
  }

  // -- Helper methods --

  private fun importPortfolio(
    name: String = "Test IBKR Portfolio",
    initialBalance: Double = 25000.0,
  ): CreateFromBrokerResult {
    val request = mapOf(
      "name" to name,
      "broker" to "IBKR",
      "credentials" to mapOf(
        "token" to "test-token",
        "queryId" to "test-query-id",
      ),
      "startDate" to "2026-01-01",
      "currency" to "USD",
      "initialBalance" to initialBalance,
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

  private fun getPositions(portfolioId: Long): List<Position> {
    val response = restTemplate.exchange(
      "/api/positions/$portfolioId",
      HttpMethod.GET,
      null,
      object : ParameterizedTypeReference<List<Position>>() {},
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
