package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.scanner.dto.ScanRequest
import com.skrymer.udgaard.scanner.model.ScanResponse
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Covers POST /api/scanner/scan over real HTTP + Postgres. Pinned: every deterministic field on
 * `ScanResponse`, both for plain `EntryStrategy` (no near-misses, no failure summary) and for
 * `DetailedEntryStrategy` (populated near-miss list + failure-summary aggregation), plus 400 on
 * unknown entry strategy via GlobalExceptionHandler.
 *
 * Test data is populated with a *recent* date range — `scan` filters quotes with
 * `quotesAfter = today.minusDays(90)`, so historic fixtures (e.g., the default
 * 2024-01-02..2024-03-29 range used by BacktestApiE2ETest) would be invisible.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScannerScanE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var dsl: DSLContext

  private val today = LocalDate.now()
  private val populateStart = today.minusDays(60)
  private val populateEnd = today.minusDays(1)
  private val expectedLastDate = generateSequence(populateEnd) { it.minusDays(1) }
    .first { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }

  @BeforeAll
  fun setupTestData() {
    dsl.deleteFrom(DSL.table("scanner_trades")).execute()
    BacktestTestDataGenerator.populate(dsl, populateStart, populateEnd)
  }

  @Test
  fun `POST scan with plain EntryStrategy returns the expected ScanResponse shape with empty detailed fields`() {
    // Given
    val request = ScanRequest(
      entryStrategyName = "TestEntry",
      exitStrategyName = "TestExit",
      stockSymbols = BacktestTestDataGenerator.ALL_SYMBOLS,
    )

    // When
    val response = restTemplate.postForEntity("/api/scanner/scan", jsonEntity(request), ScanResponse::class.java)

    // Then
    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertEquals("TestEntry", body.entryStrategyName)
    assertEquals("TestExit", body.exitStrategyName)
    assertEquals(BacktestTestDataGenerator.ALL_SYMBOLS.size, body.totalStocksScanned)
    assertEquals("Random", body.rankerName, "TestEntry has no preferredRanker, falls back to Random")
    assertEquals(expectedLastDate, body.latestDataDate, "Max breadth date from the populated range")
    assertTrue(body.nearMissCandidates.isEmpty(), "Plain EntryStrategy emits no near-misses (only DetailedEntryStrategy does)")
    assertTrue(body.conditionFailureSummary.isEmpty(), "Plain EntryStrategy emits no failure summary")
    assertTrue(body.executionTimeMs > 0)
    body.results.forEach {
      assertTrue(it.symbol in BacktestTestDataGenerator.ALL_SYMBOLS)
      assertTrue(it.closePrice > 0.0)
    }
  }

  @Test
  fun `POST scan with DetailedEntryStrategy populates near-misses and failure summary deterministically`() {
    // Given: TestDetailedEntry has minimumPrice(0.01) (always passes) + marketBreadthAbove(99.0)
    // (always fails — fixture breadth is coerced to 40..90). So no full matches, all 55 stocks are
    // near-misses, and one failure-summary entry reports stocksBlocked == totalStocksEvaluated.
    val request = ScanRequest(
      entryStrategyName = "TestDetailedEntry",
      exitStrategyName = "TestExit",
      stockSymbols = BacktestTestDataGenerator.ALL_SYMBOLS,
      nearMissLimit = BacktestTestDataGenerator.ALL_SYMBOLS.size,
    )

    // When
    val response = restTemplate.postForEntity("/api/scanner/scan", jsonEntity(request), ScanResponse::class.java)

    // Then
    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertEquals(0, body.results.size, "marketBreadthAbove(99.0) blocks every stock")

    // Failure summary: a single aggregate row for the always-failing condition.
    assertEquals(1, body.conditionFailureSummary.size)
    val summary = body.conditionFailureSummary[0]
    assertEquals("MarketBreadthAboveCondition", summary.conditionType)
    assertEquals(BacktestTestDataGenerator.ALL_SYMBOLS.size, summary.stocksBlocked)
    assertEquals(BacktestTestDataGenerator.ALL_SYMBOLS.size, summary.totalStocksEvaluated)

    // Near-misses: every stock passes minimumPrice but fails marketBreadthAbove → conditionsPassed=1,
    // conditionsTotal=2. Verifies the nested EntrySignalDetails round-trips through Jackson.
    assertEquals(BacktestTestDataGenerator.ALL_SYMBOLS.size, body.nearMissCandidates.size)
    val sample = body.nearMissCandidates[0]
    assertNotNull(sample.entrySignalDetails)
    assertEquals(1, sample.conditionsPassed)
    assertEquals(2, sample.conditionsTotal)
    assertEquals(2, sample.entrySignalDetails.conditions.size)
    assertTrue(sample.entrySignalDetails.conditions.any { it.conditionType == "MinimumPriceCondition" && it.passed })
    assertTrue(sample.entrySignalDetails.conditions.any { it.conditionType == "MarketBreadthAboveCondition" && !it.passed })
  }

  @Test
  fun `POST scan returns 400 when entry strategy is unknown`() {
    // Given
    val request = ScanRequest(
      entryStrategyName = "DefinitelyDoesNotExist",
      exitStrategyName = "TestExit",
    )

    // When
    val response = restTemplate.postForEntity("/api/scanner/scan", jsonEntity(request), String::class.java)

    // Then: GlobalExceptionHandler maps IllegalArgumentException to 400
    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
  }
}
