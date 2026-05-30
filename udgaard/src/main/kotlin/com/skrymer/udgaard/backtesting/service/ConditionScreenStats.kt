package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.data.model.StockQuote
import java.time.LocalDate

/**
 * The entry-anchored forward outcome of a single fired entry signal.
 *
 * `returnsByHorizon[N]` is `close[fill+N] / close[fill] - 1` where the fill bar is the signal
 * bar plus `entryDelayDays` — i.e. the return the strategy could actually earn, never the
 * signal→fill gap. A horizon whose target bar is past the end of the series maps to `null`.
 *
 * `fillGap` is the single-bar `close[fill] / close[signal] - 1` move the strategy does *not*
 * earn; it is reported separately so a condition whose edge lives entirely in the gap is visible
 * as untradeable rather than hidden inside the forward return.
 */
data class SignalForwardReturn(
  val symbol: String,
  val signalDate: LocalDate,
  val fillGap: Double?,
  val returnsByHorizon: Map<Int, Double?>,
)

/**
 * The forward-return distribution of a set of fired signals at one horizon.
 *
 * Descriptive only — `std` and `skew` are population statistics (divide by n), adequate for a
 * diagnostic and never used to imply a distributional model. `nSignals` is the measured sample
 * (signals with a non-null return at this horizon); `nDropped` is signals that fired but lacked a
 * full forward window. `nDates` is the count of distinct signal dates in the measured sample — the
 * honest effective sample size once same-day cross-sectional correlation is accounted for.
 */
data class ForwardReturnDistribution(
  val horizonDays: Int,
  val nSignals: Int,
  val nDropped: Int,
  val nDates: Int,
  val mean: Double,
  val median: Double,
  val std: Double,
  val skew: Double,
  val hitRate: Double,
)

/**
 * A date-clustered point estimate of forward return at one horizon.
 *
 * Same-day signals across symbols are driven by the same market move, so they are closer to one
 * observation than to N. `clusteredMean` averages per-date means first, then across dates;
 * `nDates` is the honest effective sample size; `stdError` is the sample std of the per-date means
 * divided by sqrt(nDates). Every lift confidence statement and the ARS swing threshold are built
 * on `stdError`, never on a naive pooled standard error.
 */
data class ClusteredEstimate(
  val horizonDays: Int,
  val clusteredMean: Double,
  val stdError: Double,
  val nDates: Int,
)

/**
 * The full forward-return picture of a signal set at one horizon: the descriptive distribution
 * plus its date-clustered estimate. The two share the same measured sample.
 */
data class ForwardReturnSummary(
  val distribution: ForwardReturnDistribution,
  val clustered: ClusteredEstimate,
)

/**
 * A condition's forward-return advantage over the universe all-bars baseline at one horizon — the
 * headline signal of the screen. `meanLift` is on the date-clustered means (the honest point
 * estimate); `hitRateLift` is on the raw hit rates. A high-firing condition shows lift ≈ 0 by
 * construction, which is exactly the "no edge / broad edge" distinction absolute stats hide.
 */
data class ForwardReturnLift(
  val horizonDays: Int,
  val meanLift: Double,
  val hitRateLift: Double,
)

/**
 * A condition's firing count and rate within one calendar year. `eligibleBars` is the universe
 * bar count for that year; `firingRate` is `signals / eligibleBars`. Catches regime-conditional
 * firing collapses (fires 50/yr 2017-2019, then 5/yr 2020-2021).
 */
data class FiringYear(
  val year: Int,
  val signals: Int,
  val eligibleBars: Int,
  val firingRate: Double,
)

/** A single bar on which a condition fired — the unit of a firing set. */
data class SignalKey(
  val symbol: String,
  val date: LocalDate,
)

/**
 * Symbol-date firing overlap between a condition and a reference condition. `byYear` is the
 * per-calendar-year Jaccard (a single all-history value hides era-specific cloning); `pooled` is
 * the across-all-years Jaccard, null when neither condition fired at all. Advisory only — the
 * "redundancy" / "near-clone" bands live in the analyst layer, not here.
 */
data class JaccardOverlap(
  val byYear: Map<Int, Double>,
  val pooled: Double?,
)

/** Firing rate and forward-return lift of a condition within one SPY-return regime tertile. */
data class RegimeBucket(
  val firingRate: Double,
  val meanLift: Double?,
  val nSignals: Int,
)

/**
 * A condition's behaviour across SPY 20-day-return regimes (empirical tertiles over the screen
 * window). Firing rate by tertile shows *when* the condition fires; `meanLift` by tertile shows
 * whether the edge itself flips sign with the market regime — a disguised regime detector that
 * aggregate stats hide.
 */
data class SpyRegimeBreakdown(
  val down: RegimeBucket,
  val flat: RegimeBucket,
  val up: RegimeBucket,
)

/**
 * Pure forward-return / lift statistics for `/condition-screen`. No Spring, no DB: every function
 * operates on in-memory quote and signal lists so the stat math is unit-testable in isolation.
 * `ConditionScreenService` owns data loading and condition evaluation and delegates the arithmetic
 * here.
 */
