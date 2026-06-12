package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.RegimeLabel
import com.skrymer.udgaard.backtesting.model.RegimeReadoutParams
import com.skrymer.udgaard.backtesting.strategy.CompositeEntryStrategy
import com.skrymer.udgaard.backtesting.strategy.CompositeExitStrategy
import com.skrymer.udgaard.backtesting.strategy.condition.LogicalOperator
import com.skrymer.udgaard.backtesting.strategy.condition.entry.ADXRangeCondition
import com.skrymer.udgaard.backtesting.strategy.condition.entry.EntryConditionGroup
import com.skrymer.udgaard.backtesting.strategy.condition.entry.RegimeLabelCondition
import com.skrymer.udgaard.backtesting.strategy.condition.exit.EmaCrossExit
import com.skrymer.udgaard.backtesting.strategy.condition.exit.RegimeLabelExitCondition
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
import org.mockito.kotlin.verifyNoInteractions
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
    EwReturnDaily(quoteDate = date, meanReturn = mean, crossSectionalStdev = 0.05, contributingN = 300, medianReturn = mean)

  private fun breadthOf(date: LocalDate, percent: Double, ema10: Double = percent) =
    MarketBreadthDaily(quoteDate = date, breadthPercent = percent, ema10 = ema10)

  @Test
  fun `each labeled day exposes its axis readings for diagnostics and the current-regime line`() {
    // Given: the broad-thrust tape (flat SPY, equal-weight +2%/20 bars, breadth EMA10 = 55)
    val days = dates(45)
    val spyClose = days.associateWith { 100.0 }
    val ewReturns = days.associateWith { ewOf(it, 0.02) }
    val breadth = days.associateWith { breadthOf(it, 55.0) }

    // When
    val series = service.computeReadoutSeries(spyClose, ewReturns, breadth)

    // Then: the last day carries the raw axis values the label was decided on
    val axes = requireNotNull(series.getValue(days.last()).axes)
    assertEquals(55.0, axes.breadthLevel!!, 1e-9)
    assertEquals(0.0, axes.breadthSlope!!, 1e-9)
    assertEquals(-0.02, axes.gapSmoothed!!, 1e-9)
    assertEquals(300, axes.gapContributingN)
    assertEquals(true, axes.gapTrustworthy)
    assertEquals(0.0, axes.realizedVol!!, 1e-9)
    assertEquals(0.0, axes.direction!!, 1e-9)
    assertEquals(false, axes.washoutActive)
  }

  @Test
  fun `the gap follows the median equal-weight return, immune to a contaminated mean`() {
    // Given: flat SPY while the equal-weight MEAN screams +50% per 20 bars (micro-cap moonshots /
    // bad prints) but the MEDIAN name is at +2% — the mean-gap would read -50% (manufactured
    // THRUST); the median-gap reads -2%... here the median says equal-weight genuinely leads only
    // mildly, and with the median at -2% on the other side: use median = -0.02 -> gap = +0.02 (POS)
    val days = dates(45)
    val spyClose = days.associateWith { 100.0 }
    val ewReturns =
      days.associateWith {
        EwReturnDaily(
          quoteDate = it,
          meanReturn = 0.50,
          crossSectionalStdev = 0.05,
          contributingN = 300,
          medianReturn = -0.02,
        )
      }
    val breadth = days.associateWith { breadthOf(it, 55.0) }

    // When
    val series = service.computeReadoutSeries(spyClose, ewReturns, breadth)

    // Then: the gap reads POS off the median (+2%) — not THRUST off the contaminated mean (-50%);
    // with breadth HIGH the day falls through to CHOP
    assertEquals(RegimeLabel.CHOP, series.getValue(days.last()).rawLabel)
  }

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
  fun `a thin equal-weight cross-section still labels - the trust read is advisory, not a gate`() {
    // Given: a thrust-shaped tape whose equal-weight read rests on only 50 contributing names.
    // Blanking such days proved fail-blind in v1 (the guard breached ~30% of every year and
    // concentrated in crashes/recoveries) — the read now labels, with the thinness surfaced as a flag.
    val days = dates(45)
    val spyClose = days.associateWith { 100.0 }
    val ewReturns =
      days.associateWith {
        EwReturnDaily(
          quoteDate = it,
          meanReturn = 0.02,
          crossSectionalStdev = 0.05,
          contributingN = 50,
          medianReturn = 0.02,
        )
      }
    val breadth = days.associateWith { breadthOf(it, 55.0) }

    // When
    val series = service.computeReadoutSeries(spyClose, ewReturns, breadth)

    // Then: the day labels normally and the advisory flag marks the thin cross-section
    val day = series.getValue(days.last())
    assertEquals(RegimeLabel.THRUST, day.rawLabel)
    assertEquals(false, day.axes!!.gapTrustworthy)
  }

  @Test
  fun `wide dispersion alone no longer marks a read untrustworthy - the flag is thin-N only`() {
    // Given: a deep cross-section (300 names) with explosive dispersion — the recovery/crash shape
    // that made the v1 SE ceiling fail-blind. The median gap leg is dispersion-robust, so only a
    // genuinely thin cross-section warrants the advisory flag.
    val days = dates(45)
    val spyClose = days.associateWith { 100.0 }
    val ewReturns =
      days.associateWith {
        EwReturnDaily(
          quoteDate = it,
          meanReturn = 0.02,
          crossSectionalStdev = 0.50,
          contributingN = 300,
          medianReturn = 0.02,
        )
      }
    val breadth = days.associateWith { breadthOf(it, 55.0) }

    // When
    val series = service.computeReadoutSeries(spyClose, ewReturns, breadth)

    // Then
    assertEquals(true, series.getValue(days.last()).axes!!.gapTrustworthy)
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
  fun `the gap cut respects asymmetric NEG and POS bands`() {
    // Given: a steady gap of -2% under bands frozen at NEG <= -3% / POS >= +0.1% — the gap sits in
    // the (asymmetric) NEUTRAL zone, so the quiet tape reads GRIND rather than THRUST
    val days = dates(45)
    val spyClose = days.associateWith { 100.0 }
    val ewReturns = days.associateWith { ewOf(it, 0.02) }
    val breadth = days.associateWith { breadthOf(it, 55.0) }
    val params = RegimeReadoutParams.FROZEN.copy(gapNegBand = -0.03, gapPosBand = 0.001)

    // When
    val series = service.computeReadoutSeries(spyClose, ewReturns, breadth, params)

    // Then
    assertEquals(RegimeLabel.GRIND, series.getValue(days.last()).rawLabel)
  }

  @Test
  fun `a flat melt-up on weak breadth with cap-weight leading labels NARROW`() {
    // Given: SPY grinding inside the +-2% direction dead-band (the melt-up shape that starved
    // NARROW in v1's strict D-UP rule) while the median stock loses 3% per 20 bars -> gap POS,
    // and breadth participation weak (30)
    val days = dates(45)
    val spyClose = days.mapIndexed { i, d -> d to 100.0 * Math.pow(1.0002, i.toDouble()) }.toMap()
    val ewReturns = days.associateWith { ewOf(it, -0.03) }
    val breadth = days.associateWith { breadthOf(it, 30.0) }

    // When
    val series = service.computeReadoutSeries(spyClose, ewReturns, breadth)

    // Then: NARROW no longer demands a +2% index move — only that the tape is not falling
    assertEquals(RegimeLabel.NARROW, series.getValue(days.last()).rawLabel)
  }

  @Test
  fun `a falling tape where mega-caps merely fall less is not NARROW`() {
    // Given: a shallow decline (SPY -0.3%/day, 20-bar ~ -5.8%, drawdown well above the crisis line)
    // where the median stock falls harder (-10% per 20 bars) -> gap POS + breadth weak — the
    // bear-masquerade shape: cap-weight "leading" downward must not read as an up-tape regime
    val days = dates(45)
    val spyClose = days.mapIndexed { i, d -> d to 100.0 * Math.pow(0.997, i.toDouble()) }.toMap()
    val ewReturns = days.associateWith { ewOf(it, -0.10) }
    val breadth = days.associateWith { breadthOf(it, 30.0) }

    // When
    val series = service.computeReadoutSeries(spyClose, ewReturns, breadth)

    // Then: a genuinely falling tape falls to CHOP, never NARROW
    assertEquals(RegimeLabel.CHOP, series.getValue(days.last()).rawLabel)
  }

  @Test
  fun `a slow grinding bear reads CRISIS via the drawdown leg and dwells into publication`() {
    // Given: 270 flat days then a -1%-a-day grind with breadth weak (25) but never near the washout
    // floor — the 2000/2022 shape the washout structurally misses. The close-basis drawdown from the
    // trailing 252-day high crosses -20% on decline day 23 (0.99^23 ~ 0.794).
    val days = dates(320)
    val spyClose = days.mapIndexed { i, d -> d to if (i < 270) 100.0 else 100.0 * Math.pow(0.99, (i - 269).toDouble()) }.toMap()
    val ewReturns = days.associateWith { ewOf(it, 0.005) }
    val breadth = days.associateWith { breadthOf(it, 25.0) }

    // When
    val series = service.computeReadoutSeries(spyClose, ewReturns, breadth)

    // Then: the raw label flips to CRISIS on the day the drawdown crosses -20% (day 292) with the
    // axis exposed; publication honors the 5-day dwell (unlike washout-CRISIS, which is already a
    // sustained condition and publishes immediately).
    assertEquals(RegimeLabel.CHOP, series.getValue(days[291]).rawLabel)
    assertEquals(RegimeLabel.CRISIS, series.getValue(days[292]).rawLabel)
    assertTrue(series.getValue(days[292]).axes!!.drawdownFrom252High!! <= -0.20)
    assertEquals(RegimeLabel.CHOP, series.getValue(days[295]).publishedLabel)
    assertEquals(RegimeLabel.CRISIS, series.getValue(days[296]).publishedLabel)
  }

  @Test
  fun `loadReadoutSeries loads every leg from a warm-up buffer and returns only the requested window`() {
    // Given: a thrust-shaped market spanning the warm-up buffer and a one-week window
    val after = LocalDate.of(2020, 6, 1)
    val before = LocalDate.of(2020, 6, 8)
    val allDays = (0 until 200).map { after.minusDays(190).plusDays(it.toLong()) }
    whenever(stockRepository.findBySymbol(eq("SPY"), anyOrNull()))
      .thenReturn(Stock(quotes = allDays.map { StockQuote(symbol = "SPY", date = it, closePrice = 100.0) }))
    whenever(leadershipGapRepository.ewReturnByDate(any(), any(), any(), any()))
      .thenReturn(allDays.associateWith { ewOf(it, 0.02) })
    whenever(marketBreadthRepository.findAllAsMap())
      .thenReturn(allDays.associateWith { breadthOf(it, 55.0) })

    // When
    val series = service.loadReadoutSeries(after, before)

    // Then: the legs are loaded from a year-plus buffer before the window (the drawdown leg needs a
    // full trailing 252-bar high on day 1; the gap EMA and washout need far less), the published
    // series covers only [after, before], and the in-window read is fully seeded.
    val loadAfter = argumentCaptor<LocalDate>()
    verify(leadershipGapRepository).ewReturnByDate(loadAfter.capture(), eq(before), eq(20), eq(true))
    assertTrue(loadAfter.firstValue <= after.minusDays(370))
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
  fun `no read-out is loaded for a strategy that does not gate on a regime label`() {
    // Given: an entry/exit pair with no regime-label condition anywhere
    val entryStrategy = CompositeEntryStrategy(listOf(ADXRangeCondition()))
    val exitStrategy = CompositeExitStrategy(listOf(EmaCrossExit()))

    // When
    val map = service.loadReadoutMapIfGated(entryStrategy, exitStrategy, LocalDate.of(2020, 6, 1), LocalDate.of(2020, 7, 1))

    // Then: nothing is loaded — the read-out costs nothing unless a strategy consults it
    assertTrue(map.isEmpty())
    verifyNoInteractions(leadershipGapRepository, stockRepository, marketBreadthRepository)
  }

  @Test
  fun `a regime-label condition nested inside a group still triggers the read-out load`() {
    // Given: the regime gate sits inside an OR-group, not at the strategy's top level
    val entryStrategy =
      CompositeEntryStrategy(
        listOf(
          EntryConditionGroup(
            LogicalOperator.OR,
            listOf(ADXRangeCondition(), RegimeLabelCondition(setOf(RegimeLabel.THRUST))),
          ),
        ),
      )
    val exitStrategy = CompositeExitStrategy(listOf(EmaCrossExit()))
    val after = LocalDate.of(2020, 6, 1)
    val allDays = (0 until 200).map { after.minusDays(190).plusDays(it.toLong()) }
    whenever(stockRepository.findBySymbol(eq("SPY"), anyOrNull()))
      .thenReturn(Stock(quotes = allDays.map { StockQuote(symbol = "SPY", date = it, closePrice = 100.0) }))
    whenever(leadershipGapRepository.ewReturnByDate(any(), any(), any(), any()))
      .thenReturn(allDays.associateWith { ewOf(it, 0.02) })
    whenever(marketBreadthRepository.findAllAsMap())
      .thenReturn(allDays.associateWith { breadthOf(it, 55.0) })

    // When
    val map = service.loadReadoutMapIfGated(entryStrategy, exitStrategy, after, LocalDate.of(2020, 6, 8))

    // Then: the series is loaded for the gated strategy
    assertTrue(map.isNotEmpty())
  }

  @Test
  fun `an exit-only regime gate also triggers the read-out load`() {
    // Given: the only regime condition in the whole strategy is the exit-on-CRISIS leg
    val entryStrategy = CompositeEntryStrategy(listOf(ADXRangeCondition()))
    val exitStrategy = CompositeExitStrategy(listOf(RegimeLabelExitCondition(setOf(RegimeLabel.CRISIS))))
    val after = LocalDate.of(2020, 6, 1)
    val allDays = (0 until 200).map { after.minusDays(190).plusDays(it.toLong()) }
    whenever(stockRepository.findBySymbol(eq("SPY"), anyOrNull()))
      .thenReturn(Stock(quotes = allDays.map { StockQuote(symbol = "SPY", date = it, closePrice = 100.0) }))
    whenever(leadershipGapRepository.ewReturnByDate(any(), any(), any(), any()))
      .thenReturn(allDays.associateWith { ewOf(it, 0.02) })
    whenever(marketBreadthRepository.findAllAsMap())
      .thenReturn(allDays.associateWith { breadthOf(it, 55.0) })

    // When
    val map = service.loadReadoutMapIfGated(entryStrategy, exitStrategy, after, LocalDate.of(2020, 6, 8))

    // Then
    assertTrue(map.isNotEmpty())
  }

  @Test
  fun `each day's read is unchanged by future bars (no lookahead)`() {
    // Given: a 50-day series whose SPY declines into the drawdown-CRISIS zone from day 30 (so the
    // drawdown leg participates in the guard), extended with 10 future days of deeper collapse
    // (cap-weight leadership + a breadth washout + new lows) that would flip the earlier reads —
    // including the trailing-high drawdowns — if any leaked backward.
    val allDays = dates(60)
    val baseDays = allDays.take(50)

    fun spyCloseOn(i: Int) = if (i < 30) 100.0 else 100.0 * Math.pow(0.985, (i - 29).toDouble())

    fun ewMeanOn(i: Int) = if (i < 50) 0.02 else -0.05

    fun breadthPercentOn(i: Int) = if (i < 50) 55.0 else 12.0

    val baseSeries =
      service.computeReadoutSeries(
        baseDays.mapIndexed { i, d -> d to spyCloseOn(i) }.toMap(),
        baseDays.mapIndexed { i, d -> d to ewOf(d, ewMeanOn(i)) }.toMap(),
        baseDays.mapIndexed { i, d -> d to breadthOf(d, breadthPercentOn(i)) }.toMap(),
      )
    val extendedSeries =
      service.computeReadoutSeries(
        allDays.mapIndexed { i, d -> d to spyCloseOn(i) }.toMap(),
        allDays.mapIndexed { i, d -> d to ewOf(d, ewMeanOn(i)) }.toMap(),
        allDays.mapIndexed { i, d -> d to breadthOf(d, breadthPercentOn(i)) }.toMap(),
      )

    // Then: every overlapping day's full read (raw, published, and axes incl. the drawdown) is
    // identical — causal EMA, past-only washout, trailing vol/slope/drawdown, forward-only dwell.
    assertTrue(baseDays.filter { baseSeries.containsKey(it) }.all { extendedSeries.getValue(it) == baseSeries.getValue(it) })
    assertTrue(baseSeries.values.any { (it.axes?.drawdownFrom252High ?: 0.0) <= -0.20 })
  }
}
