package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.PortfolioEquityPoint
import com.skrymer.udgaard.backtesting.model.WalkForwardWindow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.LocalDate

class WalkForwardRiskMetricsTest {
  private val service = WalkForwardService(
    backtestService = mock(),
    sectorBreadthRepository = mock(),
    marketBreadthRepository = mock(),
    positionSizingService = mock(),
    riskMetricsService = RiskMetricsService(),
    stockRepository = mock(),
    riskFreeRateService = mock(),
  )

  @Test
  fun `aggregate OOS metrics are null when every window is un-sized`() {
    // Given two un-sized windows (no equity curve, no position sizing)
    val w1 = WalkForwardService.WindowComputation(
      window = bareWindow(LocalDate.of(2010, 1, 4), oosTrades = 50),
      equityCurve = null,
      trades = emptyList(),
    )
    val w2 = WalkForwardService.WindowComputation(
      window = bareWindow(LocalDate.of(2010, 1, 11), oosTrades = 50),
      equityCurve = null,
      trades = emptyList(),
    )

    // When
    val result = service.aggregateResults(listOf(w1, w2))

    // Then: aggregates are null (mirrors un-sized single-backtest behaviour); edge/trades
    // weighted-averages still compute since they don't depend on the equity curve.
    assertEquals(null, result.aggregateOosRiskMetrics)
    assertEquals(null, result.aggregateOosCagr)
    assertEquals(null, result.aggregateOosMaxDrawdownPct)
  }

  @Test
  fun `aggregate OOS RiskMetrics is computed from stitched per-window daily returns`() {
    // Given two adjacent OOS windows, each with a 5-day equity curve. Window 1 trends up;
    // window 2 has an internal drawdown. The stitch should produce a continuous OOS series.
    val w1 = WalkForwardService.WindowComputation(
      window = bareWindow(LocalDate.of(2010, 1, 4), oosTrades = 50),
      equityCurve = curve(LocalDate.of(2010, 1, 4), listOf(10_000.0, 10_100.0, 10_200.0, 10_150.0, 10_300.0)),
      trades = emptyList(),
    )
    val w2 = WalkForwardService.WindowComputation(
      window = bareWindow(LocalDate.of(2010, 1, 11), oosTrades = 50),
      equityCurve = curve(LocalDate.of(2010, 1, 11), listOf(10_300.0, 10_000.0, 9_500.0, 9_800.0, 10_200.0)),
      trades = emptyList(),
    )

    // When
    val result = service.aggregateResults(listOf(w1, w2))

    // Then: stitched aggregate metrics are present (not null) and derived from the joined series
    assertNotNull(result.aggregateOosRiskMetrics)
    assertNotNull(result.aggregateOosRiskMetrics!!.sharpeRatio)
    assertNotNull(result.aggregateOosCagr)
    assertNotNull(result.aggregateOosMaxDrawdownPct)
    // Cross-window peak-to-trough: peak 10_300 (end of w1 / start of w2) -> trough 9_500 = 7.77%
    assertEquals(7.767, result.aggregateOosMaxDrawdownPct!!, 0.01)
  }

  @Test
  fun `stitch chains windows multiplicatively, not additively`() {
    // Given two windows with disjoint nominal values that nevertheless represent the same
    // 10 percent growth pattern: w1 grows 10k -> 11k, w2 grows 50k -> 55k. Both windows
    // are independent backtest runs starting at their own per-window capital, so the
    // correct stitched growth is (11k/10k) * (55k/50k) = 1.21 = +21%, NOT (11k+55k)/(10k+50k) ~ 1.10.
    // A test where w1.last == w2.first would pass either way; this one would not.
    val w1 = WalkForwardService.WindowComputation(
      window = bareWindow(LocalDate.of(2010, 1, 4), oosTrades = 50),
      equityCurve = curve(LocalDate.of(2010, 1, 4), listOf(10_000.0, 11_000.0)),
      trades = emptyList(),
    )
    val w2 = WalkForwardService.WindowComputation(
      window = bareWindow(LocalDate.of(2010, 1, 8), oosTrades = 50),
      equityCurve = curve(LocalDate.of(2010, 1, 8), listOf(50_000.0, 55_000.0)),
      trades = emptyList(),
    )

    // When
    val result = service.aggregateResults(listOf(w1, w2))

    // Then: stitched growth is the PRODUCT of per-window growths. Wall-clock span is
    // 2010-01-04 -> 2010-01-09 = 5 days; CAGR = 1.21 ^ (365.25/5) - 1 = enormous; we just
    // assert it's strongly positive (chain is multiplicative, not additive).
    val cagr = result.aggregateOosCagr
    assertNotNull(cagr)
    // 1.21 ^ (365.25/5) ~ 1.5e7 — a sanity floor confirms multiplicative; additive would
    // give 1.10 ^ (365.25/5) ~ 3000 — orders of magnitude lower.
    assertEquals(true, cagr!! > 1_000_000.0, "stitched CAGR should reflect 21% per-5-days, got $cagr")
  }

  private fun curve(start: LocalDate, values: List<Double>): List<PortfolioEquityPoint> =
    values.mapIndexed { i, v ->
      PortfolioEquityPoint(date = start.plusDays(i.toLong()), portfolioValue = v)
    }

  private fun bareWindow(oosStart: LocalDate, oosTrades: Int): WalkForwardWindow =
    WalkForwardWindow(
      inSampleStart = oosStart.minusYears(3),
      inSampleEnd = oosStart.minusDays(1),
      outOfSampleStart = oosStart,
      outOfSampleEnd = oosStart.plusDays(4),
      derivedSectorRanking = emptyList(),
      inSampleEdge = 0.8,
      outOfSampleEdge = 1.0,
      inSampleTrades = 100,
      outOfSampleTrades = oosTrades,
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
