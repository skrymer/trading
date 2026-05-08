package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.scanner.dto.ValidateEntriesRequest
import com.skrymer.udgaard.scanner.model.EntryValidationResponse
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers POST /api/scanner/validate-entries over real HTTP + Postgres. Pinned: response
 * shape, 400 on unknown strategy, and the new HTTP 400 boundary check on more than 30
 * symbols (was a silent take(30) before — see the validate-entries correction in PR 3).
 *
 * Test data uses a recent date range — validate filters quotes with
 * `quotesAfter = today.minusDays(90)`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScannerValidateEntriesE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var dsl: DSLContext

  private val today = LocalDate.now()
  private val populateStart = today.minusDays(60)
  private val populateEnd = today.minusDays(1)

  @BeforeAll
  fun setupTestData() {
    dsl.deleteFrom(DSL.table("scanner_trades")).execute()
    BacktestTestDataGenerator.populate(dsl, populateStart, populateEnd)
  }

  @AfterAll
  fun resetTestData() {
    BacktestTestDataGenerator.reset(dsl)
  }

  @Test
  fun `POST validate-entries returns 200 with one EntryValidationResult per requested symbol`() {
    // Given
    val request = ValidateEntriesRequest(
      symbols = listOf("AAPL", "MSFT", "NVDA"),
      entryStrategyName = "TestEntry",
      exitStrategyName = "TestExit",
    )

    // When
    val response = restTemplate.postForEntity(
      "/api/scanner/validate-entries",
      jsonEntity(request),
      EntryValidationResponse::class.java,
    )

    // Then
    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertEquals(3, body.results.size)
    assertTrue(body.validCount in 0..3)
    assertTrue(body.invalidCount in 0..3)
    assertTrue(body.doaCount in 0..3)
    // A symbol can count in multiple buckets (e.g., invalid AND DOA), so the buckets don't sum to results.size
    body.results.forEach {
      assertTrue(it.symbol in listOf("AAPL", "MSFT", "NVDA"))
    }
  }

  @Test
  fun `POST validate-entries returns 400 when entry strategy is unknown`() {
    // Given
    val request = ValidateEntriesRequest(
      symbols = listOf("AAPL"),
      entryStrategyName = "DefinitelyDoesNotExist",
      exitStrategyName = "TestExit",
    )

    // When
    val response = restTemplate.postForEntity(
      "/api/scanner/validate-entries",
      jsonEntity(request),
      String::class.java,
    )

    // Then
    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
  }

  @Test
  fun `POST validate-entries returns 400 when symbol count exceeds the per-request cap`() {
    // Given: 31 symbols, one over the cap (MAX_VALIDATE_SYMBOLS = 30)
    val request = ValidateEntriesRequest(
      symbols = (1..31).map { "S$it" },
      entryStrategyName = "TestEntry",
      exitStrategyName = "TestExit",
    )

    // When
    val response = restTemplate.postForEntity(
      "/api/scanner/validate-entries",
      jsonEntity(request),
      String::class.java,
    )

    // Then: was a silent take(30) before the PR 3 correction — now rejects loudly.
    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    val body = response.body!!
    assertTrue(body.contains("Too many symbols: 31"), "Body should surface the cap-violation message; got: $body")
    assertTrue(body.contains("max 30"), "Body should name the cap value; got: $body")
  }

  @Test
  fun `POST validate-entries accepts exactly the per-request cap of symbols`() {
    // Given: exactly 30 symbols (the boundary — verifies the comparison is inclusive).
    // Symbols don't have to resolve to real stocks for this test — the cap is enforced
    // at the request boundary, before any DB lookup.
    val request = ValidateEntriesRequest(
      symbols = (1..30).map { "S$it" },
      entryStrategyName = "TestEntry",
      exitStrategyName = "TestExit",
    )

    // When
    val response = restTemplate.postForEntity(
      "/api/scanner/validate-entries",
      jsonEntity(request),
      EntryValidationResponse::class.java,
    )

    // Then: 30 is accepted (boundary-case off-by-one regression check)
    assertEquals(HttpStatus.OK, response.statusCode)
  }
}