object ConditionScreenStats {
  /**
   * Break a condition's firing rate and forward-return lift down by SPY 20-day-return regime.
   * Eligible-bar dates are split into equal-count tertiles by their SPY 20d return (lowest third =
   * down, highest third = up). Firing rate within a tertile is condition firings over eligible bars
   * on that tertile's dates; lift is the condition's date-clustered mean minus the universe's, or
   * null when the condition never fired in that tertile.
   */
  fun spyRegimeBreakdown(
    spy20dReturnByDate: Map<LocalDate, Double>,
    conditionSignals: List<SignalForwardReturn>,
    universeSignals: List<SignalForwardReturn>,
    horizon: Int,
  ): SpyRegimeBreakdown {
    val dates =
      universeSignals
        .map { it.signalDate }
        .distinct()
        .filter { spy20dReturnByDate.containsKey(it) }
        .sortedBy { spy20dReturnByDate.getValue(it) }
    val third = dates.size / 3
    val downDates = dates.take(third).toSet()
    val upDates = dates.takeLast(third).toSet()
    val flatDates = dates.filterNot { it in downDates || it in upDates }.toSet()

    fun bucket(tertileDates: Set<LocalDate>): RegimeBucket {
      val eligible = universeSignals.count { it.signalDate in tertileDates }
      val fired = conditionSignals.filter { it.signalDate in tertileDates }
      val meanLift =
        if (fired.isEmpty()) {
          null
        } else {
          clusteredEstimateAt(fired, horizon).clusteredMean -
            clusteredEstimateAt(universeSignals.filter { it.signalDate in tertileDates }, horizon).clusteredMean
        }
      return RegimeBucket(
        firingRate = if (eligible == 0) 0.0 else fired.size.toDouble() / eligible,
        meanLift = meanLift,
        nSignals = fired.size,
      )
    }

    return SpyRegimeBreakdown(down = bucket(downDates), flat = bucket(flatDates), up = bucket(upDates))
  }

  /**
   * The ±1-step neighbour values to sweep a parameter through for ARS detection.
   *
   * A discrete parameter (one with an allowed-value [options] set, e.g. emaPeriod ∈ {5,10,20,…})
   * steps to its immediately adjacent allowed values — "±1" is the option grid, not ±1 numerically,
   * since intermediate values are not legal configs. A continuous parameter (no [options]) sweeps
   * ±10% around the centre. Integer-count tunables that lack an options set are better swept via an
   * explicit step (see the screen's sweep spec) than this ±10% default.
   */
  fun parameterVariants(
    center: Double,
    options: List<Double>?,
    relativeStep: Double = 0.10,
  ): List<Double> {
    if (options == null) {
      return listOf(center * (1 - relativeStep), center * (1 + relativeStep))
    }
    val sorted = options.sorted()
    // Match the centre on the option grid by epsilon — a JSON-parsed centre (e.g. "0.10" vs 0.1)
    // need not be bit-identical, and an exact-equality miss would silently skip the whole tunable.
    val idx = sorted.indexOfFirst { kotlin.math.abs(it - center) < 1e-9 }
    if (idx < 0) return emptyList()
    return listOfNotNull(sorted.getOrNull(idx - 1), sorted.getOrNull(idx + 1))
  }

  /** Condition firing count and rate per calendar year, over the universe's eligible bars. */
  fun firingByYear(
    conditionSignals: List<SignalForwardReturn>,
    universeSignals: List<SignalForwardReturn>,
  ): List<FiringYear> {
    val eligibleByYear = universeSignals.groupingBy { it.signalDate.year }.eachCount()
    val firedByYear = conditionSignals.groupingBy { it.signalDate.year }.eachCount()
    return eligibleByYear.keys.sorted().map { year ->
      val eligible = eligibleByYear.getValue(year)
      val fired = firedByYear[year] ?: 0
      FiringYear(
        year = year,
        signals = fired,
        eligibleBars = eligible,
        firingRate = if (eligible == 0) 0.0 else fired.toDouble() / eligible,
      )
    }
  }

  /** Per-year and pooled Jaccard similarity of two (symbol, date) firing sets. */
  fun jaccardOverlap(
    condition: Set<SignalKey>,
    reference: Set<SignalKey>,
  ): JaccardOverlap {
    val years = (condition + reference).map { it.date.year }.distinct().sorted()
    val byYear =
      years.associateWith { year ->
        jaccard(condition.filterToYear(year), reference.filterToYear(year)) ?: 0.0
      }
    return JaccardOverlap(byYear = byYear, pooled = jaccard(condition, reference))
  }

  /** The distribution and date-clustered estimate of [signals] at [horizon]. */
  fun summariseAt(
    signals: List<SignalForwardReturn>,
    horizon: Int,
  ): ForwardReturnSummary =
    ForwardReturnSummary(
      distribution = distributionAt(signals, horizon),
      clustered = clusteredEstimateAt(signals, horizon),
    )

