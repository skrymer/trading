package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.backtesting.dto.ConditionConfig
import com.skrymer.udgaard.backtesting.dto.ExitConditionEvaluationRequest
import com.skrymer.udgaard.backtesting.dto.StockExitConditionSignals
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import java.time.LocalDate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExitConditionSignalsApiE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var dsl: DSLContext

  @BeforeAll
  fun setupTestData() {
    BacktestTestDataGenerator.populate(dsl)
  }

  @Test
  fun `POST exit-condition-signals returns 200 with populated body and entryDate echo`() {
    // Given: a stock from the seeded test universe + a stopLoss exit condition
    val symbol = BacktestTestDataGenerator.ALL_SYMBOLS.first()
    val entryDate = LocalDate.of(2024, 1, 2)
    val request =
      ExitConditionEvaluationRequest(
        conditions = listOf(ConditionConfig(type = "stopLoss", parameters = mapOf("atrMultiplier" to 2.0))),
        operator = "OR",
        entryDate = entryDate,
      )

    // When
    val response =
      restTemplate.exchange(
        "/api/stocks/$symbol/exit-condition-signals",
        HttpMethod.POST,
        HttpEntity(request, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
        StockExitConditionSignals::class.java,
      )

    // Then: 200 OK + structurally valid response with the entry date echoed back
    assertEquals(HttpStatus.OK, response.statusCode)
    val body = requireNotNull(response.body)
    assertEquals(symbol, body.symbol)
    assertEquals(entryDate, body.entryDate, "entryDate must be echoed so the client can confirm what was evaluated")
    assertEquals("OR", body.operator)
    assertEquals(1, body.conditionDescriptions.size)
    assertTrue(body.totalQuotes > 0, "should evaluate at least one post-entry quote")
    // matchingQuotes can be 0 — the test data is random and stop-loss may not fire — but it must
    // never exceed totalQuotes
    assertTrue(body.matchingQuotes <= body.totalQuotes, "matching can't exceed total")
    assertEquals(body.matchingQuotes, body.quotesWithConditions.size, "list size must match the count")
  }
}
