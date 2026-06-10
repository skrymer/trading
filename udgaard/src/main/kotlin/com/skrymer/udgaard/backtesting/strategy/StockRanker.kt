package com.skrymer.udgaard.backtesting.strategy

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.model.StockQuote
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Ranks stocks when multiple entry signals occur on the same day.
 * Used to pick the top N stocks when position limits apply.
 */
interface StockRanker {
  /**
   * Calculate a score for a stock at entry. Higher score = better.
   * @param stock - the stock
   * @param entryQuote - the quote where entry signal triggered
   * @return score (higher is better)
   */
  fun score(
    stock: Stock,
    entryQuote: StockQuote,
  ): Double

  fun score(
    stock: Stock,
    entryQuote: StockQuote,
    context: BacktestContext,
  ): Double = score(stock, entryQuote)

  /**
   * Description of this ranking strategy
   */
  fun description(): String

  /**
   * Trading days of pre-window price history this ranker needs loaded before the backtest start so it
   * can score the earliest in-window entries. 0 (the default) for rankers that read precomputed per-quote
   * indicators; rankers that compute trailing returns in-engine override this with their lookback depth.
   * The engine loads this much warmup history (visible to scoring only — never traded). Without it a
   * trailing ranker is unscoreable for every entry inside a window shorter than its lookback.
   */
  fun warmupTradingDays(): Int = 0

  /**
   * Score a whole same-day cohort at once (ADR 0020) — returns scores aligned by index with [candidates].
   *
   * The default delegates to per-stock [score], so any ranker that does not override this is
   * byte-identical (same scores → same tie-break jitter → same sort). A *cross-sectional* ranker — one
   * that standardizes or blends across the day's candidates, where a per-stock score is structurally
   * impossible — overrides this instead of [score]. The engine calls it at the single point where the
   * full same-day cohort is co-resident (the day-by-day selection loop), never in the batched Pass-1
   * scan. A ranker overrides at most one of [score] / [rankCohort].
   */
  fun rankCohort(
    candidates: List<Pair<Stock, StockQuote>>,
    context: BacktestContext,
  ): List<Double> = candidates.map { (stock, quote) -> score(stock, quote, context) }

  companion object {
    /**
     * Tiny jitter added to ranking scores to randomly break ties between stocks with equal scores.
     * Small enough (1e-10) to never affect ordering of meaningfully different scores (typically 0-100 range),
     * but large enough to shuffle equal scores randomly each run.
     */
    const val TIE_BREAK_JITTER = 1e-10
  }
}

/**
 * Ranks stocks by ATR as percentage of price (volatility).
 * Theory: Higher volatility = larger potential moves.
 */
class VolatilityRanker : StockRanker {
  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
  ): Double {
    if (entryQuote.closePrice == 0.0) return 0.0
    // ATR as percentage of price
    return (entryQuote.atr / entryQuote.closePrice) * 100.0
  }

  override fun description() = "ATR as % of price (higher volatility = better)"
}

/**
 * Ranks stocks by distance from 10 EMA.
 * Theory: Closer to 10 EMA = better entry (less extended).
 */
class DistanceFrom10EmaRanker : StockRanker {
  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
  ): Double {
    if (entryQuote.closePriceEMA10 == 0.0) return 0.0
    // Distance as percentage
    val distance = ((entryQuote.closePrice - entryQuote.closePriceEMA10) / entryQuote.closePriceEMA10) * 100.0
    // Closer to EMA is better, so return negative distance
    return -Math.abs(distance)
  }

  override fun description() = "Distance from 10 EMA (closer = better)"
}

/**
 * Composite ranker that combines multiple ranking factors.
 * Default: Volatility (40%) + DistanceFrom10Ema (30%) + SectorStrength (30%)
 */
class CompositeRanker(
  private val volatilityWeight: Double = 0.4,
  private val distanceWeight: Double = 0.3,
  private val sectorWeight: Double = 0.3,
) : StockRanker {
  private val volatilityRanker = VolatilityRanker()
  private val distanceRanker = DistanceFrom10EmaRanker()
  private val sectorRanker = SectorStrengthRanker()

  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
  ): Double = score(stock, entryQuote, BacktestContext.EMPTY)

  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
    context: BacktestContext,
  ): Double {
    val volatilityScore = volatilityRanker.score(stock, entryQuote, context)
    val distanceScore = distanceRanker.score(stock, entryQuote, context)
    val sectorScore = sectorRanker.score(stock, entryQuote, context)

    // Normalize scores to 0-100 range
    val normalizedVolatility = normalize(volatilityScore, 0.0, 10.0)
    val normalizedDistance = normalize(distanceScore, -10.0, 0.0)
    val normalizedSector = normalize(sectorScore, 0.0, 100.0)

    return (normalizedVolatility * volatilityWeight) +
      (normalizedDistance * distanceWeight) +
      (normalizedSector * sectorWeight)
  }

  private fun normalize(
    value: Double,
    min: Double,
    max: Double,
  ): Double {
    if (max == min) return 50.0
    return ((value - min) / (max - min)) * 100.0
  }

  override fun description() =
    "Composite (Vol ${volatilityWeight * 100}%, Dist10EMA ${distanceWeight * 100}%, Sector ${sectorWeight * 100}%)"
}

