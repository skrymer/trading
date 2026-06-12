package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.PortfolioEquityPoint
import com.skrymer.udgaard.backtesting.model.SpyBaselineVerdict
import com.skrymer.udgaard.backtesting.model.WalkForwardWindow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.LocalDate

private const val EPSILON = 0.01

class WalkForwardSpyBaselineTest {
  private val service = WalkForwardService(
    backtestService = mock(),
    sectorBreadthRepository = mock(),
    marketBreadthRepository = mock(),
    positionSizingService = mock(),
    riskMetricsService = RiskMetricsService(),
    stockRepository = mock(),
    riskFreeRateService = mock(),
    leadershipRegimeService = mock(),
    regimeReadoutService = mock(),
  )

  @Test
  fun `verdict is PASS when strategy stitched Calmar beats SPY`() {
    // Given two OOS windows where the strategy grows strongly with a shallow ~5% dip (high Calmar)
    // while SPY suffers a 25% crash and ends down (low / negative Calmar) over the same support.
    val w1 = window(
      oosStart = LocalDate.of(2010, 1, 4),
      strategy = strongCurve(LocalDate.of(2010, 1, 4)),
      spy = crashCurve(LocalDate.of(2010, 1, 4)),
    )
    val w2 = window(
      oosStart = LocalDate.of(2010, 3, 5),
      strategy = strongCurve(LocalDate.of(2010, 3, 5)),
      spy = crashCurve(LocalDate.of(2010, 3, 5)),
    )

    // When
    val result = service.aggregateResults(listOf(w1, w2))

    // Then the gate passes and the SPY leg is reported below the strategy's Calmar
    val baseline = requireNotNull(result.spyBaselineComparison)
    assertEquals(SpyBaselineVerdict.PASS, baseline.verdict)
    val strategyCalmar = requireNotNull(baseline.strategyCalmar)
    val benchmarkCalmar = requireNotNull(baseline.benchmarkCalmar)
    assertTrue(
      strategyCalmar >= benchmarkCalmar,
      "expected strategy Calmar ($strategyCalmar) >= SPY Calmar ($benchmarkCalmar)",
    )
    assertNotNull(baseline.benchmarkSharpe)
  }

  @Test
  fun `verdict is FAIL when strategy stitched Calmar trails SPY`() {
    // Given the inverse of the PASS case: the strategy crashes (negative Calmar) while SPY grows
    // strongly with a shallow dip (high Calmar) over the same support.
    val w1 = window(
      oosStart = LocalDate.of(2010, 1, 4),
      strategy = crashCurve(LocalDate.of(2010, 1, 4)),
      spy = strongCurve(LocalDate.of(2010, 1, 4)),
    )
    val w2 = window(
      oosStart = LocalDate.of(2010, 3, 5),
      strategy = crashCurve(LocalDate.of(2010, 3, 5)),
      spy = strongCurve(LocalDate.of(2010, 3, 5)),
    )

    // When
    val result = service.aggregateResults(listOf(w1, w2))

    // Then the gate fails: strategy Calmar is below SPY's
    val baseline = requireNotNull(result.spyBaselineComparison)
    assertEquals(SpyBaselineVerdict.FAIL, baseline.verdict)
    assertTrue(
      requireNotNull(baseline.strategyCalmar) < requireNotNull(baseline.benchmarkCalmar),
      "expected strategy Calmar below SPY Calmar for a FAIL",
    )
  }

  @Test
  fun `verdict is INCONCLUSIVE when the stitched OOS series is shorter than 60 days`() {
    // Given two short windows (10 points each → 18 stitched returns, below the 60-day floor),
    // even though the strategy would otherwise dominate SPY on Calmar.
    val w1 = window(
      oosStart = LocalDate.of(2010, 1, 4),
      strategy = shortStrongCurve(LocalDate.of(2010, 1, 4)),
      spy = shortCrashCurve(LocalDate.of(2010, 1, 4)),
    )
    val w2 = window(
      oosStart = LocalDate.of(2010, 2, 1),
      strategy = shortStrongCurve(LocalDate.of(2010, 2, 1)),
      spy = shortCrashCurve(LocalDate.of(2010, 2, 1)),
    )

    // When
    val result = service.aggregateResults(listOf(w1, w2))

    // Then the block does not bind — too few OOS days to be statistically meaningful
    val baseline = requireNotNull(result.spyBaselineComparison)
    assertEquals(SpyBaselineVerdict.INCONCLUSIVE, baseline.verdict)
    assertTrue(
      requireNotNull(baseline.inconclusiveReason).contains("trading days"),
      "reason should cite the day-count floor, was '${baseline.inconclusiveReason}'",
    )
  }

