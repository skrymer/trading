package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.model.BacktestReport
import com.skrymer.udgaard.backtesting.model.PortfolioEquityPoint
import com.skrymer.udgaard.backtesting.model.PositionSizingConfig
import com.skrymer.udgaard.backtesting.model.RiskMetrics
import com.skrymer.udgaard.backtesting.model.SpyBaselineComparison
import com.skrymer.udgaard.backtesting.model.SpyBaselineVerdict
import com.skrymer.udgaard.backtesting.model.Trade
import com.skrymer.udgaard.backtesting.model.TradeStatsSummary
import com.skrymer.udgaard.backtesting.model.WalkForwardConfig
import com.skrymer.udgaard.backtesting.model.WalkForwardResult
import com.skrymer.udgaard.backtesting.model.WalkForwardWindow
import com.skrymer.udgaard.backtesting.strategy.CompositeRanker
import com.skrymer.udgaard.backtesting.strategy.EntryStrategy
import com.skrymer.udgaard.backtesting.strategy.ExitStrategy
import com.skrymer.udgaard.backtesting.strategy.SectorEdgeRanker
import com.skrymer.udgaard.backtesting.strategy.StockRanker
import com.skrymer.udgaard.data.model.MarketBreadthDaily
import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import com.skrymer.udgaard.data.repository.SectorBreadthRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.pow