/**
 * Ranks stocks by sector strength (bull percentage from context).
 * Theory: Trade stocks in the strongest sectors.
 */
class SectorStrengthRanker : StockRanker {
  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
  ): Double = score(stock, entryQuote, BacktestContext.EMPTY)

  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
    context: BacktestContext,
  ): Double = context.getSectorBreadth(stock.sectorSymbol, entryQuote.date)?.bullPercentage ?: 0.0

  override fun description() = "Sector strength (bull %)"
}

/**
 * Ranks stocks by rolling average of their sector's bull percentage over a window.
 * Theory: Persistent sector strength is a better edge predictor than spot strength.
 *
 * Uses available readings in the sector breadth map ≤ entryQuote.date (gap-tolerant).
 *
 * @param windowDays Number of trailing trading-day readings to average (>= 1).
 */
class RollingSectorStrengthRanker(
  private val windowDays: Int = 10,
) : StockRanker {
  init {
    require(windowDays >= 1) { "windowDays must be >= 1, got $windowDays" }
  }

  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
  ): Double = score(stock, entryQuote, BacktestContext.EMPTY)

  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
    context: BacktestContext,
  ): Double {
    val sector = stock.sectorSymbol ?: return 0.0
    val sectorMap = context.sectorBreadthMap[sector] ?: return 0.0
    val recent = sectorMap
      .filterKeys { !it.isAfter(entryQuote.date) }
      .toSortedMap()
      .values
      .toList()
      .takeLast(windowDays)
    if (recent.isEmpty()) return 0.0
    return recent.sumOf { it.bullPercentage } / recent.size
  }

  override fun description() = "Rolling sector strength (avg sector bull % over $windowDays days)"
}

/**
 * Ranks stocks by change in their sector's bull percentage over a window.
 * Theory: Sectors *gaining* breadth leadership have stronger follow-through than
 * sectors already at peak strength.
 *
 * Score = sectorBull(today) − sectorBull(N trading days ago).
 * Falls back to 0 when insufficient history.
 *
 * @param windowDays Number of trading-day readings back to compare against (>= 1).
 */
class SectorStrengthMomentumRanker(
  private val windowDays: Int = 10,
) : StockRanker {
  init {
    require(windowDays >= 1) { "windowDays must be >= 1, got $windowDays" }
  }

  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
  ): Double = score(stock, entryQuote, BacktestContext.EMPTY)

  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
    context: BacktestContext,
  ): Double {
    val sector = stock.sectorSymbol ?: return 0.0
    val sectorMap = context.sectorBreadthMap[sector] ?: return 0.0
    val recent = sectorMap
      .filterKeys { !it.isAfter(entryQuote.date) }
      .toSortedMap()
      .values
      .toList()
    if (recent.size < windowDays + 1) return 0.0
    val today = recent.last().bullPercentage
    val past = recent[recent.size - 1 - windowDays].bullPercentage
    return today - past
  }

  override fun description() = "Sector strength momentum (Δ sector bull % over $windowDays days)"
}

/**
 * Random ranker for the mandatory baseline comparison — a candidate's ranker must beat a
 * byte-identical Random ordering or its "edge" is just entry-universe beta.
 *
 * When a [seed] is supplied the score is a deterministic function of `(seed, symbol, date)`, so the
 * ordering is reproducible across runs and JVMs and independent of the order in which stocks are
 * scored. This is what makes the baseline byte-identical and lets a multi-seed sweep produce a
 * stable Random edge distribution (e.g. per-window p95). A null [seed] preserves the legacy
 * non-reproducible behaviour (a fresh draw each call) for ad-hoc use.
 *
 * @param seed Fixed seed for a reproducible ordering; null = non-reproducible.
 */
class RandomRanker(
  private val seed: Long? = null,
) : StockRanker {
  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
  ): Double {
    val fixedSeed = seed ?: return kotlin.random.Random.nextDouble() * SCORE_RANGE
    var hash = fixedSeed
    hash = hash * HASH_PRIME + stock.symbol.hashCode()
    hash = hash * HASH_PRIME + entryQuote.date.hashCode()
    return kotlin.random.Random(hash).nextDouble() * SCORE_RANGE
  }

  override fun description() =
    if (seed == null) "Random (baseline, unseeded — non-reproducible)" else "Random (baseline, seed=$seed)"

  companion object {
    private const val SCORE_RANGE = 100.0

    // Odd multiplier for the rolling (seed, symbol, date) hash. Long arithmetic wraps on overflow;
    // that wrap is intentional and deterministic — every Long is a valid Random seed — so do not
    // "fix" it with Math.multiplyExact, which would throw and break reproducibility.
    private const val HASH_PRIME = 31L
  }
}

/**
 * Ranks stocks by sector edge priority.
 * Accepts an ordered list of sector symbols — first = highest priority.
 * Score = listSize - index (e.g., first sector gets 11, second gets 10, etc.)
 * Stocks not in the ranking list get 0.
 */
