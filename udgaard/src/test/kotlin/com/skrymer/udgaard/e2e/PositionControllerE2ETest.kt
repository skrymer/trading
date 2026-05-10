package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.portfolio.dto.ClosePositionRequest
import com.skrymer.udgaard.portfolio.dto.CreatePortfolioRequest
import com.skrymer.udgaard.portfolio.dto.CreatePositionRequest
import com.skrymer.udgaard.portfolio.model.InstrumentType
import com.skrymer.udgaard.portfolio.model.Portfolio
import com.skrymer.udgaard.portfolio.model.Position
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
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers PUT /api/positions/{portfolioId}/{positionId}/close over real HTTP + Postgres.
 * Pinned: the close-flow's atomicity (position transitions + portfolio balance update happen
 * as one transaction), JSON shape on the close response, and the 404 paths the
 * `PositionController` enforces (missing position, mismatched portfolio).
 *
 * This is the integration that exercises the new `PositionWithExecutions` aggregate end-to-end:
 * service loads aggregate → withClosed → save position + portfolio.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PositionControllerE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var dsl: DSLContext

  @BeforeEach
  fun cleanPortfolioTables() {
    dsl.deleteFrom(DSL.table("executions")).execute()
    dsl.deleteFrom(DSL.table("positions")).execute()
    dsl.deleteFrom(DSL.table("forex_disposals")).execute()
    dsl.deleteFrom(DSL.table("forex_lots")).execute()
    dsl.deleteFrom(DSL.table("cash_transactions")).execute()
    dsl.deleteFrom(DSL.table("portfolios")).execute()
  }

  @Test
  fun `PUT close transitions the position to CLOSED, populates realizedPnl, and bumps the portfolio balance atomically`() {
    // Given: portfolio at $10,000 + open AAPL 100-share position with avgEntry $50
    val portfolio = createPortfolio(initialBalance = 10_000.0)
    val position = createStockPosition(
      portfolioId = portfolio.id!!,
      symbol = "AAPL",
      quantity = 100,
      entryPrice = 50.0,
      entryDate = LocalDate.of(2026, 1, 5),
    )

    // When: close at $60 on Jan 20
    val closeResponse = restTemplate.exchange(
      RequestEntity
        .method(HttpMethod.PUT, URI("/api/positions/${portfolio.id}/${position.id}/close"))
        .contentType(MediaType.APPLICATION_JSON)
        .body(ClosePositionRequest(exitPrice = 60.0, exitDate = LocalDate.of(2026, 1, 20))),
      String::class.java,
    )

    // Then: HTTP 200 + the response captures CLOSED status + realised P&L
    assertEquals(HttpStatus.OK, closeResponse.statusCode)
    val body = closeResponse.body!!
    assertTrue(body.contains("\"status\":\"CLOSED\""), "Response advertises CLOSED status; body: $body")
    assertTrue(body.contains("\"closedDate\":\"2026-01-20\""))
    assertTrue(body.contains("\"realizedPnl\":1000.0"), "Realised P&L = (60 − 50) × 100 = 1000; body: $body")
    assertTrue(body.contains("\"currentQuantity\":0"))

    // And: portfolio balance reflects the close (initial $10k + $1k P&L; no commissions on manual close)
    val portfolioResponse = restTemplate.getForEntity("/api/portfolio/${portfolio.id}", Portfolio::class.java)
    assertEquals(11_000.0, portfolioResponse.body!!.currentBalance, "Atomic update — balance reflects realizedPnl")
  }

  @Test
  fun `PUT close returns 404 when position does not exist`() {
    // Given: portfolio with no positions
    val portfolio = createPortfolio(initialBalance = 10_000.0)

    // When
    val response = restTemplate.exchange(
      RequestEntity
        .method(HttpMethod.PUT, URI("/api/positions/${portfolio.id}/999999/close"))
        .contentType(MediaType.APPLICATION_JSON)
        .body(ClosePositionRequest(exitPrice = 60.0, exitDate = LocalDate.of(2026, 1, 20))),
      String::class.java,
    )

    // Then
    assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
  }

  @Test
  fun `PUT close returns 404 when the position belongs to a different portfolio`() {
    // Given: two portfolios, position in portfolio A
    val portfolioA = createPortfolio(initialBalance = 10_000.0)
    val portfolioB = createPortfolio(initialBalance = 5_000.0)
    val position = createStockPosition(
      portfolioId = portfolioA.id!!,
      symbol = "AAPL",
      quantity = 100,
      entryPrice = 50.0,
      entryDate = LocalDate.of(2026, 1, 5),
    )

    // When: try to close via portfolioB's URL
    val response = restTemplate.exchange(
      RequestEntity
        .method(HttpMethod.PUT, URI("/api/positions/${portfolioB.id}/${position.id}/close"))
        .contentType(MediaType.APPLICATION_JSON)
        .body(ClosePositionRequest(exitPrice = 60.0, exitDate = LocalDate.of(2026, 1, 20))),
      String::class.java,
    )

    // Then: cross-portfolio access is rejected. The position remains open (404 implies the close
    // never executed; per-position re-fetch via the GET endpoint returns a different DTO shape
    // and is exercised in its own integration tests).
    assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
  }

  @Test
  fun `PUT close on an already-closed position returns 400`() {
    // Given: a closed position
    val portfolio = createPortfolio(initialBalance = 10_000.0)
    val position = createStockPosition(
      portfolioId = portfolio.id!!,
      symbol = "AAPL",
      quantity = 100,
      entryPrice = 50.0,
      entryDate = LocalDate.of(2026, 1, 5),
    )
    val first = restTemplate.exchange(
      RequestEntity
        .method(HttpMethod.PUT, URI("/api/positions/${portfolio.id}/${position.id}/close"))
        .contentType(MediaType.APPLICATION_JSON)
        .body(ClosePositionRequest(exitPrice = 60.0, exitDate = LocalDate.of(2026, 1, 20))),
      Position::class.java,
    )
    assertEquals(HttpStatus.OK, first.statusCode)

    // When: try to close again
    val second = restTemplate.exchange(
      RequestEntity
        .method(HttpMethod.PUT, URI("/api/positions/${portfolio.id}/${position.id}/close"))
        .contentType(MediaType.APPLICATION_JSON)
        .body(ClosePositionRequest(exitPrice = 65.0, exitDate = LocalDate.of(2026, 1, 21))),
      String::class.java,
    )

    // Then
    assertEquals(HttpStatus.BAD_REQUEST, second.statusCode)
  }

  private fun createPortfolio(initialBalance: Double): Portfolio {
    val response = restTemplate.postForEntity(
      "/api/portfolio",
      jsonEntity(
        CreatePortfolioRequest(
          name = "Test ${System.nanoTime()}",
          initialBalance = initialBalance,
          currency = "USD",
          userId = null,
        ),
      ),
      Portfolio::class.java,
    )
    assertEquals(HttpStatus.CREATED, response.statusCode)
    val body = response.body!!
    assertNotNull(body.id)
    assertNull(body.lastSyncDate)
    assertTrue(body.currentBalance == initialBalance)
    return body
  }

  private fun createStockPosition(
    portfolioId: Long,
    symbol: String,
    quantity: Int,
    entryPrice: Double,
    entryDate: LocalDate,
  ): Position {
    val response = restTemplate.postForEntity(
      "/api/positions/$portfolioId",
      jsonEntity(
        CreatePositionRequest(
          symbol = symbol,
          instrumentType = InstrumentType.STOCK,
          quantity = quantity,
          entryPrice = entryPrice,
          entryDate = entryDate,
          entryStrategy = "Test",
          exitStrategy = "Test",
          multiplier = 1,
        ),
      ),
      Position::class.java,
    )
    assertEquals(HttpStatus.CREATED, response.statusCode)
    return response.body!!
  }
}
