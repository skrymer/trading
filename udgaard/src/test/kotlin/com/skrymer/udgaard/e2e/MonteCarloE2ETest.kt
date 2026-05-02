package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.backtesting.dto.BacktestRequest
import com.skrymer.udgaard.backtesting.dto.MonteCarloRequestDto
import com.skrymer.udgaard.backtesting.dto.PredefinedStrategyConfig
import com.skrymer.udgaard.backtesting.model.BacktestResponseDto
import com.skrymer.udgaard.backtesting.model.MonteCarloResult
import com.skrymer.udgaard.backtesting.model.MonteCarloTechniqueType
import com.skrymer.udgaard.backtesting.model.PositionSizingConfig
import com.skrymer.udgaard.backtesting.service.sizer.AtrRiskSizerConfig
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
import org.springframework.http.ResponseEntity
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * E2E tests for the Monte Carlo simulate endpoint, focused on drawdown threshold probabilities + CVaR.
 * The MC endpoint depends on a cached BacktestResultStore entry, so each test posts a backtest
 * first and then runs MC against the returned backtestId.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MonteCarloE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var dsl: DSLContext

  @BeforeAll
  fun setupTestData() {
    BacktestTestDataGenerator.populate(dsl)
  }

  @Test
  fun `MC with drawdownThresholds returns sorted threshold probabilities and CVaR`() {
    // Given: a position-sized backtest cached in BacktestResultStore
    val backtestId = postSizedBacktest()

    // When: MC simulate is requested with thresholds in shuffled order
    val request = MonteCarloRequestDto(
      backtestId = backtestId,
      technique = MonteCarloTechniqueType.TRADE_SHUFFLING,
      iterations = 500,
      seed = 12345L,
      positionSizing = PositionSizingConfig(
        startingCapital = 100_000.0,
        sizer = AtrRiskSizerConfig(riskPercentage = 1.5, nAtr = 2.0),
        leverageRatio = 1.0,
      ),
      drawdownThresholds = listOf(30.0, 5.0, 10.0),
    )
    val response = postMonteCarlo(request)

    // Then: the response carries the probabilities, sorted ascending by drawdownPercent
    assertEquals(HttpStatus.OK, response.statusCode)
    val probabilities = assertNotNull(response.body!!.statistics.drawdownThresholdProbabilities)
    val percents = probabilities.map { it.drawdownPercent }
    assertEquals(listOf(5.0, 10.0, 30.0), percents)
    probabilities.forEach { p ->
      assertTrue(
        p.probability in 0.0..100.0,
        "probability ${p.probability} out of range for drawdownPercent=${p.drawdownPercent}",
      )
      // CVaR null OR CVaR > threshold (mean of strict exceedances)
      val cvar = p.expectedDrawdownGivenExceeded
      if (cvar != null) {
        assertTrue(cvar > p.drawdownPercent, "CVaR=$cvar must exceed threshold ${p.drawdownPercent}")
      }
    }
  }

  @Test
  fun `MC without drawdownThresholds yields null probabilities field`() {
    // Given: a sized backtest cached
    val backtestId = postSizedBacktest()

    // When: MC simulate is requested without drawdownThresholds
    val request = MonteCarloRequestDto(
      backtestId = backtestId,
      technique = MonteCarloTechniqueType.TRADE_SHUFFLING,
      iterations = 200,
      seed = 12345L,
      positionSizing = PositionSizingConfig(
        startingCapital = 100_000.0,
        sizer = AtrRiskSizerConfig(riskPercentage = 1.5, nAtr = 2.0),
        leverageRatio = 1.0,
      ),
      // drawdownThresholds intentionally omitted
    )
    val response = postMonteCarlo(request)

    // Then: response field is null — opt-in semantics
    assertEquals(HttpStatus.OK, response.statusCode)
    assertNull(response.body!!.statistics.drawdownThresholdProbabilities)
  }

  // ===== HELPERS =====

  private fun postSizedBacktest(): String {
    val request = BacktestRequest(
      stockSymbols = BacktestTestDataGenerator.ALL_SYMBOLS,
      startDate = "2024-01-01",
      endDate = "2024-12-31",
      entryStrategy = PredefinedStrategyConfig(name = "TestEntry"),
      exitStrategy = PredefinedStrategyConfig(name = "TestExit"),
      useUnderlyingAssets = false,
      maxPositions = 10,
      randomSeed = 42L,
      positionSizing = PositionSizingConfig(
        startingCapital = 100_000.0,
        sizer = AtrRiskSizerConfig(riskPercentage = 1.5, nAtr = 2.0),
        leverageRatio = 1.0,
      ),
    )
    val response = restTemplate.exchange(
      "/api/backtest",
      HttpMethod.POST,
      HttpEntity(request, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
      BacktestResponseDto::class.java,
    )
    assertEquals(HttpStatus.OK, response.statusCode, "backtest setup must succeed")
    val backtestId = assertNotNull(response.body!!.backtestId, "backtestId must be present in response")
    return backtestId
  }

  private fun postMonteCarlo(request: MonteCarloRequestDto): ResponseEntity<MonteCarloResult> =
    restTemplate.exchange(
      "/api/monte-carlo/simulate",
      HttpMethod.POST,
      HttpEntity(request, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
      MonteCarloResult::class.java,
    )
}