class SectorEdgeRanker(
  sectorRanking: List<String>,
) : StockRanker {
  private val sectorScores: Map<String, Double> =
    sectorRanking
      .mapIndexed { index, sector ->
        sector to (sectorRanking.size - index).toDouble()
      }.toMap()

  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
  ): Double = sectorScores[stock.sectorSymbol] ?: 0.0

  override fun description() = "Sector edge priority (ordered list)"
}

/**
 * Sector-edge priority with a base-tightness tie-breaker.
 *
 * Primary key: sector position in [sectorRanking] (same shape as [SectorEdgeRanker]).
 * Secondary key: base tightness, computed as `-ATR / close` — a smaller ATR-to-price ratio
 * gets a larger (less negative) bonus, so the tighter base wins the tie-break.
 *
 * Within a single sector, today's [SectorEdgeRanker] resolves ties with `TIE_BREAK_JITTER`
 * (uniform random), which discards information the scanner already computes. With ~5–15
 * candidates per leading sector and a 15-position cap, the within-sector ordering decides
 * 60–80% of fills, so a structurally-aligned tie-break should narrow seed dispersion and —
 * if the underlying signal is real — lift Calmar.
 *
 * The combined score is `sectorScore * SECTOR_SCALE - (ATR / close)`. `SECTOR_SCALE = 10`
 * leaves enough headroom that any sector difference (≥ 1 with 11 sectors) strictly dominates
 * any tightness difference (typical range 0.005–0.20 for liquid US stocks).
 *
 * Stocks outside [sectorRanking] get `sectorScore = 0`, so a tight base in an unranked
 * sector still ranks below the worst base in any ranked sector.
 */
class SectorEdgeWithTightnessRanker(
  sectorRanking: List<String>,
) : StockRanker {
  private val sectorScores: Map<String, Double> =
    sectorRanking
      .mapIndexed { index, sector ->
        sector to (sectorRanking.size - index).toDouble()
      }.toMap()

  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
  ): Double {
    val sectorScore = sectorScores[stock.sectorSymbol] ?: 0.0
    // Guard against ATR/close ingestion pathologies. Negative ATR is a stale-data artefact;
    // NaN propagates through every comparison silently and would make the stock unsortable.
    // Either case collapses to the sector ceiling — same as having no tightness signal at
    // all. Better than allowing a phantom positive boost from a sign-flipped row.
    val closePrice = entryQuote.closePrice
    val atr = entryQuote.atr
    val tightnessBonus = if (closePrice > 0.0 && atr.isFinite() && atr >= 0.0) -(atr / closePrice) else 0.0
    return sectorScore * SECTOR_SCALE + tightnessBonus
  }

  override fun description() = "Sector edge priority + base-tightness tie-breaker (ATR/close)"

  companion object {
    private const val SECTOR_SCALE = 10.0
  }
}

/**
 * Adaptive ranker that switches between strategies based on market conditions.
 * Theory: Use Volatility ranker in trending markets, DistanceFrom10Ema in choppy markets.
 *
 * Market Regime Detection:
 * - Trending: Market breadth above 60% (majority of stocks in uptrend)
 * - Choppy: Otherwise
 */
class AdaptiveRanker : StockRanker {
  private val volatilityRanker = VolatilityRanker()
  private val distanceRanker = DistanceFrom10EmaRanker()

  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
  ): Double = score(stock, entryQuote, BacktestContext.EMPTY)

  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
    context: BacktestContext,
  ): Double =
    if (isMarketTrending(entryQuote, context)) {
      volatilityRanker.score(stock, entryQuote)
    } else {
      distanceRanker.score(stock, entryQuote)
    }

  private fun isMarketTrending(
    quote: StockQuote,
    context: BacktestContext,
  ): Boolean {
    val marketBreadth = context.getMarketBreadth(quote.date)
    return marketBreadth != null && marketBreadth.breadthPercent > 60.0
  }

  override fun description() = "Adaptive (Volatility in trends, DistanceFrom10Ema in chop)"
}

/**
 * Ranks stocks by trailing total return, skipping the most recent [skipDays] bars — i.e. the
 * cross-sectional "12-1" momentum factor (default: 252-day return ending 21 trading days ago).
 * Theory: relative outperformers persist; skipping the last ~month sidesteps short-term reversal.
 *
 * Score = close[entry − skipDays] / close[entry − lookbackDays] − 1, using only bars *before* entry.
 * A stock without enough history (or a non-positive base price) cannot be scored on momentum and must
 * not win a momentum ranking, so it returns [INSUFFICIENT_HISTORY] — strictly below any real return
 * (which bottoms at −1.0 when price goes to zero), placing it last.
 *
 * Cost is O(log n): one binary-search index lookup plus two array reads — no per-bar iteration over
 * the quote history, so it adds no backtest-time hot-loop scan.
 *
 * @param lookbackDays Trailing window length in trading-day bars (must exceed [skipDays]).
 * @param skipDays Recent bars to exclude from the window end (>= 1, so the window ends strictly
 *   before the entry bar — this is what guarantees no entry-or-future bar can ever be read).
 */
