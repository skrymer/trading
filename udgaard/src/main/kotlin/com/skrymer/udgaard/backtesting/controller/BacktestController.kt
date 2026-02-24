package com.skrymer.udgaard.backtesting.controller

import com.skrymer.udgaard.backtesting.dto.AvailableConditionsResponse
import com.skrymer.udgaard.backtesting.dto.BacktestRequest
import com.skrymer.udgaard.backtesting.model.BacktestReport
import com.skrymer.udgaard.backtesting.model.BacktestResponseDto
import com.skrymer.udgaard.backtesting.model.Trade
import com.skrymer.udgaard.backtesting.model.toResponseDto
import com.skrymer.udgaard.backtesting.service.BacktestResultStore
import com.skrymer.udgaard.backtesting.service.BacktestService
import com.skrymer.udgaard.backtesting.service.ConditionRegistry
import com.skrymer.udgaard.backtesting.service.DynamicStrategyBuilder
import com.skrymer.udgaard.backtesting.service.PositionSizingService
import com.skrymer.udgaard.backtesting.service.StrategyRegistry
import com.skrymer.udgaard.backtesting.strategy.AdaptiveRanker
import com.skrymer.udgaard.backtesting.strategy.CompositeRanker
import com.skrymer.udgaard.backtesting.strategy.DistanceFrom10EmaRanker
import com.skrymer.udgaard.backtesting.strategy.EntryStrategy
import com.skrymer.udgaard.backtesting.strategy.ExitStrategy
import com.skrymer.udgaard.backtesting.strategy.RandomRanker
import com.skrymer.udgaard.backtesting.strategy.SectorStrengthRanker
import com.skrymer.udgaard.backtesting.strategy.StockRanker
import com.skrymer.udgaard.backtesting.strategy.VolatilityRanker
import com.skrymer.udgaard.data.model.AssetType
import com.skrymer.udgaard.data.repository.StockJooqRepository
import com.skrymer.udgaard.data.service.StockService
import com.skrymer.udgaard.data.service.SymbolService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
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
      "Running backtest with config: entry=${request.entryStrategy}, exit=${request.exitStrategy}, " +
        "symbols=${request.stockSymbols?.joinToString(",") ?: "all"}, " +
        "assetTypes=${request.assetTypes?.joinToString(",") ?: "all"}, " +
        "includeSectors=${request.includeSectors?.joinToString(",") ?: "all"}, " +
        "excludeSectors=${request.excludeSectors?.joinToString(",") ?: "none"}, " +
        "startDate=${request.startDate}, " +
        "endDate=${request.endDate}, maxPositions=${request.maxPositions}, ranker=${request.ranker}, " +
        "useUnderlyingAssets=${request.useUnderlyingAssets}, cooldownDays=${request.cooldownDays}, " +
        "entryDelayDays=${request.entryDelayDays}",
    )

    val entryStrategy =
      dynamicStrategyBuilder.buildEntryStrategy(request.entryStrategy)
        ?: return logAndBadRequest("Failed to build entry strategy from config: ${request.entryStrategy}")

    val exitStrategy =
      dynamicStrategyBuilder.buildExitStrategy(request.exitStrategy)
        ?: return logAndBadRequest("Failed to build exit strategy from config: ${request.exitStrategy}")

    val symbols =
      resolveSymbols(request)
        ?: return logAndBadRequest("No symbols found. Use Data Manager to refresh stocks first.")

    val rankerInstance = getRankerInstance(request.ranker)
      ?: throw IllegalArgumentException("Unknown ranker: ${request.ranker}")

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
    val rankers =
      mutableListOf(
        "Adaptive",
        "Volatility",
        "DistanceFrom10Ema",
        "Composite",
        "SectorStrength",
        "Random",
      )
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

  private fun resolveSymbols(request: BacktestRequest): List<String>? {
    var symbols: List<String> =
      if (!request.stockSymbols.isNullOrEmpty()) {
        request.stockSymbols.map { it.uppercase() }
      } else if (!request.assetTypes.isNullOrEmpty()) {
        val assetTypeEnums = request.assetTypes.map { AssetType.valueOf(it) }
        val filtered =
          symbolService
            .getAll()
            .filter { it.assetType in assetTypeEnums }
            .map { it.symbol }
        logger.info("Filtered to ${filtered.size} symbols matching asset types: ${request.assetTypes.joinToString(", ")}")
        filtered
      } else {
        stockRepository.findAllSymbols()
      }

    // Sector filtering requires stock→sector mapping (lightweight query)
    if (!request.includeSectors.isNullOrEmpty() || !request.excludeSectors.isNullOrEmpty()) {
      val sectorBySymbol = stockService.getAllStocksSimple().associate { it.symbol to it.sector }

      if (!request.includeSectors.isNullOrEmpty()) {
        val sectors = request.includeSectors.map { it.uppercase() }.toSet()
        val before = symbols.size
        symbols = symbols.filter { sectorBySymbol[it]?.uppercase() in sectors }
        logger.info("Sector include filter: $before → ${symbols.size} symbols (sectors: ${sectors.joinToString(", ")})")
      }

      if (!request.excludeSectors.isNullOrEmpty()) {
        val sectors = request.excludeSectors.map { it.uppercase() }.toSet()
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
  ): ResponseEntity<BacktestResponseDto> =
    try {
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
        )

      logger.info(
        "Backtest complete: ${backtestReport.trades.size} trades, " +
          "Win rate: ${String.format("%.2f", backtestReport.winRate * 100)}%, " +
          "Edge: ${String.format("%.2f", backtestReport.edge)}%",
      )

      val finalReport =
        if (request.positionSizing != null) {
          val sizingResult = positionSizingService.applyPositionSizing(backtestReport.trades, request.positionSizing)
          logger.info(
            "Position sizing applied: ${sizingResult.startingCapital} → ${String.format("%.2f", sizingResult.finalCapital)}, " +
              "return=${String.format("%.2f", sizingResult.totalReturnPct)}%, " +
              "maxDD=${String.format("%.2f", sizingResult.maxDrawdownPct)}%",
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
      ResponseEntity.ok(finalReport.toResponseDto(backtestId))
    } catch (e: IllegalArgumentException) {
      logger.error("Backtest validation failed: ${e.message}", e)
      @Suppress("UNCHECKED_CAST")
      ResponseEntity.badRequest().body(mapOf("error" to e.message)) as ResponseEntity<BacktestResponseDto>
    } catch (e: Exception) {
      logger.error("Unexpected error during backtest: ${e.message}", e)
      ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
    }

  @Suppress("UNCHECKED_CAST")
  private fun <T> logAndBadRequest(message: String): ResponseEntity<T> {
    logger.error(message)
    return ResponseEntity.badRequest().body(mapOf("error" to message)) as ResponseEntity<T>
  }

  private fun getRankerInstance(rankerName: String): StockRanker? =
    when (rankerName.lowercase()) {
      "volatility" -> VolatilityRanker()
      "distancefrom10ema" -> DistanceFrom10EmaRanker()
      "composite" -> CompositeRanker()
      "sectorstrength" -> SectorStrengthRanker()
      "random" -> RandomRanker()
      "adaptive" -> AdaptiveRanker()
      else -> {
        logger.error("Unknown ranker: $rankerName")
        null
      }
    }

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(BacktestController::class.java)
  }
}
