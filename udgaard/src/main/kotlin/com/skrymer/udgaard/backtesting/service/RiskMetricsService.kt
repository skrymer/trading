package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.BenchmarkComparison
import com.skrymer.udgaard.backtesting.model.DrawdownEpisode
import com.skrymer.udgaard.backtesting.model.PortfolioEquityPoint
import com.skrymer.udgaard.backtesting.model.PositionSizingResult
import com.skrymer.udgaard.backtesting.model.RiskMetrics
import com.skrymer.udgaard.backtesting.model.Trade
import com.skrymer.udgaard.data.model.StockQuote
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Computes post-trade risk analytics from a position-sized backtest.
 *
 * Assumes a USD-denominated equity curve. Multi-currency portfolios would conflate strategy
 * performance with FX vol — out of scope for this service. Document the assumption in any
 * caller that exposes these values to users.
 *
 * Conventions (locked during planning, see plan + quant review):
 * - Sharpe / Sortino: simple daily returns annualized by sqrt(252). RF defaults to 0 (raw Sharpe).
 *   When `riskFreeRatePct > 0`, it's subtracted from daily returns for Sharpe and used as the
 *   MAR (target return) threshold for Sortino downside calc. Both are labeled as "raw" when RF=0.
 * - CAGR: calendar-day annualized — `(V_end / V_start)^(365.25 / calendarDays) − 1`. Matches
 *   SPY's reported CAGR convention.
 * - Calmar: `CAGR / |maxDrawdownPct|` (industry standard, NOT `totalReturn / maxDD`). The
 *   pre-existing flat field used the wrong formula — corrected here.
 * - Tail ratio: `|p95| / |p5|` of trade returns. Returns null when trade count < 20 (statistically
 *   meaningless below that threshold).
 * - Benchmark correlation/beta: requires ≥ 60 days of overlap with benchmark; below that, all
 *   benchmark fields are null. Beta = `cov(strategy, benchmark) / var(benchmark)` (mathematically
 *   equivalent to OLS-with-intercept slope, NOT zero-intercept regression).
 * - "Active return vs benchmark" (`r_p_ann − β · r_b_ann`) is NOT Jensen's alpha — Jensen requires
 *   subtracting RF from both legs. We label honestly to avoid literature-comparison errors.
 * - Drawdown episodes: Magdon-Ismail state machine. Opens when DD breaches −0.5% from running peak,
 *   closes only at a new all-time peak. Returns top-N (default 10) sorted by depth.
 */
@Service
class RiskMetricsService {
  /**
   * @param trades all backtest trades (winning + losing combined; not used for Sharpe/CAGR but
   *   is the source for SQN / tailRatio)
   * @param equityCurve daily portfolio value series from PositionSizingResult
   * @param sizingResult source of `totalReturnPct` and `maxDrawdownPct` (used for legacy
   *   calmar fallback when CAGR can't be computed)
   * @param benchmarkQuotes daily benchmark close series (e.g. SPY); null disables benchmark fields
   * @param benchmarkSymbol propagated to the response so consumers know what was compared
   * @param riskFreeRatePct annualized RF in percent (e.g. 4.0 for 4%). Default 0 = raw Sharpe.
   */
  fun compute(
    trades: List<Trade>,
    equityCurve: List<PortfolioEquityPoint>,
    sizingResult: PositionSizingResult,
    benchmarkQuotes: List<StockQuote>?,
    benchmarkSymbol: String = "SPY",
    riskFreeRatePct: Double = 0.0,
  ): RiskAnalysis {
    val dailyReturns = dailyReturns(equityCurve)
    val cagr = cagr(equityCurve)
    val sharpe = sharpe(dailyReturns, riskFreeRatePct)
    val sortino = sortino(dailyReturns, riskFreeRatePct)
    val calmar = calmar(cagr, sizingResult.maxDrawdownPct)
    val sqn = sqn(trades)
    val tail = tailRatio(trades)

    val benchmark = benchmarkQuotes?.let { computeBenchmarkComparison(equityCurve, it, benchmarkSymbol) }

    val episodes = findDrawdownEpisodes(equityCurve)

    return RiskAnalysis(
      riskMetrics = RiskMetrics(
        sharpeRatio = sharpe,
        sortinoRatio = sortino,
        calmarRatio = calmar,
        sqn = sqn,
        tailRatio = tail,
      ),
      benchmarkComparison = benchmark,
      cagr = cagr,
      drawdownEpisodes = episodes,
    )
  }

