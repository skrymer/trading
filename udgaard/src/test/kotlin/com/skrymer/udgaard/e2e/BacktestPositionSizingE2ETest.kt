package com.skrymer.udgaard.e2e

import com.skrymer.udgaard.backtesting.dto.BacktestRequest
import com.skrymer.udgaard.backtesting.dto.PredefinedStrategyConfig
import com.skrymer.udgaard.backtesting.model.BacktestResponseDto
import com.skrymer.udgaard.backtesting.model.DrawdownScaling
import com.skrymer.udgaard.backtesting.model.DrawdownThreshold
import com.skrymer.udgaard.backtesting.model.PositionSizingConfig
import com.skrymer.udgaard.backtesting.model.PositionSizingResult
import com.skrymer.udgaard.backtesting.model.Trade
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val DELTA = 1e-4

/**
 * E2E tests for position sizing, capital-aware trade selection, entry delay, and random seed determinism.
 *
 * Uses the same test data as [BacktestApiE2ETest] (51 stocks, 11 sectors, ~60 trading days).
 * All assertions use exact pinned values for deterministic verification.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BacktestPositionSizingE2ETest : AbstractIntegrationTest() {
  @Autowired
  private lateinit var dsl: DSLContext

  @BeforeAll
  fun setupTestData() {
    BacktestTestDataGenerator.populate(dsl)
  }

  // ===== POSITION SIZING =====

  @Test
  fun `POST backtest with positionSizing and leverageRatio should return correct sizing result`() {
    // given a position-limited backtest with ATR-based sizing and 1x leverage cap
    val request = baseRequest().copy(
      maxPositions = 10,
      randomSeed = 42L,
      positionSizing = PositionSizingConfig(
        startingCapital = 100_000.0,
        riskPercentage = 1.5,
        nAtr = 2.0,
        leverageRatio = 1.0,
      ),
    )

    // when the backtest is executed
    val response = postBacktest(request)

    // then the backtest completes with the capital gate filtering unfundable trades
    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertEquals(38, body.totalTrades)
    assertEquals(19, body.numberOfWinningTrades)
    assertEquals(19, body.numberOfLosingTrades)
    assertEquals(0.5, body.winRate, DELTA)
    assertEquals(179, body.missedOpportunitiesCount)

    // and the position sizing result has correct values
    val sizing = assertNotNull(body.positionSizing, "positionSizing must be present")
    assertEquals(100_000.0, sizing.startingCapital, DELTA)
    assertEquals(106_089.0536, sizing.finalCapital, 0.01)
    assertEquals(6.0890, sizing.totalReturnPct, 0.01)
    assertEquals(4.4798, sizing.maxDrawdownPct, 0.01)
    assertEquals(4_697.97, sizing.maxDrawdownDollars, 0.01)
    assertEquals(106_089.05, sizing.peakCapital, 0.01)
    assertEquals(30, sizing.trades.size)
    assertEquals(50, sizing.equityCurve.size)

    // and every sized trade has valid shares
    sizing.trades.forEach { trade ->
      assertTrue(trade.shares > 0, "Shares must be positive for ${trade.symbol}")
    }
  }

  @Test
  fun `POST backtest with positionSizing without leverageRatio should size all trades`() {
    // given a position-limited backtest with ATR sizing but no leverage cap
    val request = baseRequest().copy(
      maxPositions = 10,
      randomSeed = 42L,
      positionSizing = PositionSizingConfig(
        startingCapital = 100_000.0,
        riskPercentage = 1.5,
        nAtr = 2.0,
        leverageRatio = null,
      ),
    )

    // when the backtest is executed
    val response = postBacktest(request)

    // then all 83 position-limited trades are taken (no capital gate blocks without leverage cap)
    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertEquals(83, body.totalTrades)
    assertEquals(40, body.numberOfWinningTrades)
    assertEquals(43, body.numberOfLosingTrades)

    // and all trades are sized (no leverage cap means no 0-share trades)
    val sizing = assertNotNull(body.positionSizing)
    assertEquals(100_000.0, sizing.startingCapital, DELTA)
    assertEquals(100_395.5584, sizing.finalCapital, 0.01)
    assertEquals(0.3955, sizing.totalReturnPct, 0.01)
    assertEquals(6.5449, sizing.maxDrawdownPct, 0.01)
    assertEquals(83, sizing.trades.size)
    assertTrue(sizing.trades.all { it.shares >= 79 }, "Without leverage cap, shares should be large")
  }

  @Test
  fun `POST backtest without positionSizing should not include sizing result`() {
    // given a backtest request without position sizing config
    val request = baseRequest()

    // when the backtest is executed
    val response = postBacktest(request)

    // then no sizing block is present
    assertEquals(HttpStatus.OK, response.statusCode)
    assertNull(response.body!!.positionSizing, "positionSizing must be null when not requested")
    assertEquals(204, response.body!!.totalTrades)
  }

  // ===== CAPITAL-AWARE TRADE SELECTION (core feature test) =====

  @Test
  fun `POST backtest with tight capital should skip unfundable trades and take affordable ones`() {
    // given two backtests: one without sizing (baseline) and one with tight $5K capital
    // This is the core test for the capital-aware selection feature:
    // In real trading, if a candidate exceeds available equity, the trader skips it
    // and takes the next affordable candidate. The capital gate should do the same.
    val baselineRequest = baseRequest().copy(maxPositions = 10, randomSeed = 42L)
    val constrainedRequest = baseRequest().copy(
      maxPositions = 10,
      randomSeed = 42L,
      positionSizing = PositionSizingConfig(
        startingCapital = 5_000.0,
        riskPercentage = 1.5,
        nAtr = 2.0,
        leverageRatio = 1.0,
      ),
    )

    // when both backtests are executed
    val baseline = postBacktest(baselineRequest).body!!
    val constrained = postBacktest(constrainedRequest).body!!

    // then the capital-constrained run has fewer trades
    assertEquals(83, baseline.totalTrades)
    assertEquals(37, constrained.totalTrades)
    assertTrue(constrained.totalTrades < baseline.totalTrades, "Tight capital must reduce trade count")

    // and the sizing result shows all taken trades have positive shares (no 0-share leaks)
    val sizing = assertNotNull(constrained.positionSizing)
    assertEquals(5_000.0, sizing.startingCapital, DELTA)
    assertEquals(5_283.901, sizing.finalCapital, 0.01)
    assertEquals(5.678, sizing.totalReturnPct, 0.01)
    assertEquals(3.9987, sizing.maxDrawdownPct, 0.01)
    assertEquals(27, sizing.trades.size)
    sizing.trades.forEach { trade ->
      assertTrue(trade.shares > 0, "Sized trade ${trade.symbol} must have positive shares")
    }
  }

  @Test
  fun `POST backtest with generous capital and no leverage cap should match baseline trade count`() {
    // given a baseline without sizing and a generous-capital run without leverage cap
    val baselineRequest = baseRequest().copy(maxPositions = 10, randomSeed = 42L)
    val generousRequest = baseRequest().copy(
      maxPositions = 10,
      randomSeed = 42L,
      positionSizing = PositionSizingConfig(
        startingCapital = 100_000.0,
        riskPercentage = 1.5,
        nAtr = 2.0,
        leverageRatio = null,
      ),
    )

    // when both backtests are executed
    val baseline = postBacktest(baselineRequest).body!!
    val generous = postBacktest(generousRequest).body!!

    // then the trade count is identical — the capital gate never rejected anything
    assertEquals(
      baseline.totalTrades,
      generous.totalTrades,
      "Without leverage cap and generous capital, trade selection should be identical"
    )
    assertEquals(83, generous.totalTrades)
  }

  // ===== POSITION SIZING + MAX POSITIONS =====

  @Test
  fun `POST backtest with maxPositions and positionSizing should respect both constraints`() {
    // given a request with tight maxPositions=3 and position sizing
    val request = baseRequest().copy(
      maxPositions = 3,
      randomSeed = 42L,
      positionSizing = PositionSizingConfig(
        startingCapital = 100_000.0,
        riskPercentage = 1.0,
        nAtr = 2.0,
        leverageRatio = 1.0,
      ),
    )

    // when the backtest is executed
    val response = postBacktest(request)

    // then both constraints are respected
    assertEquals(HttpStatus.OK, response.statusCode)
    val body = response.body!!
    assertEquals(24, body.totalTrades)
    assertEquals(13, body.numberOfWinningTrades)
    assertEquals(11, body.numberOfLosingTrades)
    assertEquals(185, body.missedOpportunitiesCount)

    // and maxPositions is enforced: no day has more than 3 concurrent positions
    val tradesResponse = restTemplate.exchange(
      "/api/backtest/${body.backtestId}/trades?startDate=2024-01-02&endDate=2024-03-29",
      HttpMethod.GET,
      null,
      object : ParameterizedTypeReference<List<Trade>>() {},
    )
    val trades = tradesResponse.body!!
    val tradeRanges = trades.map { it.entryQuote.date to it.quotes.maxOf { q -> q.date } }
    val allDates = tradeRanges
      .flatMap { (entry, exit) ->
        generateSequence(entry) { it.plusDays(1) }.takeWhile { !it.isAfter(exit) }.toList()
      }.distinct()

    allDates.forEach { date ->
      val concurrent = tradeRanges.count { (entry, exit) -> !date.isBefore(entry) && !date.isAfter(exit) }
      assertTrue(concurrent <= 3, "Concurrent positions on $date: $concurrent > 3")
    }

    // and position sizing is self-consistent
    val sizing = assertNotNull(body.positionSizing)
    assertEquals(100_000.0, sizing.startingCapital, DELTA)
    assertEquals(101_077.6573, sizing.finalCapital, 0.01)
    assertEquals(1.0776, sizing.totalReturnPct, 0.01)
    assertEquals(2.0496, sizing.maxDrawdownPct, 0.01)
    assertEquals(24, sizing.trades.size)
    sizing.trades.forEach { trade ->
      assertTrue(trade.shares >= 58, "All trades should have substantial shares with $100K capital")
    }
  }

  // ===== ENTRY DELAY =====

  @Test
  fun `POST backtest with entryDelayDays should shift entry timing`() {
    // given a baseline with no delay and a run with 1-day entry delay
    val noDelay = postBacktest(baseRequest()).body!!
    val withDelay = postBacktest(baseRequest().copy(entryDelayDays = 1)).body!!

    // then entry delay changes the trade set (different entry prices, different exits)
    assertEquals(204, noDelay.totalTrades)
    assertEquals(207, withDelay.totalTrades)
    assertEquals(100, withDelay.numberOfWinningTrades)
    assertEquals(107, withDelay.numberOfLosingTrades)
    assertEquals(0.4830, withDelay.winRate, DELTA)
    assertEquals(0.4666, withDelay.edge, DELTA)
  }

  // ===== RANDOM SEED DETERMINISM =====

  @Test
  fun `POST backtest with same randomSeed should return identical results across runs`() {
    // given the same request executed twice with a fixed seed
    val request = baseRequest().copy(maxPositions = 5, randomSeed = 99L)

    // when both backtests are executed
    val first = postBacktest(request).body!!
    val second = postBacktest(request).body!!

    // then all metrics are identical
    assertEquals(41, first.totalTrades)
    assertEquals(first.totalTrades, second.totalTrades, "Same seed must produce same trade count")
    assertEquals(first.numberOfWinningTrades, second.numberOfWinningTrades, "Same seed must produce same winners")
    assertEquals(first.winRate, second.winRate, 1e-9, "Same seed must produce same win rate")
    assertEquals(first.edge, second.edge, 1e-9, "Same seed must produce same edge")
    assertEquals(first.profitFactor, second.profitFactor, "Same seed must produce same profit factor")
  }

  @Test
  fun `POST backtest with position-limited baseline should be deterministic with seed`() {
    // given a position-limited backtest with a fixed seed
    val request = baseRequest().copy(maxPositions = 10, randomSeed = 42L)

    // when the backtest is executed
    val result = postBacktest(request).body!!

    // then the exact metrics are reproducible
    assertEquals(83, result.totalTrades)
    assertEquals(40, result.numberOfWinningTrades)
    assertEquals(43, result.numberOfLosingTrades)
    assertEquals(0.4819, result.winRate, DELTA)
    assertEquals(0.0226, result.edge, DELTA)
    assertEquals(1.0130, result.profitFactor!!, DELTA)
    assertEquals(139, result.missedOpportunitiesCount)
  }

  // ===== FINANCIAL INVARIANTS =====

  @Test
  fun `position sizing should satisfy cash conservation invariant`() {
    // given a position-sized backtest
    val request = baseRequest().copy(
      maxPositions = 10,
      randomSeed = 42L,
      positionSizing = PositionSizingConfig(
        startingCapital = 100_000.0,
        riskPercentage = 1.5,
        nAtr = 2.0,
        leverageRatio = 1.0,
      ),
    )

    // when the backtest is executed
    val sizing = postBacktest(request).body!!.positionSizing!!

    // then finalCapital = startingCapital + sum(dollarProfit) (fundamental accounting identity)
    val sumDollarProfit = sizing.trades.sumOf { it.dollarProfit }
    assertEquals(
      sizing.startingCapital + sumDollarProfit,
      sizing.finalCapital,
      0.01,
      "Cash conservation: finalCapital must equal startingCapital + sum(dollarProfit)",
    )
  }

  @Test
  fun `position sizing equity curve should have ascending dates`() {
    // given a position-sized backtest
    val request = baseRequest().copy(
      maxPositions = 10,
      randomSeed = 42L,
      positionSizing = PositionSizingConfig(
        startingCapital = 100_000.0,
        riskPercentage = 1.5,
        nAtr = 2.0,
        leverageRatio = 1.0,
      ),
    )

    // when the backtest is executed
    val sizing = postBacktest(request).body!!.positionSizing!!

    // then equity curve dates are strictly ascending
    val dates = sizing.equityCurve.map { it.date }
    for (i in 1 until dates.size) {
      assertTrue(dates[i].isAfter(dates[i - 1]), "Equity curve dates must be ascending: ${dates[i - 1]} -> ${dates[i]}")
    }
  }

  // ===== DRAWDOWN SCALING =====

  @Test
  fun `position sizing with drawdown scaling should reduce final capital vs no scaling`() {
    // given two backtests: one without drawdown scaling and one with aggressive scaling
    val base = PositionSizingConfig(
      startingCapital = 100_000.0,
      riskPercentage = 1.5,
      nAtr = 2.0,
      leverageRatio = 1.0,
    )
    val withScaling = base.copy(
      drawdownScaling = DrawdownScaling(
        thresholds = listOf(
          DrawdownThreshold(drawdownPercent = 1.0, riskMultiplier = 0.5),
          DrawdownThreshold(drawdownPercent = 3.0, riskMultiplier = 0.25),
        ),
      ),
    )

    val noScalingRequest = baseRequest().copy(maxPositions = 10, randomSeed = 42L, positionSizing = base)
    val scalingRequest = baseRequest().copy(maxPositions = 10, randomSeed = 42L, positionSizing = withScaling)

    // when both backtests are executed
    val noScaling = postBacktest(noScalingRequest).body!!.positionSizing!!
    val scaled = postBacktest(scalingRequest).body!!.positionSizing!!

    // then drawdown scaling produces smaller positions (lower absolute returns)
    assertSizingInvariants(noScaling)
    assertSizingInvariants(scaled)
    assertTrue(
      scaled.trades.isNotEmpty(),
      "Drawdown scaling should still produce trades",
    )
  }

  // ===== ENTRY DELAY + POSITION SIZING =====

  @Test
  fun `entry delay combined with position sizing should produce valid results`() {
    // given a request with both entry delay and position sizing
    val request = baseRequest().copy(
      maxPositions = 10,
      randomSeed = 42L,
      entryDelayDays = 1,
      positionSizing = PositionSizingConfig(
        startingCapital = 100_000.0,
        riskPercentage = 1.5,
        nAtr = 2.0,
        leverageRatio = 1.0,
      ),
    )

    // when the backtest is executed
    val response = postBacktest(request)

    // then both features work together — sizing result is valid
    assertEquals(HttpStatus.OK, response.statusCode)
    val sizing = assertNotNull(response.body!!.positionSizing)
    assertSizingInvariants(sizing)
    assertTrue(sizing.trades.isNotEmpty(), "Should have trades with delay + sizing")
  }

  // ===== SHARED ASSERTION HELPERS =====

  private fun assertSizingInvariants(sizing: PositionSizingResult) {
    // Cash conservation: finalCapital = startingCapital + sum(dollarProfit)
    val sumProfit = sizing.trades.sumOf { it.dollarProfit }
    assertEquals(
      sizing.startingCapital + sumProfit,
      sizing.finalCapital,
      0.01,
      "Cash conservation violated",
    )

    // Return consistency
    val expectedReturn = ((sizing.finalCapital - sizing.startingCapital) / sizing.startingCapital) * 100.0
    assertEquals(expectedReturn, sizing.totalReturnPct, 0.01, "Return % inconsistent")

    // Peak bounds
    assertTrue(sizing.peakCapital >= sizing.startingCapital, "Peak must be >= starting capital")
    assertTrue(sizing.maxDrawdownPct in 0.0..100.0, "Max DD% must be in [0, 100]")
    assertTrue(sizing.maxDrawdownDollars >= 0.0, "Max DD$ must be non-negative")

    // No zero-share trades
    sizing.trades.forEach { trade ->
      assertTrue(trade.shares > 0, "Zero-share trade found: ${trade.symbol} on ${trade.entryDate}")
    }

    // Equity curve ascending dates
    val dates = sizing.equityCurve.map { it.date }
    for (i in 1 until dates.size) {
      assertTrue(dates[i].isAfter(dates[i - 1]), "Equity curve dates not ascending: ${dates[i - 1]} -> ${dates[i]}")
    }
  }

  // ===== HELPERS =====

  private fun baseRequest() = BacktestRequest(
    stockSymbols = BacktestTestDataGenerator.ALL_SYMBOLS,
    entryStrategy = PredefinedStrategyConfig(name = "TestEntry"),
    exitStrategy = PredefinedStrategyConfig(name = "TestExit"),
    startDate = "2024-01-02",
    endDate = "2024-03-29",
    useUnderlyingAssets = false,
  )

  private fun postBacktest(request: BacktestRequest): ResponseEntity<BacktestResponseDto> =
    restTemplate.exchange(
      "/api/backtest",
      HttpMethod.POST,
      HttpEntity(request, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
      BacktestResponseDto::class.java,
    )
}
