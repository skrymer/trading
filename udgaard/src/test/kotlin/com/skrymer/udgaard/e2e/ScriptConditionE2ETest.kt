package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.backtesting.dto.BacktestRequest
import com.skrymer.udgaard.backtesting.dto.ConditionConfig
import com.skrymer.udgaard.backtesting.dto.CustomStrategyConfig
import com.skrymer.udgaard.backtesting.model.BacktestResponseDto
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
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScriptConditionE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var dsl: DSLContext

  @BeforeAll
  fun setupTestData() {
    BacktestTestDataGenerator.populate(dsl)
  }

  @Test
  fun `POST backtest with script entry and exit conditions runs end to end`() {
    // Given: a custom strategy whose entry and exit logic are Kotlin scripts — exercising
    // registry auto-discovery of the script conditions, routing through DynamicStrategyBuilder,
    // and compilation of both predicates, all through the real HTTP stack
    val request =
      BacktestRequest(
        stockSymbols = BacktestTestDataGenerator.ALL_SYMBOLS,
        entryStrategy =
          CustomStrategyConfig(
            conditions = listOf(
              ConditionConfig(type = "script", parameters = mapOf("script" to "quote.closePrice > 0.0")),
            ),
          ),
        exitStrategy =
          CustomStrategyConfig(
            conditions = listOf(
              ConditionConfig(
                type = "script",
                parameters = mapOf(
                  "script" to "entryQuote != null && quote.closePrice < entryQuote.closePrice - 2.0 * entryQuote.atr",
                ),
              ),
            ),
          ),
        startDate = "2024-01-02",
        endDate = "2024-03-29",
        useUnderlyingAssets = false,
      )

    // When
    val response =
      restTemplate.exchange(
        "/api/backtest",
        HttpMethod.POST,
        HttpEntity(request, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
        BacktestResponseDto::class.java,
      )

    // Then: the scripts compiled and the backtest executed and produced trades
    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertNotNull(body.backtestId)
    assertTrue(body.totalTrades > 0, "script entry/exit should produce trades on the fixture data")
  }

  @Test
  fun `POST backtest with a script that does not compile returns 400 with the compile error`() {
    // Given: an entry script that is not valid Kotlin
    val request =
      BacktestRequest(
        stockSymbols = BacktestTestDataGenerator.ALL_SYMBOLS,
        entryStrategy =
          CustomStrategyConfig(
            conditions = listOf(
              ConditionConfig(type = "script", parameters = mapOf("script" to "quote.closePrice >")),
            ),
          ),
        exitStrategy =
          CustomStrategyConfig(
            conditions = listOf(
              ConditionConfig(type = "script", parameters = mapOf("script" to "false")),
            ),
          ),
        startDate = "2024-01-02",
        endDate = "2024-03-29",
        useUnderlyingAssets = false,
      )

    // When
    val response =
      restTemplate.exchange(
        "/api/backtest",
        HttpMethod.POST,
        HttpEntity(request, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
        String::class.java,
      )

    // Then: the bad script fails the request loudly at strategy-build time, not silently
    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    assertTrue(
      response.body?.contains("failed to compile") == true,
      "expected the compiler diagnostics in the response body",
    )
  }
}
