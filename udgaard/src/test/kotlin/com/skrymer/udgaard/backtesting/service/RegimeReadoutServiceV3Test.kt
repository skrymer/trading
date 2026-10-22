package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.RegimeLabel
import com.skrymer.udgaard.data.model.EwReturnDaily
import com.skrymer.udgaard.data.model.MarketBreadthDaily
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.repository.LeadershipGapRepository
import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import com.skrymer.udgaard.data.service.TechnicalIndicatorService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate

class RegimeReadoutServiceV3Test {
  private val leadershipGapRepository: LeadershipGapRepository = mock()
  private val stockRepository: StockJooqRepository = mock()
  private val marketBreadthRepository: MarketBreadthRepository = mock()
  private val service =
    RegimeReadoutService(TechnicalIndicatorService(), leadershipGapRepository, stockRepository, marketBreadthRepository)

  private fun spyBar(
    date: LocalDate,
    high: Double,
    low: Double,
    close: Double,
  ) = StockQuote(symbol = "SPY", date = date, high = high, low = low, closePrice = close)

  private fun dates(n: Int) = (0 until n).map { LocalDate.of(2010, 1, 1).plusDays(it.toLong()) }

  private fun ewOf(date: LocalDate, mean: Double) =
    EwReturnDaily(quoteDate = date, meanReturn = mean, crossSectionalStdev = 0.05, contributingN = 300, medianReturn = mean)

  private fun breadthOf(date: LocalDate, percent: Double) =
    MarketBreadthDaily(quoteDate = date, breadthPercent = percent, ema10 = percent)

  // SPY drifting up smoothly in a tight 0.10-wide band (close +0.1/day): low realized vol and a
  // near-zero net-vs-path ratio -> Choppiness ~12 (efficient), Kaufman ER = 1.
  private fun efficientUpDriftQuotes(days: List<LocalDate>) =
    days.mapIndexed { i, d -> spyBar(d, high = 100.05 + 0.1 * i, low = 99.95 + 0.1 * i, close = 100.0 + 0.1 * i) }

  @Test
  fun `a perfectly trending window reads Choppiness 0 - maximally efficient`() {
    // Given: 31 SPY bars stepping cleanly up 1/day, each bar's range tiling the prior close to the
    // new close (high = close, low = prior close) so total path length == net window distance.
    val start = LocalDate.of(2010, 1, 1)
    val quotes =
      (0..30).map { i ->
        spyBar(start.plusDays(i.toLong()), high = 100.0 + i, low = 99.0 + i, close = 100.0 + i)
      }

    // When
    val choppiness = service.choppinessIndexByDate(quotes, window = 30)

    // Then: the final day's Choppiness is 0 (Σ true range == the window's high-low span)
    assertEquals(0.0, choppiness.getValue(quotes.last().date), 1e-9)
  }

  @Test
  fun `a perfectly oscillating window reads Choppiness 100 - maximally choppy`() {
    // Given: 31 SPY bars whipsawing inside a fixed [100, 110] band — every bar spans the full band,
    // close alternates 100/110 — so total path length is window x the (zero) net distance.
    val start = LocalDate.of(2010, 1, 1)
    val quotes =
      (0..30).map { i ->
        spyBar(start.plusDays(i.toLong()), high = 110.0, low = 100.0, close = if (i % 2 == 0) 100.0 else 110.0)
      }

    // When
    val choppiness = service.choppinessIndexByDate(quotes, window = 30)

    // Then: the final day's Choppiness is 100 (Σ true range == window x span)
    assertEquals(100.0, choppiness.getValue(quotes.last().date), 1e-9)
  }

