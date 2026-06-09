package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.LeadershipRegimeDaily
import com.skrymer.udgaard.backtesting.model.LeadershipRegimeDiagnostics
import com.skrymer.udgaard.backtesting.model.LeadershipRegimeParams
import com.skrymer.udgaard.data.model.EwReturnDaily
import com.skrymer.udgaard.data.repository.LeadershipGapRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import com.skrymer.udgaard.data.service.TechnicalIndicatorService
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.SortedMap
import kotlin.math.sqrt

/**
 * Computes the leadership-gap regime series (issue #83): per trading day, whether the equal-weight
 * universe is leading the cap-weighted market (broad thrust -> deploy) or a thin tape of mega-caps
 * is carrying the index (-> stay in cash).
 *
 * The gap, its EMA smoothing, the Schmitt hysteresis, and the crisis veto are a pure function of the
 * input series ([computeRegimeSeries]); loading those series from the database is the impure shell.
 */
@Service
class LeadershipRegimeService(
  private val technicalIndicatorService: TechnicalIndicatorService,
  private val leadershipGapRepository: LeadershipGapRepository,
  private val stockRepository: StockJooqRepository,
) {
  /**
   * Load every input the regime needs and compute the full series over `[after - warm-up, before]`,
   * so the EMA/Schmitt have seeded and the washout veto has its trailing window by the time the window
   * opens. SPY and the equal-weight aggregate are loaded from a warm-up buffer ([REGIME_WARMUP_CALENDAR_DAYS]
   * before [after]); breadth is supplied by the caller (it loads the full series anyway).
   *
   * The EMA seed origin is window-relative ([after] - warm-up), so a single backtest and a walk-forward
   * block that start on different dates anchor the EMA bootstrap differently. With ~124 trading days of
   * warm-up the EMA has long converged before the window opens, so in-window reads agree across entry
   * points except, rarely, on a bar sitting exactly on the Schmitt dead-band boundary.
   */
  fun loadRegimeMap(
    after: LocalDate,
    before: LocalDate,
    breadthByDate: Map<LocalDate, Double>,
    params: LeadershipRegimeParams = LeadershipRegimeParams.FROZEN,
  ): Map<LocalDate, LeadershipRegimeDaily> {
    val regimeLoadAfter = after.minusDays(REGIME_WARMUP_CALENDAR_DAYS)
    val spyCloseByDate =
      stockRepository
        .findBySymbol(MARKET_BENCHMARK_SYMBOL, quotesAfter = regimeLoadAfter)
        ?.quotes
        ?.associate { it.date to it.closePrice }
        ?: emptyMap()
    val spyReturn20ByDate = nBarReturns(spyCloseByDate, params.lookbackBars)
    val ewReturnByDate = leadershipGapRepository.ewReturnByDate(regimeLoadAfter, before, params.lookbackBars)
    return computeRegimeSeries(spyReturn20ByDate, ewReturnByDate, breadthByDate, params)
  }

  /**
   * Trailing [lookback]-bar simple returns over a single price series (e.g. SPY): `close[t]/close[t-lookback] - 1`.
   * The lookback is counted in trading bars (rows), not calendar days, matching the equal-weight leg's
   * `LAG(close, lookback)`. The first [lookback] dates have no prior bar and are omitted; a non-positive
   * prior close is skipped (guards against a bad print dividing the return).
   */
  fun nBarReturns(
    closeByDate: Map<LocalDate, Double>,
    lookback: Int,
  ): Map<LocalDate, Double> {
    val sortedDates = closeByDate.keys.sorted()
    val result = LinkedHashMap<LocalDate, Double>()
    for (index in lookback until sortedDates.size) {
      val priorClose = closeByDate.getValue(sortedDates[index - lookback])
      if (priorClose > 0.0) {
        result[sortedDates[index]] = closeByDate.getValue(sortedDates[index]) / priorClose - 1.0
      }
    }
    return result
  }

  /**
   * Build the regime series over every date where both legs of the gap are defined.
   *
   * `GAP(t) = spyReturn20(t) - ewMean(t)`; `GAP_s = EMA(GAP)`; the Schmitt trigger holds prior state
   * inside the dead-band and seeds default-OFF at the start of the series, so the EMA warm-up bars
   * (and any pre-window warm-up buffer) resolve to cash rather than a spurious deploy. The crisis
   * veto forces cash whenever a sustained breadth washout is active on the bar.
   */
  fun computeRegimeSeries(
    spyReturn20ByDate: Map<LocalDate, Double>,
    ewReturnByDate: Map<LocalDate, EwReturnDaily>,
    breadthByDate: Map<LocalDate, Double>,
    params: LeadershipRegimeParams = LeadershipRegimeParams.FROZEN,
  ): Map<LocalDate, LeadershipRegimeDaily> {
    val gapDates = spyReturn20ByDate.keys.intersect(ewReturnByDate.keys).sorted()
    if (gapDates.isEmpty()) return emptyMap()

    val gaps = gapDates.map { spyReturn20ByDate.getValue(it) - ewReturnByDate.getValue(it).meanReturn }
    val gapSmoothed = technicalIndicatorService.calculateEMA(gaps, params.emaPeriod)
    val sortedBreadth = breadthByDate.toSortedMap()

    var schmittOn = false
    val series = LinkedHashMap<LocalDate, LeadershipRegimeDaily>(gapDates.size)
    gapDates.forEachIndexed { index, date ->
      val smoothed = gapSmoothed[index]
      schmittOn = when {
        smoothed < -params.deadBand -> true
        smoothed > params.deadBand -> false
        else -> schmittOn
      }
      val washoutActive = sustainedWashoutActive(sortedBreadth, date, params)
      val ew = ewReturnByDate.getValue(date)
      // Sample stdev (and thus the standard error of the mean) is undefined for fewer than two names.
      val standardError =
        if (ew.contributingN > 1) ew.crossSectionalStdev / sqrt(ew.contributingN.toDouble()) else Double.POSITIVE_INFINITY
      series[date] =
        LeadershipRegimeDaily(
          quoteDate = date,
          gap = gaps[index],
          gapSmoothed = smoothed,
          schmittOn = schmittOn,
          washoutActive = washoutActive,
          regimeOn = schmittOn && !washoutActive,
          contributingN = ew.contributingN,
          standardError = standardError,
          trustworthy = ew.contributingN >= params.minTrustworthyN && standardError < params.maxTrustworthyStandardError,
        )
    }
    return series
  }

  /**
   * Summarise a (typically in-window) regime series for the observability layer: the deploy fraction
   * and the number of ON<->OFF flips. Callers pass the windowed sub-series so warm-up bars do not skew it.
   */
  fun diagnostics(series: Map<LocalDate, LeadershipRegimeDaily>): LeadershipRegimeDiagnostics {
    val ordered = series.toSortedMap().values.toList()
    val onDays = ordered.count { it.regimeOn }
    val onFraction = if (ordered.isEmpty()) 0.0 else onDays.toDouble() / ordered.size
    val flipCount = ordered.zipWithNext().count { (previous, next) -> previous.regimeOn != next.regimeOn }
    val spells = spellLengths(ordered)
    return LeadershipRegimeDiagnostics(
      onFraction = onFraction,
      flipCount = flipCount,
      medianOnSpellDays = median(spells.filter { it.first }.map { it.second }),
      medianOffSpellDays = median(spells.filterNot { it.first }.map { it.second }),
      onFractionByYear =
        ordered
          .groupBy { it.quoteDate.year }
          .mapValues { (_, days) -> days.count { it.regimeOn }.toDouble() / days.size },
      untrustworthyDays = ordered.count { !it.trustworthy },
      minContributingN = ordered.minOfOrNull { it.contributingN } ?: 0,
    )
  }

  /** Maximal consecutive runs of the same deploy/cash state, as (regimeOn, length) pairs in order. */
  private fun spellLengths(ordered: List<LeadershipRegimeDaily>): List<Pair<Boolean, Int>> {
    val spells = mutableListOf<Pair<Boolean, Int>>()
    for (day in ordered) {
      val last = spells.lastOrNull()
      if (last != null && last.first == day.regimeOn) {
        spells[spells.lastIndex] = last.first to last.second + 1
      } else {
        spells.add(day.regimeOn to 1)
      }
    }
    return spells
  }

  private fun median(values: List<Int>): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid].toDouble() else (sorted[mid - 1] + sorted[mid]) / 2.0
  }

  /**
   * True when breadth held at or below the crisis floor for at least [LeadershipRegimeParams.washoutConsecutiveDays]
   * consecutive readings within the trailing [LeadershipRegimeParams.washoutLookbackDays] window ending on (and
   * including) [asOf]. Mirrors `MarketBreadthSustainedWashoutWithinCondition`; future readings are never visible.
   */
  private fun sustainedWashoutActive(
    sortedBreadth: SortedMap<LocalDate, Double>,
    asOf: LocalDate,
    params: LeadershipRegimeParams,
  ): Boolean {
    val readings = sortedBreadth
      .headMap(asOf.plusDays(1))
      .values
      .toList()
      .takeLast(params.washoutLookbackDays)
    var longest = 0
    var current = 0
    for (reading in readings) {
      if (reading <= params.washoutThreshold) {
        current++
        if (current > longest) longest = current
      } else {
        current = 0
      }
    }
    return longest >= params.washoutConsecutiveDays
  }

  companion object {
    /** Cap-weighted market benchmark whose 20-bar return is the SPY leg of the gap. */
    const val MARKET_BENCHMARK_SYMBOL = "SPY"

    /**
     * Calendar days of warm-up loaded before the window. The regime needs ~20 bars (SPY return) + ~10
     * bars (EMA seed) before its first real read, plus a comfortable seeded buffer; ~180 calendar days
     * (~124 trading days) leaves well over 60 fully-seeded trading days before the window opens.
     */
    const val REGIME_WARMUP_CALENDAR_DAYS = 180L
  }
}