class TrailingReturnRanker(
  private val lookbackDays: Int = 252,
  private val skipDays: Int = 21,
) : StockRanker {
  init {
    require(skipDays >= 1) { "skipDays must be >= 1 (window must end before the entry bar), got $skipDays" }
    require(lookbackDays > skipDays) { "lookbackDays ($lookbackDays) must be greater than skipDays ($skipDays)" }
  }

  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
  ): Double {
    val entryIdx = stock.indexOnOrAfter(entryQuote.date)
    val startIdx = entryIdx - lookbackDays
    val endIdx = entryIdx - skipDays
    if (startIdx < 0 || endIdx < 0) return INSUFFICIENT_HISTORY
    val start = stock.quotes[startIdx].closePrice
    val end = stock.quotes[endIdx].closePrice
    if (start <= 0.0) return INSUFFICIENT_HISTORY
    return end / start - 1.0
  }

  override fun description() = "Trailing return ($lookbackDays-$skipDays day momentum)"

  override fun warmupTradingDays() = lookbackDays

  companion object {
    /** Below the −1.0 floor of any real return, so unscoreable stocks always rank last. */
    private const val INSUFFICIENT_HISTORY = -Double.MAX_VALUE
  }
}

/**
 * Ranks stocks by nearness to their own 52-week high: score = min(close / 52-week high, 1.0),
 * higher = closer to the high = better.
 *
 * The cap at 1.0 is deliberate and load-bearing. A close *at* the 52-week high is already the
 * maximally-near state; a close that has printed *above* the prior high is an overshoot, not a
 * "nearer" state. Without the cap an 8%-above-high quote (ratio 1.08) would outrank a quote sitting
 * exactly at its high — which would make this a breakout/overshoot ranker rather than a nearness
 * ranker. Capping collapses every at-or-above-high quote to the same top score 1.0 (ties resolved by
 * the usual jitter). There is no low-side floor: a far-from-high ratio (e.g. 0.3) is a legitimately
 * weak reading and should rank low.
 */
class NearnessTo52WeekHighRanker : StockRanker {
  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
  ): Double {
    val high = entryQuote.high52Week ?: return INSUFFICIENT_DATA
    if (high <= 0.0 || entryQuote.closePrice <= 0.0) return INSUFFICIENT_DATA
    return minOf(entryQuote.closePrice / high, 1.0)
  }

  override fun description() = "Nearness to 52-week high (min(close / 52-week high, 1.0); higher = closer)"

  companion object {
    private const val INSUFFICIENT_DATA = -Double.MAX_VALUE
  }
}

/**
 * Ranks stocks by single-factor *market-residual* momentum — the beta-stripped (idiosyncratic)
 * component of relative strength, NOT raw price momentum and NOT 52-week-high nearness.
 *
 * Over a trailing [estimationDays] window of daily simple returns ending the bar before entry, the
 * stock's returns are regressed on SPY's returns (OLS single factor): `r_stock = α + β·r_spy + ε`.
 * The score is the cumulative residual over a recent [momentumDays] sub-window (ending [skipDays]
 * bars before entry, to sidestep short-term reversal), standardized by the residual standard
 * deviation over the full estimation window:
 *
 *   score = Σ(ε over accumulation window) / stdev(ε over estimation window)
 *
 * Why a *sub-window*: OLS residuals over their own fit window sum to ~0 (the intercept absorbs the
 * mean), so accumulating over a recent slice of the estimation window is what makes the momentum
 * signal non-degenerate. Why the `/stdev`: it converts the raw residual sum into a per-unit-
 * idiosyncratic-volatility score, so the rank measures residual *momentum* rather than residual
 * *magnitude* (otherwise high-idio-vol names would dominate). The market beta is the only factor
 * stripped (no size/value/sector series available), so this is market-residual, not full Fama-French
 * idiosyncratic momentum — a positive cross-sectional read is clean, a null is only weakly conclusive.
 *
 * SPY returns are paired to the stock's returns *by date* (a return enters the regression only when
 * both of its endpoint dates have a SPY close), never by index — the SPY proxy is a sparse date map.
 * Every bar read is strictly before the entry bar (look-ahead safe). A stock that cannot be scored
 * — too little history, too few SPY-paired returns, a degenerate (zero-variance) SPY or residual
 * series, an empty SPY map, or any non-finite intermediate — returns [UNSCOREABLE], sorting last so
 * it never wins a fill on a degenerate computation.
 *
 * @param estimationDays Trailing window length (in bars) for the β/α regression and residual stdev.
 * @param momentumDays Length of the recent residual-accumulation sub-window (must be < estimation).
 * @param skipDays Recent bars excluded from the accumulation window end (>= 1, so it ends before entry).
 */
