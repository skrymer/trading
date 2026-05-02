package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.PortfolioEquityPoint
import com.skrymer.udgaard.backtesting.model.PositionSizingResult
import com.skrymer.udgaard.backtesting.model.Trade
import com.skrymer.udgaard.data.model.StockQuote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

private const val EPSILON = 0.01
private const val LOOSE_EPSILON = 0.5
private val START = LocalDate.of(2023, 1, 1)

class RiskMetricsServiceTest {
  private val service = RiskMetricsService()

  // ===== EQUITY-CURVE-DERIVED METRICS =====

  @Test
  fun `flat curve yields null sharpe and zero CAGR`() {
    // Given a flat 252-day equity curve (no movement at all)
    val curve = (0..252).map { i -> point(START.plusDays(i.toLong()), 100_000.0) }

    // When
    val analysis = service.compute(emptyList(), curve, sizing(curve), benchmarkQuotes = null)

    // Then sharpe is null (zero stddev), CAGR is 0
    assertNull(analysis.riskMetrics.sharpeRatio)
    assertEquals(0.0, requireNotNull(analysis.cagr), EPSILON)
  }

  @Test
  fun `constant positive daily return yields hand-computed Sharpe`() {
    // Given a constant +0.04% per trading day across 252 days (~10.6% annualized arith)
    // Sharpe = mean / stddev * sqrt(252). With zero stddev (constant return) Sharpe is null.
    // So we add a tiny perturbation to give stddev > 0 and hand-compute the resulting Sharpe.
    val daily = listOf(0.0004, 0.0005, 0.0003).repeat(84) // 252 days, mean ~0.0004
    val curve = buildCurveFromDailyReturns(START, 100_000.0, daily)

    // When
    val analysis = service.compute(emptyList(), curve, sizing(curve), benchmarkQuotes = null)

    // Then Sharpe is finite and positive (constant-ish positive returns → positive Sharpe)
    val sharpe = requireNotNull(analysis.riskMetrics.sharpeRatio)
    assertTrue(sharpe > 0.0, "expected positive Sharpe for positive-drift curve, got $sharpe")
  }

  @Test
  fun `negative drift yields negative CAGR`() {
    // Given a curve declining ~ −0.03% per day
    val daily = List(252) { -0.0003 }
    val curve = buildCurveFromDailyReturns(START, 100_000.0, daily)

    // When
    val analysis = service.compute(emptyList(), curve, sizing(curve), benchmarkQuotes = null)

    // Then CAGR is negative
    val cagr = requireNotNull(analysis.cagr)
    assertTrue(cagr < 0.0, "expected negative CAGR, got $cagr")
  }

  @Test
  fun `fewer than 2 equity points yields null Sharpe Sortino CAGR`() {
    // Given a single equity point
    val curve = listOf(point(START, 100_000.0))

    // When
    val analysis = service.compute(emptyList(), curve, sizing(curve), benchmarkQuotes = null)

    // Then everything is null
    assertNull(analysis.riskMetrics.sharpeRatio)
    assertNull(analysis.riskMetrics.sortinoRatio)
    assertNull(analysis.cagr)
  }

  @Test
  fun `Sortino with no negative returns yields null`() {
    // Given uniformly positive daily returns
    val daily = List(252) { 0.001 }
    val curve = buildCurveFromDailyReturns(START, 100_000.0, daily)

    // When
    val analysis = service.compute(emptyList(), curve, sizing(curve), benchmarkQuotes = null)

    // Then Sortino is null (zero downside deviation)
    assertNull(analysis.riskMetrics.sortinoRatio)
  }

  @Test
  fun `CAGR uses calendar-day annualization not trading-day`() {
    // Given a 1-calendar-year curve with exact 10% growth
    val curve = listOf(
      point(START, 100_000.0),
      point(START.plusDays(365), 110_000.0),
    )

    // When
    val analysis = service.compute(emptyList(), curve, sizing(curve), benchmarkQuotes = null)

    // Then CAGR ≈ 10% (calendar-day uses 365.25 base, so close to 10 not exactly)
    val cagr = requireNotNull(analysis.cagr)
    assertEquals(10.0, cagr, LOOSE_EPSILON)
  }

