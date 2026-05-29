package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.model.BacktestReport
import com.skrymer.udgaard.backtesting.model.PortfolioEquityPoint
import com.skrymer.udgaard.backtesting.model.PositionSizingConfig
import com.skrymer.udgaard.backtesting.model.RiskMetrics
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
    logger.info("Pre-loaded shared context for ${windows.size} windows")

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
    )

    val concurrency = Semaphore(MAX_CONCURRENT_WINDOWS)
    val results = runBlocking(Dispatchers.Default) {
      windows
        .map { window ->
          async { concurrency.withPermit { processWindow(window, params, sharedContext, sharedBreadthByDate) } }
        }.awaitAll()
    }
    return aggregateResults(results, params.riskFreeRatePct)
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
  )

  private fun processWindow(
    window: WindowDates,
    params: BacktestParams,
    sharedContext: BacktestContext,
    sharedBreadthByDate: Map<LocalDate, Double>,
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

    val perWindowMetrics = computeOosRiskMetrics(oosReport, params.positionSizingConfig, params.riskFreeRatePct)

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
    )
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
    positionSizingConfig: PositionSizingConfig?,
    riskFreeRatePct: Double,
  ): PerWindowMetrics {
    if (positionSizingConfig == null) {
      return PerWindowMetrics(null, null, null, null)
    }
    val sizing = positionSizingService.applyPositionSizing(oosReport.trades, positionSizingConfig)
    val dailyReturns = riskMetricsService.dailyReturns(sizing.equityCurve)
    val cagr = riskMetricsService.cagr(sizing.equityCurve)
    val sharpe = riskMetricsService.sharpe(dailyReturns, riskFreeRatePct)
    val sortino = riskMetricsService.sortino(dailyReturns, riskFreeRatePct)
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

    val stitched = stitchedAggregate(computations, riskFreeRatePct)

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
  private fun stitchedAggregate(computations: List<WindowComputation>, riskFreeRatePct: Double): StitchedAggregate {
    val sized = computations.filter { it.equityCurve != null && it.equityCurve.size >= 2 }
    if (sized.isEmpty()) return StitchedAggregate(null, null, null)

    val stitchedReturns = mutableListOf<Double>()
    val syntheticCurve = mutableListOf<Double>()
    var carry = 1.0
    syntheticCurve.add(carry)

    for (computation in sized) {
      val curve = computation.equityCurve!!
      val first = curve.first().portfolioValue
      if (first <= 0.0) continue
      stitchedReturns.addAll(riskMetricsService.dailyReturns(curve))
      for (point in curve.drop(1)) {
        val growth = point.portfolioValue / first
        syntheticCurve.add(carry * growth)
      }
      carry *= curve.last().portfolioValue / first
    }

    val wallClockDays = ChronoUnit.DAYS.between(
      sized
        .first()
        .equityCurve!!
        .first()
        .date,
      sized
        .last()
        .equityCurve!!
        .last()
        .date,
    )

    val sharpe = riskMetricsService.sharpe(stitchedReturns, riskFreeRatePct)
    val sortino = riskMetricsService.sortino(stitchedReturns, riskFreeRatePct)
    val maxDd = peakToTroughDrawdownPct(syntheticCurve)
    val cagr = if (wallClockDays > 0L && syntheticCurve.first() > 0.0) {
      val growth = syntheticCurve.last() / syntheticCurve.first()
      (growth.pow(DAYS_PER_CALENDAR_YEAR / wallClockDays.toDouble()) - 1.0) * PERCENT_SCALE
    } else {
      null
    }
    val calmar = riskMetricsService.calmar(cagr, maxDd)
    val allTrades = sized.flatMap { it.trades }
    val sqn = riskMetricsService.sqn(allTrades)
    val tailRatio = riskMetricsService.tailRatio(allTrades)

    val riskMetrics = RiskMetrics(
      sharpeRatio = sharpe,
      sortinoRatio = sortino,
      calmarRatio = calmar,
      sqn = sqn,
      tailRatio = tailRatio,
    )
    return StitchedAggregate(riskMetrics, cagr, maxDd)
  }

  private fun peakToTroughDrawdownPct(curve: List<Double>): Double {
    var peak = curve.first()
    var maxDd = 0.0
    for (value in curve) {
      if (value > peak) peak = value
      if (peak > 0.0) {
        val dd = (peak - value) / peak * PERCENT_SCALE
        if (dd > maxDd) maxDd = dd
      }
    }
    return maxDd
  }

  private data class StitchedAggregate(
    val riskMetrics: RiskMetrics?,
    val cagr: Double?,
    val maxDrawdownPct: Double?,
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
  )

  companion object {
    // Windows are processed sequentially. Earlier `=2` doubled per-window heap (two
    // BacktestReports + two evaluation contexts simultaneously), causing OOM at 12-15GB
    // on loose-entry candidates with high trade counts (e.g. MR3 with 431 OOS trades per
    // window). Wall-clock cost is ~2x for the sequential reduction; the heap reduction
    // is needed for the v4 candidate sweep against the full ~4000-stock universe.
    private const val MAX_CONCURRENT_WINDOWS = 1
    private const val RAW_RISK_FREE_RATE = 0.0
    private const val DAYS_PER_CALENDAR_YEAR = 365.25
    private const val PERCENT_SCALE = 100.0
    private val ENTRY_MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM")
  }
}
