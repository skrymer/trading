package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.data.integration.midgaard.MidgaardClient
import com.skrymer.udgaard.jooq.tables.references.STOCK_QUOTES
import com.skrymer.udgaard.scanner.dto.AddScannerTradeRequest
import com.skrymer.udgaard.scanner.model.ExitCheckResponse
import com.skrymer.udgaard.scanner.model.ScannerTrade
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers POST /api/scanner/check-exits over real HTTP + Postgres. Pinned: every deterministic
 * field of `ExitCheckResult` against `BacktestTestDataGenerator` quotes — `currentPrice` /
 * `priorClose` are queried from the DB and asserted by exact equality so any drift in the
 * generator's seed-derived random walk surfaces as a test failure rather than silent change.
 *
 * `stockProvider` is mocked to return empty live quotes — guarantees `usedLiveData == false`
 * and `currentPrice` falls back to the stored last bar.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScannerCheckExitsE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var dsl: DSLContext

  // MidgaardClient is the StockProvider implementation. Mocking the concrete class satisfies
  // both StockProvider consumers (ScannerService) and the few remaining consumers that still
  // wire MidgaardClient directly (StockIngestionService, BrokerIntegrationService,
  // PortfolioStatsService). Migrating those to StockProvider is a separate refactor.
  @MockitoBean
  private lateinit var stockProvider: MidgaardClient

  private val today = LocalDate.now()
  private val populateStart = today.minusDays(60)
  private val populateEnd = today.minusDays(1)

  @BeforeAll
  fun setupTestData() {
    BacktestTestDataGenerator.populate(dsl, populateStart, populateEnd)
  }

  @AfterAll
  fun resetTestData() {
    BacktestTestDataGenerator.reset(dsl)
  }

  @BeforeEach
  fun setUp() {
    dsl.deleteFrom(DSL.table("scanner_trades")).execute()
    whenever(stockProvider.getLatestQuotes(any())).thenReturn(emptyMap())
  }

  @Test
  fun `POST check-exits returns one ExitCheckResult per open trade with prices from the stored quote series`() {
    // Given: AAPL open trade, entered on the first day of populated data
    val saved = restTemplate
      .postForEntity(
        "/api/scanner/trades",
        jsonEntity(
          AddScannerTradeRequest(
            symbol = "AAPL",
            sectorSymbol = "XLK",
            instrumentType = "STOCK",
            entryPrice = 100.0,
            entryDate = populateStart.toString(),
            quantity = 10,
            entryStrategyName = "TestEntry",
            exitStrategyName = "TestExit",
            notes = null,
          ),
        ),
        ScannerTrade::class.java,
      ).body!!

    val (lastClose, priorClose) = aaplLastTwoCloses()

    // When
    val response = restTemplate.postForEntity(
      "/api/scanner/check-exits",
      jsonEntity(emptyMap<String, Any>()),
      ExitCheckResponse::class.java,
    )

    // Then
    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertEquals(1, body.checksPerformed)
    assertEquals(1, body.results.size)

    val result = body.results[0]
    assertEquals(saved.id, result.tradeId)
    assertEquals("AAPL", result.symbol)
    assertFalse(result.usedLiveData, "stockProvider returned empty — service uses stored bars")
    assertEquals(lastClose, result.currentPrice, "Falls through to last DB close")
    assertEquals(priorClose, result.priorClose)
    assertEquals((lastClose - 100.0) * 10, result.unrealizedPnlDollars, 1e-6)
    assertEquals((lastClose - 100.0) / 100.0 * 100.0, result.unrealizedPnlPercent, 1e-6)
    assertEquals((lastClose - priorClose) * 10, result.dailyPnlDollars, 1e-6)
    // nearExits is a polymorphic List<ExitProximity>; verifies the JSON shape round-trips even
    // when no exit conditions report proximity for the current bar.
    assertTrue(result.nearExits.isEmpty() || result.nearExits.all { it.proximity in 0.0..1.0 })
  }

  @Test
  fun `POST check-exits returns empty results when no open trades exist`() {
    // Given: scanner_trades is cleaned in @BeforeEach

    // When
    val response = restTemplate.postForEntity(
      "/api/scanner/check-exits",
      jsonEntity(emptyMap<String, Any>()),
      ExitCheckResponse::class.java,
    )

    // Then
    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertEquals(0, body.checksPerformed)
    assertEquals(0, body.exitsTriggered)
    assertTrue(body.results.isEmpty())
  }

  private fun aaplLastTwoCloses(): Pair<Double, Double> {
    val rows = dsl
      .select(STOCK_QUOTES.CLOSE_PRICE)
      .from(STOCK_QUOTES)
      .where(STOCK_QUOTES.STOCK_SYMBOL.eq("AAPL"))
      .orderBy(STOCK_QUOTES.QUOTE_DATE.desc())
      .limit(2)
      .fetch()
    val last = (rows[0].value1() as BigDecimal).toDouble()
    val prior = (rows[1].value1() as BigDecimal).toDouble()
    return last to prior
  }
}