  @Test
  fun `riskFreeRatePct of 4 percent reduces Sharpe vs RF zero`() {
    // Given the same curve evaluated with RF=0 and RF=4%
    val daily = listOf(0.0005, 0.0003, 0.0007).repeat(84) // 252 days
    val curve = buildCurveFromDailyReturns(START, 100_000.0, daily)

    // When
    val rfZero = service.compute(emptyList(), curve, sizing(curve), benchmarkQuotes = null, riskFreeRatePct = 0.0)
    val rfFour = service.compute(emptyList(), curve, sizing(curve), benchmarkQuotes = null, riskFreeRatePct = 4.0)

    // Then non-zero RF lowers Sharpe (excess return is smaller)
    val sharpeZero = requireNotNull(rfZero.riskMetrics.sharpeRatio)
    val sharpeFour = requireNotNull(rfFour.riskMetrics.sharpeRatio)
    assertTrue(sharpeFour < sharpeZero, "expected RF=4 Sharpe ($sharpeFour) < RF=0 Sharpe ($sharpeZero)")
  }

  @Test
  fun `Sortino with non-zero MAR treats sub-MAR returns as downside`() {
    // Given a curve with all-positive small daily returns and MAR set above the daily return
    val daily = List(252) { 0.0001 } // 0.01% per day
    val curve = buildCurveFromDailyReturns(START, 100_000.0, daily)

    // When MAR threshold of 5% (above what the strategy returns annualized)
    val analysis = service.compute(emptyList(), curve, sizing(curve), benchmarkQuotes = null, riskFreeRatePct = 5.0)

    // Then Sortino is non-null and (since the strategy underperforms MAR every day) is negative
    val sortino = requireNotNull(analysis.riskMetrics.sortinoRatio)
    assertTrue(sortino < 0.0, "expected negative Sortino when strategy underperforms MAR, got $sortino")
  }

  // ===== CALMAR (FIXED FORMULA) =====

  @Test
  fun `calmar uses CAGR not totalReturn`() {
    // Given a curve with known CAGR and a known maxDrawdown
    val curve = listOf(
      point(START, 100_000.0),
      point(START.plusDays(365), 110_000.0), // CAGR ≈ 10%
    )
    // Hand-supply maxDrawdownPct = 5
    val sizing = sizing(curve, totalReturnPct = 10.0, maxDrawdownPct = 5.0)

    // When
    val analysis = service.compute(emptyList(), curve, sizing, benchmarkQuotes = null)

    // Then Calmar = CAGR / |maxDrawdownPct| ≈ 10/5 = 2
    val calmar = requireNotNull(analysis.riskMetrics.calmarRatio)
    assertEquals(2.0, calmar, LOOSE_EPSILON)
  }

  @Test
  fun `calmar is null when maxDrawdownPct is zero`() {
    // Given a curve that never drew down
    val curve = listOf(
      point(START, 100_000.0),
      point(START.plusDays(365), 110_000.0),
    )
    val sizing = sizing(curve, totalReturnPct = 10.0, maxDrawdownPct = 0.0)

    // When
    val analysis = service.compute(emptyList(), curve, sizing, benchmarkQuotes = null)

    // Then Calmar is null (no drawdown to divide by)
    assertNull(analysis.riskMetrics.calmarRatio)
  }

  // ===== SQN / TAILRATIO (PORTED FROM BacktestReport) =====

