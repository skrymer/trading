package com.skrymer.udgaard.backtesting.service

import com.skrymer.udgaard.backtesting.model.BacktestContext
import com.skrymer.udgaard.backtesting.model.BacktestReport
import com.skrymer.udgaard.backtesting.model.WalkForwardConfig
import com.skrymer.udgaard.backtesting.model.WalkForwardResult
import com.skrymer.udgaard.backtesting.model.WalkForwardWindow
import com.skrymer.udgaard.backtesting.strategy.CompositeRanker
import com.skrymer.udgaard.backtesting.strategy.EntryStrategy
import com.skrymer.udgaard.backtesting.strategy.ExitStrategy
import com.skrymer.udgaard.backtesting.strategy.StockRanker
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

@Service
class WalkForwardService(
  private val backtestService: BacktestService,
  private val sectorBreadthRepository: SectorBreadthRepository,
  private val marketBreadthRepository: MarketBreadthRepository,
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
  ): WalkForwardResult {
    require(config.inSampleYears > 0) { "inSampleYears must be positive" }
    require(config.outOfSampleYears > 0) { "outOfSampleYears must be positive" }
    require(config.stepYears > 0) { "stepYears must be positive" }
    require(config.startDate.isBefore(config.endDate)) { "startDate must be before endDate" }

    val windows = generateWindows(config)
    logger.info(
      "Walk-forward: ${windows.size} windows, " +
        "IS=${config.inSampleYears}y, OOS=${config.outOfSampleYears}y",
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
    )

    val concurrency = Semaphore(MAX_CONCURRENT_WINDOWS)
    val results = runBlocking(Dispatchers.Default) {
      windows.map { window ->
        async { concurrency.withPermit { processWindow(window, params, sharedContext, sharedBreadthByDate) } }
      }.awaitAll()
    }
    return aggregateResults(results)
  }

  private fun processWindow(
    window: WindowDates,
    params: BacktestParams,
    sharedContext: BacktestContext,
    sharedBreadthByDate: Map<LocalDate, Double>,
  ): WalkForwardWindow {
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

    val oosReport = runBacktest(params, window.oosStart, window.oosEnd, sharedContext, sharedBreadthByDate)
    logger.info(
      "OOS result: ${oosReport.totalTrades} trades, " +
        "edge=${String.format("%.2f", oosReport.edge)}",
    )

    return WalkForwardWindow(
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
    )
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
  )

  private fun aggregateResults(results: List<WalkForwardWindow>): WalkForwardResult {
    if (results.isEmpty()) {
      return WalkForwardResult(
        windows = emptyList(),
        aggregateOosEdge = 0.0,
        aggregateOosTrades = 0,
        aggregateOosWinRate = 0.0,
        walkForwardEfficiency = 0.0,
      )
    }

    val totalOosTrades = results.sumOf { it.outOfSampleTrades }
    val weightedOosEdge = if (totalOosTrades > 0) {
      results.sumOf { it.outOfSampleEdge * it.outOfSampleTrades } / totalOosTrades
    } else {
      0.0
    }
    val weightedOosWinRate = if (totalOosTrades > 0) {
      results.sumOf { it.outOfSampleWinRate * it.outOfSampleTrades } / totalOosTrades
    } else {
      0.0
    }
    val totalIsTrades = results.sumOf { it.inSampleTrades }
    val weightedIsEdge = if (totalIsTrades > 0) {
      results.sumOf { it.inSampleEdge * it.inSampleTrades } / totalIsTrades
    } else {
      0.0
    }
    val wfe = if (weightedIsEdge != 0.0) weightedOosEdge / weightedIsEdge else 0.0

    logger.info(
      "Walk-forward complete: WFE=${String.format("%.2f", wfe)}, " +
        "OOS edge=${String.format("%.2f", weightedOosEdge)}, " +
        "IS edge=${String.format("%.2f", weightedIsEdge)}",
    )

    return WalkForwardResult(
      windows = results,
      aggregateOosEdge = weightedOosEdge,
      aggregateOosTrades = totalOosTrades,
      aggregateOosWinRate = weightedOosWinRate,
      walkForwardEfficiency = wfe,
    )
  }

  private fun generateWindows(config: WalkForwardConfig): List<WindowDates> {
    val windows = mutableListOf<WindowDates>()
    var isStart = config.startDate

    while (true) {
      val isEnd = isStart.plusYears(config.inSampleYears.toLong())
      val oosStart = isEnd.plusDays(1)
      val oosEnd = oosStart.plusYears(config.outOfSampleYears.toLong()).minusDays(1)

      if (oosEnd.isAfter(config.endDate)) break

      windows.add(WindowDates(isStart, isEnd, oosStart, oosEnd))
      isStart = isStart.plusYears(config.stepYears.toLong())
    }

    return windows
  }

  private data class WindowDates(
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
  )

  companion object {
    private const val MAX_CONCURRENT_WINDOWS = 2
  }
}