  @Test
  fun `verdict is INCONCLUSIVE when the strategy stitched maxDD is below the 3 percent floor`() {
    // Given a long-enough series (64 stitched returns) but a strategy whose stitched maxDD is only
    // ~2% — a trivially small denominator that would otherwise manufacture an explosive Calmar.
    val w1 = window(
      oosStart = LocalDate.of(2010, 1, 4),
      strategy = lowDrawdownCurve(LocalDate.of(2010, 1, 4)),
      spy = crashCurve(LocalDate.of(2010, 1, 4)),
    )
    val w2 = window(
      oosStart = LocalDate.of(2010, 3, 5),
      strategy = lowDrawdownCurve(LocalDate.of(2010, 3, 5)),
      spy = crashCurve(LocalDate.of(2010, 3, 5)),
    )

    // When
    val result = service.aggregateResults(listOf(w1, w2))

    // Then the block does not bind — the tiny denominator is a small-sample Calmar artifact
    val baseline = requireNotNull(result.spyBaselineComparison)
    assertEquals(SpyBaselineVerdict.INCONCLUSIVE, baseline.verdict)
    assertTrue(
      requireNotNull(baseline.inconclusiveReason).contains("maxDD"),
      "reason should cite the maxDD floor, was '${baseline.inconclusiveReason}'",
    )
  }

  @Test
  fun `SPY aggregate maxDD straddling the window seam exceeds the max per-window SPY maxDD`() {
    // Given SPY peaks INSIDE window 1 (then dips slightly to the seam) and keeps declining through
    // window 2. The worst peak-to-trough straddles the A/B seam — an excursion neither window's own
    // maxDD can see. This is the non-redundancy proof: the aggregate must be a genuine restitch,
    // not a function (min/avg) of the sub-window Calmars.
    val spyW1 = seamSpyWindow1(LocalDate.of(2010, 1, 4)) // rises 100->120 then eases to 114
    val spyW2 = seamSpyWindow2(LocalDate.of(2010, 3, 5)) // declines 114->100
    val w1 = window(LocalDate.of(2010, 1, 4), strategy = strongCurve(LocalDate.of(2010, 1, 4)), spy = spyW1)
    val w2 = window(LocalDate.of(2010, 3, 5), strategy = strongCurve(LocalDate.of(2010, 3, 5)), spy = spyW2)

    // When
    val result = service.aggregateResults(listOf(w1, w2))

    // Then the stitched-aggregate SPY maxDD strictly exceeds the worst single-window SPY maxDD
    val aggregateMaxDd = requireNotNull(result.spyBaselineComparison?.benchmarkMaxDrawdownPct)
    val perWindowMax = maxOf(
      RiskMetricsService().maxDrawdownPct(spyW1),
      RiskMetricsService().maxDrawdownPct(spyW2),
    )
    assertTrue(
      aggregateMaxDd > perWindowMax,
      "aggregate SPY maxDD ($aggregateMaxDd) should exceed max per-window ($perWindowMax)",
    )
  }

  @Test
  fun `SPY stitch chains per-window growth multiplicatively without a cross-window jump`() {
    // Given two SPY windows at disjoint nominal price levels that each grow 10%: w1 100->110,
    // w2 500->550. The correct stitched growth is (110/100)*(550/500) = 1.21, NOT a spurious
    // 110->500 cross-window jump return (which a naive price-concatenation would manufacture).
    val spyW1 = curve(LocalDate.of(2010, 1, 4), listOf(100.0, 110.0))
    val spyW2 = curve(LocalDate.of(2010, 1, 8), listOf(500.0, 550.0))
    val w1 = window(LocalDate.of(2010, 1, 4), strategy = curve(LocalDate.of(2010, 1, 4), listOf(100.0, 110.0)), spy = spyW1)
    val w2 = window(LocalDate.of(2010, 1, 8), strategy = curve(LocalDate.of(2010, 1, 8), listOf(500.0, 550.0)), spy = spyW2)

    // When
    val result = service.aggregateResults(listOf(w1, w2))

    // Then SPY CAGR reflects the PRODUCT of per-window growths (1.21 over a 5-day wall-clock span,
    // i.e. astronomically large). A cross-window jump would compound 110->500->550 and balloon far
    // higher still; the multiplicative chain is bounded by 1.21^(365.25/5). We assert it is large
    // (multiplicative) — additive 1.10^(365.25/5) would be orders of magnitude lower (~3000).
    val spyBaseline = requireNotNull(result.spyBaselineComparison)
    val benchmarkCagr = requireNotNull(spyBaseline.benchmarkCagr)
    assertTrue(benchmarkCagr > 1_000_000.0, "stitched SPY CAGR should reflect 21% per-5-days, got $benchmarkCagr")
    // And no spurious cross-window drawdown: both windows rise monotonically, so stitched maxDD is ~0.
    assertEquals(0.0, requireNotNull(spyBaseline.benchmarkMaxDrawdownPct), EPSILON)
  }

  @Test
  fun `spyBaselineComparison is null when no SPY curve is available`() {
    // Given sized strategy windows but no benchmark (SPY) curve on any window
    val w1 = window(LocalDate.of(2010, 1, 4), strategy = strongCurve(LocalDate.of(2010, 1, 4)), spy = null)
    val w2 = window(LocalDate.of(2010, 3, 5), strategy = strongCurve(LocalDate.of(2010, 3, 5)), spy = null)

    // When
    val result = service.aggregateResults(listOf(w1, w2))

    // Then there is no baseline comparison to report (mirrors aggregate-metrics null semantics)
    assertEquals(null, result.spyBaselineComparison)
  }