  /** Lift of a [condition] summary over a [universe] baseline summary at the same horizon. */
  fun lift(
    condition: ForwardReturnSummary,
    universe: ForwardReturnSummary,
  ): ForwardReturnLift =
    ForwardReturnLift(
      horizonDays = condition.distribution.horizonDays,
      meanLift = condition.clustered.clusteredMean - universe.clustered.clusteredMean,
      hitRateLift = condition.distribution.hitRate - universe.distribution.hitRate,
    )

  /**
   * Date-clustered forward-return estimate at [horizon]: average per-date means, then across dates.
   */
  fun clusteredEstimateAt(
    signals: List<SignalForwardReturn>,
    horizon: Int,
  ): ClusteredEstimate {
    val dateMeans =
      signals
        .mapNotNull { s -> s.returnsByHorizon[horizon]?.let { s.signalDate to it } }
        .groupBy({ it.first }, { it.second })
        .map { (_, returns) -> returns.average() }
    val clusteredMean = dateMeans.average().orZeroIfNaN()
    return ClusteredEstimate(
      horizonDays = horizon,
      clusteredMean = clusteredMean,
      stdError = sampleStdError(dateMeans, clusteredMean),
      nDates = dateMeans.size,
    )
  }

  /**
   * Summarise the forward-return distribution of [signals] at [horizon]. Signals whose return at
   * this horizon is null are excluded from the statistics but counted in `nDropped`.
   */
  fun distributionAt(
    signals: List<SignalForwardReturn>,
    horizon: Int,
  ): ForwardReturnDistribution {
    val measured = signals.mapNotNull { s -> s.returnsByHorizon[horizon]?.let { s.signalDate to it } }
    val values = measured.map { it.second }
    val dropped = signals.count { it.returnsByHorizon[horizon] == null }
    val mean = values.average().orZeroIfNaN()
    val std = populationStd(values, mean)
    return ForwardReturnDistribution(
      horizonDays = horizon,
      nSignals = values.size,
      nDropped = dropped,
      nDates = measured.map { it.first }.distinct().size,
      mean = mean,
      median = median(values),
      std = std,
      skew = populationSkew(values, mean, std),
      hitRate = if (values.isEmpty()) 0.0 else values.count { it > 0 }.toDouble() / values.size,
    )
  }

  /**
   * Compute the entry-anchored forward returns and signal→fill gap for one signal.
   *
   * @param quotes the symbol's quotes sorted ascending by date
   * @param signalIndex index of the signal (decision) bar in [quotes]
   * @param entryDelayDays bars between the signal bar and the fill bar
   * @param horizons forward horizons N to measure, in trading days from the fill bar
   */
  fun signalForwardReturn(
    quotes: List<StockQuote>,
    signalIndex: Int,
    entryDelayDays: Int,
    horizons: List<Int>,
  ): SignalForwardReturn {
    val signal = quotes[signalIndex]
    val fillIndex = signalIndex + entryDelayDays
    val fill = quotes.getOrNull(fillIndex)
    val fillGap = fill?.let { it.closePrice / signal.closePrice - 1 }

    val returnsByHorizon =
      horizons.associateWith { n ->
        val target = quotes.getOrNull(fillIndex + n)
        if (fill != null && target != null) {
          target.closePrice / fill.closePrice - 1
        } else {
          null
        }
      }

    return SignalForwardReturn(
      symbol = signal.symbol,
      signalDate = signal.date,
      fillGap = fillGap,
      returnsByHorizon = returnsByHorizon,
    )
  }
}

private fun Set<SignalKey>.filterToYear(year: Int): Set<SignalKey> = filterTo(mutableSetOf()) { it.date.year == year }

/** Jaccard index of two sets, or null when their union is empty (undefined). */
private fun jaccard(
  a: Set<SignalKey>,
  b: Set<SignalKey>,
): Double? {
  val union = a.size + b.size - a.count { it in b }
  if (union == 0) return null
  val intersection = a.count { it in b }
  return intersection.toDouble() / union
}

/** Sample standard error of the mean: sample std (Bessel) divided by sqrt(n). */
private fun sampleStdError(
  values: List<Double>,
  mean: Double,
): Double {
  if (values.size < 2) return 0.0
  val sampleVariance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
  return kotlin.math.sqrt(sampleVariance) / kotlin.math.sqrt(values.size.toDouble())
}

private fun median(values: List<Double>): Double {
  if (values.isEmpty()) return 0.0
  val sorted = values.sorted()
  val mid = sorted.size / 2
  return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
}

private fun populationStd(
  values: List<Double>,
  mean: Double,
): Double {
  if (values.size < 2) return 0.0
  return kotlin.math.sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
}

private fun populationSkew(
  values: List<Double>,
  mean: Double,
  std: Double,
): Double {
  if (values.size < 2 || std == 0.0) return 0.0
  val m3 = values.sumOf { (it - mean) * (it - mean) * (it - mean) } / values.size
  return m3 / (std * std * std)
}

private fun Double.orZeroIfNaN(): Double = if (isNaN()) 0.0 else this
