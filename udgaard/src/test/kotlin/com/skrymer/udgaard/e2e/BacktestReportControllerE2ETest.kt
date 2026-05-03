package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.backtesting.controller.BatchDeleteResponse
import com.skrymer.udgaard.backtesting.model.BacktestReport
import com.skrymer.udgaard.backtesting.model.BacktestReportListItem
import com.skrymer.udgaard.backtesting.model.BacktestReportMetadata
import com.skrymer.udgaard.backtesting.model.Trade
import com.skrymer.udgaard.backtesting.service.BacktestResultStore
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.jooq.tables.references.BACKTEST_REPORTS
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.time.LocalDate
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BacktestReportControllerE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var store: BacktestResultStore

  @Autowired
  private lateinit var dsl: DSLContext

  @BeforeEach
  fun cleanReports() {
    // The shared test container persists across tests; isolate listing/delete cases
    // by clearing the table before each.
    dsl.deleteFrom(BACKTEST_REPORTS).execute()
  }

  @Test
  fun `GET reports returns empty list when no reports stored`() {
    // Given an empty store
    // When listing
    val response = restTemplate.exchange(
      "/api/backtest/reports",
      HttpMethod.GET,
      null,
      object : ParameterizedTypeReference<List<BacktestReportListItem>>() {},
    )

    // Then 200 with empty array
    assertEquals(HttpStatus.OK, response.statusCode)
    assertEquals(0, response.body?.size)
  }

  @Test
  fun `GET reports lists stored reports sorted by createdAt desc`() {
    // Given three reports stored sequentially
    val firstId = store.store(fixtureReport(), metadata("a"))
    Thread.sleep(10) // ensure distinguishable created_at timestamps
    val secondId = store.store(fixtureReport(), metadata("b"))
    Thread.sleep(10)
    val thirdId = store.store(fixtureReport(), metadata("c"))

    // When listing
    val response = restTemplate.exchange(
      "/api/backtest/reports",
      HttpMethod.GET,
      null,
      object : ParameterizedTypeReference<List<BacktestReportListItem>>() {},
    )

    // Then 3 items in newest-first order
    assertEquals(HttpStatus.OK, response.statusCode)
    val body = requireNotNull(response.body)
    assertEquals(3, body.size)
    assertEquals(UUID.fromString(thirdId), body[0].backtestId)
    assertEquals(UUID.fromString(secondId), body[1].backtestId)
    assertEquals(UUID.fromString(firstId), body[2].backtestId)
    // and each row carries the metadata + summary
    assertEquals("c-entry", body[0].metadata.entryStrategyName)
    assertNotNull(body[0].summary.totalTrades)
  }

  @Test
  fun `DELETE reports by id removes the report`() {
    // Given a stored report
    val id = store.store(fixtureReport(), metadata("x"))

    // When deleted
    val response = restTemplate.exchange(
      "/api/backtest/reports/$id",
      HttpMethod.DELETE,
      null,
      Void::class.java,
    )

    // Then 204 + subsequent get returns null (cache miss)
    assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
    assertEquals(null, store.get(id))
  }

  @Test
  fun `DELETE reports by unknown id returns 404`() {
    // Given an empty store
    // When deleting a random UUID
    val response = restTemplate.exchange(
      "/api/backtest/reports/${UUID.randomUUID()}",
      HttpMethod.DELETE,
      null,
      Void::class.java,
    )

    // Then 404
    assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
  }

  @Test
  fun `POST batch-delete removes all listed ids`() {
    // Given five reports stored
    val ids = (1..5).map { store.store(fixtureReport(), metadata("r$it")) }

    // When batch-deleting the first three
    val toDelete = ids.take(3).map(UUID::fromString)
    val response = restTemplate.exchange(
      "/api/backtest/reports/batch-delete",
      HttpMethod.POST,
      HttpEntity(toDelete, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
      BatchDeleteResponse::class.java,
    )

    // Then the response counts the three deletions; remaining two are still retrievable
    assertEquals(HttpStatus.OK, response.statusCode)
    assertEquals(3, response.body?.deleted)
    assertEquals(null, store.get(ids[0]))
    assertEquals(null, store.get(ids[1]))
    assertEquals(null, store.get(ids[2]))
    assertNotNull(store.get(ids[3]))
    assertNotNull(store.get(ids[4]))
  }

  @Test
  fun `batch-delete rejects oversized id lists with 400`() {
    // Given a request body that exceeds the per-call cap
    val oversized = (1..501).map { UUID.randomUUID() }

    // When posted
    val response = restTemplate.exchange(
      "/api/backtest/reports/batch-delete",
      HttpMethod.POST,
      HttpEntity(oversized, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
      String::class.java,
    )

    // Then 400 — request is rejected before the repository is called
    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
  }

  @Test
  fun `batch-delete is idempotent on missing ids`() {
    // Given one stored report
    val realId = UUID.fromString(store.store(fixtureReport(), metadata("only")))
    val mixed = listOf(realId, UUID.randomUUID(), UUID.randomUUID())

    // When batch-deleting a mix of valid + unknown ids
    val response = restTemplate.exchange(
      "/api/backtest/reports/batch-delete",
      HttpMethod.POST,
      HttpEntity(mixed, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
      BatchDeleteResponse::class.java,
    )

    // Then 200 with count = 1; no error
    assertEquals(HttpStatus.OK, response.statusCode)
    assertEquals(1, response.body?.deleted)
    assertTrue(store.get(realId.toString()) == null)
  }

  // ===== HELPERS =====

  private fun fixtureReport(): BacktestReport {
    val winners = listOf(createTrade(5.0, LocalDate.of(2024, 1, 1)))
    val losers = listOf(createTrade(-2.0, LocalDate.of(2024, 1, 3)))
    return BacktestReport(winningTrades = winners, losingTrades = losers)
  }

  private fun metadata(label: String) = BacktestReportMetadata(
    entryStrategyName = "$label-entry",
    exitStrategyName = "$label-exit",
    startDate = LocalDate.of(2024, 1, 1),
    endDate = LocalDate.of(2024, 12, 31),
  )

  private fun createTrade(profitPercentage: Double, entryDate: LocalDate): Trade {
    val entry = StockQuote(date = entryDate, closePrice = 100.0)
    val exit = StockQuote(date = entryDate.plusDays(5), closePrice = 100.0 + profitPercentage)
    return Trade(
      stockSymbol = "TEST",
      entryQuote = entry,
      quotes = listOf(exit),
      exitReason = "test-exit",
      profit = profitPercentage,
      startDate = entryDate,
      sector = "Technology",
    )
  }
}
