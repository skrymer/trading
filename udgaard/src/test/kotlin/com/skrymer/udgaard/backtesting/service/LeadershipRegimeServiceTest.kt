package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.LeadershipRegimeParams
import com.skrymer.udgaard.data.model.EwReturnDaily
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.repository.LeadershipGapRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import com.skrymer.udgaard.data.service.TechnicalIndicatorService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
import kotlin.math.sqrt

class LeadershipRegimeServiceTest {
  private val leadershipGapRepository: LeadershipGapRepository = mock()
  private val stockRepository: StockJooqRepository = mock()
  private val service = LeadershipRegimeService(TechnicalIndicatorService(), leadershipGapRepository, stockRepository)

  private fun dates(n: Int) = (0 until n).map { LocalDate.of(2020, 1, 1).plusDays(it.toLong()) }

  private fun ewOf(date: LocalDate, mean: Double) =
    EwReturnDaily(quoteDate = date, meanReturn = mean, crossSectionalStdev = 0.20, contributingN = 300)

  private fun regimeDay(date: LocalDate, on: Boolean, n: Int = 5000, trustworthy: Boolean = true) =
    com.skrymer.udgaard.backtesting.model.LeadershipRegimeDaily(
      quoteDate = date,
      gap = 0.0,
      gapSmoothed = 0.0,
      schmittOn = on,
      washoutActive = false,
      regimeOn = on,
      contributingN = n,
      standardError = 0.001,
      trustworthy = trustworthy,
    )

  private fun seriesOf(vararg on: Boolean) =
    dates(on.size).mapIndexed { i, d -> d to regimeDay(d, on[i]) }.toMap()

  @Test
  fun `regime turns ON once smoothed gap shows equal-weight leadership past EMA seeding`() {
    // Given: 12 days where the equal-weight 20d return (2%) leads SPY's (1%) -> GAP = -1% (broad
    // thrust, the deploy signal), with breadth healthy so the crisis veto stays clear.
    val days = dates(12)
    val spyReturn20 = days.associateWith { 0.01 }
    val ewReturns =
      days.associateWith {
        EwReturnDaily(quoteDate = it, meanReturn = 0.02, crossSectionalStdev = 0.20, contributingN = 300)
      }
    val breadth = days.associateWith { 50.0 }

    // When
    val series = service.computeRegimeSeries(spyReturn20, ewReturns, breadth)

    // Then: by the last day the EMA10 of GAP has crossed below the -0.5% dead-band and the regime is ON
    assertTrue(series.getValue(days.last()).regimeOn)
  }

  @Test
  fun `a gap that never breaches the deploy threshold stays cash (default-OFF)`() {
    // Given: SPY and the equal-weight universe post identical 20d returns -> GAP = 0 everywhere,
    // which sits inside the dead-band on every bar
    val days = dates(30)
    val spyReturn20 = days.associateWith { 0.01 }
    val ewReturns = days.associateWith { ewOf(it, 0.01) }
    val breadth = days.associateWith { 50.0 }

    // When
    val series = service.computeRegimeSeries(spyReturn20, ewReturns, breadth)

    // Then: the Schmitt never trips, so the regime defaults OFF on every bar (no spurious deploy)
    assertFalse(series.values.any { it.regimeOn })
  }

