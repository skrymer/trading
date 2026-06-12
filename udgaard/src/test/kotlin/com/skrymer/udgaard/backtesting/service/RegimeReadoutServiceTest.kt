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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class RegimeReadoutServiceTest {
  private val leadershipGapRepository: LeadershipGapRepository = mock()
  private val stockRepository: StockJooqRepository = mock()
  private val marketBreadthRepository: MarketBreadthRepository = mock()
  private val service =
    RegimeReadoutService(TechnicalIndicatorService(), leadershipGapRepository, stockRepository, marketBreadthRepository)

  private fun dates(n: Int) = (0 until n).map { LocalDate.of(2010, 1, 1).plusDays(it.toLong()) }

  private fun ewOf(date: LocalDate, mean: Double) =
    EwReturnDaily(quoteDate = date, meanReturn = mean, crossSectionalStdev = 0.05, contributingN = 300)

  private fun breadthOf(date: LocalDate, percent: Double, ema10: Double = percent) =
    MarketBreadthDaily(quoteDate = date, breadthPercent = percent, ema10 = ema10)

  @Test
  fun `a broad-thrust tape - breadth high with equal-weight leading - labels THRUST`() {
    // Given: 45 days of flat SPY (20-bar return 0) while the equal-weight universe gains 2% per
    // 20 bars -> gap = -2% (equal-weight leads), with breadth participation high (EMA10 = 55 >= 50)
    // and no washout in sight.
    val days = dates(45)
    val spyClose = days.associateWith { 100.0 }
    val ewReturns = days.associateWith { ewOf(it, 0.02) }
    val breadth = days.associateWith { breadthOf(it, 55.0) }

    // When
    val series = service.computeReadoutSeries(spyClose, ewReturns, breadth)

    // Then: once the gap EMA has seeded, the day reads THRUST (L HIGH and gap NEG)
    assertEquals(RegimeLabel.THRUST, series.getValue(days.last()).rawLabel)
  }

  @Test
  fun `a sustained breadth washout overrides an otherwise-THRUST tape as CRISIS`() {
    // Given: the same thrust-shaped tape (gap NEG, breadth EMA10 high), but raw breadth collapses
    // to 12 for the final 10 consecutive days — the sustained-washout crisis definition. The EMA10
    // is pinned high so the thrust legs stay satisfied; only the washout distinguishes the days.
    val days = dates(45)
    val spyClose = days.associateWith { 100.0 }
    val ewReturns = days.associateWith { ewOf(it, 0.02) }
    val breadth = days.mapIndexed { i, d -> d to breadthOf(d, percent = if (i >= 35) 12.0 else 55.0, ema10 = 55.0) }.toMap()

    // When
    val series = service.computeReadoutSeries(spyClose, ewReturns, breadth)

    // Then: four days into the collapse the run is not yet sustained — still THRUST; on the day the
    // 10th consecutive sub-threshold reading lands, CRISIS takes precedence over the thrust read.
    assertEquals(RegimeLabel.THRUST, series.getValue(days[38]).rawLabel)
    assertEquals(RegimeLabel.CRISIS, series.getValue(days.last()).rawLabel)
  }

  @Test
  fun `an index rising on weak breadth with cap-weight leading labels NARROW`() {
    // Given: SPY grinds up ~4% per 20 bars (direction UP) while the equal-weight universe makes
    // only 1% -> gap = +3% (cap-weight leads), and breadth participation is weak (EMA10 = 30 <= 35)
    // without ever touching the washout floor.
    val days = dates(45)
    val spyClose = days.mapIndexed { i, d -> d to 100.0 * Math.pow(1.002, i.toDouble()) }.toMap()
    val ewReturns = days.associateWith { ewOf(it, 0.01) }
    val breadth = days.associateWith { breadthOf(it, percent = 30.0) }

    // When
    val series = service.computeReadoutSeries(spyClose, ewReturns, breadth)

    // Then: the day reads NARROW (D UP, gap POS, L WEAK)
    assertEquals(RegimeLabel.NARROW, series.getValue(days.last()).rawLabel)
  }

  @Test
  fun `a quiet tape - gap neutral and realized vol low on mid breadth - labels GRIND`() {
    // Given: SPY drifts up 0.05% every day -> realized vol ~0 (LOW) and a 20-bar return of ~1%
    // (direction FLAT), the equal-weight universe matches it (gap ~0, NEUTRAL), and breadth
    // participation sits mid-band (EMA10 = 42).
    val days = dates(45)
    val spyClose = days.mapIndexed { i, d -> d to 100.0 * Math.pow(1.0005, i.toDouble()) }.toMap()
    val ewReturns = days.associateWith { ewOf(it, 0.01) }
    val breadth = days.associateWith { breadthOf(it, percent = 42.0) }

    // When
    val series = service.computeReadoutSeries(spyClose, ewReturns, breadth)

    // Then: the day reads GRIND (gap NEUTRAL, vol LOW, L MID, no falling slope, no down direction)
    assertEquals(RegimeLabel.GRIND, series.getValue(days.last()).rawLabel)
  }

  @Test
  fun `a trendless high-vol whipsaw matching no structured regime labels CHOP`() {
    // Given: SPY whipsaws +-3% daily with no net 20-bar direction (realized vol ~47%, far above the
    // HIGH band), the equal-weight universe matches it (gap NEUTRAL), breadth sits mid-band, and no
    // washout fires. Not THRUST (gap not NEG), not NARROW (D FLAT), not GRIND (vol not LOW).
    val days = dates(45)
    val spyClose = days.mapIndexed { i, d -> d to if (i % 2 == 0) 100.0 else 103.0 }.toMap()
    val ewReturns = days.associateWith { ewOf(it, 0.0) }
    val breadth = days.associateWith { breadthOf(it, percent = 42.0) }

    // When
    val series = service.computeReadoutSeries(spyClose, ewReturns, breadth)

    // Then: the day falls through every structured definition and reads CHOP
    assertEquals(RegimeLabel.CHOP, series.getValue(days.last()).rawLabel)
  }

  @Test
  fun `an early thrust - breadth still mid-band but rising sharply with equal-weight leading - labels THRUST`() {
    // Given: the equal-weight universe leads flat SPY (gap NEG) while breadth participation climbs
    // 0.8 points a day — at the asserted day the level is only ~45 (below the HIGH band) but the
    // 5-bar slope is +4.0, the rising leg of the thrust definition.
    val days = dates(45)
    val spyClose = days.associateWith { 100.0 }
    val ewReturns = days.associateWith { ewOf(it, 0.02) }
    val breadth = days.mapIndexed { i, d -> d to breadthOf(d, percent = 42.0, ema10 = 10.0 + 0.8 * i) }.toMap()

    // When
    val series = service.computeReadoutSeries(spyClose, ewReturns, breadth)

    // Then: the day reads THRUST via (S RISING) even though L is not yet HIGH
    assertEquals(RegimeLabel.THRUST, series.getValue(days.last()).rawLabel)
  }

  @Test
  fun `an index rising on deteriorating breadth - mid level but falling - labels NARROW`() {
    // Given: SPY climbs ~4% per 20 bars with cap-weight leading (gap POS) while breadth
    // participation erodes 0.8 points a day — at the asserted day the level is ~45 (still mid-band)
    // but the 5-bar slope is -4.0, the falling leg of the narrow definition.
    val days = dates(45)
    val spyClose = days.mapIndexed { i, d -> d to 100.0 * Math.pow(1.002, i.toDouble()) }.toMap()
    val ewReturns = days.associateWith { ewOf(it, 0.01) }
    val breadth = days.mapIndexed { i, d -> d to breadthOf(d, percent = 42.0, ema10 = 80.0 - 0.8 * i) }.toMap()

    // When
    val series = service.computeReadoutSeries(spyClose, ewReturns, breadth)

    // Then: the day reads NARROW via (S FALLING) even though L is not WEAK
    assertEquals(RegimeLabel.NARROW, series.getValue(days.last()).rawLabel)
  }

  @Test
  fun `a quiet tape with falling breadth is not a GRIND - it falls through to CHOP`() {
    // Given: the same quiet tape as the GRIND case (gap NEUTRAL, vol LOW, level mid-band) except
    // breadth participation erodes 0.8 points a day — a quiet-but-deteriorating drift, which the
    // grind definition excludes (S must not be FALLING).
    val days = dates(45)
    val spyClose = days.mapIndexed { i, d -> d to 100.0 * Math.pow(1.0005, i.toDouble()) }.toMap()
    val ewReturns = days.associateWith { ewOf(it, 0.01) }
    val breadth = days.mapIndexed { i, d -> d to breadthOf(d, percent = 42.0, ema10 = 80.0 - 0.8 * i) }.toMap()

    // When
    val series = service.computeReadoutSeries(spyClose, ewReturns, breadth)

    // Then: not GRIND (slope FALLING) and not NARROW (direction FLAT) - the residual CHOP
    assertEquals(RegimeLabel.CHOP, series.getValue(days.last()).rawLabel)
  }

  @Test
  fun `a day whose equal-weight cross-section is too thin to trust carries no label`() {
    // Given: a thrust-shaped tape, but the equal-weight mean rests on only 50 contributing names —
    // below the trust floor, so the gap read is not defensible.
    val days = dates(45)
    val spyClose = days.associateWith { 100.0 }
    val ewReturns =
      days.associateWith {
        EwReturnDaily(quoteDate = it, meanReturn = 0.02, crossSectionalStdev = 0.05, contributingN = 50)
      }
    val breadth = days.associateWith { breadthOf(it, 55.0) }

    // When
    val series = service.computeReadoutSeries(spyClose, ewReturns, breadth)

    // Then: the day is unlabeled (fail-closed), not guessed into a regime
    assertEquals(null, series.getValue(days.last()).rawLabel)
  }

  @Test
  fun `a day with no breadth reading carries no label`() {
    // Given: a thrust-shaped tape whose final day has no breadth row at all
    val days = dates(45)
    val spyClose = days.associateWith { 100.0 }
    val ewReturns = days.associateWith { ewOf(it, 0.02) }
    val breadth = days.dropLast(1).associateWith { breadthOf(it, 55.0) }

    // When
    val series = service.computeReadoutSeries(spyClose, ewReturns, breadth)

    // Then: the day with the missing input is unlabeled; the prior day still reads normally
    assertEquals(null, series.getValue(days.last()).rawLabel)
    assertEquals(RegimeLabel.THRUST, series.getValue(days[43]).rawLabel)
  }

  @Test
  fun `the published label ignores a 4-day raw blip and switches only after 5 persistent days`() {
    // Given: a thrust tape (gap NEG, breadth EMA10 = 55) whose breadth level dips to mid-band for a
    // 4-day blip (raw CHOP, days 40-43), recovers (days 44-46), then deteriorates for good (day 47 on).
    val days = dates(60)
    val spyClose = days.associateWith { 100.0 }
    val ewReturns = days.associateWith { ewOf(it, 0.02) }
    val breadth =
      days
        .mapIndexed { i, d ->
          d to breadthOf(d, percent = 42.0, ema10 = if (i in 40..43 || i >= 47) 42.0 else 55.0)
        }.toMap()

    // When
    val series = service.computeReadoutSeries(spyClose, ewReturns, breadth)

    // Then: the blip diverges raw from published but never publishes; the sustained flip publishes
    // on its 5th consecutive raw day.
    assertEquals(RegimeLabel.CHOP, series.getValue(days[41]).rawLabel)
    assertEquals(RegimeLabel.THRUST, series.getValue(days[41]).publishedLabel)
    assertEquals(RegimeLabel.THRUST, series.getValue(days[43]).publishedLabel)
    assertEquals(RegimeLabel.THRUST, series.getValue(days[49]).publishedLabel)
    assertEquals(RegimeLabel.CHOP, series.getValue(days[51]).publishedLabel)
  }

  @Test
  fun `CRISIS publishes the day the washout completes but exits only after the dwell`() {
    // Given: a thrust tape whose raw breadth collapses to 12 for days 35-44 (the washout completes
    // on day 44) and then recovers. The washout stays visible in the trailing window through day 74,
    // so the raw label returns to THRUST on day 75.
    val days = dates(85)
    val spyClose = days.associateWith { 100.0 }
    val ewReturns = days.associateWith { ewOf(it, 0.02) }
    val breadth =
      days
        .mapIndexed { i, d ->
          d to breadthOf(d, percent = if (i in 35..44) 12.0 else 55.0, ema10 = 55.0)
        }.toMap()

    // When
    val series = service.computeReadoutSeries(spyClose, ewReturns, breadth)

    // Then: no dwell lag into CRISIS (day 44 publishes CRISIS immediately), but the exit back to
    // THRUST waits out the 5-day dwell (days 75-78 still publish CRISIS; day 79 switches).
    assertEquals(RegimeLabel.THRUST, series.getValue(days[43]).publishedLabel)
    assertEquals(RegimeLabel.CRISIS, series.getValue(days[44]).publishedLabel)
    assertEquals(RegimeLabel.THRUST, series.getValue(days[75]).rawLabel)
    assertEquals(RegimeLabel.CRISIS, series.getValue(days[78]).publishedLabel)
    assertEquals(RegimeLabel.THRUST, series.getValue(days[79]).publishedLabel)
  }

  @Test
  fun `loadReadoutSeries loads every leg from a warm-up buffer and returns only the requested window`() {
    // Given: a thrust-shaped market spanning the warm-up buffer and a one-week window
    val after = LocalDate.of(2020, 6, 1)
    val before = LocalDate.of(2020, 6, 8)
    val allDays = (0 until 200).map { after.minusDays(190).plusDays(it.toLong()) }
    whenever(stockRepository.findBySymbol(eq("SPY"), anyOrNull()))
      .thenReturn(Stock(quotes = allDays.map { StockQuote(symbol = "SPY", date = it, closePrice = 100.0) }))
    whenever(leadershipGapRepository.ewReturnByDate(any(), any(), any()))
      .thenReturn(allDays.associateWith { ewOf(it, 0.02) })
    whenever(marketBreadthRepository.findAllAsMap())
      .thenReturn(allDays.associateWith { breadthOf(it, 55.0) })

    // When
    val series = service.loadReadoutSeries(after, before)

    // Then: the equal-weight leg is loaded from well before the window (so the gap EMA has seeded),
    // the published series covers only [after, before], and the in-window read is fully seeded.
    val loadAfter = argumentCaptor<LocalDate>()
    verify(leadershipGapRepository).ewReturnByDate(loadAfter.capture(), eq(before), eq(20))
    assertTrue(loadAfter.firstValue <= after.minusDays(84))
    assertTrue(series.keys.all { !it.isBefore(after) && !it.isAfter(before) })
    assertEquals(RegimeLabel.THRUST, series.getValue(LocalDate.of(2020, 6, 3)).publishedLabel)
  }

  @Test
  fun `days before the gap EMA has seeded carry no label`() {
    // Given: the broad-thrust tape — but the first 9 gap days (EMA period 10) have no real smoothed
    // gap yet, only the seed placeholder, so no axis read on them is defensible.
    val days = dates(45)
    val spyClose = days.associateWith { 100.0 }
    val ewReturns = days.associateWith { ewOf(it, 0.02) }
    val breadth = days.associateWith { breadthOf(it, 55.0) }

    // When
    val series = service.computeReadoutSeries(spyClose, ewReturns, breadth)

    // Then: the un-seeded days (gap days 1-9, i.e. days 20-28) are unlabeled; the first seeded day
    // (day 29) reads THRUST.
    assertEquals(null, series.getValue(days[20]).rawLabel)
    assertEquals(null, series.getValue(days[28]).rawLabel)
    assertEquals(RegimeLabel.THRUST, series.getValue(days[29]).rawLabel)
  }

  @Test
  fun `an unlabeled day resets the published state and the next defensible read publishes immediately`() {
    // Given: an established thrust tape with one missing breadth row mid-stream (day 50)
    val days = dates(60)
    val spyClose = days.associateWith { 100.0 }
    val ewReturns = days.associateWith { ewOf(it, 0.02) }
    val breadth = days.filterIndexed { i, _ -> i != 50 }.associateWith { breadthOf(it, 55.0) }

    // When
    val series = service.computeReadoutSeries(spyClose, ewReturns, breadth)

    // Then: nothing is published on the gap day (fail-closed), and the next defensible day
    // re-establishes THRUST immediately — a data gap does not impose a dwell on re-entry.
    assertEquals(RegimeLabel.THRUST, series.getValue(days[49]).publishedLabel)
    assertEquals(null, series.getValue(days[50]).publishedLabel)
    assertEquals(RegimeLabel.THRUST, series.getValue(days[51]).publishedLabel)
  }

  @Test
  fun `each day's read is unchanged by future bars (no lookahead)`() {
    // Given: a 50-day thrust series, and the same series extended with 10 future days whose values
    // (cap-weight leadership + a breadth collapse) would flip the earlier reads if they leaked backward.
    val allDays = dates(60)
    val baseDays = allDays.take(50)

    fun ewMeanOn(i: Int) = if (i < 50) 0.02 else -0.05

    fun breadthPercentOn(i: Int) = if (i < 50) 55.0 else 12.0

    val baseSeries =
      service.computeReadoutSeries(
        baseDays.associateWith { 100.0 },
        baseDays.mapIndexed { i, d -> d to ewOf(d, ewMeanOn(i)) }.toMap(),
        baseDays.mapIndexed { i, d -> d to breadthOf(d, breadthPercentOn(i)) }.toMap(),
      )
    val extendedSeries =
      service.computeReadoutSeries(
        allDays.associateWith { 100.0 },
        allDays.mapIndexed { i, d -> d to ewOf(d, ewMeanOn(i)) }.toMap(),
        allDays.mapIndexed { i, d -> d to breadthOf(d, breadthPercentOn(i)) }.toMap(),
      )

    // Then: every overlapping day's full read (raw and published) is identical — causal EMA,
    // past-only washout, trailing vol/slope, forward-only dwell.
    assertTrue(baseDays.filter { baseSeries.containsKey(it) }.all { extendedSeries.getValue(it) == baseSeries.getValue(it) })
  }
}
