package com.skrymer.udgaard.backtesting.controller

import com.skrymer.udgaard.backtesting.dto.AvailableConditionsResponse
import com.skrymer.udgaard.backtesting.dto.BacktestRequest
import com.skrymer.udgaard.backtesting.dto.CustomStrategyConfig
import com.skrymer.udgaard.backtesting.dto.PredefinedStrategyConfig
import com.skrymer.udgaard.backtesting.dto.StrategyConfig
import com.skrymer.udgaard.backtesting.dto.WalkForwardRequest
import com.skrymer.udgaard.backtesting.model.BacktestReport
import com.skrymer.udgaard.backtesting.model.BacktestResponseDto
import com.skrymer.udgaard.backtesting.model.Trade
import com.skrymer.udgaard.backtesting.model.WalkForwardConfig
import com.skrymer.udgaard.backtesting.model.WalkForwardResult
import com.skrymer.udgaard.backtesting.model.toResponseDto
import com.skrymer.udgaard.backtesting.service.BacktestResultStore
import com.skrymer.udgaard.backtesting.service.BacktestService
import com.skrymer.udgaard.backtesting.service.ConditionRegistry
import com.skrymer.udgaard.backtesting.service.DynamicStrategyBuilder
import com.skrymer.udgaard.backtesting.service.PositionSizingService
import com.skrymer.udgaard.backtesting.service.StrategyRegistry
import com.skrymer.udgaard.backtesting.service.WalkForwardService
import com.skrymer.udgaard.backtesting.strategy.CompositeRanker
import com.skrymer.udgaard.backtesting.strategy.EntryStrategy
import com.skrymer.udgaard.backtesting.strategy.ExitStrategy
import com.skrymer.udgaard.backtesting.strategy.RankerFactory
import com.skrymer.udgaard.backtesting.strategy.StockRanker
import com.skrymer.udgaard.data.model.AssetType
import com.skrymer.udgaard.data.repository.StockJooqRepository
import com.skrymer.udgaard.data.service.StockService
import com.skrymer.udgaard.data.service.SymbolService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * REST controller for backtesting operations.
 *
 * Handles:
 * - Running backtests with query parameters
 * - Running backtests with request body (predefined or custom strategies)
 * - Retrieving available strategies, rankers, and conditions
 */
