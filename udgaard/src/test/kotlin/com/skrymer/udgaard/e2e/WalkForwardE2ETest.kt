package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.backtesting.dto.PredefinedStrategyConfig
import com.skrymer.udgaard.backtesting.dto.WalkForwardRequest
import com.skrymer.udgaard.backtesting.model.PositionSizingConfig
import com.skrymer.udgaard.backtesting.model.WalkForwardResult
import com.skrymer.udgaard.backtesting.service.sizer.AtrRiskSizerConfig
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
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
import java.time.LocalDate
import kotlin.test.assertTrue

private const val DELTA = 1e-9

/**
 * E2E tests for the walk-forward endpoint — validates month-based stepping,
 * randomSeed/positionSizing threading, validation rules, and year-based backward compat.
 *
 * Uses a 2-year fixture (2021-01-04 to 2022-12-30). Assertions deliberately avoid
 * pinning opaque numeric outputs; instead each test derives its expectations from
 * first principles (hand-computed window boundaries, weighted-average aggregation
 * applied to the returned per-window data, determinism under fixed seed).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WalkForwardE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var dsl: DSLContext

  private val fixtureStart = LocalDate.of(2021, 1, 4)
  private val fixtureEnd = LocalDate.of(2022, 12, 30)

  @BeforeAll
  fun setupTestData() {
    BacktestTestDataGenerator.populate(dsl, fixtureStart, fixtureEnd)
  }

  // ===== WINDOW GENERATION =====

  @Test
  fun `quarterly disjoint config produces windows with hand-computed boundaries`() {
    val request = baseRequest().copy(
      inSampleMonths = 6,
      outOfSampleMonths = 3,
      stepMonths = 3,
    )

    val body = postWalkForward(request).body!!

    val expected = computeExpectedWindows(
      startDate = fixtureStart,
      endDate = fixtureEnd,
      inSampleMonths = 6,
      outOfSampleMonths = 3,
      stepMonths = 3,
    )

    assertEquals(expected.size, body.windows.size, "window count mismatch")
    body.windows.zip(expected).forEachIndexed { i, (actual, exp) ->
      assertEquals(exp.isStart, actual.inSampleStart, "window $i: isStart")
      assertEquals(exp.isEnd, actual.inSampleEnd, "window $i: isEnd")
      assertEquals(exp.oosStart, actual.outOfSampleStart, "window $i: oosStart")
      assertEquals(exp.oosEnd, actual.outOfSampleEnd, "window $i: oosEnd")
    }

    // Disjoint OOS invariant: each OOS starts exactly one day after the previous OOS ended
    body.windows.zipWithNext().forEach { (prev, next) ->
      assertEquals(prev.outOfSampleEnd.plusDays(1), next.outOfSampleStart)
    }
  }

  @Test
  fun `year-based request produces identical windows and aggregates to month-based equivalent`() {
    val yearBased = baseRequest().copy(inSampleYears = 1, outOfSampleYears = 1, stepYears = 1)
    val monthBased = baseRequest().copy(inSampleMonths = 12, outOfSampleMonths = 12, stepMonths = 12)

    val yearBody = postWalkForward(yearBased).body!!
    val monthBody = postWalkForward(monthBased).body!!

    assertEquals(yearBody.windows.size, monthBody.windows.size)
    yearBody.windows.zip(monthBody.windows).forEach { (y, m) ->
      assertEquals(y.inSampleStart, m.inSampleStart)
      assertEquals(y.outOfSampleStart, m.outOfSampleStart)
      assertEquals(y.outOfSampleEnd, m.outOfSampleEnd)
    }
    assertEquals(yearBody.aggregateOosEdge, monthBody.aggregateOosEdge, DELTA)
    assertEquals(yearBody.walkForwardEfficiency, monthBody.walkForwardEfficiency, DELTA)
    assertEquals(yearBody.aggregateOosTrades, monthBody.aggregateOosTrades)
  }

  // ===== AGGREGATE CONSISTENCY =====

  @Test
  fun `aggregate OOS edge and WFE equal the weighted averages of per-window values`() {
    val request = baseRequest().copy(
      inSampleMonths = 6,
      outOfSampleMonths = 3,
      stepMonths = 3,
    )

    val body = postWalkForward(request).body!!

    // Recompute aggregates from the per-window data and assert equality — proves the aggregator
    // isn't double-counting or misweighting, independent of the underlying edge values.
    val totalIsTrades = body.windows.sumOf { it.inSampleTrades }
    val totalOosTrades = body.windows.sumOf { it.outOfSampleTrades }

    assertEquals(totalOosTrades, body.aggregateOosTrades, "aggregateOosTrades mismatch")

    if (totalOosTrades > 0) {
      val expectedOosEdge = body.windows.sumOf { it.outOfSampleEdge * it.outOfSampleTrades } / totalOosTrades
      assertEquals(expectedOosEdge, body.aggregateOosEdge, DELTA, "aggregateOosEdge != weighted mean")

      val expectedOosWr = body.windows.sumOf { it.outOfSampleWinRate * it.outOfSampleTrades } / totalOosTrades
      assertEquals(expectedOosWr, body.aggregateOosWinRate, DELTA, "aggregateOosWinRate != weighted mean")
    }

    if (totalIsTrades > 0 && totalOosTrades > 0) {
      val expectedIsEdge = body.windows.sumOf { it.inSampleEdge * it.inSampleTrades } / totalIsTrades
      val expectedOosEdge = body.windows.sumOf { it.outOfSampleEdge * it.outOfSampleTrades } / totalOosTrades
      val expectedWfe = if (expectedIsEdge != 0.0) expectedOosEdge / expectedIsEdge else 0.0
      assertEquals(expectedWfe, body.walkForwardEfficiency, DELTA, "WFE != OOS/IS weighted ratio")
    }
  }

  // ===== DETERMINISM =====

  @Test
  fun `same seed and config produces identical results across runs`() {
    val request = baseRequest().copy(
      inSampleMonths = 6,
      outOfSampleMonths = 3,
      stepMonths = 3,
      randomSeed = 42L,
      positionSizing = PositionSizingConfig(
        startingCapital = 100_000.0,
        sizer = AtrRiskSizerConfig(riskPercentage = 1.25, nAtr = 2.0),
        leverageRatio = 1.0,
      ),
    )

    val first = postWalkForward(request).body!!
    val second = postWalkForward(request).body!!

    assertEquals(first.windows.size, second.windows.size)
    assertEquals(first.aggregateOosTrades, second.aggregateOosTrades)
    assertEquals(first.aggregateOosEdge, second.aggregateOosEdge, DELTA)
    assertEquals(first.walkForwardEfficiency, second.walkForwardEfficiency, DELTA)
    first.windows.zip(second.windows).forEach { (a, b) ->
      assertEquals(a.outOfSampleTrades, b.outOfSampleTrades)
      assertEquals(a.outOfSampleEdge, b.outOfSampleEdge, DELTA)
      assertEquals(a.inSampleEdge, b.inSampleEdge, DELTA)
    }
  }

  // ===== POSITION SIZING THREADING =====

  @Test
  fun `positionSizing with tight capital takes fewer or equal OOS trades vs unlimited`() {
    val unlimited = baseRequest().copy(
      inSampleMonths = 6,
      outOfSampleMonths = 3,
      stepMonths = 3,
      randomSeed = 42L,
    )
    val constrained = unlimited.copy(
      positionSizing = PositionSizingConfig(
        startingCapital = 2_000.0, // tight
        sizer = AtrRiskSizerConfig(riskPercentage = 1.25, nAtr = 2.0),
        leverageRatio = 1.0,
      ),
    )

    val unlim = postWalkForward(unlimited).body!!
    val cons = postWalkForward(constrained).body!!

    val unlimTotal = unlim.windows.sumOf { it.outOfSampleTrades }
    val consTotal = cons.windows.sumOf { it.outOfSampleTrades }

    // Must have some baseline activity for the test to be meaningful
    assertTrue(unlimTotal > 0, "baseline must produce OOS trades on this fixture")
    // Capital-aware selection can only reject candidates, never add — monotonicity invariant
    assertTrue(
      consTotal <= unlimTotal,
      "capital-constrained walk-forward must not take more trades than unlimited ($consTotal > $unlimTotal)",
    )
  }

  @Test
  fun `positionSizing absent leaves aggregates unchanged vs repeat run without it`() {
    // Running the same unlimited request twice should yield identical aggregates.
    // This is a weaker form of determinism (no seed) but confirms that omitting
    // positionSizing doesn't introduce nondeterminism via null-path code.
    val request = baseRequest().copy(inSampleMonths = 6, outOfSampleMonths = 3, stepMonths = 3)

    val first = postWalkForward(request).body!!
    val second = postWalkForward(request).body!!

    assertEquals(first.windows.size, second.windows.size)
    assertEquals(first.aggregateOosTrades, second.aggregateOosTrades)
    assertEquals(first.aggregateOosEdge, second.aggregateOosEdge, DELTA)
  }

  // ===== VALIDATION =====

  @Test
  fun `overlapping OOS windows (stepMonths less than outOfSampleMonths) rejected with 400`() {
    val request = baseRequest().copy(
      inSampleMonths = 6,
      outOfSampleMonths = 12,
      stepMonths = 3, // would produce 75% overlap between consecutive OOS periods
    )
    val response = postWalkForwardExpectingError(request)
    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
  }

  @Test
  fun `zero stepMonths rejected with 400`() {
    val request = baseRequest().copy(inSampleMonths = 6, outOfSampleMonths = 3, stepMonths = 0)
    val response = postWalkForwardExpectingError(request)
    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
  }

  @Test
  fun `negative outOfSampleMonths rejected with 400`() {
    val request = baseRequest().copy(inSampleMonths = 6, outOfSampleMonths = -1, stepMonths = 3)
    val response = postWalkForwardExpectingError(request)
    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
  }

  // ===== HELPERS =====

  private data class ExpectedWindow(
    val isStart: LocalDate,
    val isEnd: LocalDate,
    val oosStart: LocalDate,
    val oosEnd: LocalDate,
  )

  /**
   * Independently computes the expected window boundaries given the same semantics
   * as WalkForwardService.generateWindows: IS window is inSampleMonths long starting
   * from isStart; OOS window is outOfSampleMonths long starting the day after IS ends;
   * emit only if OOS end is within endDate; advance isStart by stepMonths.
   */
  private fun computeExpectedWindows(
    startDate: LocalDate,
    endDate: LocalDate,
    inSampleMonths: Int,
    outOfSampleMonths: Int,
    stepMonths: Int,
  ): List<ExpectedWindow> {
    val windows = mutableListOf<ExpectedWindow>()
    var isStart = startDate
    while (true) {
      val isEnd = isStart.plusMonths(inSampleMonths.toLong())
      val oosStart = isEnd.plusDays(1)
      val oosEnd = oosStart.plusMonths(outOfSampleMonths.toLong()).minusDays(1)
      if (oosEnd.isAfter(endDate)) break
      windows.add(ExpectedWindow(isStart, isEnd, oosStart, oosEnd))
      isStart = isStart.plusMonths(stepMonths.toLong())
    }
    return windows
  }

  private fun baseRequest() = WalkForwardRequest(
    entryStrategy = PredefinedStrategyConfig(name = "TestEntry"),
    exitStrategy = PredefinedStrategyConfig(name = "TestExit"),
    stockSymbols = BacktestTestDataGenerator.ALL_SYMBOLS,
    startDate = fixtureStart.toString(),
    endDate = fixtureEnd.toString(),
    useUnderlyingAssets = false,
  )

  private fun postWalkForward(request: WalkForwardRequest): ResponseEntity<WalkForwardResult> =
    restTemplate.exchange(
      "/api/backtest/walk-forward",
      HttpMethod.POST,
      HttpEntity(request, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
      WalkForwardResult::class.java,
    )

  private fun postWalkForwardExpectingError(request: WalkForwardRequest): ResponseEntity<String> =
    restTemplate.exchange(
      "/api/backtest/walk-forward",
      HttpMethod.POST,
      HttpEntity(request, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
      String::class.java,
    )
}