  @Test
  fun `a non-positive bad-print bar inside the window omits the day - fail-closed`() {
    // Given: an otherwise-trending window where one mid-window bar carries a bad print (high/low 0).
    val start = LocalDate.of(2010, 1, 1)
    val quotes =
      (0..30).map { i ->
        if (i == 15) {
          spyBar(start.plusDays(i.toLong()), high = 0.0, low = 0.0, close = 0.0)
        } else {
          spyBar(start.plusDays(i.toLong()), high = 100.0 + i, low = 99.0 + i, close = 100.0 + i)
        }
      }

    // When
    val choppiness = service.choppinessIndexByDate(quotes, window = 30)

    // Then: the final day's trailing window contains the bad print, so it carries no Choppiness
    assertFalse(choppiness.containsKey(quotes.last().date))
  }

  @Test
  fun `a non-positive prior close inside the window omits the day - the prior close enters true range`() {
    // Given: an otherwise-trending window where one bar closes at 0 (a bad print) while its high/low
    // stay positive, so the NEXT bar's true range would read a zero prior close.
    val start = LocalDate.of(2010, 1, 1)
    val quotes =
      (0..30).map { i ->
        if (i == 14) {
          spyBar(start.plusDays(i.toLong()), high = 114.0, low = 113.0, close = 0.0)
        } else {
          spyBar(start.plusDays(i.toLong()), high = 100.0 + i, low = 99.0 + i, close = 100.0 + i)
        }
      }

    // When
    val choppiness = service.choppinessIndexByDate(quotes, window = 30)

    // Then: the window spanning the zero prior close carries no Choppiness (fail-closed)
    assertFalse(choppiness.containsKey(quotes.last().date))
  }

  @Test
  fun `an inefficient GRIND spell is demoted wholesale to CHOP`() {
    // Given: a five-day published GRIND spell whose every day reads trend-inefficient.
    val labels = List(5) { RegimeLabel.GRIND }
    val efficient = List(5) { false }

    // When
    val overlaid = service.demoteInefficientGrindSpells(labels, efficient)

    // Then: the whole spell flips to CHOP — a low-efficiency whipsaw, not a grind
    assertEquals(List(5) { RegimeLabel.CHOP }, overlaid)
  }

  @Test
  fun `a majority-efficient GRIND spell survives as GRIND`() {
    // Given: a five-day GRIND spell, three of five days trend-efficient (3/5 > 0.50).
    val labels = List(5) { RegimeLabel.GRIND }
    val efficient = listOf(true, true, true, false, false)

    // When
    val overlaid = service.demoteInefficientGrindSpells(labels, efficient)

    // Then: a strict majority of efficient days keeps the whole spell GRIND
    assertEquals(List(5) { RegimeLabel.GRIND }, overlaid)
  }

  @Test
  fun `a GRIND spell with exactly half its days efficient is demoted - ties demote`() {
    // Given: a four-day GRIND spell, two of four efficient (2/4 = 0.50, not a strict majority).
    val labels = List(4) { RegimeLabel.GRIND }
    val efficient = listOf(true, true, false, false)

    // When
    val overlaid = service.demoteInefficientGrindSpells(labels, efficient)

    // Then: a tie is not a majority — the spell demotes to CHOP
    assertEquals(List(4) { RegimeLabel.CHOP }, overlaid)
  }

  @Test
  fun `non-GRIND labels are copied verbatim and each GRIND spell is judged independently`() {
    // Given: an efficient GRIND spell, a THRUST day, then an inefficient GRIND spell, then CRISIS.
    val labels =
      listOf(
        RegimeLabel.GRIND,
        RegimeLabel.GRIND,
        RegimeLabel.THRUST,
        RegimeLabel.GRIND,
        RegimeLabel.GRIND,
        RegimeLabel.CRISIS,
      )
    val efficient = listOf(true, true, false, false, false, false)

    // When
    val overlaid = service.demoteInefficientGrindSpells(labels, efficient)

    // Then: backbone days untouched; the first (efficient) spell survives, the second demotes
    assertEquals(
      listOf(
        RegimeLabel.GRIND,
        RegimeLabel.GRIND,
        RegimeLabel.THRUST,
        RegimeLabel.CHOP,
        RegimeLabel.CHOP,
        RegimeLabel.CRISIS,
      ),
      overlaid,
    )
  }

