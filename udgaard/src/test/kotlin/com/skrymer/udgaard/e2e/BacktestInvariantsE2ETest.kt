package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.backtesting.dto.BacktestRequest
import com.skrymer.udgaard.backtesting.dto.PredefinedStrategyConfig
import com.skrymer.udgaard.backtesting.dto.WalkForwardRequest
import com.skrymer.udgaard.backtesting.model.BacktestResponseDto
import com.skrymer.udgaard.backtesting.model.PositionSizingConfig
import com.skrymer.udgaard.backtesting.model.Trade
import com.skrymer.udgaard.backtesting.model.WalkForwardResult
import com.skrymer.udgaard.backtesting.service.sizer.AtrRiskSizerConfig
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.time.LocalDate
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Engine verification tests — asserts invariants the backtest output MUST satisfy
 * regardless of strategy, seed, or fixture. Failures indicate engine bugs, not data bugs.
 *
 * Three classes of invariants, each mapped to a concrete failure mode:
 * 1. **Per-trade arithmetic & ordering** — catches entry/exit date miscalculation, profit
 *    math drift, duplicate-entry bugs. (Covers #1 from the verification menu.)
 * 2. **Leverage cap at entry decisions** — under leverageRatio=1.0 every sized entry must
 *    consume ≤ available equity at decision time. A violation would mean the capital-aware
 *    selector silently over-deployed. (Covers #5.)
 * 3. **Walk-forward sector-ranker derivation** — WF's `derivedSectorRanking` per window must
 *    exactly equal the ranking produced by a standalone backtest over the same IS range.
 *    A mismatch would prove IS→OOS leak or a stale ranker being reused across windows.
 *    (Covers #4 — the highest-consequence class of bug.)
 *
 * Fixtures: default 3-month fixture (51 stocks × ~60 days) for single-run tests;
 * 2-year fixture for the walk-forward assertion, which needs multiple windows.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BacktestInvariantsE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var dsl: DSLContext

  private val wfFixtureStart = LocalDate.of(2021, 1, 4)
  private val wfFixtureEnd = LocalDate.of(2022, 12, 30)

  @BeforeAll
  fun setupTestData() {
    BacktestTestDataGenerator.populate(dsl)
    BacktestTestDataGenerator.populate(dsl, wfFixtureStart, wfFixtureEnd)
  }

  // ===== #1 TRADE-LEVEL MATH INVARIANTS =====

  @Test
  fun `every trade has entry date before exit date and profit matches exit close minus entry close`() {
    val body = postBacktest(defaultBacktestRequest()).body!!
    val trades = fetchAllTrades(body.backtestId, "2024-01-02", "2024-03-29")

    assertTrue(trades.isNotEmpty(), "default backtest must produce trades for this invariant test to be meaningful")

    trades.forEach { trade ->
      val entryDate = trade.entryQuote.date
      val exitQuote = trade.quotes.lastOrNull() ?: error("trade for ${trade.stockSymbol} has no quotes")
      val exitDate = exitQuote.date

      assertTrue(entryDate.isBefore(exitDate), "${trade.stockSymbol}: entry $entryDate must precede exit $exitDate")

      // Engine sets profit = exitPrice - entryClose (BacktestService.kt:210); CompositeExitStrategy.exitPrice
      // returns `quote.closePrice` when >= 1.0 (else falls back to the previous close for penny edge cases).
      // For normal-price test data the simpler predicate always holds.
      val expectedProfit = exitQuote.closePrice - trade.entryQuote.closePrice
      assertEquals(
        expectedProfit,
        trade.profit,
        1e-6,
        "${trade.stockSymbol} ($entryDate..$exitDate): profit ${trade.profit} != exitClose-entryClose $expectedProfit",
      )

      // profitPercentage is a computed property; asserting it confirms DTO serialization round-trips correctly.
      val expectedPct = (trade.profit / trade.entryQuote.closePrice) * 100.0
      assertEquals(expectedPct, trade.profitPercentage, 1e-9, "${trade.stockSymbol}: profitPercentage drift")
    }
  }

  @Test
  fun `no two trades share the same (symbol, entryDate) and every entry date is within the request range`() {
    val request = defaultBacktestRequest()
    val startDate = requireNotNull(request.startDate)
    val endDate = requireNotNull(request.endDate)
    val body = postBacktest(request).body!!
    val trades = fetchAllTrades(body.backtestId, startDate, endDate)

    val rangeStart = LocalDate.parse(startDate)
    val rangeEnd = LocalDate.parse(endDate)

    val seen = mutableSetOf<Pair<String, LocalDate>>()
    trades.forEach { trade ->
      val key = trade.stockSymbol to trade.entryQuote.date
      assertTrue(
        seen.add(key),
        "duplicate (symbol, entryDate) pair $key — the engine should never open two simultaneous positions in the same symbol",
      )
      assertTrue(
        !trade.entryQuote.date.isBefore(rangeStart) && !trade.entryQuote.date.isAfter(rangeEnd),
        "${trade.stockSymbol} entry ${trade.entryQuote.date} falls outside the request range [$rangeStart, $rangeEnd]",
      )
    }
  }

  @Test
  fun `every stop-loss exit satisfies the close-below-stop predicate`() {
    // TestExit uses stopLoss(2.0) — see TestExitStrategy.kt:24.
    val atrMultiplier = 2.0
    val body = postBacktest(defaultBacktestRequest()).body!!
    val trades = fetchAllTrades(body.backtestId, "2024-01-02", "2024-03-29")

    val stopLossTrades = trades.filter { it.exitReason.startsWith("Stop loss triggered") }
    // Not asserting count > 0 — the fixture may not produce any stop-loss trades in every run;
    // the invariant only bites if such a trade exists.

    stopLossTrades.forEach { trade ->
      val exitQuote = trade.quotes.last()
      val stopLevel = trade.entryQuote.closePrice - atrMultiplier * trade.entryQuote.atr
      assertTrue(
        exitQuote.closePrice < stopLevel,
        "${trade.stockSymbol} stop-loss exit ${exitQuote.date}: close ${exitQuote.closePrice} " +
          "must be below stopLevel $stopLevel (entryClose=${trade.entryQuote.closePrice}, entryAtr=${trade.entryQuote.atr})",
      )
    }
  }

  // ===== #5 LEVERAGE CAP INVARIANT =====

  @Test
  fun `sized trade notional at entry never exceeds leverage cap`() {
    val leverageRatio = 1.0
    val request = defaultBacktestRequest().copy(
      maxPositions = 10,
      randomSeed = 42L,
      positionSizing = PositionSizingConfig(
        startingCapital = 100_000.0,
        sizer = AtrRiskSizerConfig(riskPercentage = 1.5, nAtr = 2.0),
        leverageRatio = leverageRatio,
      ),
    )
    val body = postBacktest(request).body!!
    val sizing = assertNotNull(body.positionSizing, "positionSizing must be present when requested")

    assertTrue(sizing.trades.isNotEmpty(), "test config must produce at least one sized trade")

    // The leverage cap is enforced at the point of decision: the new position's notional plus
    // existing open notional must not exceed leverageRatio × equity. We can't reconstruct the
    // *exact* open-notional-at-entry from the sized-trade list alone, so the weaker but still
    // meaningful assertion is: each trade individually must fit under the cap relative to the
    // portfolio value at its entry (the engine's leverageRatio × equity upper bound).
    // Stronger check would need the missed-trades endpoint's entryContext; kept simple here.
    sizing.trades.forEach { t ->
      val cap = leverageRatio * t.portfolioValueAtEntry
      val notional = t.shares * t.entryPrice
      assertTrue(
        notional <= cap + 1e-4,
        "${t.symbol} on ${t.entryDate}: notional=$notional exceeds leverage cap $cap " +
          "(shares=${t.shares}, entryPrice=${t.entryPrice}, portfolioValue=${t.portfolioValueAtEntry})",
      )
    }
  }

  // ===== #4 WALK-FORWARD SECTOR-RANKER DERIVATION =====

  @Test
  fun `walk-forward derivedSectorRanking for each window matches ranking from a standalone IS backtest`() {
    // Run WF over the 2-year fixture with a short IS so we have several windows.
    val wfRequest = WalkForwardRequest(
      entryStrategy = PredefinedStrategyConfig(name = "TestEntry"),
      exitStrategy = PredefinedStrategyConfig(name = "TestExit"),
      stockSymbols = BacktestTestDataGenerator.ALL_SYMBOLS,
      startDate = wfFixtureStart.toString(),
      endDate = wfFixtureEnd.toString(),
      useUnderlyingAssets = false,
      inSampleMonths = 6,
      outOfSampleMonths = 3,
      stepMonths = 3,
    )
    val wf = postWalkForward(wfRequest).body!!
    assertTrue(wf.windows.isNotEmpty(), "WF must produce at least one window for this test")

    // For each window, run a standalone backtest over its IS range and compare rankings.
    wf.windows.forEach { window ->
      val isBody = postBacktest(
        BacktestRequest(
          stockSymbols = BacktestTestDataGenerator.ALL_SYMBOLS,
          entryStrategy = PredefinedStrategyConfig(name = "TestEntry"),
          exitStrategy = PredefinedStrategyConfig(name = "TestExit"),
          startDate = window.inSampleStart.toString(),
          endDate = window.inSampleEnd.toString(),
          useUnderlyingAssets = false,
        ),
      ).body!!

      // SectorEdgeRanker (WalkForwardService.kt:96-109) builds the ranking as:
      // sectorPerformance sorted by avgProfit descending, mapped to sector symbols.
      val expectedRanking = isBody.sectorPerformance
        .sortedByDescending { it.avgProfit }
        .map { it.sector }

      assertEquals(
        expectedRanking,
        window.derivedSectorRanking,
        "window ${window.inSampleStart}..${window.inSampleEnd}: " +
          "derivedSectorRanking ${window.derivedSectorRanking} != IS-derived ranking $expectedRanking",
      )
    }

    // Cross-window check: rankings must not be byte-identical across all windows — otherwise
    // the ranker would clearly be using data independent of IS (e.g., a hardcoded list).
    val distinctRankings = wf.windows.map { it.derivedSectorRanking }.toSet()
    assertTrue(
      distinctRankings.size > 1 || wf.windows.size <= 1,
      "derivedSectorRanking is identical across all ${wf.windows.size} windows — suggests the ranker is not derived from IS data",
    )
  }

  // ===== HELPERS =====

  private fun defaultBacktestRequest() = BacktestRequest(
    stockSymbols = BacktestTestDataGenerator.ALL_SYMBOLS,
    entryStrategy = PredefinedStrategyConfig(name = "TestEntry"),
    exitStrategy = PredefinedStrategyConfig(name = "TestExit"),
    startDate = "2024-01-02",
    endDate = "2024-03-29",
    useUnderlyingAssets = false,
  )

  private fun postBacktest(request: BacktestRequest): ResponseEntity<BacktestResponseDto> =
    restTemplate
      .exchange(
        "/api/backtest",
        HttpMethod.POST,
        HttpEntity(request, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
        BacktestResponseDto::class.java,
      ).also { assertEquals(HttpStatus.OK, it.statusCode, "backtest endpoint must return 200") }

  private fun postWalkForward(request: WalkForwardRequest): ResponseEntity<WalkForwardResult> =
    restTemplate
      .exchange(
        "/api/backtest/walk-forward",
        HttpMethod.POST,
        HttpEntity(request, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
        WalkForwardResult::class.java,
      ).also { assertEquals(HttpStatus.OK, it.statusCode, "walk-forward endpoint must return 200") }

  private fun fetchAllTrades(backtestId: String, startDate: String, endDate: String): List<Trade> {
    val response: ResponseEntity<List<Trade>> = restTemplate.exchange(
      "/api/backtest/$backtestId/trades?startDate=$startDate&endDate=$endDate",
      HttpMethod.GET,
      null,
      object : ParameterizedTypeReference<List<Trade>>() {},
    )
    assertEquals(HttpStatus.OK, response.statusCode, "trades endpoint must return 200")
    return response.body ?: emptyList()
  }
}