  // ===== EQUITY-CURVE-DERIVED METRICS =====

  internal fun dailyReturns(curve: List<PortfolioEquityPoint>): List<Double> {
    if (curve.size < 2) return emptyList()
    return curve.zipWithNext { prev, next ->
      if (prev.portfolioValue == 0.0) 0.0 else (next.portfolioValue - prev.portfolioValue) / prev.portfolioValue
    }
  }

  internal fun sharpe(dailyReturns: List<Double>, riskFreeRatePct: Double): Double? {
    if (dailyReturns.size < 2) return null
    val rfDaily = riskFreeRatePct / 100.0 / TRADING_DAYS_PER_YEAR
    val excess = dailyReturns.map { it - rfDaily }
    val mean = excess.average()
    val variance = excess.map { (it - mean) * (it - mean) }.average()
    val stdDev = sqrt(variance)
    if (stdDev == 0.0) return null
    return mean / stdDev * sqrt(TRADING_DAYS_PER_YEAR.toDouble())
  }

  internal fun sortino(dailyReturns: List<Double>, riskFreeRatePct: Double): Double? {
    if (dailyReturns.size < 2) return null
    val marDaily = riskFreeRatePct / 100.0 / TRADING_DAYS_PER_YEAR
    // LPM2 — divide by N (all returns), not N_negative. Standard lower-partial-moment-of-order-2.
    val downsideSquares = dailyReturns.map {
      val d = it - marDaily
      if (d < 0) d * d else 0.0
    }
    val downsideDev = sqrt(downsideSquares.average())
    if (downsideDev == 0.0) return null
    val excessMean = dailyReturns.map { it - marDaily }.average()
    return excessMean / downsideDev * sqrt(TRADING_DAYS_PER_YEAR.toDouble())
  }

  internal fun cagr(curve: List<PortfolioEquityPoint>): Double? {
    if (curve.size < 2) return null
    val first = curve.first()
    val last = curve.last()
    if (first.portfolioValue <= 0.0) return null
    val calendarDays = ChronoUnit.DAYS.between(first.date, last.date).toDouble()
    if (calendarDays <= 0.0) return null
    val growth = last.portfolioValue / first.portfolioValue
    return (growth.pow(DAYS_PER_CALENDAR_YEAR / calendarDays) - 1.0) * 100.0
  }

  internal fun calmar(cagr: Double?, maxDrawdownPct: Double): Double? {
    if (cagr == null) return null
    if (maxDrawdownPct <= 0.0) return null
    return cagr / abs(maxDrawdownPct)
  }

  // ===== TRADE-LIST-DERIVED METRICS (ported from BacktestReport.kt helpers) =====

  internal fun sqn(trades: List<Trade>): Double? {
    if (trades.size < 2) return null
    val profits = trades.map { it.profitPercentage }
    val mean = profits.average()
    val variance = profits.map { (it - mean) * (it - mean) }.average()
    val stdDev = sqrt(variance)
    if (stdDev == 0.0) return null
    return sqrt(trades.size.toDouble()) * mean / stdDev
  }

  internal fun tailRatio(trades: List<Trade>): Double? {
    if (trades.size < MIN_TRADES_FOR_TAIL_RATIO) return null
    val sorted = trades.map { it.profitPercentage }.sorted()
    val p5 = sorted[(sorted.size * 0.05).toInt()]
    val p95 = sorted[((sorted.size * 0.95).toInt()).coerceAtMost(sorted.size - 1)]
    val absP5 = abs(p5)
    if (absP5 == 0.0) return null
    return abs(p95) / absP5
  }

  // ===== BENCHMARK COMPARISON =====

