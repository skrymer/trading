package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.RegimeAxes
import com.skrymer.udgaard.backtesting.model.RegimeLabel
import com.skrymer.udgaard.backtesting.model.RegimeReadoutDaily
import com.skrymer.udgaard.backtesting.model.RegimeReadoutParams
import com.skrymer.udgaard.backtesting.model.RegimeReadoutParamsV3
import com.skrymer.udgaard.backtesting.strategy.CompositeEntryStrategy
import com.skrymer.udgaard.backtesting.strategy.CompositeExitStrategy
import com.skrymer.udgaard.backtesting.strategy.EntryStrategy
import com.skrymer.udgaard.backtesting.strategy.ExitStrategy
import com.skrymer.udgaard.backtesting.strategy.condition.entry.RegimeLabelCondition
import com.skrymer.udgaard.backtesting.strategy.condition.exit.RegimeLabelExitCondition
import com.skrymer.udgaard.data.model.EwReturnDaily
import com.skrymer.udgaard.data.model.MarketBreadthDaily
import com.skrymer.udgaard.data.model.StockQuote
import com.skrymer.udgaard.data.repository.LeadershipGapRepository
import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import com.skrymer.udgaard.data.service.TechnicalIndicatorService
import org.springframework.stereotype.Service
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.log10
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
    val ewReturnByDate =
      leadershipGapRepository.ewReturnByDate(loadAfter, before, params.lookbackBars, includePercentiles = true)
    val breadthByDate = marketBreadthRepository.findAllAsMap()
    return computeReadoutSeries(spyCloseByDate, ewReturnByDate, breadthByDate, params)
      .filterKeys { !it.isBefore(after) && !it.isAfter(before) }
  }

  /**
   * The v3 trend-efficiency series (ADR 0028) over `[after, before]`, warm-up-buffered like
   * [loadReadoutSeries] but keeping the full SPY OHLC quotes the Choppiness leg needs. Provisional
   * candidate, exposed only for validation — never the production read-out.
   */
  fun loadReadoutSeriesV3(
    after: LocalDate,
    before: LocalDate,
    v2Params: RegimeReadoutParams = RegimeReadoutParams.FROZEN,
    v3Params: RegimeReadoutParamsV3 = RegimeReadoutParamsV3.FROZEN,
  ): Map<LocalDate, RegimeReadoutDaily> {
    val loadAfter = after.minusDays(WARMUP_CALENDAR_DAYS)
    val spyQuotes = stockRepository.findBySymbol(MARKET_BENCHMARK_SYMBOL, quotesAfter = loadAfter)?.quotes ?: emptyList()
    val ewReturnByDate =
      leadershipGapRepository.ewReturnByDate(loadAfter, before, v2Params.lookbackBars, includePercentiles = true)
    val breadthByDate = marketBreadthRepository.findAllAsMap()
    return computeReadoutSeriesV3(spyQuotes, ewReturnByDate, breadthByDate, v2Params, v3Params)
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
   * The multi-week SPY Choppiness Index (ADR 0028, the v3 trend-efficiency leg): per day,
   * `100 · log10(Σ trueRange / (max high − min low)) / log10(window)` over the trailing [window]
   * SPY bars. 0 = a perfectly efficient trend (path length equals net distance); 100 = a perfectly
   * choppy range (path length is [window]× the net distance). Computed on the split+dividend-adjusted
   * `(high, low, closePrice)` — all on one basis. A day is omitted (undefined, treated as not-efficient
   * by the overlay) until a full window has seeded, or when a bad print makes a bar non-positive or
   * collapses the window's high-low span to zero.
   */
  fun choppinessIndexByDate(
    spyQuotes: List<StockQuote>,
    window: Int = RegimeReadoutParamsV3.FROZEN.efficiencyWindowBars,
  ): Map<LocalDate, Double> {
    require(window > 1) { "Choppiness window must be > 1 (log10(window) is the denominator), got $window" }
    val sorted = spyQuotes.sortedBy { it.date }
    val result = LinkedHashMap<LocalDate, Double>()
    for (index in window until sorted.size) {
      val chop = choppinessForWindow(sorted, index, window) ?: continue
      result[sorted[index].date] = chop
    }
    return result
  }

  /**
   * The Choppiness Index for the single trailing [window] ending at [index] (inclusive), or null
   * when the window has not seeded into a defined value — a non-positive high/low/prior-close bad
   * print (the prior close enters the true range, so it is guarded like the other legs), or a
   * collapsed (non-positive) high-low span.
   */
  private fun choppinessForWindow(
    sorted: List<StockQuote>,
    index: Int,
    window: Int,
  ): Double? {
    var trueRangeSum = 0.0
    var maxHigh = Double.NEGATIVE_INFINITY
    var minLow = Double.POSITIVE_INFINITY
    for (i in (index - window + 1)..index) {
      val bar = sorted[i]
      val priorClose = sorted[i - 1].closePrice
      if (bar.high <= 0.0 || bar.low <= 0.0 || priorClose <= 0.0) return null
      trueRangeSum += maxOf(bar.high - bar.low, abs(bar.high - priorClose), abs(bar.low - priorClose))
      if (bar.high > maxHigh) maxHigh = bar.high
      if (bar.low < minLow) minLow = bar.low
    }
    val span = maxHigh - minLow
    if (span <= 0.0) return null
    return 100.0 * log10(trueRangeSum / span) / log10(window.toDouble())
  }

  /**
   * The Kaufman Efficiency Ratio (ADR 0028, advisory only): `|close[t] − close[t−window]| /
   * Σ|close[i] − close[i−1]|` over the trailing [window] SPY bars — net distance over total path,
   * 1 = a perfectly efficient trend, 0 = a directionless whipsaw. Surfaced for observability beside
   * the Choppiness Index; never an input to a label (the overlay keys only on Choppiness). A day is
   * omitted until the window has seeded, on a non-positive close, or on a zero total path.
   */
  fun kaufmanEfficiencyRatioByDate(
    spyQuotes: List<StockQuote>,
    window: Int = RegimeReadoutParamsV3.FROZEN.efficiencyWindowBars,
  ): Map<LocalDate, Double> {
    require(window > 1) { "Efficiency-ratio window must be > 1, got $window" }
    val sorted = spyQuotes.sortedBy { it.date }
    val result = LinkedHashMap<LocalDate, Double>()
    for (index in window until sorted.size) {
      var pathSum = 0.0
      var badPrint = false
      for (i in (index - window + 1)..index) {
        val close = sorted[i].closePrice
        val priorClose = sorted[i - 1].closePrice
        if (close <= 0.0 || priorClose <= 0.0) {
          badPrint = true
          break
        }
        pathSum += abs(close - priorClose)
      }
      if (badPrint || pathSum <= 0.0) continue
      result[sorted[index].date] = abs(sorted[index].closePrice - sorted[index - window].closePrice) / pathSum
    }
    return result
  }

  /**
   * The v3 trend-efficiency overlay (ADR 0028): demote whole low-efficiency v2-GRIND spells to CHOP.
   * Walks the published-label sequence (ordered by date, parallel to [efficient]); for each maximal
   * run of consecutive GRIND days it keeps GRIND only when the efficient fraction is a strict
   * majority (`> majorityThreshold` — ties demote), otherwise relabels the whole run to CHOP. Every
   * other label is copied verbatim, so the CRISIS/THRUST/NARROW backbone is byte-identical and the
   * result satisfies `v3-GRIND ⊆ v2-GRIND`. Operates on the published stream, never the raw labels —
   * efficiency only filters the up-tape GRIND the v2 directional legs already claimed.
   *
   * NON-CAUSAL by design: a spell's first day is relabeled on the whole spell's efficiency, so the
   * demoted stream is retrospective, not point-in-time. This is sound for the descriptive read-out
   * and its anchor/surrogate/OOS validation, but it is why v3 must NOT be wired to anything that
   * consumes the published label as-of an entry date (the per-trade decomposition, regime gating)
   * without first converting the overlay to a causal as-of rule (ADR 0028 — descriptive-only).
   */
  fun demoteInefficientGrindSpells(
    publishedLabels: List<RegimeLabel?>,
    efficient: List<Boolean>,
    majorityThreshold: Double = RegimeReadoutParamsV3.FROZEN.grindMajorityThreshold,
  ): List<RegimeLabel?> {
    require(efficient.size == publishedLabels.size) {
      "efficient (${efficient.size}) must be parallel to publishedLabels (${publishedLabels.size})"
    }
    val result = publishedLabels.toMutableList()
    var runStart = 0
    while (runStart < publishedLabels.size) {
      if (publishedLabels[runStart] != RegimeLabel.GRIND) {
        runStart++
        continue
      }
      var runEnd = runStart
      while (runEnd + 1 < publishedLabels.size && publishedLabels[runEnd + 1] == RegimeLabel.GRIND) runEnd++
      val length = runEnd - runStart + 1
      val efficientCount = (runStart..runEnd).count { efficient[it] }
      if (efficientCount.toDouble() / length <= majorityThreshold) {
        for (index in runStart..runEnd) result[index] = RegimeLabel.CHOP
      }
      runStart = runEnd + 1
    }
    return result
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
    val drawdownByDate = drawdownFromTrailingHigh(spyCloseByDate, params.ddLookbackBars)
    val gapDates = spyReturnByDate.keys.intersect(ewReturnByDate.keys).sorted()
    if (gapDates.isEmpty()) return emptyMap()

    // The equal-weight leg is the MEDIAN per-name return: immune to the micro-cap moonshots/bad
    // prints that contaminate the mean, and stationary across year-over-year tail-quality drift.
    val gaps = gapDates.map { spyReturnByDate.getValue(it) - ewReturnByDate.getValue(it).medianReturn }
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
          // Thin-N only — a dispersion ceiling proved fail-blind (dispersion explodes in exactly
          // the crash/recovery tape the read-out must label), and the median gap leg is
          // dispersion-robust anyway. The raw IQR is exposed for the analyst.
          gapTrustworthy = ew.contributingN >= params.minTrustworthyN,
          ewMedianReturn = ew.medianReturn,
          ewIqr = ew.iqr,
          realizedVol = spyVolByDate[date],
          direction = spyReturnByDate.getValue(date),
          washoutActive = washoutIndex.activeAt(date),
          drawdownFrom252High = drawdownByDate[date],
        )
      }
    val rawLabels = axesByDay.map { rawLabel(decisionAxes(it, params)) }

    val publishedLabels = publish(rawLabels, axesByDay.map { it.washoutActive }, params)
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
   * The v3 trend-efficiency read-out (ADR 0028): the frozen v2 series, post-processed so that whole
   * low-efficiency GRIND spells read CHOP. Runs the entire v2 pipeline byte-identically, computes the
   * SPY Choppiness Index, and demotes via [demoteInefficientGrindSpells]; the CRISIS/THRUST/NARROW
   * backbone and every raw label are copied verbatim. Provisional candidate, never the production
   * read-out — exposed only for validation (the `version=V3` read). The demoted published stream is
   * retrospective (see [demoteInefficientGrindSpells]) — descriptive-only, not an as-of signal.
   */
  fun computeReadoutSeriesV3(
    spyQuotes: List<StockQuote>,
    ewReturnByDate: Map<LocalDate, EwReturnDaily>,
    breadthByDate: Map<LocalDate, MarketBreadthDaily>,
    v2Params: RegimeReadoutParams = RegimeReadoutParams.FROZEN,
    v3Params: RegimeReadoutParamsV3 = RegimeReadoutParamsV3.FROZEN,
  ): Map<LocalDate, RegimeReadoutDaily> {
    val spyCloseByDate = spyQuotes.associate { it.date to it.closePrice }
    val v2Series = computeReadoutSeries(spyCloseByDate, ewReturnByDate, breadthByDate, v2Params)
    val choppiness = choppinessIndexByDate(spyQuotes, v3Params.efficiencyWindowBars)
    val efficiencyRatio = kaufmanEfficiencyRatioByDate(spyQuotes, v3Params.efficiencyWindowBars)
    val orderedDates = v2Series.keys.toList()
    val publishedV2 = orderedDates.map { v2Series.getValue(it).publishedLabel }
    val efficientFlags =
      orderedDates.map { date ->
        val ci = choppiness[date]
        ci != null && ci <= v3Params.choppinessEfficientBand
      }
    val publishedV3 = demoteInefficientGrindSpells(publishedV2, efficientFlags, v3Params.grindMajorityThreshold)
    val series = LinkedHashMap<LocalDate, RegimeReadoutDaily>(orderedDates.size)
    orderedDates.forEachIndexed { index, date ->
      val v2Daily = v2Series.getValue(date)
      series[date] =
        v2Daily.copy(
          publishedLabel = publishedV3[index],
          axes = v2Daily.axes?.copy(choppinessIndex30 = choppiness[date], efficiencyRatio30 = efficiencyRatio[date]),
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
    val ddCrisis: Boolean,
    val gapNegative: Boolean,
    val gapPositive: Boolean,
    val gapNeutral: Boolean,
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
    val gapNegative = gapSmoothed != null && gapSmoothed <= params.gapNegBand
    val gapPositive = gapSmoothed != null && gapSmoothed >= params.gapPosBand
    val slope = readings.breadthSlope
    val slopeRising = slope != null && slope >= params.slopeBand
    val slopeFalling = slope != null && slope <= -params.slopeBand
    val direction = readings.direction
    return DayAxes(
      // gapTrustworthy is advisory only — a fail-closed dispersion guard proved fail-blind in v1
      // (it breached ~30% of every year, concentrated in crashes/recoveries). Null label remains
      // for the genuinely-undefined cases: un-seeded gap EMA, missing breadth.
      defensible = gapSmoothed != null && breadthLevel != null,
      washoutActive = readings.washoutActive,
      ddCrisis =
        readings.drawdownFrom252High != null &&
          readings.drawdownFrom252High <= params.ddCrisisThreshold,
      gapNegative = gapNegative,
      gapPositive = gapPositive,
      gapNeutral = gapSmoothed != null && !gapNegative && !gapPositive,
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
      // Two CRISIS legs: the washout senses crash velocity; the drawdown senses slow correlated
      // attrition (2000, 2022) the washout structurally misses.
      axes.washoutActive || axes.ddCrisis -> RegimeLabel.CRISIS
      axes.breadthHighOrRising && axes.gapNegative -> RegimeLabel.THRUST
      // "Not falling" rather than "up": a strict +2% leg starves NARROW in melt-ups that grind
      // inside the direction dead-band, while the not-DOWN guard still refuses the bear-masquerade
      // (mega-caps falling less than the median stock reads gap POS in a declining tape).
      !axes.directionDown && axes.gapPositive && axes.breadthWeakOrFalling -> RegimeLabel.NARROW
      axes.gapNeutral && axes.volLow && axes.breadthAboveWeak && !axes.slopeFalling && !axes.directionDown -> RegimeLabel.GRIND
      else -> RegimeLabel.CHOP
    }

  /**
   * Debounce raw labels into published ones: a new label must persist [RegimeReadoutParams.dwellDays]
   * consecutive raw days before the published label switches — except entry into washout-CRISIS,
   * which publishes immediately (the washout is already a sustained condition needing no further
   * debounce, and a crash is where reporting lag hurts most). Drawdown-CRISIS dwells like any other
   * label: a single-day -20% touch must not flip the published stream. An unlabeled day publishes
   * nothing and breaks any pending streak (fail-closed); the first defensible label publishes
   * immediately.
   */
  private fun publish(
    rawLabels: List<RegimeLabel?>,
    washoutByDay: List<Boolean>,
    params: RegimeReadoutParams,
  ): List<RegimeLabel?> {
    var published: RegimeLabel? = null
    var pending: RegimeLabel? = null
    var pendingCount = 0
    return rawLabels.mapIndexed { index, raw ->
      when {
        raw == null -> {
          published = null
          pending = null
          pendingCount = 0
        }
        (raw == RegimeLabel.CRISIS && washoutByDay[index]) || published == null || raw == published -> {
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
   * Close-basis drawdown from the trailing [lookback]-bar high (including the current bar):
   * `close[t] / max(close[t-lookback+1 .. t]) - 1`. A partial early window uses the bars available —
   * the leg only ever ADDS crises, so an early under-stated high can never blank a day, and the
   * production warm-up loads a full year before the window anyway.
   */
  private fun drawdownFromTrailingHigh(
    closeByDate: Map<LocalDate, Double>,
    lookback: Int,
  ): Map<LocalDate, Double> {
    val sortedDates = closeByDate.keys.sorted()
    val closes = sortedDates.map { closeByDate.getValue(it) }
    val result = LinkedHashMap<LocalDate, Double>(sortedDates.size)
    for (index in sortedDates.indices) {
      // Mirror the other legs' bad-print guard: a non-positive current close cannot price a drawdown.
      if (closes[index] <= 0.0) continue
      val windowStart = maxOf(0, index - lookback + 1)
      var high = 0.0
      for (i in windowStart..index) {
        if (closes[i] > high) high = closes[i]
      }
      if (high > 0.0) result[sortedDates[index]] = closes[index] / high - 1.0
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
     * Calendar days of warm-up loaded before the window: the drawdown leg needs a full trailing
     * 252-bar (~365 calendar-day) high on the window's first day; the gap EMA, vol window, washout
     * window, and dwell all seed well inside that.
     */
    const val WARMUP_CALENDAR_DAYS = 400L
  }
}