  @Test
  fun `the v3 series keeps the v2 backbone byte-identical and routes only GRIND through the overlay`() {
    // Given: 45 days of a low-vol, gap-neutral, mid-breadth tape -> v2 publishes GRIND; but SPY is
    // dead flat (high = low = close), so the trend-efficiency read is undefined -> not efficient.
    val days = dates(45)
    val spyQuotes = days.map { spyBar(it, high = 100.0, low = 100.0, close = 100.0) }
    val ewReturns = days.associateWith { ewOf(it, 0.0) }
    val breadth = days.associateWith { breadthOf(it, 45.0) }
    val v2 = service.computeReadoutSeries(days.associateWith { 100.0 }, ewReturns, breadth)

    // When
    val v3 = service.computeReadoutSeriesV3(spyQuotes, ewReturns, breadth)

    // Then: v2 published GRIND on the seeded tail; v3 demotes that inefficient spell to CHOP; and
    // every non-GRIND v2 day is byte-identical in v3 (the backbone is copied, never recomputed).
    assertEquals(RegimeLabel.GRIND, v2.getValue(days.last()).publishedLabel)
    assertEquals(RegimeLabel.CHOP, v3.getValue(days.last()).publishedLabel)
    v2.keys.forEach { day ->
      val v2Label = v2.getValue(day).publishedLabel
      val v3Label = v3.getValue(day).publishedLabel
      if (v2Label != RegimeLabel.GRIND) {
        assertEquals(v2Label, v3Label)
      } else {
        assertTrue(v3Label == RegimeLabel.GRIND || v3Label == RegimeLabel.CHOP)
      }
    }
  }

  @Test
  fun `a trend-efficient GRIND spell survives in the v3 series`() {
    // Given: the same low-vol, gap-neutral, mid-breadth GRIND tape, but SPY now drifts up smoothly
    // in a tight 0.10-wide band (close +0.1/day) -> Choppiness ~12 (efficient). Long enough that the
    // published GRIND spell sits wholly inside the seeded Choppiness window.
    val days = dates(120)
    val spyClose = days.mapIndexed { i, d -> d to 100.0 + 0.1 * i }.toMap()
    val spyQuotes = efficientUpDriftQuotes(days)
    val ewReturns = days.associateWith { ewOf(it, 0.019) }
    val breadth = days.associateWith { breadthOf(it, 45.0) }
    val v2 = service.computeReadoutSeries(spyClose, ewReturns, breadth)

    // When
    val v3 = service.computeReadoutSeriesV3(spyQuotes, ewReturns, breadth)

    // Then: v2 published GRIND, and the efficient spell is NOT demoted — v3 keeps GRIND
    assertEquals(RegimeLabel.GRIND, v2.getValue(days.last()).publishedLabel)
    assertEquals(RegimeLabel.GRIND, v3.getValue(days.last()).publishedLabel)
  }

  @Test
  fun `the v3 axes surface the day's Choppiness reading while the v2 axes leave it null`() {
    // Given: the efficient GRIND tape — Choppiness ~11.92 on the seeded tail.
    val days = dates(120)
    val spyClose = days.mapIndexed { i, d -> d to 100.0 + 0.1 * i }.toMap()
    val spyQuotes = efficientUpDriftQuotes(days)
    val ewReturns = days.associateWith { ewOf(it, 0.019) }
    val breadth = days.associateWith { breadthOf(it, 45.0) }

    // When
    val v2 = service.computeReadoutSeries(spyClose, ewReturns, breadth)
    val v3 = service.computeReadoutSeriesV3(spyQuotes, ewReturns, breadth)

    // Then: v3 exposes the Choppiness value for the report; v2's axes leave it null
    assertEquals(11.92, v3.getValue(days.last()).axes!!.choppinessIndex30!!, 0.01)
    assertNull(v2.getValue(days.last()).axes!!.choppinessIndex30)
  }