  @Test
  fun `sqn matches sqrt N times mean over stddev formula`() {
    // Given trades with known per-trade returns
    val trades = listOf(
      tradeWithProfitPct(2.0),
      tradeWithProfitPct(-1.0),
      tradeWithProfitPct(3.0),
      tradeWithProfitPct(-1.0),
      tradeWithProfitPct(2.0),
    )
    val curve = listOf(point(START, 100_000.0), point(START.plusDays(365), 110_000.0))

    // When
    val analysis = service.compute(trades, curve, sizing(curve), benchmarkQuotes = null)

    // Then SQN = sqrt(5) * mean / stddev
    val sqn = requireNotNull(analysis.riskMetrics.sqn)
    val mean = trades.map { it.profitPercentage }.average()
    val variance = trades
      .map {
        val d = it.profitPercentage - mean
        d * d
      }.average()
    val expectedSqn = kotlin.math.sqrt(5.0) * mean / kotlin.math.sqrt(variance)
    assertEquals(expectedSqn, sqn, EPSILON)
  }

  @Test
  fun `tailRatio returns null when fewer than 20 trades`() {
    // Given 15 trades (below the n=20 statistical-meaningfulness floor)
    val trades = (1..15).map { tradeWithProfitPct(it.toDouble()) }
    val curve = listOf(point(START, 100_000.0), point(START.plusDays(365), 110_000.0))

    // When
    val analysis = service.compute(trades, curve, sizing(curve), benchmarkQuotes = null)

    // Then tailRatio is null (gated by MIN_TRADES_FOR_TAIL_RATIO)
    assertNull(analysis.riskMetrics.tailRatio)
  }

  @Test
  fun `tailRatio computes when at least 20 trades`() {
    // Given 30 trades with mixed signs
    val trades = (1..15).map { tradeWithProfitPct(it.toDouble()) } +
      (1..15).map { tradeWithProfitPct(-it.toDouble()) }
    val curve = listOf(point(START, 100_000.0), point(START.plusDays(365), 110_000.0))

    // When
    val analysis = service.compute(trades, curve, sizing(curve), benchmarkQuotes = null)

    // Then tailRatio is non-null and positive
    val tail = requireNotNull(analysis.riskMetrics.tailRatio)
    assertTrue(tail > 0.0)
  }

  // ===== BENCHMARK COMPARISON =====

  @Test
  fun `null benchmarkQuotes yields null benchmarkComparison`() {
    // Given a healthy equity curve but no benchmark data
    val curve = buildCurveFromDailyReturns(START, 100_000.0, List(80) { 0.0005 })

    // When
    val analysis = service.compute(emptyList(), curve, sizing(curve), benchmarkQuotes = null)

    // Then benchmarkComparison is null
    assertNull(analysis.benchmarkComparison)
  }

  @Test
  fun `benchmark overlap below 60 days yields null benchmark comparison`() {
    // Given a 50-day equity curve and matching benchmark quotes
    val curve = buildCurveFromDailyReturns(START, 100_000.0, List(50) { 0.0005 })
    val benchmarkQuotes = curve.map { quote(it.date, 100.0 + (it.portfolioValue - 100_000.0) / 1000) }

    // When
    val analysis = service.compute(emptyList(), curve, sizing(curve), benchmarkQuotes = benchmarkQuotes)

    // Then benchmark comparison is null (overlap < 60)
    val benchmark = requireNotNull(analysis.benchmarkComparison)
    assertEquals("SPY", benchmark.benchmarkSymbol)
    assertNull(benchmark.correlation, "correlation should be null when overlap < 60")
    assertNull(benchmark.beta)
    assertNull(benchmark.activeReturnVsBenchmark)
  }

  @Test
  fun `strategy and benchmark moving identically yields correlation near 1`() {
    // Given strategy returns equal to benchmark returns over 80 days
    val daily = List(80) { 0.0005 + (it % 5 - 2) * 0.0002 } // varied so variance is non-zero
    val curve = buildCurveFromDailyReturns(START, 100_000.0, daily)
    val benchmarkPriceSeries = buildPriceSeriesFromDailyReturns(100.0, daily)
    val benchmarkQuotes = benchmarkPriceSeries.mapIndexed { i, price ->
      quote(START.plusDays(i.toLong()), price)
    }

    // When
    val analysis = service.compute(emptyList(), curve, sizing(curve), benchmarkQuotes = benchmarkQuotes)

    // Then correlation ≈ 1.0, beta ≈ 1.0
    val benchmark = requireNotNull(analysis.benchmarkComparison)
    assertEquals(1.0, requireNotNull(benchmark.correlation), EPSILON)
    assertEquals(1.0, requireNotNull(benchmark.beta), EPSILON)
  }