  internal fun computeBenchmarkComparison(
    equityCurve: List<PortfolioEquityPoint>,
    benchmarkQuotes: List<StockQuote>,
    benchmarkSymbol: String,
  ): BenchmarkComparison {
    val benchmarkByDate = benchmarkQuotes.associateBy { it.date }
    val (strategyReturns, benchmarkReturns) = alignedDailyReturns(equityCurve, benchmarkByDate)

    if (strategyReturns.size < MIN_OVERLAP_DAYS_FOR_CORRELATION) {
      return BenchmarkComparison(
        benchmarkSymbol = benchmarkSymbol,
        correlation = null,
        beta = null,
        activeReturnVsBenchmark = null,
      )
    }

    val benchmarkVar = variance(benchmarkReturns)
    if (benchmarkVar == 0.0) {
      return BenchmarkComparison(
        benchmarkSymbol = benchmarkSymbol,
        correlation = null,
        beta = null,
        activeReturnVsBenchmark = null,
      )
    }

    val cov = covariance(strategyReturns, benchmarkReturns)
    val strategyVar = variance(strategyReturns)
    val correlation = if (strategyVar > 0.0) cov / sqrt(strategyVar * benchmarkVar) else null
    val beta = cov / benchmarkVar
    val annualizationFactor = TRADING_DAYS_PER_YEAR.toDouble()
    val strategyAnn = strategyReturns.average() * annualizationFactor * 100.0
    val benchmarkAnn = benchmarkReturns.average() * annualizationFactor * 100.0
    val activeReturn = strategyAnn - beta * benchmarkAnn

    return BenchmarkComparison(
      benchmarkSymbol = benchmarkSymbol,
      correlation = correlation,
      beta = beta,
      activeReturnVsBenchmark = activeReturn,
    )
  }

  // ===== DRAWDOWN EPISODES (Magdon-Ismail state machine) =====

  internal fun findDrawdownEpisodes(curve: List<PortfolioEquityPoint>): List<DrawdownEpisode> {
    if (curve.size < 2) return emptyList()
    val state = DrawdownState(curve.first().portfolioValue, curve.first().date)
    val episodes = mutableListOf<DrawdownEpisode>()

    for (point in curve) {
      state.advance(point, episodes)
    }
    state.flush(episodes)

    return episodes.sortedByDescending { it.maxDrawdownPct }.take(TOP_N_DRAWDOWNS)
  }

  /**
   * Bundle returned to callers. The controller spreads these into BacktestReport.
   */
  data class RiskAnalysis(
    val riskMetrics: RiskMetrics,
    val benchmarkComparison: BenchmarkComparison?,
    val cagr: Double?,
    val drawdownEpisodes: List<DrawdownEpisode>,
  )

  private companion object {
    const val TOP_N_DRAWDOWNS = 10
    const val MIN_OVERLAP_DAYS_FOR_CORRELATION = 60
    const val MIN_TRADES_FOR_TAIL_RATIO = 20
    const val TRADING_DAYS_PER_YEAR = 252
    const val DAYS_PER_CALENDAR_YEAR = 365.25
  }
}

/**
 * Returns (strategyReturns, benchmarkReturns) over the dates where BOTH have a return value.
 * A return for date D requires both D and D−1 to have prices in the same series, AND requires
 * D to exist in the other series.
 */
private fun alignedDailyReturns(
  equityCurve: List<PortfolioEquityPoint>,
  benchmarkByDate: Map<LocalDate, StockQuote>,
): Pair<List<Double>, List<Double>> {
  val strategy = mutableListOf<Double>()
  val benchmark = mutableListOf<Double>()
  val sortedBenchmark = benchmarkByDate.values.sortedBy { it.date }
  val benchmarkPriceByDate = sortedBenchmark.associate { it.date to it.closePrice }
  val benchmarkPrevByDate = sortedBenchmark.zipWithNext().associate { (prev, next) -> next.date to prev.closePrice }

  equityCurve.zipWithNext { prev, next ->
    val pair = pairedReturn(prev, next, benchmarkPriceByDate, benchmarkPrevByDate)
    if (pair != null) {
      strategy += pair.first
      benchmark += pair.second
    }
  }
  return strategy to benchmark
}

/**
 * Returns (strategyReturn, benchmarkReturn) for the (prev, next) equity-curve transition, or
 * null when either side is missing or has a zero denominator. Extracted to flatten the
 * boolean condition into early-return null gates (detekt: ComplexCondition).
 */
