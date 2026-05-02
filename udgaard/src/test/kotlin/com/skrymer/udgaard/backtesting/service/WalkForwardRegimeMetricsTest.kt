package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.data.model.MarketBreadthDaily
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.time.LocalDate

class WalkForwardRegimeMetricsTest {
  private val service = WalkForwardService(
    backtestService = mock(),
    sectorBreadthRepository = mock(),
    marketBreadthRepository = mock(),
  )

  private fun row(date: LocalDate, breadth: Double, ema10: Double) =
    MarketBreadthDaily(quoteDate = date, breadthPercent = breadth, ema10 = ema10)

  @Test
  fun `all uptrend days yields 100 percent uptrend`() {
    // Given: a 5-day breadth window where every day has breadthPercent above ema10
    val start = LocalDate.of(2024, 1, 1)
    val end = LocalDate.of(2024, 1, 5)
    val breadthMap = (0..4).associate { i ->
      val d = start.plusDays(i.toLong())
      d to row(d, breadth = 70.0, ema10 = 50.0)
    }

    // When
    val (uptrendPct, breadthAvg) = service.computeRegimeMetrics(start, end, breadthMap)

    // Then
    assertEquals(100.0, uptrendPct, EPSILON)
    assertEquals(70.0, breadthAvg, EPSILON)
  }

  @Test
  fun `zero uptrend days yields 0 percent uptrend`() {
    // Given: a 5-day breadth window where every day has breadthPercent at or below ema10
    val start = LocalDate.of(2024, 1, 1)
    val end = LocalDate.of(2024, 1, 5)
    val breadthMap = (0..4).associate { i ->
      val d = start.plusDays(i.toLong())
      d to row(d, breadth = 30.0, ema10 = 50.0)
    }

    // When
    val (uptrendPct, breadthAvg) = service.computeRegimeMetrics(start, end, breadthMap)

    // Then
    assertEquals(0.0, uptrendPct, EPSILON)
    assertEquals(30.0, breadthAvg, EPSILON)
  }

  @Test
  fun `mixed uptrend days yields hand-computed ratio`() {
    // Given: 4 days, 3 uptrend (breadth 80) + 1 non-uptrend (breadth 40), ema10 fixed at 50
    val start = LocalDate.of(2024, 1, 1)
    val end = LocalDate.of(2024, 1, 4)
    val breadthMap = mapOf(
      start to row(start, breadth = 80.0, ema10 = 50.0),
      start.plusDays(1) to row(start.plusDays(1), breadth = 80.0, ema10 = 50.0),
      start.plusDays(2) to row(start.plusDays(2), breadth = 80.0, ema10 = 50.0),
      start.plusDays(3) to row(start.plusDays(3), breadth = 40.0, ema10 = 50.0),
    )

    // When
    val (uptrendPct, breadthAvg) = service.computeRegimeMetrics(start, end, breadthMap)

    // Then: 3 of 4 = 75%; mean breadth = (80+80+80+40)/4 = 70
    assertEquals(75.0, uptrendPct, EPSILON)
    assertEquals(70.0, breadthAvg, EPSILON)
  }

  @Test
  fun `empty breadth range yields zero zero default`() {
    // Given: a date range where no breadth rows exist
    val start = LocalDate.of(2024, 1, 1)
    val end = LocalDate.of(2024, 1, 5)
    val breadthMap = emptyMap<LocalDate, MarketBreadthDaily>()

    // When
    val (uptrendPct, breadthAvg) = service.computeRegimeMetrics(start, end, breadthMap)

    // Then: both default to 0.0 — analyst inspects the date range to disambiguate "no data" from "0 uptrend"
    assertEquals(0.0, uptrendPct, EPSILON)
    assertEquals(0.0, breadthAvg, EPSILON)
  }

  @Test
  fun `missing dates within range are skipped not zero-imputed`() {
    // Given: a 5-day range with breadth rows only on days 1, 3, 5 (weekends/holidays simulated as missing)
    val start = LocalDate.of(2024, 1, 1)
    val end = LocalDate.of(2024, 1, 5)
    val breadthMap = mapOf(
      start to row(start, breadth = 90.0, ema10 = 50.0), // uptrend
      start.plusDays(2) to row(start.plusDays(2), breadth = 90.0, ema10 = 50.0), // uptrend
      start.plusDays(4) to row(start.plusDays(4), breadth = 30.0, ema10 = 50.0), // non-uptrend
    )

    // When
    val (uptrendPct, breadthAvg) = service.computeRegimeMetrics(start, end, breadthMap)

    // Then: average is over the 3 present rows only — 2/3 uptrend = 66.67%, mean breadth = 70
    assertEquals(2.0 / 3.0 * 100.0, uptrendPct, EPSILON)
    assertEquals(70.0, breadthAvg, EPSILON)
  }

  companion object {
    private const val EPSILON = 0.001
  }
}
