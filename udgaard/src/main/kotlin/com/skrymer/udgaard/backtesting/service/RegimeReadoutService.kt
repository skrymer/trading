package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.RegimeAxes
import com.skrymer.udgaard.backtesting.model.RegimeLabel
import com.skrymer.udgaard.backtesting.model.RegimeReadoutDaily
import com.skrymer.udgaard.backtesting.model.RegimeReadoutParams
import com.skrymer.udgaard.backtesting.strategy.CompositeEntryStrategy
import com.skrymer.udgaard.backtesting.strategy.CompositeExitStrategy
import com.skrymer.udgaard.backtesting.strategy.EntryStrategy
import com.skrymer.udgaard.backtesting.strategy.ExitStrategy
import com.skrymer.udgaard.backtesting.strategy.condition.entry.RegimeLabelCondition
import com.skrymer.udgaard.backtesting.strategy.condition.exit.RegimeLabelExitCondition
import com.skrymer.udgaard.data.model.EwReturnDaily
import com.skrymer.udgaard.data.model.MarketBreadthDaily
import com.skrymer.udgaard.data.repository.LeadershipGapRepository
import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import com.skrymer.udgaard.data.service.TechnicalIndicatorService
import org.springframework.stereotype.Service
import java.time.LocalDate
import kotlin.math.sqrt

/**
 * Computes the pre-registered 5-label regime read-out (ADR 0023): per trading day, which of the
 * five canonical market regimes the tape is in. Strategy-blind by construction — every input is a
 * market series; no strategy result ever feeds it.
 */