  @Test
  fun `inverse-correlated returns yields correlation near minus one`() {
    // Given strategy returns inverse to benchmark returns over 80 days
    val daily = List(80) { 0.0005 + (it % 5 - 2) * 0.0002 }
    val inverseDaily = daily.map { -it }
    val curve = buildCurveFromDailyReturns(START, 100_000.0, daily)
    val benchmarkPriceSeries = buildPriceSeriesFromDailyReturns(100.0, inverseDaily)
    val benchmarkQuotes = benchmarkPriceSeries.mapIndexed { i, price ->
      quote(START.plusDays(i.toLong()), price)
    }

    // When
    val analysis = service.compute(emptyList(), curve, sizing(curve), benchmarkQuotes = benchmarkQuotes)

    // Then correlation ≈ -1.0
    val benchmark = requireNotNull(analysis.benchmarkComparison)
    assertEquals(-1.0, requireNotNull(benchmark.correlation), EPSILON)
  }

  @Test
  fun `zero-variance benchmark yields null comparison`() {
    // Given a flat benchmark and a varying strategy
    val daily = List(80) { 0.0005 + (it % 5 - 2) * 0.0002 }
    val curve = buildCurveFromDailyReturns(START, 100_000.0, daily)
    val benchmarkQuotes = (0..80).map { quote(START.plusDays(it.toLong()), 100.0) } // constant price

    // When
    val analysis = service.compute(emptyList(), curve, sizing(curve), benchmarkQuotes = benchmarkQuotes)

    // Then benchmark fields are null (variance == 0)
    val benchmark = requireNotNull(analysis.benchmarkComparison)
    assertNull(benchmark.correlation)
    assertNull(benchmark.beta)
  }

  @Test
  fun `benchmarkSymbol propagates to result`() {
    // Given a different benchmark symbol passed explicitly
    val daily = List(80) { 0.0005 + (it % 5 - 2) * 0.0002 }
    val curve = buildCurveFromDailyReturns(START, 100_000.0, daily)
    val benchmarkQuotes = buildPriceSeriesFromDailyReturns(100.0, daily).mapIndexed { i, p ->
      quote(START.plusDays(i.toLong()), p)
    }

    // When
    val analysis = service.compute(emptyList(), curve, sizing(curve), benchmarkQuotes, benchmarkSymbol = "QQQ")

    // Then symbol propagates
    assertEquals("QQQ", analysis.benchmarkComparison?.benchmarkSymbol)
  }

  // ===== DRAWDOWN EPISODES (NEW STATE MACHINE) =====

  @Test
  fun `monotonically rising curve yields no drawdown episodes`() {
    // Given a curve that only goes up
    val curve = (0..50).map { point(START.plusDays(it.toLong()), 100_000.0 + it * 100.0) }

    // When
    val analysis = service.compute(emptyList(), curve, sizing(curve), benchmarkQuotes = null)

    // Then no episodes
    assertEquals(0, analysis.drawdownEpisodes.size)
  }

