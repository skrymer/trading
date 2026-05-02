package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.backtesting.dto.BacktestRequest
import com.skrymer.udgaard.backtesting.dto.PredefinedStrategyConfig
import com.skrymer.udgaard.backtesting.model.BacktestResponseDto
import com.skrymer.udgaard.backtesting.model.PositionSizingConfig
import com.skrymer.udgaard.backtesting.service.sizer.AtrRiskSizerConfig
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

/**
 * E2E tests for the new risk-adjusted-metric fields on the backtest response.
 * Position-sized backtests populate riskMetrics + benchmarkComparison + cagr + drawdownEpisodes;
 * un-sized backtests leave them all null.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BacktestRiskMetricsE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var dsl: DSLContext

  @BeforeAll
  fun setupTestData() {
    BacktestTestDataGenerator.populate(dsl)
  }

  @Test
  fun `position-sized backtest populates riskMetrics, benchmarkComparison, cagr, drawdownEpisodes`() {
    // Given a position-sized backtest with the standard fixture
    val request = sizedRequest()

    // When
    val response = postBacktest(request)

    // Then all four new fields are present and well-shaped
    assertEquals(HttpStatus.OK, response.statusCode)
    val body = requireNotNull(response.body)
    requireNotNull(body.riskMetrics) { "riskMetrics must be populated for sized backtest" }
    val benchmarkComparison = requireNotNull(body.benchmarkComparison) {
      "benchmarkComparison must be populated when SPY is in the fixture"
    }
    assertEquals("SPY", benchmarkComparison.benchmarkSymbol)
    requireNotNull(body.cagr) { "cagr must be populated for sized backtest" }
    val episodes = requireNotNull(body.drawdownEpisodes) { "drawdownEpisodes must be populated for sized backtest" }
    assertTrue(episodes.size in 0..10, "drawdownEpisodes capped at top-10, got ${episodes.size}")
  }

  @Test
  fun `un-sized backtest leaves new fields null`() {
    // Given a backtest WITHOUT positionSizing
    val request = sizedRequest().copy(positionSizing = null)

    // When
    val response = postBacktest(request)

    // Then all four new fields are null (RiskMetricsService isn't called)
    assertEquals(HttpStatus.OK, response.statusCode)
    val body = requireNotNull(response.body)
    assertNull(body.riskMetrics, "riskMetrics must be null for un-sized backtest")
    assertNull(body.benchmarkComparison, "benchmarkComparison must be null for un-sized backtest")
    assertNull(body.cagr, "cagr must be null for un-sized backtest")
    assertNull(body.drawdownEpisodes, "drawdownEpisodes must be null for un-sized backtest")
  }

  @Test
  fun `Calmar uses CAGR not totalReturn (regression vs prior wrong formula)`() {
    // Given a position-sized backtest with a measurable drawdown
    val request = sizedRequest()

    // When
    val response = postBacktest(request)
    val body = requireNotNull(response.body)
    val sizing = requireNotNull(body.positionSizing)
    val riskMetrics = requireNotNull(body.riskMetrics)

    // Then calmarRatio (when present) approximately equals CAGR / |maxDD|.
    // Equivalently: calmarRatio is NOT equal to totalReturn / maxDD (which would be the old wrong formula).
    val calmar = riskMetrics.calmarRatio
    val cagr = body.cagr
    if (calmar != null && cagr != null && sizing.maxDrawdownPct > 0) {
      val expected = cagr / sizing.maxDrawdownPct
      assertEquals(expected, calmar, 0.001, "Calmar should equal CAGR / |maxDD|")
      // Sanity: the OLD wrong formula would give totalReturn/maxDD; for any backtest > 1 calendar day with
      // CAGR != totalReturn, the values differ. We don't require this to fail strictly because trivial fixtures
      // may yield CAGR ≈ totalReturn.
    }
  }

  @Test
  fun `riskFreeRatePct=4 reduces Sharpe vs default`() {
    // Given the same backtest with RF=0 (default) and RF=4
    val rfZero = postBacktest(sizedRequest()).body!!
    val rfFour = postBacktest(sizedRequest().copy(riskFreeRatePct = 4.0)).body!!

    // Then Sharpe drops when RF rises (excess return is smaller)
    val sharpeZero = rfZero.riskMetrics?.sharpeRatio
    val sharpeFour = rfFour.riskMetrics?.sharpeRatio
    if (sharpeZero != null && sharpeFour != null) {
      assertTrue(sharpeFour < sharpeZero, "expected RF=4 Sharpe ($sharpeFour) < RF=0 Sharpe ($sharpeZero)")
    }
  }

  // ===== HELPERS =====

  private fun sizedRequest() = BacktestRequest(
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

  private fun postBacktest(request: BacktestRequest): ResponseEntity<BacktestResponseDto> =
    restTemplate.exchange(
      "/api/backtest",
      HttpMethod.POST,
      HttpEntity(request, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
      BacktestResponseDto::class.java,
    )
}