@Service
class WalkForwardService(
  private val backtestService: BacktestService,
  private val sectorBreadthRepository: SectorBreadthRepository,
  private val marketBreadthRepository: MarketBreadthRepository,
  private val positionSizingService: PositionSizingService,
  private val riskMetricsService: RiskMetricsService,
  private val stockRepository: StockJooqRepository,
  private val riskFreeRateService: RiskFreeRateService,
) {
  private val logger = LoggerFactory.getLogger(WalkForwardService::class.java)

  fun runWalkForward(
    config: WalkForwardConfig,
    entryStrategy: EntryStrategy,
    exitStrategy: ExitStrategy,
    symbols: List<String>,
    ranker: StockRanker = CompositeRanker(),
    maxPositions: Int? = null,
    useUnderlyingAssets: Boolean = true,
    customUnderlyingMap: Map<String, String>? = null,
    cooldownDays: Int = 0,
    entryDelayDays: Int = 0,
    randomSeed: Long? = null,
    positionSizingConfig: PositionSizingConfig? = null,
    riskFreeRatePct: Double = RAW_RISK_FREE_RATE,
    creditIdleCash: Boolean = true,
  ): WalkForwardResult {
    val windows = generateWindows(config)
    logger.info(
      "Walk-forward: ${windows.size} windows, " +
        "IS=${config.inSampleMonths}mo, OOS=${config.outOfSampleMonths}mo, step=${config.stepMonths}mo",
    )

    val sharedContext = BacktestContext(
      sectorBreadthMap = sectorBreadthRepository.findAllAsMap(),
      marketBreadthMap = marketBreadthRepository.findAllAsMap(),
    )
    val sharedBreadthByDate = marketBreadthRepository.calculateBreadthByDate()
    // SPY closes for the buy-and-hold Calmar baseline (ADR 0013). Loaded once; each window's SPY
    // curve is aligned to that window's strategy OOS support. Absent SPY data → null gate (non-fatal).
    val spyCloseByDate = stockRepository
      .findBySymbol(BENCHMARK_SYMBOL, quotesAfter = config.startDate)
      ?.quotes
      ?.associate { it.date to it.closePrice }
      ?: emptyMap()
    logger.info("Pre-loaded shared context for ${windows.size} windows (${spyCloseByDate.size} SPY closes)")

    // Idle-cash crediting (ADR 0016): one rf provider + the SPY trading-day calendar, shared across
    // windows. Accrual is bounded per-window by each window's activity span, so it never crosses the
    // stitched IS gaps (F7).
    val rfProvider = riskFreeRateService.loadProvider(BacktestContext.DEFAULT_IDLE_CASH_EXPENSE_PCT)
    val spyCalendar = spyCloseByDate.keys.sorted()

    val params = BacktestParams(
      entryStrategy = entryStrategy,
      exitStrategy = exitStrategy,
      symbols = symbols,
      ranker = ranker,
      maxPositions = maxPositions,
      useUnderlyingAssets = useUnderlyingAssets,
      customUnderlyingMap = customUnderlyingMap,
      cooldownDays = cooldownDays,
      entryDelayDays = entryDelayDays,
      randomSeed = randomSeed,
      positionSizingConfig = positionSizingConfig,
      riskFreeRatePct = riskFreeRatePct,
      creditIdleCash = creditIdleCash,
      rfProvider = rfProvider,
      tradingCalendar = spyCalendar,
    )

    val concurrency = Semaphore(MAX_CONCURRENT_WINDOWS)
    val results = runBlocking(Dispatchers.Default) {
      windows
        .map { window ->
          async {
            concurrency.withPermit { processWindow(window, params, sharedContext, sharedBreadthByDate, spyCloseByDate) }
          }
        }.awaitAll()
    }
    return aggregateResults(results, params.riskFreeRatePct, params.rfProvider, params.creditIdleCash)
  }

  /**
   * A window's `WalkForwardWindow` (the public DTO row) plus the heavy intermediate data
   * the aggregator needs to stitch a continuous OOS daily-return series per ADR-0005. The
   * curve and trade list are NOT in the public DTO — they are internal aggregation inputs.
   */
  internal data class WindowComputation(
    val window: WalkForwardWindow,
    val equityCurve: List<PortfolioEquityPoint>?,
    val trades: List<Trade>,
    // SPY buy-and-hold equity curve for this window's OOS support, stitched through the same path
    // as `equityCurve` to produce the SPY leg of the Calmar baseline gate (ADR 0013). Null when no
    // SPY data was available for the window.
    val benchmarkEquityCurve: List<PortfolioEquityPoint>? = null,
  )

  private fun processWindow(
    window: WindowDates,
    params: BacktestParams,
    sharedContext: BacktestContext,
    sharedBreadthByDate: Map<LocalDate, Double>,
    spyCloseByDate: Map<LocalDate, Double>,
  ): WindowComputation {
    logger.info(
      "Window: IS ${window.isStart} to ${window.isEnd}, " +
        "OOS ${window.oosStart} to ${window.oosEnd}",
    )

    val isReport = runBacktest(params, window.isStart, window.isEnd, sharedContext, sharedBreadthByDate)
    val sectorRanking = isReport.sectorPerformance
      .sortedByDescending { it.avgProfit }
      .map { it.sector }

    logger.info(
      "IS result: ${isReport.totalTrades} trades, " +
        "edge=${String.format("%.2f", isReport.edge)}, " +
        "sectors=${sectorRanking.take(5).joinToString(",")}",
    )

    // Use IS-derived sector ranking for OOS to prevent look-ahead bias
    val oosParams = if (params.ranker is SectorEdgeRanker && sectorRanking.isNotEmpty()) {
      params.copy(ranker = SectorEdgeRanker(sectorRanking))
    } else {
      params
    }
    val oosReport = runBacktest(oosParams, window.oosStart, window.oosEnd, sharedContext, sharedBreadthByDate)
    logger.info(
      "OOS result: ${oosReport.totalTrades} trades, " +
        "edge=${String.format("%.2f", oosReport.edge)}",
    )

    val perWindowMetrics = computeOosRiskMetrics(oosReport, params)

    val (isUptrendPct, isBreadthAvg) =
      computeRegimeMetrics(window.isStart, window.isEnd, sharedContext.marketBreadthMap)
    val (oosUptrendPct, oosBreadthAvg) =
      computeRegimeMetrics(window.oosStart, window.oosEnd, sharedContext.marketBreadthMap)

    val walkForwardWindow = WalkForwardWindow(
      inSampleStart = window.isStart,
      inSampleEnd = window.isEnd,
      outOfSampleStart = window.oosStart,
      outOfSampleEnd = window.oosEnd,
      derivedSectorRanking = sectorRanking,
      inSampleEdge = isReport.edge,
      outOfSampleEdge = oosReport.edge,
      inSampleTrades = isReport.totalTrades,
      outOfSampleTrades = oosReport.totalTrades,
      inSampleWinRate = isReport.winRate,
      outOfSampleWinRate = oosReport.winRate,
      outOfSampleCagr = perWindowMetrics.cagr,
      outOfSampleMaxDrawdownPct = perWindowMetrics.maxDrawdownPct,
      outOfSampleRiskMetrics = perWindowMetrics.riskMetrics,
      inSampleBreadthUptrendPercent = isUptrendPct,
      inSampleBreadthAvg = isBreadthAvg,
      outOfSampleBreadthUptrendPercent = oosUptrendPct,
      outOfSampleBreadthAvg = oosBreadthAvg,
      outOfSampleStatsByEntryMonth = bucketByEntryMonth(oosReport.trades),
    )
    return WindowComputation(
      window = walkForwardWindow,
      equityCurve = perWindowMetrics.equityCurve,
      trades = oosReport.trades,
      benchmarkEquityCurve = perWindowMetrics.equityCurve?.let { benchmarkCurveFor(it, spyCloseByDate) },
    )
  }

  /**
   * Builds the SPY buy-and-hold curve for this window by mapping every date on the strategy's OOS
   * equity curve to that day's SPY close. Anchoring to the strategy curve's dates guarantees the
   * two legs share identical trading-day support (ADR 0013) — the gate compares "what holding SPY
   * did on exactly these days" against the strategy. Returns null when fewer than two SPY closes
   * line up (no usable benchmark curve to stitch).
   */
  private fun benchmarkCurveFor(
    strategyCurve: List<PortfolioEquityPoint>,
    spyCloseByDate: Map<LocalDate, Double>,
  ): List<PortfolioEquityPoint>? {
    val benchmarkCurve = strategyCurve.mapNotNull { point ->
      spyCloseByDate[point.date]?.let { close -> PortfolioEquityPoint(date = point.date, portfolioValue = close) }
    }
    return benchmarkCurve.takeIf { it.size >= 2 }
  }

  /**
   * Per-window OOS risk metrics + the equity curve they were derived from. The sub-backtest
   * does not compute these itself, so position sizing is applied here to the OOS trades —
   * the same post-backtest step the controller runs. All fields null for an un-sized run.
   */
  private data class PerWindowMetrics(
    val cagr: Double?,
    val maxDrawdownPct: Double?,
    val riskMetrics: RiskMetrics?,
    val equityCurve: List<PortfolioEquityPoint>?,
  )

  private fun computeOosRiskMetrics(
    oosReport: BacktestReport,
    params: BacktestParams,
  ): PerWindowMetrics {
    val positionSizingConfig = params.positionSizingConfig ?: return PerWindowMetrics(null, null, null, null)
    // Credit idle cash within this window's OOS span only (F7) — the spine never extends across the
    // IS gap to the next window because applyPositionSizing bounds it by the trades' activity span.
    val sizing = positionSizingService.applyPositionSizing(
      oosReport.trades,
      positionSizingConfig,
      tradingCalendar = params.tradingCalendar,
      riskFreeRateProvider = params.rfProvider,
      creditIdleCash = params.creditIdleCash,
    )
    val cagr = riskMetricsService.cagr(sizing.equityCurve)
    val sharpe: Double?
    val sortino: Double?
    if (params.creditIdleCash) {
      val dated = riskMetricsService.datedDailyReturns(sizing.equityCurve)
      sharpe = riskMetricsService.sharpe(dated, params.rfProvider)
      sortino = riskMetricsService.sortino(dated, params.rfProvider)
    } else {
      val dailyReturns = riskMetricsService.dailyReturns(sizing.equityCurve)
      sharpe = riskMetricsService.sharpe(dailyReturns, params.riskFreeRatePct)
      sortino = riskMetricsService.sortino(dailyReturns, params.riskFreeRatePct)
    }
    val calmar = riskMetricsService.calmar(cagr, sizing.maxDrawdownPct)
    val sqn = riskMetricsService.sqn(oosReport.trades)
    val tailRatio = riskMetricsService.tailRatio(oosReport.trades)
    val riskMetrics = RiskMetrics(
      sharpeRatio = sharpe,
      sortinoRatio = sortino,
      calmarRatio = calmar,
      sqn = sqn,
      tailRatio = tailRatio,
    )
    return PerWindowMetrics(cagr, sizing.maxDrawdownPct, riskMetrics, sizing.equityCurve)
  }

  /**
   * Buckets trades by their entry-date month (`entryQuote.date`, key "yyyy-MM") into a
   * `TradeStatsSummary` per month. The slicing is intentionally monthly so the engine stays
   * agnostic to which sub-window range a downstream gate cares about — callers re-aggregate the
   * months they need. Per ADR-0006.
   */
  internal fun bucketByEntryMonth(trades: List<Trade>): Map<String, TradeStatsSummary> =
    trades
      .groupBy { ENTRY_MONTH_KEY.format(it.entryQuote.date) }
      .mapValues { (_, monthTrades) -> TradeStatsSummary.fromTrades(monthTrades) }

  /**
   * Returns (uptrendPercent, breadthAvg) over breadth rows whose date falls in [start, end].
   * Uses MarketBreadthDaily.isInUptrend() (breadthPercent > ema10) — the canonical project definition.
   * Both metrics default to 0.0 when no breadth rows exist in the range.
   */
  internal fun computeRegimeMetrics(
    start: LocalDate,
    end: LocalDate,
    breadthMap: Map<LocalDate, MarketBreadthDaily>,
  ): Pair<Double, Double> {
    val daysInRange = breadthMap.entries.filter { it.key in start..end }
    if (daysInRange.isEmpty()) return 0.0 to 0.0
    val uptrendPct = daysInRange.count { it.value.isInUptrend() }.toDouble() / daysInRange.size * 100.0
    val breadthAvg = daysInRange.sumOf { it.value.breadthPercent } / daysInRange.size
    return uptrendPct to breadthAvg
  }

  private fun runBacktest(
    params: BacktestParams,
    after: LocalDate,
    before: LocalDate,
    sharedContext: BacktestContext,
    sharedBreadthByDate: Map<LocalDate, Double>,
  ): BacktestReport = backtestService.backtest(
    entryStrategy = params.entryStrategy,
    exitStrategy = params.exitStrategy,
    symbols = params.symbols,
    after = after,
    before = before,
    maxPositions = params.maxPositions,
    ranker = params.ranker,
    useUnderlyingAssets = params.useUnderlyingAssets,
    customUnderlyingMap = params.customUnderlyingMap,
    cooldownDays = params.cooldownDays,
    entryDelayDays = params.entryDelayDays,
    sharedContext = sharedContext,
    sharedBreadthByDate = sharedBreadthByDate,
    randomSeed = params.randomSeed,
    positionSizingConfig = params.positionSizingConfig,
  )

  internal fun aggregateResults(
    computations: List<WindowComputation>,
    riskFreeRatePct: Double = RAW_RISK_FREE_RATE,
    rfProvider: RiskFreeRateProvider = ZERO_RF_PROVIDER,
    creditIdleCash: Boolean = false,
  ): WalkForwardResult {
    if (computations.isEmpty()) {
      return emptyResult()
    }

    val windows = computations.map { it.window }
    val totalOosTrades = windows.sumOf { it.outOfSampleTrades }
    val weightedOosEdge = if (totalOosTrades > 0) {
      windows.sumOf { it.outOfSampleEdge * it.outOfSampleTrades } / totalOosTrades
    } else {
      0.0
    }
    val weightedOosWinRate = if (totalOosTrades > 0) {
      windows.sumOf { it.outOfSampleWinRate * it.outOfSampleTrades } / totalOosTrades
    } else {
      0.0
    }
    val totalIsTrades = windows.sumOf { it.inSampleTrades }
    val weightedIsEdge = if (totalIsTrades > 0) {
      windows.sumOf { it.inSampleEdge * it.inSampleTrades } / totalIsTrades
    } else {
      0.0
    }
    val wfe = if (weightedIsEdge != 0.0) weightedOosEdge / weightedIsEdge else 0.0

    // Strategy leg credits idle cash (Sharpe via aligned rf); the SPY leg is always 100% invested
    // (idle ≡ 0, F2), so its stitch never credits and keeps a raw Sharpe.
    val stitched = stitchedAggregate(computations, riskFreeRatePct, rfProvider, creditIdleCash)
    val spyBaseline = computeSpyBaseline(computations, stitched, riskFreeRatePct)

    logger.info(
      "Walk-forward complete: WFE=${String.format("%.2f", wfe)}, " +
        "OOS edge=${String.format("%.2f", weightedOosEdge)}, " +
        "IS edge=${String.format("%.2f", weightedIsEdge)}",
    )

    return WalkForwardResult(
      windows = windows,
      aggregateOosEdge = weightedOosEdge,
      aggregateOosTrades = totalOosTrades,
      aggregateOosWinRate = weightedOosWinRate,
      walkForwardEfficiency = wfe,
      aggregateOosRiskMetrics = stitched.riskMetrics,
      aggregateOosCagr = stitched.cagr,
      aggregateOosMaxDrawdownPct = stitched.maxDrawdownPct,
      spyBaselineComparison = spyBaseline,
    )
  }

  /**
   * SPY buy-and-hold Calmar baseline gate (ADR 0013). Stitches the per-window SPY curves through
   * the IDENTICAL path as the strategy curve (per-window `dailyReturns` concatenated, same
   * wall-clock CAGR, same gap-excluded synthetic-curve maxDD) and compares Calmars. Only windows
   * that contributed BOTH a strategy and a SPY curve participate, so neither leg ever sees a
   * cross-window jump and both share the same OOS support.
   *
   * The gate is PASS when the strategy's stitched Calmar is at least SPY's. It is INCONCLUSIVE
   * (binds nothing, never auto-fails) when the stitched OOS series is shorter than
   * [RiskMetricsService.MIN_OVERLAP_DAYS_FOR_CORRELATION] trading days, or when the strategy's
   * stitched maxDD is below [MIN_STITCHED_MAXDD_PCT] — a trivially tiny denominator manufactures
   * an explosive Calmar that would falsely "beat" SPY (quant-adjudicated floor, top of ADR 0013's
   * ~2–3% band). The guard is on the strategy maxDD only: it is the denominator that can explode;
   * SPY's maxDD over the same 60+-day support is never trivially tiny.
   *
   * Returns null when the run is un-sized (no stitched strategy curve) or no SPY curve was
   * available — mirrors the aggregate-metrics null semantics.
   */
  private fun computeSpyBaseline(
    computations: List<WindowComputation>,
    strategy: StitchedAggregate,
    riskFreeRatePct: Double,
  ): SpyBaselineComparison? {
    val spyCurves = computations
      .filter { it.equityCurve != null && it.equityCurve.size >= 2 }
      .filter { it.benchmarkEquityCurve != null && it.benchmarkEquityCurve.size >= 2 }
      .map { it.benchmarkEquityCurve!! }
    if (spyCurves.isEmpty()) return null
    // SPY buy-and-hold is always fully invested → idle ≡ 0 (F2): never credit its stitch.
    val spy = stitchCurves(spyCurves, riskFreeRatePct, ZERO_RF_PROVIDER, creditIdleCash = false) ?: return null

    val strategyCalmar = strategy.riskMetrics?.calmarRatio
    val strategyMaxDd = strategy.maxDrawdownPct
    val strategyReturnsCount = strategy.stitchedReturnsCount

    val (verdict, reason) = when {
      strategyReturnsCount == null || strategyReturnsCount < RiskMetricsService.MIN_OVERLAP_DAYS_FOR_CORRELATION ->
        SpyBaselineVerdict.INCONCLUSIVE to
          "stitched OOS series < ${RiskMetricsService.MIN_OVERLAP_DAYS_FOR_CORRELATION} trading days"
      strategyMaxDd == null || strategyMaxDd < MIN_STITCHED_MAXDD_PCT ->
        SpyBaselineVerdict.INCONCLUSIVE to
          "strategy stitched maxDD < $MIN_STITCHED_MAXDD_PCT% (explosive-Calmar small-sample artifact)"
      strategyCalmar == null || spy.calmar == null ->
        SpyBaselineVerdict.INCONCLUSIVE to "Calmar could not be computed on one leg"
      strategyCalmar >= spy.calmar -> SpyBaselineVerdict.PASS to null
      else -> SpyBaselineVerdict.FAIL to null
    }

    return SpyBaselineComparison(
      verdict = verdict,
      strategyCalmar = strategyCalmar,
      benchmarkCalmar = spy.calmar,
      benchmarkCagr = spy.cagr,
      benchmarkMaxDrawdownPct = spy.maxDrawdownPct,
      benchmarkSharpe = spy.sharpe,
      inconclusiveReason = reason,
    )
  }

  /**
   * Stitches per-window OOS equity curves and trade lists into a single continuous OOS view,
   * then computes aggregate Sharpe / Sortino / Calmar / CAGR / max DD per ADR-0005. Each
   * per-window curve is normalised to its window's starting value and chained multiplicatively
   * into a synthetic continuous-compounding curve; daily returns are concatenated for Sharpe
   * and Sortino; trades are concatenated for SQN and tail ratio. CAGR annualises the total
   * compounded growth over the **wall-clock span** from the first window's first bar to the
   * last window's last bar — matches the SPY-benchmark convention. When `stepMonths >
   * outOfSampleMonths` the strategy is assumed flat (zero return) during gap days, which is
   * the conservative direction. Returns the empty `StitchedAggregate` (all null) when no
   * window contributed an equity curve with at least two points.
   */
  private fun stitchedAggregate(
    computations: List<WindowComputation>,
    riskFreeRatePct: Double,
    rfProvider: RiskFreeRateProvider,
    creditIdleCash: Boolean,
  ): StitchedAggregate {
    val sized = computations.filter { it.equityCurve != null && it.equityCurve.size >= 2 }
    val curve = stitchCurves(sized.map { it.equityCurve!! }, riskFreeRatePct, rfProvider, creditIdleCash)
      ?: return StitchedAggregate(null, null, null, null)

    val allTrades = sized.flatMap { it.trades }
    val riskMetrics = RiskMetrics(
      sharpeRatio = curve.sharpe,
      sortinoRatio = curve.sortino,
      calmarRatio = curve.calmar,
      sqn = riskMetricsService.sqn(allTrades),
      tailRatio = riskMetricsService.tailRatio(allTrades),
    )
    return StitchedAggregate(riskMetrics, curve.cagr, curve.maxDrawdownPct, curve.stitchedReturnsCount)
  }

  /**
   * Core stitch shared by the strategy and SPY legs (ADR 0005 / ADR 0013). Each per-window curve
   * is normalised to its own starting value and chained multiplicatively into a synthetic
   * continuous-compounding curve; per-window `dailyReturns` are concatenated (never one series
   * over a price-concatenated curve, so no window join produces a spurious jump return). CAGR
   * annualises the compounded growth over the wall-clock span from the first window's first bar to
   * the last window's last bar; maxDD is the peak-to-trough of the synthetic curve, so it captures
   * cross-window (seam-straddling) drawdowns a per-window max cannot. Returns null when no curve
   * has at least two points.
   */
  private fun stitchCurves(
    curves: List<List<PortfolioEquityPoint>>,
    riskFreeRatePct: Double,
    rfProvider: RiskFreeRateProvider,
    creditIdleCash: Boolean,
  ): StitchedCurve? {
    val sized = curves.filter { it.size >= 2 }
    if (sized.isEmpty()) return null

    val stitchedReturns = mutableListOf<Double>()
    // When crediting idle cash, Sharpe/Sortino subtract the per-day rf_step over each return's own
    // calendar gap, so the idle leg nets to zero excess — the same coherence the single backtest uses.
    val stitchedExcess = mutableListOf<Double>()
    val syntheticCurve = mutableListOf(1.0)
    var carry = 1.0

    for (curve in sized) {
      val first = curve.first().portfolioValue
      if (first <= 0.0) continue
      stitchedReturns.addAll(riskMetricsService.dailyReturns(curve))
      riskMetricsService
        .datedDailyReturns(curve)
        .mapTo(stitchedExcess) { it.ret - rfProvider.stepRate(it.fromDate, it.toDate) }
      for (point in curve.drop(1)) {
        syntheticCurve.add(carry * (point.portfolioValue / first))
      }
      carry *= curve.last().portfolioValue / first
    }

    val wallClockDays = ChronoUnit.DAYS.between(sized.first().first().date, sized.last().last().date)
    val maxDd = peakToTroughDrawdownPct(syntheticCurve)
    val cagr = if (wallClockDays > 0L && syntheticCurve.first() > 0.0) {
      val growth = syntheticCurve.last() / syntheticCurve.first()
      (growth.pow(DAYS_PER_CALENDAR_YEAR / wallClockDays.toDouble()) - 1.0) * PERCENT_SCALE
    } else {
      null
    }
    val sharpe =
      if (creditIdleCash) {
        riskMetricsService.sharpe(stitchedExcess, RAW_RISK_FREE_RATE)
      } else {
        riskMetricsService.sharpe(stitchedReturns, riskFreeRatePct)
      }
    val sortino =
      if (creditIdleCash) {
        riskMetricsService.sortino(stitchedExcess, RAW_RISK_FREE_RATE)
      } else {
        riskMetricsService.sortino(stitchedReturns, riskFreeRatePct)
      }
    return StitchedCurve(
      cagr = cagr,
      maxDrawdownPct = maxDd,
      sharpe = sharpe,
      sortino = sortino,
      calmar = riskMetricsService.calmar(cagr, maxDd),
      stitchedReturnsCount = stitchedReturns.size,
    )
  }

  private fun peakToTroughDrawdownPct(curve: List<Double>): Double {
    var peak = curve.first()
    var maxDrawdown = 0.0
    for (value in curve) {
      if (value > peak) peak = value
      if (peak > 0.0) {
        val drawdown = (peak - value) / peak * PERCENT_SCALE
        if (drawdown > maxDrawdown) maxDrawdown = drawdown
      }
    }
    return maxDrawdown
  }

  /** Metrics from a single stitched curve (one leg). `stitchedReturnsCount` feeds the OOS-day guard. */
  private data class StitchedCurve(
    val cagr: Double?,
    val maxDrawdownPct: Double?,
    val sharpe: Double?,
    val sortino: Double?,
    val calmar: Double?,
    val stitchedReturnsCount: Int,
  )

  private data class StitchedAggregate(
    val riskMetrics: RiskMetrics?,
    val cagr: Double?,
    val maxDrawdownPct: Double?,
    val stitchedReturnsCount: Int?,
  )

  private fun emptyResult(): WalkForwardResult = WalkForwardResult(
    windows = emptyList(),
    aggregateOosEdge = 0.0,
    aggregateOosTrades = 0,
    aggregateOosWinRate = 0.0,
    walkForwardEfficiency = 0.0,
    aggregateOosRiskMetrics = null,
    aggregateOosCagr = null,
    aggregateOosMaxDrawdownPct = null,
  )

  internal fun generateWindows(config: WalkForwardConfig): List<WindowDates> {
    require(config.inSampleMonths > 0) { "inSampleMonths must be positive, got ${config.inSampleMonths}" }
    require(config.outOfSampleMonths > 0) { "outOfSampleMonths must be positive, got ${config.outOfSampleMonths}" }
    require(config.stepMonths > 0) { "stepMonths must be positive, got ${config.stepMonths}" }
    require(config.stepMonths >= config.outOfSampleMonths) {
      "stepMonths (${config.stepMonths}) must be >= outOfSampleMonths (${config.outOfSampleMonths}); " +
        "smaller step produces overlapping OOS windows, which would double-count trades in WFE aggregation"
    }
    require(config.startDate.isBefore(config.endDate)) {
      "startDate (${config.startDate}) must be before endDate (${config.endDate})"
    }

    val windows = mutableListOf<WindowDates>()
    var isStart = config.startDate

    while (true) {
      val isEnd = isStart.plusMonths(config.inSampleMonths.toLong())
      val oosStart = isEnd.plusDays(1)
      val oosEnd = oosStart.plusMonths(config.outOfSampleMonths.toLong()).minusDays(1)

      if (oosEnd.isAfter(config.endDate)) break

      windows.add(WindowDates(isStart, isEnd, oosStart, oosEnd))
      isStart = isStart.plusMonths(config.stepMonths.toLong())
    }

    return windows
  }

  internal data class WindowDates(
    val isStart: LocalDate,
    val isEnd: LocalDate,
    val oosStart: LocalDate,
    val oosEnd: LocalDate,
  )

  private data class BacktestParams(
    val entryStrategy: EntryStrategy,
    val exitStrategy: ExitStrategy,
    val symbols: List<String>,
    val ranker: StockRanker,
    val maxPositions: Int?,
    val useUnderlyingAssets: Boolean,
    val customUnderlyingMap: Map<String, String>?,
    val cooldownDays: Int,
    val entryDelayDays: Int,
    val randomSeed: Long?,
    val positionSizingConfig: PositionSizingConfig?,
    val riskFreeRatePct: Double,
    val creditIdleCash: Boolean,
    val rfProvider: RiskFreeRateProvider,
    val tradingCalendar: List<LocalDate>,
  )

  companion object {
    // Windows are processed sequentially. Earlier `=2` doubled per-window heap (two
    // BacktestReports + two evaluation contexts simultaneously), causing OOM at 12-15GB
    // on loose-entry candidates with high trade counts (e.g. MR3 with 431 OOS trades per
    // window). Wall-clock cost is ~2x for the sequential reduction; the heap reduction
    // is needed for the v4 candidate sweep against the full ~4000-stock universe.
    private const val MAX_CONCURRENT_WINDOWS = 1
    private const val RAW_RISK_FREE_RATE = 0.0

    // Idle-cash crediting off by default in the aggregate path (the public test entry point); the
    // real walk-forward run injects a Midgaard-backed provider + creditIdleCash=true via runWalkForward.
    private val ZERO_RF_PROVIDER = RiskFreeRateProvider(emptyMap(), expensePct = 0.0)
    private const val DAYS_PER_CALENDAR_YEAR = 365.25
    private const val PERCENT_SCALE = 100.0
    private const val BENCHMARK_SYMBOL = "SPY"

    // SPY-baseline gate INCONCLUSIVE floor (ADR 0013). Below this stitched maxDD the Calmar
    // denominator is small enough that the ratio explodes and would falsely "beat" SPY as a
    // small-sample artifact. Quant-adjudicated to the top of the ADR's ~2–3% band: an asymmetric
    // cost favours the protective end, and a real tradable book's stitched-OOS maxDD sits well
    // clear of 3%. Guard fires on the STRATEGY maxDD only (the denominator that can explode).
    private const val MIN_STITCHED_MAXDD_PCT = 3.0
    private val ENTRY_MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM")
  }
}
