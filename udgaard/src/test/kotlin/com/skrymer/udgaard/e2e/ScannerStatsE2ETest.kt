package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.scanner.dto.AddScannerTradeRequest
import com.skrymer.udgaard.scanner.dto.CloseScannerTradeRequest
import com.skrymer.udgaard.scanner.dto.ClosedTradeStatsResponse
import com.skrymer.udgaard.scanner.dto.DrawdownStatsResponse
import com.skrymer.udgaard.scanner.model.ScannerTrade
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the scanner analytics endpoints over real HTTP + Postgres:
 * - GET /api/scanner/drawdown-stats
 * - GET /api/scanner/trades/closed/stats
 *
 * Pinned: response JSON shape including null fields for the empty-database case
 * (`overall == null`) and the all-winners case (`profitFactor == null`).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScannerStatsE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var dsl: DSLContext

  @BeforeEach
  fun cleanScannerTrades() {
    dsl.deleteFrom(DSL.table("scanner_trades")).execute()
  }

  @Test
  fun `GET drawdown-stats returns zero metrics when no trades exist`() {
    // Given: no trades

    // When
    val response = restTemplate.exchange(
      "/api/scanner/drawdown-stats",
      HttpMethod.GET,
      null,
      DrawdownStatsResponse::class.java,
    )

    // Then
    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertEquals(0.0, body.totalRealizedPnl)
    assertEquals(0, body.closedTradeCount)
    assertEquals(0.0, body.totalUnrealizedPnl)
    assertEquals(0.0, body.winRate)
  }

  @Test
  fun `GET drawdown-stats reflects closed-trade realized PnL in currentEquity`() {
    // Given: one open trade + one closed winner, +$1000 realized
    val trade = createOpenTrade()
    closeTrade(trade.id!!)

    // When
    val response = restTemplate.exchange(
      "/api/scanner/drawdown-stats",
      HttpMethod.GET,
      null,
      DrawdownStatsResponse::class.java,
    )

    // Then: realized PnL contributes; currentEquity = portfolioValue + realized + unrealized
    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertEquals(1, body.closedTradeCount)
    assertEquals(1000.0, body.totalRealizedPnl)
    assertEquals(1.0, body.winRate, "single winner")
    assertTrue(body.currentEquity >= body.totalRealizedPnl, "currentEquity includes the realized PnL")
  }

  @Test
  fun `GET trades closed stats returns null overall when no closed trades exist`() {
    // Given: no closed trades

    // When
    val response = restTemplate.exchange(
      "/api/scanner/trades/closed/stats",
      HttpMethod.GET,
      null,
      ClosedTradeStatsResponse::class.java,
    )

    // Then: Jackson serialises `overall = null` cleanly; byStrategy is empty list
    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertNull(body.overall)
    assertTrue(body.byStrategy.isEmpty())
  }

  @Test
  fun `GET trades closed stats returns null profitFactor when there are no losses`() {
    // Given: one closed winner, zero losers
    val trade = createOpenTrade()
    closeTrade(trade.id!!)

    // When
    val response = restTemplate.exchange(
      "/api/scanner/trades/closed/stats",
      HttpMethod.GET,
      null,
      ClosedTradeStatsResponse::class.java,
    )

    // Then: profitFactor is null because the loss-side denominator is zero
    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertNotNull(body.overall)
    assertEquals(1, body.overall.trades)
    assertEquals(1, body.overall.wins)
    assertEquals(0, body.overall.losses)
    assertNull(body.overall.profitFactor, "no losses → profitFactor is null, not 0 or infinity")
  }

  private fun createOpenTrade(): ScannerTrade =
    restTemplate
      .postForEntity(
        "/api/scanner/trades",
        HttpEntity(
          AddScannerTradeRequest(
            symbol = "AAPL",
            sectorSymbol = "XLK",
            instrumentType = "STOCK",
            entryPrice = 100.0,
            entryDate = "2024-01-15",
            quantity = 10,
            entryStrategyName = "TestEntry",
            exitStrategyName = "TestExit",
            notes = null,
          ),
          org.springframework.http
            .HttpHeaders()
            .apply { contentType = MediaType.APPLICATION_JSON },
        ),
        ScannerTrade::class.java,
      ).body!!

  private fun closeTrade(id: Long) {
    restTemplate.exchange(
      RequestEntity
        .put(URI.create("/api/scanner/trades/$id/close"))
        .contentType(MediaType.APPLICATION_JSON)
        .body(CloseScannerTradeRequest(exitPrice = 200.0, exitDate = "2024-02-15")),
      ScannerTrade::class.java,
    )
  }
}