class MarketResidualMomentumRanker(
  private val estimationDays: Int = 504,
  private val momentumDays: Int = 252,
  private val skipDays: Int = 21,
) : StockRanker {
  init {
    require(skipDays >= 1) { "skipDays must be >= 1 (accumulation must end before the entry bar), got $skipDays" }
    require(momentumDays > skipDays) { "momentumDays ($momentumDays) must be greater than skipDays ($skipDays)" }
    require(estimationDays >= momentumDays + skipDays) {
      "estimationDays ($estimationDays) must be >= momentumDays + skipDays (${momentumDays + skipDays})"
    }
  }

  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
  ): Double = score(stock, entryQuote, BacktestContext.EMPTY)

  @Suppress("detekt:ReturnCount")
  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
    context: BacktestContext,
  ): Double {
    val spyMap = context.spyQuoteMap
    if (spyMap.isEmpty()) return UNSCOREABLE

    val entryIdx = stock.indexOnOrAfter(entryQuote.date)
    val estStart = entryIdx - estimationDays
    if (estStart < 0) return UNSCOREABLE

    // Accumulation sub-window covers return indices [accStart .. accEnd], all strictly before entry.
    val accEnd = entryIdx - skipDays
    val accStart = accEnd - momentumDays + 1

    // Pair stock returns to SPY returns by date over the estimation window [estStart+1 .. entryIdx-1].
    val stockReturns = ArrayList<Double>(estimationDays)
    val spyReturns = ArrayList<Double>(estimationDays)
    val inAccumulation = ArrayList<Boolean>(estimationDays)
    for (t in (estStart + 1) until entryIdx) {
      val curr = stock.quotes[t]
      val prev = stock.quotes[t - 1]
      val spyCurr = spyMap[curr.date]
      val spyPrev = spyMap[prev.date]
      val pricesPositive = prev.closePrice > 0.0 && spyPrev != null && spyPrev.closePrice > 0.0
      if (spyCurr != null && spyPrev != null && pricesPositive) {
        stockReturns.add(curr.closePrice / prev.closePrice - 1.0)
        spyReturns.add(spyCurr.closePrice / spyPrev.closePrice - 1.0)
        inAccumulation.add(t in accStart..accEnd)
      }
    }

    val expectedReturns = estimationDays - 1
    val minPaired = ceil(MIN_PAIRED_FRACTION * expectedReturns).toInt()
    if (stockReturns.size < minPaired) return UNSCOREABLE

    val pairedCount = stockReturns.size
    // The sample variance below divides by pairedCount - 1; the init constraints + 80% floor keep
    // pairedCount >= 2 today, but guard explicitly so a future MIN_PAIRED_FRACTION change can't
    // silently re-introduce a divide-by-zero.
    if (pairedCount < 2) return UNSCOREABLE
    val meanStock = stockReturns.average()
    val meanSpy = spyReturns.average()
    var covXY = 0.0
    var varX = 0.0
    for (i in 0 until pairedCount) {
      val dx = spyReturns[i] - meanSpy
      covXY += dx * (stockReturns[i] - meanStock)
      varX += dx * dx
    }
    if (varX < EPSILON) return UNSCOREABLE
    val beta = covXY / varX
    val alpha = meanStock - beta * meanSpy

    var residualSumSq = 0.0
    var accumulatedResidual = 0.0
    var accumulatedCount = 0
    for (i in 0 until pairedCount) {
      val residual = stockReturns[i] - alpha - beta * spyReturns[i]
      residualSumSq += residual * residual
      if (inAccumulation[i]) {
        accumulatedResidual += residual
        accumulatedCount++
      }
    }
    // No paired return survived inside the accumulation window (e.g. SPY gapped across it): there is
    // no momentum reading, so the stock is unscoreable rather than a competitive zero that could win
    // a fill on missing data.
    if (accumulatedCount == 0) return UNSCOREABLE
    // Residuals are mean-zero by OLS construction, so the population sum of squares is the deviation
    // sum of squares; sample variance divides by n - 1.
    val residualStdev = sqrt(residualSumSq / (pairedCount - 1))
    if (residualStdev < EPSILON) return UNSCOREABLE

    val score = accumulatedResidual / residualStdev
    return if (score.isFinite()) score else UNSCOREABLE
  }

  override fun description() =
    "Market-residual momentum ($estimationDays-day SPY-beta regression, " +
      "$momentumDays-$skipDays day residual accumulation)"

  override fun warmupTradingDays() = estimationDays

  companion object {
    /** Below the floor of any real score, so unscoreable stocks always rank last. */
    private const val UNSCOREABLE = -Double.MAX_VALUE

    /** Minimum fraction of estimation-window returns that must have a SPY match for a trustworthy β. */
    private const val MIN_PAIRED_FRACTION = 0.8

    /** Degeneracy floor for the SPY-return variance and the residual stdev. */
    private const val EPSILON = 1e-12
  }
}

/**
 * Multi-factor generalization of [MarketResidualMomentumRanker]: strips BOTH the market (SPY) and the
 * stock's sector-ETF co-movement before reading residual momentum, so the score reflects idiosyncratic
 * relative strength rather than factor (market/sector) momentum — the component theory predicts decays
 * in narrow-leadership tape.
 *
 * Per entry: multivariate OLS regresses the stock's daily returns on [SPY return, sector-ETF return]
 * over the estimation window, then accumulates the standardized residual over the recent
 * momentumDays-skipDays sub-window (the same shape as the single-factor ranker).
 *
 * Only the residual is consumed; the individual partial betas are intentionally never exposed — with a
 * cap-weighted sector ETF (~market plus a tilt) the regressors are collinear and the per-factor betas
 * are unstable, while the fitted value, hence the residual, stays well-defined. The regressors are held
 * as a column list so additional factors (size/value) drop in without restructuring the solve.
 */