  @Test
  fun `regime holds state inside the dead-band and only flips past the opposite threshold`() {
    // Given: a hand-built gap path (identity smoothing via emaPeriod=1) that goes deploy, drifts into
    // the dead-band, crosses to the cash threshold, drifts back into the band, then re-arms deploy.
    // gap is driven by ewMean = -gap with SPY return pinned at 0.
    val gapPath = listOf(-0.01, -0.01, 0.0, 0.0, 0.01, 0.01, 0.0, 0.0, -0.01)
    val days = dates(gapPath.size)
    val spyReturn20 = days.associateWith { 0.0 }
    val ewReturns = days.mapIndexed { i, d -> d to ewOf(d, -gapPath[i]) }.toMap()
    val breadth = days.associateWith { 50.0 }

    // When
    val series = service.computeRegimeSeries(spyReturn20, ewReturns, breadth, LeadershipRegimeParams.FROZEN.copy(emaPeriod = 1))

    // Then: ON is held through the dead-band, released only when the gap crosses +dead-band, then
    // OFF is held through the band until the gap crosses -dead-band again.
    assertTrue(series.getValue(days[3]).regimeOn) // drifted into band while ON -> still deploy
    assertFalse(series.getValue(days[4]).regimeOn) // crossed +dead-band -> cash
    assertFalse(series.getValue(days[7]).regimeOn) // drifted into band while OFF -> still cash
    assertTrue(series.getValue(days[8]).regimeOn) // crossed -dead-band -> deploy again
  }

  @Test
  fun `a sustained breadth washout vetoes deployment while the Schmitt stays ON`() {
    // Given: the gap says deploy on every bar (ON via identity smoothing), but breadth holds at a
    // crisis floor for 10 consecutive days (indices 5..14), the sustained-washout trigger.
    val days = dates(20)
    val spyReturn20 = days.associateWith { 0.0 }
    val ewReturns = days.mapIndexed { i, d -> d to ewOf(d, 0.01) }.toMap() // gap = -0.01 -> ON
    val breadth = days.mapIndexed { i, d -> d to if (i in 5..14) 12.0 else 50.0 }.toMap()

    // When
    val series = service.computeRegimeSeries(spyReturn20, ewReturns, breadth, LeadershipRegimeParams.FROZEN.copy(emaPeriod = 1))

    // Then: a bar before the washout deploys; the bar ending the 10-day run is vetoed to cash even
    // though the Schmitt remains ON (the two are reported separately for observability).
    val healthy = series.getValue(days[0])
    assertTrue(healthy.regimeOn)
    assertFalse(healthy.washoutActive)
    val crisis = series.getValue(days[14])
    assertTrue(crisis.schmittOn)
    assertTrue(crisis.washoutActive)
    assertFalse(crisis.regimeOn)
  }

  @Test
  fun `each bar's read is unchanged by future bars (no lookahead)`() {
    // Given: a 15-bar deploy series, and the same series extended with 5 future bars whose values
    // (mega-cap leadership + a breadth crisis) would flip the earlier reads if they leaked backward.
    val allDays = dates(20)
    val baseDays = allDays.take(15)

    fun ewMeanOn(i: Int) = if (i < 15) 0.01 else -0.05 // gap -0.01 (ON) early, +0.05 (OFF) in the future

    fun breadthOn(i: Int) = if (i < 15) 50.0 else 12.0

    val baseSeries =
      service.computeRegimeSeries(
        baseDays.associateWith { 0.0 },
        baseDays.mapIndexed { i, d -> d to ewOf(d, ewMeanOn(i)) }.toMap(),
        baseDays.associateWith { 50.0 },
      )
    val extendedSeries =
      service.computeRegimeSeries(
        allDays.associateWith { 0.0 },
        allDays.mapIndexed { i, d -> d to ewOf(d, ewMeanOn(i)) }.toMap(),
        allDays.mapIndexed { i, d -> d to breadthOn(i) }.toMap(),
      )

    // Then: every overlapping bar's full read is identical (causal EMA + Schmitt + past-only washout)
    assertTrue(baseDays.all { extendedSeries.getValue(it) == baseSeries.getValue(it) })
  }