@Service
class RegimeReadoutService(
  private val technicalIndicatorService: TechnicalIndicatorService,
  private val leadershipGapRepository: LeadershipGapRepository,
  private val stockRepository: StockJooqRepository,
  private val marketBreadthRepository: MarketBreadthRepository,
) {
  /**
   * Load every market leg the read-out needs from a warm-up buffer before [after] (so the gap EMA,
   * the washout window, and the dwell have all seeded by the time the window opens) and return the
   * series trimmed to `[after, before]` — callers never see warm-up bars.
   */
  fun loadReadoutSeries(
    after: LocalDate,
    before: LocalDate,
    params: RegimeReadoutParams = RegimeReadoutParams.FROZEN,
  ): Map<LocalDate, RegimeReadoutDaily> {
    val loadAfter = after.minusDays(WARMUP_CALENDAR_DAYS)
    val spyCloseByDate =
      stockRepository
        .findBySymbol(MARKET_BENCHMARK_SYMBOL, quotesAfter = loadAfter)
        ?.quotes
        ?.associate { it.date to it.closePrice }
        ?: emptyMap()
    val ewReturnByDate = leadershipGapRepository.ewReturnByDate(loadAfter, before, params.lookbackBars)
    val breadthByDate = marketBreadthRepository.findAllAsMap()
    return computeReadoutSeries(spyCloseByDate, ewReturnByDate, breadthByDate, params)
      .filterKeys { !it.isBefore(after) && !it.isAfter(before) }
  }

  /**
   * The read-out series for a backtest, loaded only when the strategy actually gates on a regime
   * label (entry or exit, including conditions nested in groups) — otherwise an empty map at zero
   * cost. The backtest engines call this at context-build time.
   */
  fun loadReadoutMapIfGated(
    entryStrategy: EntryStrategy,
    exitStrategy: ExitStrategy,
    after: LocalDate,
    before: LocalDate,
  ): Map<LocalDate, RegimeReadoutDaily> {
    val entryGates =
      (entryStrategy as? CompositeEntryStrategy)?.getConditions()?.any { it is RegimeLabelCondition } ?: false
    val exitGates =
      (exitStrategy as? CompositeExitStrategy)?.getConditions()?.any { it is RegimeLabelExitCondition } ?: false
    if (!entryGates && !exitGates) return emptyMap()
    return loadReadoutSeries(after, before)
  }

  /**
   * Build the read-out series over every date where the gap legs are defined. The decision table
   * is evaluated top-down, first match wins; a day failing any fail-closed guard carries no label.
   */
  fun computeReadoutSeries(
    spyCloseByDate: Map<LocalDate, Double>,
    ewReturnByDate: Map<LocalDate, EwReturnDaily>,
    breadthByDate: Map<LocalDate, MarketBreadthDaily>,
    params: RegimeReadoutParams = RegimeReadoutParams.FROZEN,
  ): Map<LocalDate, RegimeReadoutDaily> {
    val spyReturnByDate = nBarReturns(spyCloseByDate, params.lookbackBars)
    val spyVolByDate = annualizedRealizedVol(spyCloseByDate, params.lookbackBars)
    val gapDates = spyReturnByDate.keys.intersect(ewReturnByDate.keys).sorted()
    if (gapDates.isEmpty()) return emptyMap()

    val gaps = gapDates.map { spyReturnByDate.getValue(it) - ewReturnByDate.getValue(it).meanReturn }
    val gapSmoothed = technicalIndicatorService.calculateEMA(gaps, params.emaPeriod)
    val washoutIndex = WashoutIndex(breadthByDate, params)
    val breadthSlopeByDate = breadthSlopes(breadthByDate, params.slopeBars)

    val axesByDay =
      gapDates.mapIndexed { index, date ->
        val ew = ewReturnByDate.getValue(date)
        // Sample stdev (and thus the standard error of the mean) is undefined for fewer than two names.
        val standardError =
          if (ew.contributingN > 1) ew.crossSectionalStdev / sqrt(ew.contributingN.toDouble()) else null
        RegimeAxes(
          breadthLevel = breadthByDate[date]?.ema10,
          breadthSlope = breadthSlopeByDate[date],
          // The EMA's first period-1 slots are seed placeholders, not real smoothed values — a gap
          // axis read on them would be fabricated (0.0 sits inside the dead-band).
          gapSmoothed = if (index >= params.emaPeriod - 1) gapSmoothed[index] else null,
          gapStandardError = standardError,
          gapContributingN = ew.contributingN,
          gapTrustworthy =
            ew.contributingN >= params.minTrustworthyN &&
              standardError != null &&
              standardError < params.maxTrustworthyStandardError,
          realizedVol = spyVolByDate[date],
          direction = spyReturnByDate.getValue(date),
          washoutActive = washoutIndex.activeAt(date),
        )
      }
    val rawLabels = axesByDay.map { rawLabel(decisionAxes(it, params)) }

    val publishedLabels = publish(rawLabels, params)
    val series = LinkedHashMap<LocalDate, RegimeReadoutDaily>(gapDates.size)
    gapDates.forEachIndexed { index, date ->
      series[date] =
        RegimeReadoutDaily(
          quoteDate = date,
          rawLabel = rawLabels[index],
          publishedLabel = publishedLabels[index],
          axes = axesByDay[index],
        )
    }
    return series
  }

  /**
   * One day's axis reads, reduced to the exact primitives the decision table consumes.
   * [defensible] is the fail-closed guard: false when any input the table needs is missing or the
   * equal-weight cross-section is too thin/noisy to trust.
   */
  private data class DayAxes(
    val defensible: Boolean,
    val washoutActive: Boolean,
    val gapNegative: Boolean,
    val gapPositive: Boolean,
    val gapNeutral: Boolean,
    val directionUp: Boolean,
    val directionDown: Boolean,
    val volLow: Boolean,
    val slopeFalling: Boolean,
    val breadthHighOrRising: Boolean,
    val breadthWeakOrFalling: Boolean,
    val breadthAboveWeak: Boolean,
  )

  private fun decisionAxes(
    readings: RegimeAxes,
    params: RegimeReadoutParams,
  ): DayAxes {
    val breadthLevel = readings.breadthLevel
    val gapSmoothed = readings.gapSmoothed
    val gapNegative = gapSmoothed != null && gapSmoothed <= -params.gapDeadBand
    val gapPositive = gapSmoothed != null && gapSmoothed >= params.gapDeadBand
    val slope = readings.breadthSlope
    val slopeRising = slope != null && slope >= params.slopeBand
    val slopeFalling = slope != null && slope <= -params.slopeBand
    val direction = readings.direction
    return DayAxes(
      defensible = readings.gapTrustworthy == true && gapSmoothed != null && breadthLevel != null,
      washoutActive = readings.washoutActive,
      gapNegative = gapNegative,
      gapPositive = gapPositive,
      gapNeutral = gapSmoothed != null && !gapNegative && !gapPositive,
      directionUp = direction != null && direction >= params.directionDeadBand,
      directionDown = direction != null && direction <= -params.directionDeadBand,
      volLow = readings.realizedVol != null && readings.realizedVol <= params.volLowBand,
      slopeFalling = slopeFalling,
      breadthHighOrRising = breadthLevel != null && (breadthLevel >= params.breadthHighBand || slopeRising),
      breadthWeakOrFalling = breadthLevel != null && (breadthLevel <= params.breadthWeakBand || slopeFalling),
      breadthAboveWeak = breadthLevel != null && breadthLevel > params.breadthWeakBand,
    )
  }

  /** The pre-registered decision table: precedence top-down, first match wins, fail-closed first. */
  private fun rawLabel(axes: DayAxes): RegimeLabel? =
    when {
      !axes.defensible -> null
      axes.washoutActive -> RegimeLabel.CRISIS
      axes.breadthHighOrRising && axes.gapNegative -> RegimeLabel.THRUST
      axes.directionUp && axes.gapPositive && axes.breadthWeakOrFalling -> RegimeLabel.NARROW
      axes.gapNeutral && axes.volLow && axes.breadthAboveWeak && !axes.slopeFalling && !axes.directionDown -> RegimeLabel.GRIND
      else -> RegimeLabel.CHOP
    }

  /**
   * Debounce raw labels into published ones: a new label must persist [RegimeReadoutParams.dwellDays]
   * consecutive raw days before the published label switches — except entry into CRISIS, which
   * publishes immediately (a crisis is the one regime where a reporting lag is severe; the washout
   * is already a sustained condition, so it needs no further debounce). An unlabeled day publishes
   * nothing and breaks any pending streak (fail-closed); the first defensible label publishes
   * immediately.
   */
  private fun publish(
    rawLabels: List<RegimeLabel?>,
    params: RegimeReadoutParams,
  ): List<RegimeLabel?> {
    var published: RegimeLabel? = null
    var pending: RegimeLabel? = null
    var pendingCount = 0
    return rawLabels.map { raw ->
      when {
        raw == null -> {
          published = null
          pending = null
          pendingCount = 0
        }
        raw == RegimeLabel.CRISIS || published == null || raw == published -> {
          published = raw
          pending = null
          pendingCount = 0
        }
        else -> {
          if (raw == pending) {
            pendingCount++
          } else {
            pending = raw
            pendingCount = 1
          }
          if (pendingCount >= params.dwellDays) {
            published = raw
            pending = null
            pendingCount = 0
          }
        }
      }
      published
    }
  }

  /**
   * Precomputed sustained-washout reads: true at a date when breadth held at or below the crisis
   * floor for at least [RegimeReadoutParams.washoutConsecutiveDays] consecutive readings within the
   * trailing [RegimeReadoutParams.washoutLookbackDays] readings ending on (and including) that date.
   * Verbatim the frozen sustained-washout definition; future readings are never visible. Built once
   * per series so each day's read is a bounded window scan, not a full-history copy.
   */
  private class WashoutIndex(
    breadthByDate: Map<LocalDate, MarketBreadthDaily>,
    private val params: RegimeReadoutParams,
  ) {
    private val dates = breadthByDate.keys.sorted()
    private val runLengths =
      IntArray(dates.size).also { runs ->
        dates.forEachIndexed { i, date ->
          val subThreshold = breadthByDate.getValue(date).breadthPercent <= params.washoutThreshold
          runs[i] = if (subThreshold) (if (i > 0) runs[i - 1] else 0) + 1 else 0
        }
      }

    fun activeAt(asOf: LocalDate): Boolean {
      val searched = dates.binarySearch(asOf)
      val last = if (searched >= 0) searched else -(searched + 1) - 1
      if (last < 0) return false
      val windowStart = maxOf(0, last - params.washoutLookbackDays + 1)
      // A run ending at j counts only with the readings of it that fall inside the window.
      val longestInWindow = (windowStart..last).maxOf { j -> minOf(runLengths[j], j - windowStart + 1) }
      return longestInWindow >= params.washoutConsecutiveDays
    }
  }

  /**
   * Breadth-level slope: `ema10[t] - ema10[t - slopeBars]`, counted in breadth bars. The first
   * [slopeBars] dates have no prior bar and are omitted.
   */
  private fun breadthSlopes(
    breadthByDate: Map<LocalDate, MarketBreadthDaily>,
    slopeBars: Int,
  ): Map<LocalDate, Double> {
    val sortedDates = breadthByDate.keys.sorted()
    val result = LinkedHashMap<LocalDate, Double>()
    for (index in slopeBars until sortedDates.size) {
      result[sortedDates[index]] =
        breadthByDate.getValue(sortedDates[index]).ema10 - breadthByDate.getValue(sortedDates[index - slopeBars]).ema10
    }
    return result
  }

  /**
   * Annualized realized volatility: sample stdev of the trailing [lookback] daily simple returns,
   * scaled by sqrt(252). Defined only once [lookback] daily returns exist (i.e. [lookback] + 1 closes).
   */
  private fun annualizedRealizedVol(
    closeByDate: Map<LocalDate, Double>,
    lookback: Int,
  ): Map<LocalDate, Double> {
    val sortedDates = closeByDate.keys.sorted()
    val dailyReturns =
      sortedDates.zipWithNext().mapNotNull { (previous, current) ->
        val priorClose = closeByDate.getValue(previous)
        if (priorClose > 0.0) current to closeByDate.getValue(current) / priorClose - 1.0 else null
      }
    val result = LinkedHashMap<LocalDate, Double>()
    for (index in lookback - 1 until dailyReturns.size) {
      val window = dailyReturns.subList(index - lookback + 1, index + 1).map { it.second }
      val mean = window.average()
      val variance = window.sumOf { (it - mean) * (it - mean) } / (window.size - 1)
      result[dailyReturns[index].first] = sqrt(variance) * sqrt(TRADING_DAYS_PER_YEAR)
    }
    return result
  }

  /**
   * Trailing [lookback]-bar simple returns: `close[t]/close[t-lookback] - 1`, counted in bars, not
   * calendar days. The first [lookback] dates have no prior bar and are omitted; a non-positive
   * prior close is skipped (guards against a bad print dividing the return).
   */
  private fun nBarReturns(
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

  companion object {
    private const val TRADING_DAYS_PER_YEAR = 252.0

    /** Cap-weighted market benchmark: the SPY leg of the gap, the vol series, and the direction axis. */
    const val MARKET_BENCHMARK_SYMBOL = "SPY"

    /**
     * Calendar days of warm-up loaded before the window: ~20 bars (returns/vol) + ~10 bars (EMA
     * seed) + the 40-reading washout window + the dwell, with a comfortable seeded buffer.
     */
    const val WARMUP_CALENDAR_DAYS = 180L
  }
}