class MultiFactorResidualMomentumRanker(
  private val estimationDays: Int = 504,
  private val momentumDays: Int = 252,
  private val skipDays: Int = 21,
) : StockRanker {
  init {
    require(skipDays >= 1) { "skipDays must be >= 1 (accumulation must end before the entry bar), got $skipDays" }
    require(momentumDays > skipDays) { "momentumDays ($momentumDays) must be greater than skipDays ($skipDays)" }
    require(estimationDays >= momentumDays + skipDays) {
      "estimationDays ($estimationDays) must be >= momentumDays + skipDays (${momentumDays + skipDays})"
    }
  }

  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
  ): Double = score(stock, entryQuote, BacktestContext.EMPTY)

  @Suppress("detekt:ReturnCount")
  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
    context: BacktestContext,
  ): Double {
    val spyMap = context.spyQuoteMap
    if (spyMap.isEmpty()) return UNSCOREABLE
    val sectorMap = stock.sectorSymbol?.let { context.sectorEtfQuoteMap[it] }
    if (sectorMap.isNullOrEmpty()) return UNSCOREABLE

    val entryIdx = stock.indexOnOrAfter(entryQuote.date)
    val estStart = entryIdx - estimationDays
    if (estStart < 0) return UNSCOREABLE

    // Accumulation sub-window covers return indices [accStart .. accEnd], all strictly before entry.
    val accEnd = entryIdx - skipDays
    val accStart = accEnd - momentumDays + 1

    val paired = pairReturns(stock, spyMap, sectorMap, estStart, entryIdx, accStart, accEnd)
    val minPaired = ceil(MIN_PAIRED_FRACTION * (estimationDays - 1)).toInt()
    if (paired.size < minPaired) return UNSCOREABLE

    return scoreResidual(paired)
  }

  /**
   * Pair stock returns to BOTH factor series by date over [estStart+1 .. entryIdx-1]; a return is kept
   * only when the stock, SPY and the sector ETF all have the bar and the prior bar at positive prices.
   */
  private fun pairReturns(
    stock: Stock,
    spyMap: Map<LocalDate, StockQuote>,
    sectorMap: Map<LocalDate, StockQuote>,
    estStart: Int,
    entryIdx: Int,
    accStart: Int,
    accEnd: Int,
  ): PairedReturns {
    val stockReturns = ArrayList<Double>(estimationDays)
    val marketReturns = ArrayList<Double>(estimationDays)
    val sectorReturns = ArrayList<Double>(estimationDays)
    val inAccumulation = ArrayList<Boolean>(estimationDays)
    for (t in (estStart + 1) until entryIdx) {
      val curr = stock.quotes[t]
      val prev = stock.quotes[t - 1]
      val spyCurr = spyMap[curr.date]
      val spyPrev = spyMap[prev.date]
      val secCurr = sectorMap[curr.date]
      val secPrev = sectorMap[prev.date]
      if (!barComplete(prev, spyCurr, spyPrev, secCurr, secPrev)) continue
      stockReturns.add(curr.closePrice / prev.closePrice - 1.0)
      marketReturns.add(spyCurr!!.closePrice / spyPrev!!.closePrice - 1.0)
      sectorReturns.add(secCurr!!.closePrice / secPrev!!.closePrice - 1.0)
      inAccumulation.add(t in accStart..accEnd)
    }
    return PairedReturns(stockReturns, listOf(marketReturns, sectorReturns), inAccumulation)
  }

  /** True when the stock, SPY and sector ETF all have current + positive-priced prior bars. */
  private fun barComplete(
    prev: StockQuote,
    spyCurr: StockQuote?,
    spyPrev: StockQuote?,
    secCurr: StockQuote?,
    secPrev: StockQuote?,
  ): Boolean {
    if (spyCurr == null || spyPrev == null) return false
    if (secCurr == null || secPrev == null) return false
    return prev.closePrice > 0.0 && spyPrev.closePrice > 0.0 && secPrev.closePrice > 0.0
  }

  /**
   * OLS (stock ~ intercept + market + sector) over the paired returns, then the standardized residual
   * accumulated across the recent sub-window. A singular X'X or degenerate residual -> unscoreable.
   */
  private fun scoreResidual(paired: PairedReturns): Double {
    val pairedCount = paired.size
    val coefficients = solveOls(paired.factorColumns, paired.stockReturns) ?: return UNSCOREABLE
    val alpha = coefficients[0]

    var residualSumSq = 0.0
    var accumulatedResidual = 0.0
    var accumulatedCount = 0
    for (i in 0 until pairedCount) {
      var fitted = alpha
      for (f in paired.factorColumns.indices) {
        fitted += coefficients[f + 1] * paired.factorColumns[f][i]
      }
      val residual = paired.stockReturns[i] - fitted
      residualSumSq += residual * residual
      if (paired.inAccumulation[i]) {
        accumulatedResidual += residual
        accumulatedCount++
      }
    }
    // No paired return survived inside the accumulation window: there is no momentum reading, so the
    // stock is unscoreable rather than a competitive zero that could win a fill on missing data.
    if (accumulatedCount == 0) return UNSCOREABLE
    // Sample-stdev normalization (divide by pairedCount - 1), matching the single-factor ranker so the
    // two scores are read on the same scale; it is a ranking normalizer, not a regression-dof estimate.
    val residualStdev = sqrt(residualSumSq / (pairedCount - 1))
    if (residualStdev < EPSILON) return UNSCOREABLE

    val score = accumulatedResidual / residualStdev
    return if (score.isFinite()) score else UNSCOREABLE
  }

  override fun description() =
    "Multi-factor-residual momentum ($estimationDays-day market+sector regression, " +
      "$momentumDays-$skipDays day residual accumulation)"

  override fun warmupTradingDays() = estimationDays

  /**
   * OLS by the normal equations for design columns [1, factor_1, ... factor_k]. Returns the coefficient
   * vector [intercept, beta_1, ... beta_k], or null when X'X is singular (degenerate / collinear data).
   */
  private fun solveOls(
    factorColumns: List<List<Double>>,
    response: List<Double>,
  ): DoubleArray? {
    val rows = response.size
    val designColumns = ArrayList<DoubleArray>(factorColumns.size + 1)
    designColumns.add(DoubleArray(rows) { 1.0 })
    factorColumns.forEach { designColumns.add(it.toDoubleArray()) }
    val params = designColumns.size

    val xtx = Array(params) { i -> DoubleArray(params) { j -> dot(designColumns[i], designColumns[j], rows) } }
    val xty = DoubleArray(params) { i -> dotWith(designColumns[i], response, rows) }
    return gaussianSolve(xtx, xty)
  }

  private fun dot(left: DoubleArray, right: DoubleArray, rows: Int): Double {
    var sum = 0.0
    for (i in 0 until rows) sum += left[i] * right[i]
    return sum
  }

  private fun dotWith(left: DoubleArray, right: List<Double>, rows: Int): Double {
    var sum = 0.0
    for (i in 0 until rows) sum += left[i] * right[i]
    return sum
  }

  /** Gaussian elimination with partial pivoting; null if the matrix is singular within tolerance. */
  private fun gaussianSolve(
    matrix: Array<DoubleArray>,
    rhs: DoubleArray,
  ): DoubleArray? {
    val size = rhs.size
    val aug = Array(size) { i -> matrix[i].copyOf() }
    val rhsCopy = rhs.copyOf()
    for (col in 0 until size) {
      var pivotRow = col
      for (r in col + 1 until size) {
        if (kotlin.math.abs(aug[r][col]) > kotlin.math.abs(aug[pivotRow][col])) pivotRow = r
      }
      if (kotlin.math.abs(aug[pivotRow][col]) < EPSILON) return null
      val tmpRow = aug[col]
      aug[col] = aug[pivotRow]
      aug[pivotRow] = tmpRow
      val tmpRhs = rhsCopy[col]
      rhsCopy[col] = rhsCopy[pivotRow]
      rhsCopy[pivotRow] = tmpRhs
      for (r in 0 until size) {
        if (r == col) continue
        val factor = aug[r][col] / aug[col][col]
        for (c in col until size) aug[r][c] -= factor * aug[col][c]
        rhsCopy[r] -= factor * rhsCopy[col]
      }
    }
    return DoubleArray(size) { i -> rhsCopy[i] / aug[i][i] }
  }

  /** Date-paired return series across the stock + factor columns, with the accumulation-window mask. */
  private class PairedReturns(
    val stockReturns: List<Double>,
    val factorColumns: List<List<Double>>,
    val inAccumulation: List<Boolean>,
  ) {
    val size: Int get() = stockReturns.size
  }

  companion object {
    /** Below the floor of any real score, so unscoreable stocks always rank last. */
    private const val UNSCOREABLE = -Double.MAX_VALUE

    /** Minimum fraction of estimation-window returns that must pair across all factor series. */
    private const val MIN_PAIRED_FRACTION = 0.8

    /** Degeneracy floor for the pivot magnitude and the residual stdev. */
    private const val EPSILON = 1e-12
  }
}