  @Test
  fun `single V-shape produces one episode with new-ATH recovery`() {
    // Given peak at day 0 (100k), trough at day 5 (90k), full recovery at day 10 (101k)
    val curve = listOf(
      point(START, 100_000.0),
      point(START.plusDays(1), 99_000.0),
      point(START.plusDays(2), 95_000.0),
      point(START.plusDays(3), 92_000.0),
      point(START.plusDays(4), 90_000.0),
      point(START.plusDays(5), 91_000.0),
      point(START.plusDays(6), 95_000.0),
      point(START.plusDays(7), 99_000.0),
      point(START.plusDays(8), 100_500.0),
      point(START.plusDays(9), 101_000.0), // new ATH closes episode
    )

    // When
    val analysis = service.compute(emptyList(), curve, sizing(curve), benchmarkQuotes = null)

    // Then exactly 1 episode with hand-computed values
    assertEquals(1, analysis.drawdownEpisodes.size)
    val ep = analysis.drawdownEpisodes[0]
    assertEquals(START, ep.peakDate)
    assertEquals(START.plusDays(4), ep.troughDate)
    // Day 8 is the FIRST new ATH (100_500 > 100_000), so it closes the episode — not day 9.
    assertEquals(START.plusDays(8), ep.recoveryDate)
    assertEquals(10.0, ep.maxDrawdownPct, EPSILON) // (100k − 90k) / 100k = 10%
  }

  @Test
  fun `noise-floor recovery within open episode does NOT close it`() {
    // Given peak 100k → dip to 99k (1% DD opens episode) → recovery to 99.7 (0.3% DD, NOT a close)
    // → second deeper dip to 85k → recovery to new ATH 101k
    val curve = listOf(
      point(START, 100_000.0),
      point(START.plusDays(1), 99_000.0), // -1% DD opens
      point(START.plusDays(2), 99_700.0), // -0.3% DD — below 0.5% noise; should NOT close
      point(START.plusDays(3), 95_000.0), // deeper
      point(START.plusDays(4), 85_000.0), // deepest
      point(START.plusDays(5), 99_000.0),
      point(START.plusDays(6), 101_000.0), // new ATH closes
    )

    // When
    val analysis = service.compute(emptyList(), curve, sizing(curve), benchmarkQuotes = null)

    // Then ONE episode, with trough at 85k (not split into two episodes)
    assertEquals(1, analysis.drawdownEpisodes.size)
    val ep = analysis.drawdownEpisodes[0]
    assertEquals(START.plusDays(4), ep.troughDate)
    assertEquals(15.0, ep.maxDrawdownPct, EPSILON)
  }

  @Test
  fun `unrecovered drawdown at series end yields null recovery fields`() {
    // Given a curve that drops and never recovers
    val curve = listOf(
      point(START, 100_000.0),
      point(START.plusDays(5), 80_000.0),
      point(START.plusDays(10), 75_000.0),
    )

    // When
    val analysis = service.compute(emptyList(), curve, sizing(curve), benchmarkQuotes = null)

    // Then exactly 1 episode with null recovery fields
    assertEquals(1, analysis.drawdownEpisodes.size)
    val ep = analysis.drawdownEpisodes[0]
    assertNull(ep.recoveryDate)
    assertNull(ep.recoveryDays)
    assertNull(ep.totalDays)
    assertEquals(25.0, ep.maxDrawdownPct, EPSILON)
  }

  @Test
  fun `two distinct drawdowns separated by new ATH yield two episodes sorted deepest first`() {
    // Given two V-shapes with a clean ATH between them
    val curve = listOf(
      point(START, 100_000.0),
      point(START.plusDays(1), 95_000.0), // shallow dip 5%
      point(START.plusDays(2), 102_000.0), // new ATH closes episode 1
      point(START.plusDays(3), 80_000.0), // deep dip 21.5%
      point(START.plusDays(4), 105_000.0), // new ATH closes episode 2
    )

    // When
    val analysis = service.compute(emptyList(), curve, sizing(curve), benchmarkQuotes = null)

    // Then 2 episodes, sorted deepest first
    assertEquals(2, analysis.drawdownEpisodes.size)
    assertTrue(analysis.drawdownEpisodes[0].maxDrawdownPct >= analysis.drawdownEpisodes[1].maxDrawdownPct)
    assertTrue(analysis.drawdownEpisodes[0].maxDrawdownPct > 20.0)
  }