  @Test
  fun `a perfect trend reads Kaufman efficiency ratio 1 - all net distance, no wasted path`() {
    // Given: 31 SPY bars stepping up 1/day — net change equals the summed bar-to-bar change.
    val start = LocalDate.of(2010, 1, 1)
    val quotes = (0..30).map { i -> spyBar(start.plusDays(i.toLong()), high = 100.0 + i, low = 99.0 + i, close = 100.0 + i) }

    // When
    val ratio = service.kaufmanEfficiencyRatioByDate(quotes, window = 30)

    // Then: a perfectly efficient trend is ER = 1
    assertEquals(1.0, ratio.getValue(quotes.last().date), 1e-9)
  }

  @Test
  fun `a flat oscillation reads Kaufman efficiency ratio 0 - all path, no net distance`() {
    // Given: 31 SPY bars closing alternately 100/110 — large summed path, zero net change.
    val start = LocalDate.of(2010, 1, 1)
    val quotes =
      (0..30).map { i ->
        spyBar(start.plusDays(i.toLong()), high = 110.0, low = 100.0, close = if (i % 2 == 0) 100.0 else 110.0)
      }

    // When
    val ratio = service.kaufmanEfficiencyRatioByDate(quotes, window = 30)

    // Then: a directionless whipsaw is ER = 0
    assertEquals(0.0, ratio.getValue(quotes.last().date), 1e-9)
  }

  @Test
  fun `the v3 axes surface the advisory Kaufman efficiency ratio`() {
    // Given: the smooth up-drift tape (close +0.1/day) — net change equals the summed path -> ER = 1.
    val days = dates(120)
    val spyQuotes = efficientUpDriftQuotes(days)
    val ewReturns = days.associateWith { ewOf(it, 0.019) }
    val breadth = days.associateWith { breadthOf(it, 45.0) }

    // When
    val v3 = service.computeReadoutSeriesV3(spyQuotes, ewReturns, breadth)

    // Then: the advisory efficiency ratio is surfaced on the v3 axes
    assertEquals(1.0, v3.getValue(days.last()).axes!!.efficiencyRatio30!!, 1e-9)
  }

  @Test
  fun `loadReadoutSeriesV3 warm-up-loads SPY OHLC, trims to the window, and feeds the Choppiness leg`() {
    // Given: a thrust-shaped tape spanning the warm-up buffer (flat close, ew +2%, breadth 55) with a
    // genuine 2-wide intraday band so the Choppiness leg is defined.
    val after = LocalDate.of(2020, 6, 1)
    val before = LocalDate.of(2020, 6, 8)
    val allDays = (0 until 200).map { after.minusDays(190).plusDays(it.toLong()) }
    whenever(stockRepository.findBySymbol(eq("SPY"), anyOrNull()))
      .thenReturn(Stock(quotes = allDays.map { StockQuote(symbol = "SPY", date = it, high = 101.0, low = 99.0, closePrice = 100.0) }))
    whenever(leadershipGapRepository.ewReturnByDate(any(), any(), any(), any()))
      .thenReturn(allDays.associateWith { ewOf(it, 0.02) })
    whenever(marketBreadthRepository.findAllAsMap())
      .thenReturn(allDays.associateWith { breadthOf(it, 55.0) })

    // When
    val series = service.loadReadoutSeriesV3(after, before)

    // Then: trimmed to [after, before]; the THRUST backbone is intact (the overlay only touches
    // GRIND); and the Choppiness reading flowed through from the loaded OHLC quotes.
    assertTrue(series.keys.all { !it.isBefore(after) && !it.isAfter(before) })
    assertEquals(RegimeLabel.THRUST, series.getValue(LocalDate.of(2020, 6, 3)).publishedLabel)
    assertNotNull(series.getValue(LocalDate.of(2020, 6, 3)).axes!!.choppinessIndex30)
  }
}