/**
 * Orders same-day entry candidates by gross-profitability quality (ADR 0019). A cross-sectional ranker:
 * `score = levelWeight·z_level + trendWeight·z_trend`, where each leg is z-scored *intra-subset* — over
 * the stocks firing the entry that day — so the two differently-scaled legs combine on a common scale
 * (the standardization is the only reason the blend is meaningful; a single leg alone would be a
 * rank-preserving no-op, ADR 0020). It therefore overrides [rankCohort], not [score].
 *
 * - `z_level` standardizes `grossProfit_TTM / totalAssets_asof` — how profitable-per-asset, read via the
 *   canonical [Stock.grossProfitTtmAsOf] / [Stock.latestFundamentalAsOf] (same definition the Midgaard
 *   gate uses, so gate and ranker never disagree about what "quality" means).
 * - `z_trend` standardizes the signed YoY operating-margin change
 *   `operatingMarginTtmAsOf(D) − operatingMarginTtmPriorYearAsOf(D)` (magnitude, not sign-only; the two
 *   TTM windows are disjoint, so it needs eight visible filings).
 *
 * A leg's value is undefined for a name (e.g. fewer than eight filings for the trend) → that name is
 * excluded from the leg's mean/stdev and assigned `z = 0.0` (neutral, never `−MAX` — a quality holding
 * with a missing reading must not be deterministically sunk). A degenerate day (fewer than two defined
 * values, or zero cross-sectional spread) yields `z = 0.0` for the whole leg. Reads point-in-time
 * fundamentals, not trailing price bars, so [warmupTradingDays] is 0.
 */
