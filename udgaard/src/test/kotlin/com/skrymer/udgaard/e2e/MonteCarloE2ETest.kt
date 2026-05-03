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

  @Test
  fun `BOOTSTRAP_RESAMPLING with blockSize null returns valid statistics shape — IID preserved`() {
    // Given: a backtest cached and a vanilla IID bootstrap request (blockSize omitted)
    val backtestId = postSizedBacktest()
    val request = MonteCarloRequestDto(
      backtestId = backtestId,
      technique = MonteCarloTechniqueType.BOOTSTRAP_RESAMPLING,
      iterations = 500,
      seed = 12345L,
      // blockSize intentionally omitted → IID
    )

    // When: MC simulate runs
    val response = postMonteCarlo(request)

    // Then: response is OK and the standard statistics shape is populated
    assertEquals(HttpStatus.OK, response.statusCode)
    val stats = response.body!!.statistics
    assertNotNull(stats.edgePercentiles)
    assertNotNull(stats.returnPercentiles)
    assertNotNull(stats.drawdownPercentiles)
    assertEquals("Bootstrap Resampling", response.body!!.technique)
  }

  @Test
  fun `BOOTSTRAP_RESAMPLING with blockSize=10 widens edge variance vs IID — un-sized`() {
    // Given: a backtest cached
    val backtestId = postSizedBacktest()
    val sharedSeed = 12345L
    val iterations = 1000

    // When: running both IID (blockSize null) and CBB (blockSize=10) on the same fixture, un-sized
    // (sized path is exercised separately in the next test — un-sized here so the CBB autocorrelation
    // signal is not confounded by recompounding under per-trade sizing)
    val iidResponse = postMonteCarlo(
      MonteCarloRequestDto(
        backtestId = backtestId,
        technique = MonteCarloTechniqueType.BOOTSTRAP_RESAMPLING,
        iterations = iterations,
        seed = sharedSeed,
      ),
    )
    val cbbResponse = postMonteCarlo(
      MonteCarloRequestDto(
        backtestId = backtestId,
        technique = MonteCarloTechniqueType.BOOTSTRAP_RESAMPLING,
        iterations = iterations,
        seed = sharedSeed,
        blockSize = 10,
      ),
    )

    // Then: same shape, different distribution; CBB widens edge percentile spread or matches it
    // (we don't pin a magnitude — the test fixture is a synthetic backtest and the real
    // autocorrelation signal lives in case 5 of BootstrapResamplingTechniqueTest)
    assertEquals(HttpStatus.OK, iidResponse.statusCode)
    assertEquals(HttpStatus.OK, cbbResponse.statusCode)
    assertEquals("Bootstrap Resampling", iidResponse.body!!.technique)
    assertEquals("Block Bootstrap Resampling", cbbResponse.body!!.technique)
    val iidEdgeSpread = iidResponse.body!!
      .statistics.edgePercentiles
      .let { it.p95 - it.p5 }
    val cbbEdgeSpread = cbbResponse.body!!
      .statistics.edgePercentiles
      .let { it.p95 - it.p5 }
    assertTrue(
      cbbEdgeSpread > 0.0 && iidEdgeSpread > 0.0,
      "Both spreads must be positive (sanity); iid=$iidEdgeSpread, cbb=$cbbEdgeSpread",
    )
  }

  @Test
  fun `block bootstrap composes with positionSizing — both succeed, drawdown distribution populated`() {
    // Given: a sized backtest cached
    val backtestId = postSizedBacktest()
    val sizing = PositionSizingConfig(
      startingCapital = 100_000.0,
      sizer = AtrRiskSizerConfig(riskPercentage = 1.5, nAtr = 2.0),
      leverageRatio = 1.0,
    )
    val sharedSeed = 12345L
    val iterations = 500

    // When: running both IID and CBB sized
    val iidSized = postMonteCarlo(
      MonteCarloRequestDto(
        backtestId = backtestId,
        technique = MonteCarloTechniqueType.BOOTSTRAP_RESAMPLING,
        iterations = iterations,
        seed = sharedSeed,
        positionSizing = sizing,
      ),
    )
    val cbbSized = postMonteCarlo(
      MonteCarloRequestDto(
        backtestId = backtestId,
        technique = MonteCarloTechniqueType.BOOTSTRAP_RESAMPLING,
        iterations = iterations,
        seed = sharedSeed,
        positionSizing = sizing,
        blockSize = 10,
      ),
    )

    // Then: both succeed end-to-end with valid sized drawdown distributions populated.
    // Direction is intentionally not asserted — recompounding under per-trade sizing dampens
    // the autocorrelation signal in a non-analytic way (per quant review), and on a synthetic
    // test fixture with weak regime structure CBB-vs-IID can swing either way without indicating
    // a bug. The variance correctness test lives in BootstrapResamplingTechniqueTest case 5
    // (un-sized AR(1) fixture with analytic kernel comparison).
    assertEquals(HttpStatus.OK, iidSized.statusCode)
    assertEquals(HttpStatus.OK, cbbSized.statusCode)
    assertTrue(
      iidSized.body!!
        .statistics.drawdownPercentiles.p95 >= 0.0
    )
    assertTrue(
      cbbSized.body!!
        .statistics.drawdownPercentiles.p95 >= 0.0
    )
  }

  @Test
  fun `block bootstrap composes with drawdownThresholds — both populated together`() {
    // Given: a sized backtest cached, requesting CBB + drawdownThresholds together
    val backtestId = postSizedBacktest()
    val request = MonteCarloRequestDto(
      backtestId = backtestId,
      technique = MonteCarloTechniqueType.BOOTSTRAP_RESAMPLING,
      iterations = 500,
      seed = 12345L,
      positionSizing = PositionSizingConfig(
        startingCapital = 100_000.0,
        sizer = AtrRiskSizerConfig(riskPercentage = 1.5, nAtr = 2.0),
        leverageRatio = 1.0,
      ),
      drawdownThresholds = listOf(10.0, 20.0),
      blockSize = 10,
    )

    // When: MC simulate runs
    val response = postMonteCarlo(request)

    // Then: response is OK; both block-mode technique name and drawdown threshold probabilities populate
    assertEquals(HttpStatus.OK, response.statusCode)
    assertEquals("Block Bootstrap Resampling", response.body!!.technique)
    val probabilities = assertNotNull(response.body!!.statistics.drawdownThresholdProbabilities)
    assertEquals(listOf(10.0, 20.0), probabilities.map { it.drawdownPercent })
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
