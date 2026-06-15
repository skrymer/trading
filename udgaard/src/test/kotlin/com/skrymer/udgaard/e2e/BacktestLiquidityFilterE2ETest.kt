package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.backtesting.dto.BacktestRequest
import com.skrymer.udgaard.backtesting.dto.PredefinedStrategyConfig
import com.skrymer.udgaard.backtesting.model.BacktestResponseDto
import org.jooq.DSLContext
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end check, over the real HTTP stack + Postgres, that the default-on tradable-universe gate
 * (ADR 0026) loads enough pre-window history for its 252-bar age gate. The backtest window starts
 * ~273 trading bars into the fixture, so every name genuinely has >252 bars of real history by the
 * window start. Because the generator's names are all liquid and $50+, the gate must be a NO-OP here:
 * a default (filter-on) run must trade exactly what an `applyLiquidityFilter=false` run trades.
 *
 * This is the honest counterpart to the unit-level [com.skrymer.udgaard.backtesting.service.BacktestServiceLiquidityFilterTest]:
 * if the engine loaded quotes only from the window start, the age gate would mis-judge every name as
 * freshly-listed and starve the first ~252 bars of entries — the filter-on count would fall below the
 * filter-off count. Equality proves the minBars liquidity warmup is loaded end-to-end.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BacktestLiquidityFilterE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var dsl: DSLContext

  // ~2.5y so the window start sits well past the 252-bar age gate with room for in-window trades.
  private val fixtureStart = LocalDate.of(2017, 1, 2)
  private val fixtureEnd = LocalDate.of(2019, 6, 28)
  private val windowStart = LocalDate.of(2018, 2, 1) // ~273 trading bars into the fixture
  private val windowEnd = fixtureEnd

  @BeforeAll
  fun setupTestData() {
    BacktestTestDataGenerator.populate(dsl, fixtureStart, fixtureEnd)
  }

  @AfterAll
  fun resetTestData() {
    // Non-default range pollutes the shared tables; reset so later tests get a clean slate.
    BacktestTestDataGenerator.reset(dsl)
  }

  @Test
  fun `default-on filter loads the age-gate warmup so deep-window entries are not starved`() {
    // When: the same window is run filtered (default) and unfiltered
    val filtered = postBacktest(request(applyLiquidityFilter = true)).body!!
    val unfiltered = postBacktest(request(applyLiquidityFilter = false)).body!!

    // Then: the gate is a no-op over this all-liquid fixture — equal, non-zero trade counts. (Without the
    // pre-window warmup load, the filtered run would starve the first ~252 window bars and trail.)
    assertTrue(unfiltered.totalTrades > 0, "fixture/strategy must produce trades for the test to be meaningful")
    assertEquals(
      unfiltered.totalTrades,
      filtered.totalTrades,
      "default-on liquidity gate dropped trades over an all-liquid, fully-aged universe — warmup not loaded?",
    )
  }

  private fun request(applyLiquidityFilter: Boolean) = BacktestRequest(
    stockSymbols = BacktestTestDataGenerator.ALL_SYMBOLS,
    entryStrategy = PredefinedStrategyConfig(name = "TestEntry"),
    exitStrategy = PredefinedStrategyConfig(name = "TestExit"),
    startDate = windowStart.toString(),
    endDate = windowEnd.toString(),
    useUnderlyingAssets = false,
    costBps = 0.0,
    applyLiquidityFilter = applyLiquidityFilter,
  )

  private fun postBacktest(request: BacktestRequest): ResponseEntity<BacktestResponseDto> =
    restTemplate.exchange(
      "/api/backtest",
      HttpMethod.POST,
      jsonEntity(request),
      BacktestResponseDto::class.java,
    )
}