class FundamentalQualityRanker(
  private val levelWeight: Double = 0.5,
  private val trendWeight: Double = 0.5,
) : StockRanker {
  // Required by the interface but never consulted for selection — the engine ranks this via rankCohort.
  // A single-stock cohort has no cross-sectional spread, so the neutral score is 0.0 (ADR 0020).
  override fun score(
    stock: Stock,
    entryQuote: StockQuote,
  ): Double = 0.0

  override fun rankCohort(
    candidates: List<Pair<Stock, StockQuote>>,
    context: BacktestContext,
  ): List<Double> {
    val levels = candidates.map { (stock, quote) -> qualityLevel(stock, quote.date) }
    val trends = candidates.map { (stock, quote) -> marginTrend(stock, quote.date) }
    val zLevels = zScores(levels)
    val zTrends = zScores(trends)
    return candidates.indices.map { i -> levelWeight * zLevels[i] + trendWeight * zTrends[i] }
  }

  /** Gross profitability `grossProfit_TTM / totalAssets_asof`, or null when undefined. */
  private fun qualityLevel(
    stock: Stock,
    date: LocalDate,
  ): Double? {
    val grossProfit = stock.grossProfitTtmAsOf(date) ?: return null
    val totalAssets = stock.latestFundamentalAsOf(date)?.totalAssets ?: return null
    if (totalAssets <= 0.0) return null
    val ratio = grossProfit / totalAssets
    // GP_TA_CEILING (ADR 0019): exclude an implausible GP/TA as an EODHD bad print so it can't rank top
    // on garbage. Fail-closed (null), floor open (negative kept). Mirrors the Midgaard L2 SQL guard.
    return if (ratio > GP_TA_CEILING) null else ratio
  }

  /**
   * Signed YoY operating-margin change, or null when either TTM window is undefined (< 8 filings) or
   * carries an implausible operating margin (`|opMargin| > OPMARGIN_CEILING`, an EODHD bad print — ADR
   * 0019). A corrupted margin leg neutralises the trend z (fail-closed), so it can't rank top on garbage.
   */
  private fun marginTrend(
    stock: Stock,
    date: LocalDate,
  ): Double? {
    val current = stock.operatingMarginTtmAsOf(date) ?: return null
    val priorYear = stock.operatingMarginTtmPriorYearAsOf(date) ?: return null
    if (abs(current) > OPMARGIN_CEILING || abs(priorYear) > OPMARGIN_CEILING) return null
    return current - priorYear
  }

  /**
   * Intra-subset z-scores aligned by index with [values]. Sample stdev (n−1) over the defined values
   * only; a null value is excluded from the mean/stdev and assigned 0.0 (neutral). Returns all-0.0 for a
   * degenerate leg — fewer than two defined values, or a cross-sectional stdev below the spread floor.
   */
  private fun zScores(values: List<Double?>): List<Double> {
    val present = values.filterNotNull()
    if (present.size < 2) return List(values.size) { 0.0 }
    val mean = present.average()
    val variance = present.sumOf { (it - mean) * (it - mean) } / (present.size - 1)
    val stdev = sqrt(variance)
    if (stdev < SPREAD_FLOOR) return List(values.size) { 0.0 }
    return values.map { value -> if (value == null) 0.0 else (value - mean) / stdev }
  }

  override fun description() =
    "Fundamental quality (${"%.1f".format(levelWeight)}·z-level GP/TA + ${"%.1f".format(trendWeight)}·z-trend op-margin YoY)"

  override fun warmupTradingDays() = 0

  companion object {
    /** Cross-sectional standard-deviation floor below which a leg has no usable spread → neutral. */
    private const val SPREAD_FLOOR = 1e-12

    /**
     * Fail-closed data-quality ceiling on `GP/TA` (ADR 0019; CONTEXT *Gross-profitability quality
     * percentile*) — exclude `GP/TA > 5` as an EODHD bad print so a garbage value can't rank top.
     * Pre-registered (real GP/TA p99.9 ≈ 3.85), never tuned. Must move in lockstep with the Midgaard
     * L2 SQL `GP_TA_CEILING` in `midgaard/.../repository/QuoteRepository.kt` — there is no shared
     * module asserting the two are equal.
     */
    private const val GP_TA_CEILING = 5.0

    /** The same fail-closed ceiling for the operating-margin trend leg — drop a leg whose `|op-margin| > 5`. */
    private const val OPMARGIN_CEILING = 5.0
  }
}