  @Test
  fun `noise below half percent does not open an episode`() {
    // Given small wiggles below 0.5%
    val curve = listOf(
      point(START, 100_000.0),
      point(START.plusDays(1), 99_700.0), // -0.3% DD — below noise floor
      point(START.plusDays(2), 99_800.0),
      point(START.plusDays(3), 100_200.0), // new ATH
      point(START.plusDays(4), 99_900.0), // -0.3% DD relative to 100,200
    )

    // When
    val analysis = service.compute(emptyList(), curve, sizing(curve), benchmarkQuotes = null)

    // Then no episodes opened
    assertEquals(0, analysis.drawdownEpisodes.size)
  }

  @Test
  fun `recoveryDays measured from troughDate not peakDate`() {
    // Given peak day 0, trough day 5, recovery day 10 → declineDays=5, recoveryDays=5, totalDays=10
    val curve = listOf(
      point(START, 100_000.0),
      point(START.plusDays(1), 95_000.0),
      point(START.plusDays(5), 90_000.0), // trough
      point(START.plusDays(10), 101_000.0), // new ATH (recovery)
    )

    // When
    val analysis = service.compute(emptyList(), curve, sizing(curve), benchmarkQuotes = null)

    // Then ep.declineDays = 5 (peak→trough), recoveryDays = 5 (trough→recovery), totalDays = 10 (peak→recovery)
    val ep = analysis.drawdownEpisodes[0]
    assertEquals(5, ep.declineDays)
    assertEquals(5, ep.recoveryDays)
    assertEquals(10, ep.totalDays)
  }

  // ===== HELPERS =====

  private fun point(date: LocalDate, value: Double) = PortfolioEquityPoint(date = date, portfolioValue = value)

  private fun sizing(
    curve: List<PortfolioEquityPoint>,
    totalReturnPct: Double = 0.0,
    maxDrawdownPct: Double = 0.0,
  ) = PositionSizingResult(
    startingCapital = curve.first().portfolioValue,
    finalCapital = curve.last().portfolioValue,
    totalReturnPct = totalReturnPct,
    maxDrawdownPct = maxDrawdownPct,
    maxDrawdownDollars = 0.0,
    peakCapital = curve.first().portfolioValue,
    trades = emptyList(),
    equityCurve = curve,
  )

  private fun buildCurveFromDailyReturns(
    start: LocalDate,
    initial: Double,
    dailyReturns: List<Double>,
  ): List<PortfolioEquityPoint> {
    val points = mutableListOf(point(start, initial))
    var v = initial
    for ((i, r) in dailyReturns.withIndex()) {
      v *= (1.0 + r)
      points += point(start.plusDays((i + 1).toLong()), v)
    }
    return points
  }

  private fun buildPriceSeriesFromDailyReturns(initial: Double, dailyReturns: List<Double>): List<Double> {
    val series = mutableListOf(initial)
    var p = initial
    for (r in dailyReturns) {
      p *= (1.0 + r)
      series += p
    }
    return series
  }

  private fun tradeWithProfitPct(profitPct: Double): Trade {
    val entryPrice = 100.0
    val exitPrice = 100.0 + profitPct
    val entryQuote = StockQuote(symbol = "TEST", date = START, closePrice = entryPrice)
    val exitQuote = StockQuote(symbol = "TEST", date = START.plusDays(5), closePrice = exitPrice)
    return Trade(
      stockSymbol = "TEST",
      entryQuote = entryQuote,
      quotes = listOf(exitQuote),
      exitReason = "test",
      profit = profitPct,
      startDate = START,
      sector = "Technology",
    )
  }

  private fun quote(date: LocalDate, close: Double) = StockQuote(symbol = "SPY", date = date, closePrice = close)

  private fun <T> List<T>.repeat(n: Int): List<T> {
    val out = mutableListOf<T>()
    repeat(n) { out += this }
    return out
  }
}
