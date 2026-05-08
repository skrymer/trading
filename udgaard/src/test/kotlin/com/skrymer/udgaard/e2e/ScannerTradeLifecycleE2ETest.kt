package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.scanner.dto.AddScannerTradeRequest
import com.skrymer.udgaard.scanner.dto.CloseScannerTradeRequest
import com.skrymer.udgaard.scanner.dto.RollScannerTradeRequest
import com.skrymer.udgaard.scanner.dto.UpdateScannerTradeRequest
import com.skrymer.udgaard.scanner.model.ScannerTrade
import com.skrymer.udgaard.scanner.model.TradeStatus
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Covers the scanner trade lifecycle (add / get / update / close / delete) over real HTTP +
 * Postgres. Pinned in particular: the 404 collapse for missing-or-closed-trade guards across
 * close / update / delete (post-grilling decision — single-user app, 404 with descriptive
 * message is enough; no separate 409 distinction). The pure orchestration sanity check
 * for `closeTrade` lives in `ScannerServiceTest`; the entity formula in `ScannerTradeTest`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScannerTradeLifecycleE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var dsl: DSLContext

  @BeforeEach
  fun cleanScannerTradesTable() {
    dsl.deleteFrom(DSL.table("scanner_trades")).execute()
  }

  @Test
  fun `POST trades returns 201 with id assigned and currentBalance-style invariants`() {
    // When
    val response = restTemplate.postForEntity(
      "/api/scanner/trades",
      jsonEntity(addRequest()),
      ScannerTrade::class.java,
    )

    // Then
    assertEquals(HttpStatus.CREATED, response.statusCode)
    val body = response.body!!
    assertNotNull(body.id)
    assertEquals("AAPL", body.symbol)
    assertEquals(150.0, body.entryPrice)
    assertEquals(TradeStatus.OPEN, body.status)
  }

  @Test
  fun `GET trades returns saved trades`() {
    // Given
    addTrade()

    // When
    val response = restTemplate.exchange(
      "/api/scanner/trades",
      HttpMethod.GET,
      null,
      Array<ScannerTrade>::class.java,
    )

    // Then
    assertEquals(HttpStatus.OK, response.statusCode)
    assertEquals(1, response.body!!.size)
    assertEquals("AAPL", response.body!![0].symbol)
  }

  @Test
  fun `PUT trades updates notes`() {
    // Given
    val saved = addTrade()

    // When
    val response = restTemplate.exchange(
      RequestEntity
        .put(URI.create("/api/scanner/trades/${saved.id}"))
        .contentType(MediaType.APPLICATION_JSON)
        .body(UpdateScannerTradeRequest(notes = "updated notes")),
      ScannerTrade::class.java,
    )

    // Then
    assertEquals(HttpStatus.OK, response.statusCode)
    assertEquals("updated notes", response.body!!.notes)
  }

  @Test
  fun `PUT trades on missing id returns 404`() {
    // When
    val response = restTemplate.exchange(
      RequestEntity
        .put(URI.create("/api/scanner/trades/99999"))
        .contentType(MediaType.APPLICATION_JSON)
        .body(UpdateScannerTradeRequest(notes = "x")),
      String::class.java,
    )

    // Then: GlobalExceptionHandler maps NoSuchElementException to 404
    assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
  }

  @Test
  fun `PUT trades on already-closed trade returns 404`() {
    // Given
    val saved = addTrade()
    closeTrade(saved.id!!)

    // When: try to update notes after close
    val response = restTemplate.exchange(
      RequestEntity
        .put(URI.create("/api/scanner/trades/${saved.id}"))
        .contentType(MediaType.APPLICATION_JSON)
        .body(UpdateScannerTradeRequest(notes = "should fail")),
      String::class.java,
    )

    // Then: 404 collapse — same as close-on-already-closed
    assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
  }

  @Test
  fun `PUT close persists CLOSED state with realizedPnl computed by the entity rule`() {
    // Given: STOCK trade entered at $150, 10 shares
    val saved = addTrade()

    // When: close at $160
    val response = restTemplate.exchange(
      RequestEntity
        .put(URI.create("/api/scanner/trades/${saved.id}/close"))
        .contentType(MediaType.APPLICATION_JSON)
        .body(CloseScannerTradeRequest(exitPrice = 160.0, exitDate = "2024-02-15")),
      ScannerTrade::class.java,
    )

    // Then: status is CLOSED, realizedPnl == (160 - 150) * 10 == 100.0 (per ScannerTrade.computeRealizedPnl)
    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertEquals(TradeStatus.CLOSED, body.status)
    assertEquals(160.0, body.exitPrice)
    assertEquals(100.0, body.realizedPnl)
    assertNotNull(body.closedAt)
  }

  @Test
  fun `PUT close on missing id returns 404`() {
    // When
    val response = restTemplate.exchange(
      RequestEntity
        .put(URI.create("/api/scanner/trades/99999/close"))
        .contentType(MediaType.APPLICATION_JSON)
        .body(CloseScannerTradeRequest(exitPrice = 1.0, exitDate = "2024-01-01")),
      String::class.java,
    )

    // Then
    assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
  }

  @Test
  fun `PUT close on already-closed trade returns 404`() {
    // Given: trade closed once
    val saved = addTrade()
    closeTrade(saved.id!!)

    // When: close again
    val response = restTemplate.exchange(
      RequestEntity
        .put(URI.create("/api/scanner/trades/${saved.id}/close"))
        .contentType(MediaType.APPLICATION_JSON)
        .body(CloseScannerTradeRequest(exitPrice = 170.0, exitDate = "2024-03-01")),
      String::class.java,
    )

    // Then: 404 collapse — error message differentiates "already closed", status code does not
    assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
  }

  @Test
  fun `DELETE trades returns 204 and removes the row`() {
    // Given
    val saved = addTrade()

    // When
    val deleteResponse = restTemplate.exchange(
      "/api/scanner/trades/${saved.id}",
      HttpMethod.DELETE,
      null,
      Void::class.java,
    )

    // Then
    assertEquals(HttpStatus.NO_CONTENT, deleteResponse.statusCode)
    assertEquals(0, dsl.fetchCount(DSL.table("scanner_trades")))
  }

  @Test
  fun `DELETE on missing id returns 404`() {
    // When
    val response = restTemplate.exchange(
      "/api/scanner/trades/99999",
      HttpMethod.DELETE,
      null,
      String::class.java,
    )

    // Then
    assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
  }

  @Test
  fun `POST roll on missing id returns 404`() {
    // When: roll a trade that doesn't exist
    val response = restTemplate.postForEntity(
      "/api/scanner/trades/99999/roll",
      jsonEntity(
        RollScannerTradeRequest(
          closePrice = 1.0,
          newStrikePrice = 100.0,
          newExpirationDate = "2024-03-15",
          newEntryPrice = 1.0,
          newEntryDate = "2024-02-15",
          newQuantity = 1,
        ),
      ),
      String::class.java,
    )

    // Then: GlobalExceptionHandler maps NoSuchElementException to 404
    assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
  }

  @Test
  fun `GET trades closed returns trades whose status is CLOSED`() {
    // Given
    val saved = addTrade()
    closeTrade(saved.id!!)

    // When
    val response = restTemplate.exchange(
      "/api/scanner/trades/closed",
      HttpMethod.GET,
      null,
      Array<ScannerTrade>::class.java,
    )

    // Then
    assertEquals(HttpStatus.OK, response.statusCode)
    assertEquals(1, response.body!!.size)
    assertEquals(TradeStatus.CLOSED, response.body!![0].status)
  }

  private fun addRequest() = AddScannerTradeRequest(
    symbol = "AAPL",
    sectorSymbol = "XLK",
    instrumentType = "STOCK",
    entryPrice = 150.0,
    entryDate = "2024-01-15",
    quantity = 10,
    entryStrategyName = "TestEntry",
    exitStrategyName = "TestExit",
    notes = null,
  )

  private fun addTrade(): ScannerTrade =
    restTemplate
      .postForEntity(
        "/api/scanner/trades",
        jsonEntity(addRequest()),
        ScannerTrade::class.java,
      ).body!!

  private fun closeTrade(id: Long) {
    restTemplate.exchange(
      RequestEntity
        .put(URI.create("/api/scanner/trades/$id/close"))
        .contentType(MediaType.APPLICATION_JSON)
        .body(CloseScannerTradeRequest(exitPrice = 160.0, exitDate = "2024-02-15")),
      ScannerTrade::class.java,
    )
  }
}