  // ===== HELPERS =====

  /** SPY window 1: rises 100→120 (global peak), then eases to 114 at the seam. Own maxDD ~5%. */
  private fun seamSpyWindow1(start: LocalDate): List<PortfolioEquityPoint> {
    val rise = (0..16).map { 100.0 + it * (20.0 / 16.0) } // 100 -> 120
    val ease = (1..16).map { 120.0 - it * (6.0 / 16.0) } // 120 -> 114
    return curve(start, rise + ease)
  }

  /** SPY window 2: declines 114→100. Own maxDD ~12%; combined with w1's peak the straddle is larger. */
  private fun seamSpyWindow2(start: LocalDate): List<PortfolioEquityPoint> {
    val decline = (0..32).map { 114.0 - it * (14.0 / 32.0) } // 114 -> 100
    return curve(start, decline)
  }

  /** 33-day curve: +0.5%/day with a single −2% day → ~2% maxDD, below the 3% INCONCLUSIVE floor. */
  private fun lowDrawdownCurve(start: LocalDate): List<PortfolioEquityPoint> {
    val values = mutableListOf(10_000.0)
    for (day in 1 until 33) {
      val dailyReturn = if (day == 16) -0.02 else 0.005
      values += values.last() * (1.0 + dailyReturn)
    }
    return curve(start, values)
  }

  /** 10-day +1%/day curve (with one −5% day) — too short to clear the 60-day floor. */
  private fun shortStrongCurve(start: LocalDate): List<PortfolioEquityPoint> {
    val values = mutableListOf(10_000.0)
    for (day in 1 until 10) {
      val dailyReturn = if (day == 5) -0.05 else 0.01
      values += values.last() * (1.0 + dailyReturn)
    }
    return curve(start, values)
  }

  /** 10-day curve with a 25% crash — too short to clear the 60-day floor. */
  private fun shortCrashCurve(start: LocalDate): List<PortfolioEquityPoint> {
    val values = mutableListOf(400.0)
    for (day in 1 until 10) {
      val dailyReturn = if (day == 5) -0.25 else 0.001
      values += values.last() * (1.0 + dailyReturn)
    }
    return curve(start, values)
  }

  /** 33-day curve: +1%/day with a single −5% day at the midpoint → ~5% maxDD, strong net growth. */
  private fun strongCurve(start: LocalDate): List<PortfolioEquityPoint> {
    val values = mutableListOf(10_000.0)
    for (day in 1 until 33) {
      val dailyReturn = if (day == 16) -0.05 else 0.01
      values += values.last() * (1.0 + dailyReturn)
    }
    return curve(start, values)
  }

  /** 33-day curve: +0.1%/day with a single −25% crash at the midpoint → ~25% maxDD, ends down. */
  private fun crashCurve(start: LocalDate): List<PortfolioEquityPoint> {
    val values = mutableListOf(400.0)
    for (day in 1 until 33) {
      val dailyReturn = if (day == 16) -0.25 else 0.001
      values += values.last() * (1.0 + dailyReturn)
    }
    return curve(start, values)
  }

  private fun curve(start: LocalDate, values: List<Double>): List<PortfolioEquityPoint> =
    values.mapIndexed { i, value -> PortfolioEquityPoint(date = start.plusDays(i.toLong()), portfolioValue = value) }

  private fun window(
    oosStart: LocalDate,
    strategy: List<PortfolioEquityPoint>?,
    spy: List<PortfolioEquityPoint>?,
  ): WalkForwardService.WindowComputation =
    WalkForwardService.WindowComputation(
      window = bareWindow(oosStart),
      equityCurve = strategy,
      trades = emptyList(),
      benchmarkEquityCurve = spy,
    )

  private fun bareWindow(oosStart: LocalDate): WalkForwardWindow =
    WalkForwardWindow(
      inSampleStart = oosStart.minusYears(3),
      inSampleEnd = oosStart.minusDays(1),
      outOfSampleStart = oosStart,
      outOfSampleEnd = oosStart.plusDays(32),
      derivedSectorRanking = emptyList(),
      inSampleEdge = 0.8,
      outOfSampleEdge = 1.0,
      inSampleTrades = 100,
      outOfSampleTrades = 50,
      inSampleWinRate = 0.5,
      outOfSampleWinRate = 0.55,
      outOfSampleCagr = 0.0,
      outOfSampleMaxDrawdownPct = 0.0,
      outOfSampleRiskMetrics = null,
      inSampleBreadthUptrendPercent = 50.0,
      inSampleBreadthAvg = 50.0,
      outOfSampleBreadthUptrendPercent = 50.0,
      outOfSampleBreadthAvg = 50.0,
    )
}