private fun pairedReturn(
  prev: PortfolioEquityPoint,
  next: PortfolioEquityPoint,
  benchmarkPriceByDate: Map<LocalDate, Double>,
  benchmarkPrevByDate: Map<LocalDate, Double>,
): Pair<Double, Double>? {
  val prevValue = prev.portfolioValue.takeIf { it != 0.0 } ?: return null
  val benchmarkClose = benchmarkPriceByDate[next.date] ?: return null
  val benchmarkPrev = benchmarkPrevByDate[next.date]?.takeIf { it != 0.0 } ?: return null
  val strategyReturn = (next.portfolioValue - prevValue) / prevValue
  val benchmarkReturn = (benchmarkClose - benchmarkPrev) / benchmarkPrev
  return strategyReturn to benchmarkReturn
}

private fun variance(values: List<Double>): Double {
  if (values.isEmpty()) return 0.0
  val mean = values.average()
  return values.map { (it - mean) * (it - mean) }.average()
}

private fun covariance(xs: List<Double>, ys: List<Double>): Double {
  require(xs.size == ys.size) { "covariance inputs must have equal size" }
  if (xs.isEmpty()) return 0.0
  val xMean = xs.average()
  val yMean = ys.average()
  return xs.zip(ys) { x, y -> (x - xMean) * (y - yMean) }.average()
}

/**
 * Mutable state for the drawdown-episode state machine (Magdon-Ismail). Encapsulating it here
 * keeps `RiskMetricsService` under detekt's TooManyFunctions threshold while preserving the
 * single-pass sweep logic.
 */
private class DrawdownState(
  initialValue: Double,
  initialDate: LocalDate
) {
  private var runningPeak: Double = initialValue
  private var runningPeakDate: LocalDate = initialDate
  private var inEpisode: Boolean = false
  private var episodePeak: Double = initialValue
  private var episodePeakDate: LocalDate = initialDate
  private var troughValue: Double = initialValue
  private var troughDate: LocalDate = initialDate

  fun advance(point: PortfolioEquityPoint, episodes: MutableList<DrawdownEpisode>) {
    val v = point.portfolioValue
    when {
      v > runningPeak -> {
        if (inEpisode) {
          episodes += closedEpisode(point.date)
          inEpisode = false
        }
        runningPeak = v
        runningPeakDate = point.date
      }
      !inEpisode -> {
        val drawdownPct = (runningPeak - v) / runningPeak * 100.0
        if (drawdownPct > DRAWDOWN_NOISE_PCT_THRESHOLD) {
          inEpisode = true
          episodePeak = runningPeak
          episodePeakDate = runningPeakDate
          troughValue = v
          troughDate = point.date
        }
      }
      v < troughValue -> {
        troughValue = v
        troughDate = point.date
      }
    }
  }

  fun flush(episodes: MutableList<DrawdownEpisode>) {
    if (!inEpisode) return
    episodes += DrawdownEpisode(
      peakDate = episodePeakDate,
      troughDate = troughDate,
      recoveryDate = null,
      maxDrawdownPct = (episodePeak - troughValue) / episodePeak * 100.0,
      declineDays = ChronoUnit.DAYS.between(episodePeakDate, troughDate).toInt(),
      recoveryDays = null,
      totalDays = null,
    )
  }

  private fun closedEpisode(recoveryDate: LocalDate): DrawdownEpisode = DrawdownEpisode(
    peakDate = episodePeakDate,
    troughDate = troughDate,
    recoveryDate = recoveryDate,
    maxDrawdownPct = (episodePeak - troughValue) / episodePeak * 100.0,
    declineDays = ChronoUnit.DAYS.between(episodePeakDate, troughDate).toInt(),
    recoveryDays = ChronoUnit.DAYS.between(troughDate, recoveryDate).toInt(),
    totalDays = ChronoUnit.DAYS.between(episodePeakDate, recoveryDate).toInt(),
  )

  companion object {
    // Noise floor (in %) below which a dip is ignored when opening a new drawdown episode.
    const val DRAWDOWN_NOISE_PCT_THRESHOLD = 0.5
  }
}