@RestController
@RequestMapping("/api/backtest")
class BacktestController(
  private val stockService: StockService,
  private val stockRepository: StockJooqRepository,
  private val symbolService: SymbolService,
  private val backtestService: BacktestService,
  private val strategyRegistry: StrategyRegistry,
  private val dynamicStrategyBuilder: DynamicStrategyBuilder,
  private val conditionRegistry: ConditionRegistry,
  private val backtestResultStore: BacktestResultStore,
  private val positionSizingService: PositionSizingService,
  private val walkForwardService: WalkForwardService,
) {
  /**
   * Run backtest with request body.
   * Supports both predefined and custom strategies.
   *
   * Example: POST /api/backtest with JSON body
   */
  @PostMapping
  fun runBacktestWithConfig(
    @RequestBody request: BacktestRequest,
  ): ResponseEntity<BacktestResponseDto> {
    logger.info(
      "Running backtest: entry={}, exit={}, symbols={}, assetTypes={}, " +
        "includeSectors={}, excludeSectors={}, dates={}..{}, " +
        "maxPositions={}, ranker={}, rankerConfig={}, randomSeed={}, " +
        "useUnderlyingAssets={}, cooldownDays={}, entryDelayDays={}, " +
        "positionSizing={}",
      summarizeStrategy(request.entryStrategy),
      summarizeStrategy(request.exitStrategy),
      request.stockSymbols?.size ?: "all",
      request.assetTypes?.joinToString(",") ?: "all",
      request.includeSectors?.joinToString(",") ?: "all",
      request.excludeSectors?.joinToString(",") ?: "none",
      request.startDate,
      request.endDate,
      request.maxPositions,
      request.ranker,
      request.rankerConfig,
      request.randomSeed,
      request.useUnderlyingAssets,
      request.cooldownDays,
      request.entryDelayDays,
      request.positionSizing,
    )
    logger.debug("Full backtest request: {}", request)

    val entryStrategy =
      dynamicStrategyBuilder.buildEntryStrategy(request.entryStrategy)
        ?: throw IllegalArgumentException(
          "Failed to build entry strategy from config: ${request.entryStrategy}",
        )

    val exitStrategy =
      requireNotNull(dynamicStrategyBuilder.buildExitStrategy(request.exitStrategy)) {
        "Failed to build exit strategy from config: ${request.exitStrategy}"
      }

    val symbols =
      requireNotNull(resolveSymbols(request)) {
        "No symbols found. Use Data Manager to refresh stocks first."
      }

    val rankerInstance = if (request.ranker == null) {
      entryStrategy.preferredRanker() ?: CompositeRanker()
    } else {
      requireNotNull(RankerFactory.create(request.ranker, request.rankerConfig)) {
        "Unknown ranker: ${request.ranker}"
      }
    }

    val start = request.startDate?.let { LocalDate.parse(it) } ?: LocalDate.parse("2016-01-01")
    val end = request.endDate?.let { LocalDate.parse(it) } ?: LocalDate.now()
    logger.info("Date range: $start to $end")

    return executeBacktest(entryStrategy, exitStrategy, symbols, start, end, request, rankerInstance)
  }

  /**
   * Fetch trades for a specific entry date (or date range) from a cached backtest result.
   * Used for on-demand drill-down from the overview bar chart.
   *
   * Example: GET /api/backtest/{backtestId}/trades?startDate=2024-01-15&endDate=2024-01-19
   */
  @GetMapping("/{backtestId}/trades")
  fun getTradesForDate(
    @PathVariable backtestId: String,
    @RequestParam startDate: String,
    @RequestParam(required = false) endDate: String?,
  ): ResponseEntity<List<Trade>> {
    val report = backtestResultStore.get(backtestId)
    if (report == null) {
      logger.warn("Backtest result not found: $backtestId")
      return ResponseEntity.notFound().build()
    }

    val start = LocalDate.parse(startDate)
    val end = endDate?.let { LocalDate.parse(it) } ?: start

    val matchingTrades =
      report.trades.filter { trade ->
        val entryDate = trade.entryQuote.date
        !entryDate.isBefore(start) && !entryDate.isAfter(end)
      }

    logger.info("Returning ${matchingTrades.size} trades for backtest $backtestId, dates $start to $end")
    return ResponseEntity.ok(matchingTrades)
  }

  /**
   * Fetch missed (not-selected) trades for a date range. Includes capital-skipped and slot-limit skipped.
   * Each trade carries its entryContext snapshot for post-hoc selection-bias analysis.
   *
   * Example: GET /api/backtest/{backtestId}/missed-trades?startDate=2020-01-01&endDate=2020-12-31
   */
  @GetMapping("/{backtestId}/missed-trades")
  fun getMissedTrades(
    @PathVariable backtestId: String,
    @RequestParam startDate: String,
    @RequestParam(required = false) endDate: String?,
  ): ResponseEntity<List<Trade>> {
    val report = backtestResultStore.get(backtestId) ?: return ResponseEntity.notFound().build()

    val start = LocalDate.parse(startDate)
    val end = endDate?.let { LocalDate.parse(it) } ?: start

    val matching = report.missedTrades.filter { trade ->
      val d = trade.entryQuote.date
      !d.isBefore(start) && !d.isAfter(end)
    }

    logger.info("Returning ${matching.size} missed trades for backtest $backtestId, dates $start to $end")
    return ResponseEntity.ok(matching)
  }

  /**
   * Get available entry and exit strategies.
   *
   * Example: GET /api/backtest/strategies
   */
  @GetMapping("/strategies")
  fun getAvailableStrategies(): ResponseEntity<Map<String, List<String>>> {
    logger.info("Retrieving available strategies")
    val strategies =
      mapOf(
        "entryStrategies" to strategyRegistry.getAvailableEntryStrategies(),
        "exitStrategies" to strategyRegistry.getAvailableExitStrategies(),
      )
    logger.info("Returning ${strategies["entryStrategies"]?.size} entry and ${strategies["exitStrategies"]?.size} exit strategies")
    return ResponseEntity.ok(strategies)
  }

  /**
   * Get available rankers for stock selection.
   *
   * Example: GET /api/backtest/rankers
   */
  @GetMapping("/rankers")
  fun getAvailableRankers(): ResponseEntity<List<String>> {
    logger.info("Retrieving available rankers")
    val rankers = RankerFactory.availableRankers()
    logger.info("Returning ${rankers.size} rankers")
    return ResponseEntity.ok(rankers)
  }

  /**
   * Get available conditions for building custom strategies in the UI.
   *
   * Example: GET /api/backtest/conditions
   */
  @GetMapping("/conditions")
  fun getAvailableConditions(): ResponseEntity<AvailableConditionsResponse> {
    logger.info("Retrieving available conditions for strategy builder")
    val conditions =
      AvailableConditionsResponse(
        entryConditions = conditionRegistry.getEntryConditionMetadata(),
        exitConditions = conditionRegistry.getExitConditionMetadata(),
      )
    logger.info("Returning ${conditions.entryConditions.size} entry conditions and ${conditions.exitConditions.size} exit conditions")
    return ResponseEntity.ok(conditions)
  }

  @PostMapping("/walk-forward")
  fun runWalkForward(
    @RequestBody request: WalkForwardRequest,
  ): ResponseEntity<WalkForwardResult> {
    val entryStrategy = dynamicStrategyBuilder.buildEntryStrategy(request.entryStrategy)
      ?: throw IllegalArgumentException("Failed to build entry strategy from config: ${request.entryStrategy}")

    val exitStrategy = requireNotNull(dynamicStrategyBuilder.buildExitStrategy(request.exitStrategy)) {
      "Failed to build exit strategy from config: ${request.exitStrategy}"
    }

    val symbols = requireNotNull(resolveSymbols(request)) {
      "No symbols found. Use Data Manager to refresh stocks first."
    }

    val rankerInstance = if (request.ranker == null) {
      entryStrategy.preferredRanker() ?: CompositeRanker()
    } else {
      requireNotNull(RankerFactory.create(request.ranker, request.rankerConfig)) {
        "Unknown ranker: ${request.ranker}"
      }
    }

    val start = request.startDate?.let { LocalDate.parse(it) } ?: LocalDate.parse("2016-01-01")
    val end = request.endDate?.let { LocalDate.parse(it) } ?: LocalDate.now()

    val inSampleMonths = request.inSampleMonths ?: (request.inSampleYears * MONTHS_PER_YEAR)
    val outOfSampleMonths = request.outOfSampleMonths ?: (request.outOfSampleYears * MONTHS_PER_YEAR)
    val stepMonths = request.stepMonths ?: (request.stepYears * MONTHS_PER_YEAR)

    val config = WalkForwardConfig(
      inSampleMonths = inSampleMonths,
      outOfSampleMonths = outOfSampleMonths,
      stepMonths = stepMonths,
      startDate = start,
      endDate = end,
    )

    logger.info(
      "Running walk-forward: IS={}mo, OOS={}mo, step={}mo, ranker={}, randomSeed={}, positionSized={}",
      config.inSampleMonths,
      config.outOfSampleMonths,
      config.stepMonths,
      request.ranker,
      request.randomSeed,
      request.positionSizing != null,
    )
    logger.debug("Full walk-forward request: {}", request)

    val result = walkForwardService.runWalkForward(
      config = config,
      entryStrategy = entryStrategy,
      exitStrategy = exitStrategy,
      symbols = symbols,
      ranker = rankerInstance,
      maxPositions = request.maxPositions,
      useUnderlyingAssets = request.useUnderlyingAssets,
      customUnderlyingMap = request.customUnderlyingMap,
      cooldownDays = request.cooldownDays,
      entryDelayDays = request.entryDelayDays,
      randomSeed = request.randomSeed,
      positionSizingConfig = request.positionSizing,
    )

    logger.info(
      "Walk-forward complete: WFE=${String.format("%.2f", result.walkForwardEfficiency)}, " +
        "OOS edge=${String.format("%.2f", result.aggregateOosEdge)}",
    )

    return ResponseEntity.ok(result)
  }

  private fun resolveSymbols(request: WalkForwardRequest): List<String>? =
    resolveSymbols(
      stockSymbols = request.stockSymbols,
      assetTypes = request.assetTypes,
      includeSectors = request.includeSectors,
      excludeSectors = request.excludeSectors,
    )

  private fun resolveSymbols(request: BacktestRequest): List<String>? =
    resolveSymbols(
      stockSymbols = request.stockSymbols,
      assetTypes = request.assetTypes,
      includeSectors = request.includeSectors,
      excludeSectors = request.excludeSectors,
    )

  private fun resolveSymbols(
    stockSymbols: List<String>?,
    assetTypes: List<String>?,
    includeSectors: List<String>?,
    excludeSectors: List<String>?,
  ): List<String>? {
    var symbols: List<String> =
      if (!stockSymbols.isNullOrEmpty()) {
        stockSymbols.map { it.uppercase() }
      } else if (!assetTypes.isNullOrEmpty()) {
        val assetTypeEnums = assetTypes.map { AssetType.valueOf(it) }
        val filtered =
          symbolService
            .getAll()
            .filter { it.assetType in assetTypeEnums }
            .map { it.symbol }
        logger.info("Filtered to ${filtered.size} symbols matching asset types: ${assetTypes.joinToString(", ")}")
        filtered
      } else {
        stockRepository.findAllSymbols()
      }

    if (!includeSectors.isNullOrEmpty() || !excludeSectors.isNullOrEmpty()) {
      val sectorBySymbol = stockService.getAllStocksSimple().associate { it.symbol to it.sector }

      if (!includeSectors.isNullOrEmpty()) {
        val sectors = includeSectors.map { it.uppercase() }.toSet()
        val before = symbols.size
        symbols = symbols.filter { sectorBySymbol[it]?.uppercase() in sectors }
        logger.info("Sector include filter: $before → ${symbols.size} symbols (sectors: ${sectors.joinToString(", ")})")
      }

      if (!excludeSectors.isNullOrEmpty()) {
        val sectors = excludeSectors.map { it.uppercase() }.toSet()
        val before = symbols.size
        symbols = symbols.filter { sectorBySymbol[it]?.uppercase() !in sectors }
        logger.info("Sector exclude filter: $before → ${symbols.size} symbols (excluded: ${sectors.joinToString(", ")})")
      }
    }

    if (symbols.isEmpty()) {
      logger.warn("No stocks found in database. Use Data Manager to refresh stocks first.")
      return null
    }

    logger.info("${symbols.size} symbols resolved")
    return symbols
  }

  private fun executeBacktest(
    entryStrategy: EntryStrategy,
    exitStrategy: ExitStrategy,
    symbols: List<String>,
    start: LocalDate,
    end: LocalDate,
    request: BacktestRequest,
    ranker: StockRanker,
  ): ResponseEntity<BacktestResponseDto> {
    logger.info("Starting backtest execution...")
    val backtestReport =
      backtestService.backtest(
        entryStrategy,
        exitStrategy,
        symbols,
        start,
        end,
        request.maxPositions,
        ranker,
        request.useUnderlyingAssets,
        request.customUnderlyingMap,
        request.cooldownDays,
        request.entryDelayDays,
        randomSeed = request.randomSeed,
        positionSizingConfig = request.positionSizing,
      )

    logger.info(
      "Backtest complete: ${backtestReport.trades.size} trades, " +
        "Win rate: ${String.format("%.2f", backtestReport.winRate * 100)}%, " +
        "Edge: ${String.format("%.2f", backtestReport.edge)}%",
    )

    val finalReport =
      if (request.positionSizing != null) {
        val sizingResult = positionSizingService.applyPositionSizing(backtestReport.trades, request.positionSizing)
        val postHocZeroed = backtestReport.trades.size - sizingResult.trades.size
        logger.info(
          "Position sizing applied: ${sizingResult.startingCapital} → ${String.format("%.2f", sizingResult.finalCapital)}, " +
            "return=${String.format("%.2f", sizingResult.totalReturnPct)}%, " +
            "maxDD=${String.format("%.2f", sizingResult.maxDrawdownPct)}%, " +
            "postHocZeroed=$postHocZeroed",
        )
        BacktestReport(
          winningTrades = backtestReport.winningTrades,
          losingTrades = backtestReport.losingTrades,
          missedTrades = backtestReport.missedTrades,
          timeBasedStats = backtestReport.timeBasedStats,
          exitReasonAnalysis = backtestReport.exitReasonAnalysis,
          sectorPerformance = backtestReport.sectorPerformance,
          stockPerformance = backtestReport.stockPerformance,
          atrDrawdownStats = backtestReport.atrDrawdownStats,
          marketConditionAverages = backtestReport.marketConditionAverages,
          edgeConsistencyScore = backtestReport.edgeConsistencyScore,
          positionSizingResult = sizingResult,
        )
      } else {
        backtestReport
      }

    val backtestId = backtestResultStore.store(finalReport)
    return ResponseEntity.ok(finalReport.toResponseDto(backtestId))
  }

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(BacktestController::class.java)
    private const val MONTHS_PER_YEAR = 12

    private fun summarizeStrategy(cfg: StrategyConfig): String =
      when (cfg) {
        is PredefinedStrategyConfig -> "predefined:${cfg.name}"
        is CustomStrategyConfig -> "custom(${cfg.conditions.size} conditions, op=${cfg.operator.ifBlank { "default" }})"
      }
  }
}