  @Test
  fun `standard error and trustworthy flag reflect the width and breadth of the cross-section`() {
    // Given: three probe bars with distinct cross-section shapes (wide universe, too-few names, noisy)
    val days = dates(12)
    val spyReturn20 = days.associateWith { 0.0 }
    val ewReturns = days.associateWith { ewOf(it, 0.0) }.toMutableMap()
    ewReturns[days[9]] = EwReturnDaily(days[9], meanReturn = 0.0, crossSectionalStdev = 0.20, contributingN = 5000)
    ewReturns[days[10]] = EwReturnDaily(days[10], meanReturn = 0.0, crossSectionalStdev = 0.20, contributingN = 100)
    ewReturns[days[11]] = EwReturnDaily(days[11], meanReturn = 0.0, crossSectionalStdev = 0.50, contributingN = 5000)
    val breadth = days.associateWith { 50.0 }

    // When
    val series = service.computeRegimeSeries(spyReturn20, ewReturns, breadth)

    // Then: SE = cross-sectional stdev / sqrt(N); a read is trustworthy only with enough names AND a tight SE
    assertEquals(0.20 / sqrt(5000.0), series.getValue(days[9]).standardError, 1e-9)
    assertTrue(series.getValue(days[9]).trustworthy) // wide universe, tight SE
    assertFalse(series.getValue(days[10]).trustworthy) // too few names (N < 200)
    assertFalse(series.getValue(days[11]).trustworthy) // SE >= 0.5% despite many names
  }

  @Test
  fun `the series spans only dates where both the SPY and equal-weight legs are defined`() {
    // Given: SPY 20d returns exist for days 2..11, equal-weight returns for days 0..9
    val days = dates(12)
    val spyReturn20 = days.subList(2, 12).associateWith { 0.0 }
    val ewReturns = days.subList(0, 10).associateWith { ewOf(it, 0.01) }
    val breadth = days.associateWith { 50.0 }

    // When
    val series = service.computeRegimeSeries(spyReturn20, ewReturns, breadth)

    // Then: the gap (and thus the regime) is defined only on the overlap, days 2..9
    assertEquals(days.subList(2, 10).toSet(), series.keys)
  }

  @Test
  fun `nBarReturns computes the trailing simple return and omits the unpriced warm-up head`() {
    // Given: 5 daily closes and a 2-bar lookback
    val days = dates(5)
    val closes = mapOf(days[0] to 100.0, days[1] to 110.0, days[2] to 121.0, days[3] to 133.1, days[4] to 146.41)

    // When
    val returns = service.nBarReturns(closes, 2)

    // Then: r(t) = close[t]/close[t-2] - 1, and the first two bars have no 2-bar-ago close
    assertEquals(setOf(days[2], days[3], days[4]), returns.keys)
    assertEquals(121.0 / 100.0 - 1, returns.getValue(days[2]), 1e-9)
    assertEquals(133.1 / 110.0 - 1, returns.getValue(days[3]), 1e-9)
  }

  @Test
  fun `nBarReturns counts trading bars, not calendar days`() {
    // Given: three closes on irregularly spaced dates (a 5-day gap), 1-bar lookback
    val d0 = LocalDate.of(2020, 1, 1)
    val closes = mapOf(d0 to 100.0, d0.plusDays(5) to 110.0, d0.plusDays(6) to 121.0)

    // When
    val returns = service.nBarReturns(closes, 1)

    // Then: one bar back is the previous row regardless of the calendar gap between them
    assertEquals(110.0 / 100.0 - 1, returns.getValue(d0.plusDays(5)), 1e-9)
    assertEquals(121.0 / 110.0 - 1, returns.getValue(d0.plusDays(6)), 1e-9)
  }

  @Test
  fun `diagnostics report the deploy fraction and the number of regime flips`() {
    // Given: a regime series that is ON 4 of 10 days with three ON<->OFF transitions
    val series = seriesOf(true, true, true, false, false, true, false, false, false, false)

    // When
    val diagnostics = service.diagnostics(series)

    // Then
    assertEquals(0.4, diagnostics.onFraction, 1e-9)
    assertEquals(3, diagnostics.flipCount)
  }

