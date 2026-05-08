package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.portfolio.dto.BrokerErrorResponse
import com.skrymer.udgaard.portfolio.dto.CreatePortfolioRequest
import com.skrymer.udgaard.portfolio.dto.UpdatePortfolioRequest
import com.skrymer.udgaard.portfolio.model.Portfolio
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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortfolioControllerE2ETest : AbstractIntegrationTest() {
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
  fun `POST returns 201 with currentBalance seeded from initialBalance and id assigned`() {
    // Given
    val request = CreatePortfolioRequest(
      name = "E2E test",
      initialBalance = 10_000.0,
      currency = "USD",
      userId = "alice",
    )

    // When
    val response = restTemplate.postForEntity(
      "/api/portfolio",
      jsonEntity(request),
      Portfolio::class.java,
    )

    // Then
    assertEquals(HttpStatus.CREATED, response.statusCode)
    val body = response.body!!
    assertNotNull(body.id)
    assertEquals("E2E test", body.name)
    assertEquals(10_000.0, body.initialBalance)
    assertEquals(10_000.0, body.currentBalance)
    assertEquals("USD", body.currency)
    assertEquals("alice", body.userId)
    assertNull(body.lastSyncDate)
  }

  @Test
  fun `POST seeds baseCurrency from currency for non-USD portfolios`() {
    // Given
    val request = CreatePortfolioRequest(
      name = "AUD account",
      initialBalance = 30_000.0,
      currency = "AUD",
      userId = null,
    )

    // When
    val response = restTemplate.postForEntity(
      "/api/portfolio",
      jsonEntity(request),
      Portfolio::class.java,
    )

    // Then: baseCurrency follows currency rather than silently defaulting to USD
    assertEquals(HttpStatus.CREATED, response.statusCode)
    assertEquals("AUD", response.body!!.currency)
    assertEquals("AUD", response.body!!.baseCurrency)
  }

  @Test
  fun `POST returns 400 when initialBalance is not positive`() {
    // Given: body that violates @field:Positive
    val invalid = mapOf(
      "name" to "Bad",
      "initialBalance" to -1.0,
      "currency" to "USD",
    )

    // When
    val response = restTemplate.postForEntity(
      "/api/portfolio",
      jsonEntity(invalid),
      String::class.java,
    )

    // Then
    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
  }

  @Test
  fun `GET list returns saved portfolios filtered by userId`() {
    // Given
    createPortfolio(name = "Alice's", userId = "alice")
    createPortfolio(name = "Bob's", userId = "bob")

    // When
    val byAlice = restTemplate.exchange(
      "/api/portfolio?userId=alice",
      HttpMethod.GET,
      null,
      Array<Portfolio>::class.java,
    )
    val all = restTemplate.exchange(
      "/api/portfolio",
      HttpMethod.GET,
      null,
      Array<Portfolio>::class.java,
    )

    // Then
    assertEquals(1, byAlice.body!!.size)
    assertEquals("Alice's", byAlice.body!![0].name)
    assertEquals(2, all.body!!.size)
  }

  @Test
  fun `GET single returns 404 for missing portfolio`() {
    // When
    val response = restTemplate.exchange(
      "/api/portfolio/99999",
      HttpMethod.GET,
      null,
      String::class.java,
    )

    // Then
    assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
  }

  @Test
  fun `PUT updates currentBalance and bumps lastUpdated`() {
    // Given
    val saved = createPortfolio(initialBalance = 10_000.0)
    val originalLastUpdated = saved.lastUpdated

    // When
    val response = restTemplate.exchange(
      RequestEntity
        .put(URI.create("/api/portfolio/${saved.id}"))
        .contentType(MediaType.APPLICATION_JSON)
        .body(UpdatePortfolioRequest(currentBalance = 12_500.0)),
      Portfolio::class.java,
    )

    // Then
    assertEquals(HttpStatus.OK, response.statusCode)
    assertEquals(12_500.0, response.body!!.currentBalance)
    assertTrue(
      !response.body!!.lastUpdated.isBefore(originalLastUpdated),
      "lastUpdated should advance",
    )
  }

  @Test
  fun `PUT returns 404 for missing portfolio`() {
    // When
    val response = restTemplate.exchange(
      RequestEntity
        .put(URI.create("/api/portfolio/99999"))
        .contentType(MediaType.APPLICATION_JSON)
        .body(UpdatePortfolioRequest(currentBalance = 1.0)),
      String::class.java,
    )

    // Then
    assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
  }

  @Test
  fun `DELETE returns 204 and CASCADEs to child rows`() {
    // Given
    val saved = createPortfolio()
    insertChildPosition(saved.id!!)
    insertChildCashTransaction(saved.id)
    assertEquals(1, dsl.fetchCount(DSL.table("positions")))
    assertEquals(1, dsl.fetchCount(DSL.table("cash_transactions")))

    // When
    val response = restTemplate.exchange(
      "/api/portfolio/${saved.id}",
      HttpMethod.DELETE,
      null,
      Void::class.java,
    )

    // Then: portfolio gone, child rows CASCADE-deleted
    assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    assertEquals(0, dsl.fetchCount(DSL.table("portfolios")))
    assertEquals(0, dsl.fetchCount(DSL.table("positions")))
    assertEquals(0, dsl.fetchCount(DSL.table("cash_transactions")))
  }

  @Test
  fun `POST sync returns 404 with BrokerErrorResponse body for missing portfolio`() {
    // Given: empty body — request shape isn't reached because portfolio lookup happens first
    val body = mapOf("credentials" to mapOf<String, Any>("token" to "x", "queryId" to "q"))

    // When
    val response = restTemplate.postForEntity(
      "/api/portfolio/99999/sync",
      jsonEntity(body),
      BrokerErrorResponse::class.java,
    )

    // Then
    assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    assertNotNull(response.body)
    assertEquals("Not Found", response.body!!.error)
  }

  private fun createPortfolio(
    name: String = "Test",
    initialBalance: Double = 5_000.0,
    userId: String? = null,
  ): Portfolio {
    val request = CreatePortfolioRequest(
      name = name,
      initialBalance = initialBalance,
      currency = "USD",
      userId = userId,
    )
    val response = restTemplate.postForEntity(
      "/api/portfolio",
      jsonEntity(request),
      Portfolio::class.java,
    )
    return response.body!!
  }

  private fun insertChildPosition(portfolioId: Long) {
    dsl
      .insertInto(DSL.table("positions"))
      .set(DSL.field("portfolio_id"), portfolioId)
      .set(DSL.field("symbol"), "AAPL")
      .set(DSL.field("instrument_type"), "STOCK")
      .set(DSL.field("current_quantity"), 100)
      .set(DSL.field("average_entry_price"), 150.0)
      .set(DSL.field("total_cost"), 15_000.0)
      .set(DSL.field("status"), "OPEN")
      .set(DSL.field("opened_date"), java.time.LocalDate.now())
      .set(DSL.field("source"), "MANUAL")
      .execute()
  }

  private fun insertChildCashTransaction(portfolioId: Long) {
    dsl
      .insertInto(DSL.table("cash_transactions"))
      .set(DSL.field("portfolio_id"), portfolioId)
      .set(DSL.field("type"), "DEPOSIT")
      .set(DSL.field("amount"), 1000.0)
      .set(DSL.field("currency"), "USD")
      .set(DSL.field("transaction_date"), java.time.LocalDate.now())
      .set(DSL.field("source"), "MANUAL")
      .execute()
  }

  private fun jsonEntity(body: Any): HttpEntity<Any> {
    val headers = org.springframework.http.HttpHeaders().apply {
      contentType = MediaType.APPLICATION_JSON
    }
    return HttpEntity(body, headers)
  }
}
