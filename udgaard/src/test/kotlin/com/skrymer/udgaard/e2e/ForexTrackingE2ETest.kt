package com.skrymer.udgaard.e2e

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.skrymer.udgaard.data.integration.midgaard.MidgaardClient
import com.skrymer.udgaard.portfolio.controller.ForexSummary
import com.skrymer.udgaard.portfolio.integration.ibkr.IBKRFlexQueryClient
import com.skrymer.udgaard.portfolio.integration.ibkr.dto.FlexQueryResponse
import com.skrymer.udgaard.portfolio.model.ForexDisposal
import com.skrymer.udgaard.portfolio.model.ForexLot
import com.skrymer.udgaard.portfolio.model.ForexLotStatus
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * E2E tests for forex/FX tracking functionality.
 *
 * Uses a test XML fixture with AUD base currency account and fxRateToBase
 * on each trade. Verifies:
 * - Portfolio gets baseCurrency=AUD
 * - Executions store fxRateToBase
 * - Closed positions have realizedPnlBase calculated
 * - Forex lots are created for USD acquisitions (sell executions)
 * - Forex disposals are created for USD disposals (buy executions)
 * - FIFO lot consumption is correct
 * - Forex API endpoints return correct data
 */
@Suppress("KotlinRedundantDiagnosticSuppress")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ForexTrackingE2ETest : AbstractIntegrationTest() {
  @MockitoBean
  private lateinit var flexQueryClient: IBKRFlexQueryClient

  @MockitoBean
  private lateinit var midgaardClient: MidgaardClient

  @Autowired
  private lateinit var dsl: DSLContext

  @BeforeEach
  fun setup() {
    // Clean all tables (reverse FK order)
    dsl.deleteFrom(DSL.table("forex_disposals")).execute()
    dsl.deleteFrom(DSL.table("forex_lots")).execute()
    dsl.deleteFrom(DSL.table("executions")).execute()
    dsl.deleteFrom(DSL.table("positions")).execute()
    dsl.deleteFrom(DSL.table("cash_transactions")).execute()
    dsl.deleteFrom(DSL.table("portfolios")).execute()

    val xml = ResourceUtils.getFile("classpath:ibkr-test-trades-fx.xml").readText()
    val parsedResponse = XmlMapper().readValue(xml, FlexQueryResponse::class.java)

    whenever(flexQueryClient.sendRequest(any(), any(), anyOrNull(), anyOrNull()))
      .thenReturn("TEST_REF_FX")
    whenever(flexQueryClient.getStatement(any(), any()))
      .thenReturn(xml)
    whenever(flexQueryClient.parseXml(any()))
      .thenReturn(parsedResponse)

    // Mock FX rates from Midgaard: historical rate at portfolio start and current rate
    // initialFxRate (historical USD->AUD at start date 2026-01-01): 1.60
    // currentFxRate (live USD->AUD): 1.45
    whenever(midgaardClient.getHistoricalExchangeRate(any(), any(), any()))
      .thenReturn(1.60)
    whenever(midgaardClient.getExchangeRate(any(), any()))
      .thenReturn(1.45)
  }

  @Test
  fun `import should set baseCurrency and initialFxRate from account info`() {
    val result = importPortfolio()

    assertEquals("AUD", result.portfolio.baseCurrency, "Portfolio should have AUD base currency")
    assertEquals("USD", result.portfolio.currency, "Trade currency should remain USD")
    assertNotNull(result.portfolio.initialFxRate, "Should have initial FX rate")
    assertEquals(1.60, result.portfolio.initialFxRate, 0.001, "Initial FX rate should be 1.60")
  }

  @Test
  fun `executions should store fxRateToBase`() {
    val result = importPortfolio()
    val positions = getPositions(result.portfolio.id!!)
    val tqqq = positions.first { it.symbol == "TQQQ" }

    val positionDetail = getPositionDetail(result.portfolio.id, tqqq.id!!)

    @Suppress("UNCHECKED_CAST")
    val executions = positionDetail["executions"] as List<Map<String, Any>>

    // All 3 TQQQ executions should have fxRateToBase
    assertTrue(
      executions.all { it["fxRateToBase"] != null },
      "All executions should have fxRateToBase",
    )

    // Check specific rates
    val rates = executions.map { (it["fxRateToBase"] as Number).toDouble() }.sorted()
    assertEquals(1.45, rates[0], 0.001, "Lowest FX rate should be 1.45")
    assertEquals(1.50, rates[1], 0.001, "Middle FX rate should be 1.50")
    assertEquals(1.60, rates[2], 0.001, "Highest FX rate should be 1.60")
  }

  @Test
  fun `closed stock position should have realizedPnlBase`() {
    val result = importPortfolio()
    val positions = getPositions(result.portfolio.id!!)
    val tqqq = positions.first { it.symbol == "TQQQ" }

    assertEquals(PositionStatus.CLOSED, tqqq.status)

    // USD P&L: (60*55 + 40*52) - 100*50 = 5380 - 5000 = 380
    assertEquals(380.0, tqqq.realizedPnl!!, 0.01, "USD realized P&L should be 380")

    // Base P&L: sells (60*55*1.50 + 40*52*1.45) - buy (100*50*1.60)
    //         = (4950 + 3016) - 8000 = -34 AUD
    assertNotNull(tqqq.realizedPnlBase, "Should have base currency P&L")
    assertEquals(-34.0, tqqq.realizedPnlBase, 0.01, "AUD realized P&L should be -34")
  }

  @Test
  fun `closed option position should have realizedPnlBase`() {
    val result = importPortfolio()
    val positions = getPositions(result.portfolio.id!!)
    val ego = positions.first { it.underlyingSymbol == "EGO" || it.symbol.contains("EGO") }

    assertEquals(PositionStatus.CLOSED, ego.status)

    // USD P&L: (8.50 - 7.00) * 100 * 2 = 300
    // But closePosition sums: totalSold = 2*8.50 = 17.0, totalBought = 2*7.00 = 14.0
    // realizedPnl = (17.0 - 14.0) * 100 = 300.0
    assertEquals(300.0, ego.realizedPnl!!, 0.01, "USD realized P&L should be 300")

    // Base P&L: sells (2*8.50*1.48) - buy (2*7.00*1.55)
    //         = (25.16 - 21.70) * 100 = 346.0
    assertNotNull(ego.realizedPnlBase, "Should have base currency P&L")
    assertEquals(346.0, ego.realizedPnlBase, 0.01, "AUD realized P&L should be 346")
  }

  @Test
  fun `forex lots should be created for sell executions (USD acquisitions)`() {
    val result = importPortfolio()
    val lots = getForexLots(result.portfolio.id!!)

    // Sell executions create acquisition lots:
    // TQQQ SELL 60 → lot of 3299.40 USD @ 1.50
    // TQQQ SELL 40 → lot of 2079.60 USD @ 1.45
    // EGO SELL 2 → lot of 1698.59 USD @ 1.48
    val acquisitionLots = lots.filter { it.costRate > 0 }
    assertEquals(3, acquisitionLots.size, "Should have 3 forex acquisition lots from 3 sell trades")

    // Verify lot rates
    val ratesSorted = acquisitionLots.map { it.costRate }.sorted()
    assertEquals(1.45, ratesSorted[0], 0.001)
    assertEquals(1.48, ratesSorted[1], 0.001)
    assertEquals(1.50, ratesSorted[2], 0.001)
  }

  @Test
  fun `forex disposals should be created for buy executions (USD disposals)`() {
    val result = importPortfolio()
    val disposals = getForexDisposals(result.portfolio.id!!)

    // BUY executions consume USD lots via FIFO:
    // TQQQ BUY 100 → disposal of 5001.00 USD, but no lots exist yet → unmatched (no disposal records)
    // EGO BUY 2 → disposal of 1401.40 USD, consumes from first lot (TQQQ sell @ 1.50)
    // The TQQQ buy happens first (opening trades processed before closing in importLotGroup),
    // but since it's the FIRST trade, there are no lots to consume.
    // EGO buy at rate 1.55 consumes from lot @ 1.50 → FX P&L = 1401.40 * (1.55 - 1.50) = 70.07
    assertTrue(disposals.isNotEmpty(), "Should have at least one forex disposal")
  }

  @Test
  fun `FIFO lot consumption should partially consume first lot`() {
    val result = importPortfolio()
    val lots = getForexLots(result.portfolio.id!!)

    // The first lot (3299.40 @ 1.50) should be partially consumed by EGO buy (1401.40)
    val lot150 = lots.first { it.costRate == 1.50 }
    val expectedRemaining = 3299.40 - 1401.40
    assertEquals(expectedRemaining, lot150.remainingQuantity, 0.01, "First lot should be partially consumed")
    assertEquals(ForexLotStatus.OPEN, lot150.status, "Partially consumed lot should still be OPEN")
  }

  @Test
  fun `forex summary endpoint should return correct totals`() {
    val result = importPortfolio()
    val summary = getForexSummary(result.portfolio.id!!)

    assertNotNull(summary)
    assertTrue(summary.openLotsCount >= 2, "Should have at least 2 open lots")
    assertTrue(summary.totalRealizedFxPnl != 0.0, "Should have non-zero realized FX P&L")
  }

  @Test
  fun `forex lots endpoint should return all lots`() {
    val result = importPortfolio()
    val lots = getForexLots(result.portfolio.id!!)

    assertTrue(lots.isNotEmpty(), "Should return forex lots")
    lots.forEach { lot ->
      assertNotNull(lot.id)
      assertEquals(result.portfolio.id, lot.portfolioId)
      assertTrue(lot.quantity > 0, "Lot quantity should be positive")
      assertTrue(lot.costRate > 0, "Lot cost rate should be positive")
    }
  }

  @Test
  fun `forex disposals endpoint should return all disposals`() {
    val result = importPortfolio()
    val disposals = getForexDisposals(result.portfolio.id!!)

    disposals.forEach { disposal ->
      assertNotNull(disposal.id)
      assertEquals(result.portfolio.id, disposal.portfolioId)
      assertTrue(disposal.quantity > 0, "Disposal quantity should be positive")
      assertTrue(disposal.costRate > 0, "Disposal cost rate should be positive")
      assertTrue(disposal.disposalRate > 0, "Disposal rate should be positive")
    }
  }

  @Test
  fun `forex disposal should have correct FX P&L calculation`() {
    val result = importPortfolio()
    val disposals = getForexDisposals(result.portfolio.id!!)

    disposals.forEach { disposal ->
      // FX P&L = quantity * (disposalRate - costRate)
      val expectedPnl = disposal.quantity * (disposal.disposalRate - disposal.costRate)
      assertEquals(
        expectedPnl,
        disposal.realizedFxPnl,
        0.01,
        "FX P&L should equal quantity * (disposalRate - costRate)",
      )

      // Also verify cost basis and proceeds
      assertEquals(
        disposal.quantity * disposal.costRate,
        disposal.costBasisAud,
        0.01,
        "Cost basis AUD should be quantity * costRate",
      )
      assertEquals(
        disposal.quantity * disposal.disposalRate,
        disposal.proceedsAud,
        0.01,
        "Proceeds AUD should be quantity * disposalRate",
      )
    }
  }

  @Test
  fun `stats totalRealizedFxPnl should reflect FX impact on current balance`() {
    val result = importPortfolio()
    val stats = getStats(result.portfolio.id!!)

    // FX impact on current balance using weighted avg acquisition rate:
    // initialBalance = 50,000 AUD, initialFxRate = 1.60, currentFxRate = 1.45
    // No cash transactions → avgAcquisitionRate = initialFxRate = 1.60
    // currentBalance = 50,000 + 380 (TQQQ) - 2.00 (TQQQ comm) + 300 (EGO) - 2.81 (EGO comm) = 50,675.19
    // FX P&L (in USD) = 50,675.19 * (1 - 1.60/1.45) = 50,675.19 * (-3/29) ≈ -5,242.26
    assertNotNull(stats.totalRealizedFxPnl, "Should have FX impact in stats")
    assertEquals(
      -5242.26,
      stats.totalRealizedFxPnl,
      1.0,
      "FX P&L should reflect impact of rate change on current balance",
    )
  }

  @Test
  fun `option totalCost should include multiplier for correct return percentages`() {
    val result = importPortfolio()
    val positions = getPositions(result.portfolio.id!!)

    val ego = positions.first { it.underlyingSymbol == "EGO" || it.symbol.contains("EGO") }

    // EGO: BUY 2 @ $7.00, multiplier=100
    // totalCost = 2 * 7.00 * 100 = 1400.00 (not 14.00 without multiplier)
    assertEquals(1400.0, ego.totalCost, 0.01, "Option totalCost should include multiplier")

    // With correct totalCost, return % should be reasonable
    val stats = getStats(result.portfolio.id)
    assertTrue(
      stats.avgWin < 50.0,
      "avgWin should be reasonable (not inflated by missing multiplier), got ${stats.avgWin}",
    )
  }

  // -- Helper methods --

  private fun importPortfolio(): CreateFromBrokerResult {
    val request = mapOf(
      "name" to "Test FX Portfolio",
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

  private fun getPositionDetail(portfolioId: Long, positionId: Long): Map<*, *> {
    val response = restTemplate.exchange(
      "/api/positions/$portfolioId/$positionId",
      HttpMethod.GET,
      null,
      Map::class.java,
    )
    assertEquals(HttpStatus.OK, response.statusCode)
    return response.body!!
  }

  private fun getForexLots(portfolioId: Long): List<ForexLot> {
    val response = restTemplate.exchange(
      "/api/portfolio/$portfolioId/forex/lots",
      HttpMethod.GET,
      null,
      object : ParameterizedTypeReference<List<ForexLot>>() {},
    )
    assertEquals(HttpStatus.OK, response.statusCode)
    return response.body!!
  }

  private fun getForexDisposals(portfolioId: Long): List<ForexDisposal> {
    val response = restTemplate.exchange(
      "/api/portfolio/$portfolioId/forex/disposals",
      HttpMethod.GET,
      null,
      object : ParameterizedTypeReference<List<ForexDisposal>>() {},
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

  private fun getForexSummary(portfolioId: Long): ForexSummary {
    val response = restTemplate.exchange(
      "/api/portfolio/$portfolioId/forex/summary",
      HttpMethod.GET,
      null,
      ForexSummary::class.java,
    )
    assertEquals(HttpStatus.OK, response.statusCode)
    return response.body!!
  }
}