  @Test
  fun `diagnostics report the median ON-spell and OFF-spell lengths`() {
    // Given: ON spells of length 3 and 1, OFF spells of length 2 and 4
    val series = seriesOf(true, true, true, false, false, true, false, false, false, false)

    // When
    val diagnostics = service.diagnostics(series)

    // Then: the median of {3,1} is 2, the median of {2,4} is 3 (Gate-0 reads these spell lengths)
    assertEquals(2.0, diagnostics.medianOnSpellDays, 1e-9)
    assertEquals(3.0, diagnostics.medianOffSpellDays, 1e-9)
  }

  @Test
  fun `diagnostics break the deploy fraction down by calendar year`() {
    // Given: a series spanning two years - all-deploy in 2014, mostly cash in 2015
    val series =
      listOf(
        LocalDate.of(2014, 12, 30) to true,
        LocalDate.of(2014, 12, 31) to true,
        LocalDate.of(2015, 1, 2) to false,
        LocalDate.of(2015, 1, 5) to false,
        LocalDate.of(2015, 1, 6) to true,
      ).associate { (date, on) -> date to regimeDay(date, on) }

    // When
    val byYear = service.diagnostics(series).onFractionByYear

    // Then: Gate-1 can read 2015's cash dominance directly
    assertEquals(1.0, byYear.getValue(2014), 1e-9)
    assertEquals(1.0 / 3.0, byYear.getValue(2015), 1e-9)
  }

  @Test
  fun `diagnostics count untrustworthy days and report the thinnest cross-section`() {
    // Given: four bars, two of which carry an untrustworthy regime read (thin or noisy cross-section)
    val days = dates(4)
    val series =
      mapOf(
        days[0] to regimeDay(days[0], on = true, n = 5000, trustworthy = true),
        days[1] to regimeDay(days[1], on = true, n = 150, trustworthy = false),
        days[2] to regimeDay(days[2], on = false, n = 4000, trustworthy = false),
        days[3] to regimeDay(days[3], on = false, n = 3000, trustworthy = true),
      )

    // When
    val diagnostics = service.diagnostics(series)

    // Then
    assertEquals(2, diagnostics.untrustworthyDays)
    assertEquals(150, diagnostics.minContributingN)
  }

  @Test
  fun `loadRegimeMap loads SPY and the equal-weight aggregate from a warm-up buffer before the window`() {
    // Given: stubbed loaders (the regime math itself is covered by the pure tests above)
    val after = LocalDate.of(2020, 6, 1)
    val before = LocalDate.of(2020, 7, 1)
    whenever(stockRepository.findBySymbol(eq("SPY"), anyOrNull())).thenReturn(Stock())
    whenever(leadershipGapRepository.ewReturnByDate(any(), any(), any(), any())).thenReturn(emptyMap())

    // When
    service.loadRegimeMap(after, before, emptyMap())

    // Then: both legs are loaded from well before the window (>= 60 trading days, ~>= 84 calendar days)
    // so the EMA/Schmitt and the washout veto have seeded by the time it opens.
    val loadAfter = argumentCaptor<LocalDate>()
    verify(leadershipGapRepository).ewReturnByDate(loadAfter.capture(), eq(before), eq(20), eq(false))
    assertTrue(loadAfter.firstValue <= after.minusDays(84))
    verify(stockRepository).findBySymbol(eq("SPY"), anyOrNull())
  }

  @Test
  fun `a single-name day reports an undefined standard error and is untrustworthy`() {
    // Given: one bar whose cross-section is a single name (sample stdev is undefined for N=1)
    val days = dates(12)
    val ewReturns = days.associateWith { ewOf(it, 0.0) }.toMutableMap()
    ewReturns[days[11]] = EwReturnDaily(days[11], meanReturn = 0.0, crossSectionalStdev = 0.0, contributingN = 1)

    // When
    val series = service.computeRegimeSeries(days.associateWith { 0.0 }, ewReturns, days.associateWith { 50.0 })

    // Then: the standard error of the mean is undefined (not a deceptively tight 0.0), and the read is untrustworthy
    val single = series.getValue(days[11])
    assertTrue(single.standardError.isInfinite())
    assertFalse(single.trustworthy)
  }
}
