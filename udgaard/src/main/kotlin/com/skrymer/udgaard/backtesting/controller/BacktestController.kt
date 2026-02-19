package com.skrymer.udgaard.backtesting.controller

import com.skrymer.udgaard.backtesting.dto.AvailableConditionsResponse
import com.skrymer.udgaard.backtesting.dto.BacktestRequest
import com.skrymer.udgaard.backtesting.model.BacktestResponseDto
import com.skrymer.udgaard.backtesting.model.Trade
import com.skrymer.udgaard.backtesting.model.toResponseDto
import com.skrymer.udgaard.backtesting.service.BacktestResultStore
import com.skrymer.udgaard.backtesting.service.BacktestService
import com.skrymer.udgaard.backtesting.service.ConditionRegistry
import com.skrymer.udgaard.backtesting.service.DynamicStrategyBuilder
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
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.service.StockService
import com.skrymer.udgaard.data.service.SymbolService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
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
@CrossOrigin(origins = ["http://localhost:3000", "http://localhost:8080"])
class BacktestController(
  private val stockService: StockService,
  private val symbolService: SymbolService,
  private val backtestService: BacktestService,
  private val strategyRegistry: StrategyRegistry,
  private val dynamicStrategyBuilder: DynamicStrategyBuilder,
  private val conditionRegistry: ConditionRegistry,
  private val backtestResultStore: BacktestResultStore,
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
        "useUnderlyingAssets=${request.useUnderlyingAssets}, cooldownDays=${request.cooldownDays}",
    )

    val entryStrategy =
      dynamicStrategyBuilder.buildEntryStrategy(request.entryStrategy)
        ?: return logAndBadRequest("Failed to build entry strategy from config: ${request.entryStrategy}")

    val exitStrategy =
      dynamicStrategyBuilder.buildExitStrategy(request.exitStrategy)
        ?: return logAndBadRequest("Failed to build exit strategy from config: ${request.exitStrategy}")

    val stocks = resolveStocks(request)
    val rankerInstance = getRankerInstance(request.ranker)
    if (stocks == null || rankerInstance == null) {
      return ResponseEntity.badRequest().build()
    }

    val start = request.startDate?.let { LocalDate.parse(it) } ?: LocalDate.parse("2016-01-01")
    val end = request.endDate?.let { LocalDate.parse(it) } ?: LocalDate.now()
    logger.info("Date range: $start to $end")

    return executeBacktest(entryStrategy, exitStrategy, stocks, start, end, request, rankerInstance)
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
        entryDate != null && !entryDate.isBefore(start) && !entryDate.isAfter(end)
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

  private fun resolveStocks(request: BacktestRequest): List<Stock>? {
    var stocks =
      if (!request.stockSymbols.isNullOrEmpty()) {
        stockService.getStocksBySymbols(request.stockSymbols.map { it.uppercase() })
      } else if (!request.assetTypes.isNullOrEmpty()) {
        val assetTypeEnums = request.assetTypes.map { AssetType.valueOf(it) }
        val symbols =
          symbolService
            .getAll()
            .filter { it.assetType in assetTypeEnums }
            .map { it.symbol }
        logger.info("Filtered to ${symbols.size} symbols matching asset types: ${request.assetTypes.joinToString(", ")}")
        stockService.getStocksBySymbols(symbols)
      } else {
        stockService.getAllStocks()
      }

    if (!request.includeSectors.isNullOrEmpty()) {
      val sectors = request.includeSectors.map { it.uppercase() }.toSet()
      val before = stocks.size
      stocks = stocks.filter { it.sectorSymbol?.uppercase() in sectors }
      logger.info("Sector include filter: $before → ${stocks.size} stocks (sectors: ${sectors.joinToString(", ")})")
    }

    if (!request.excludeSectors.isNullOrEmpty()) {
      val sectors = request.excludeSectors.map { it.uppercase() }.toSet()
      val before = stocks.size
      stocks = stocks.filter { it.sectorSymbol?.uppercase() !in sectors }
      logger.info("Sector exclude filter: $before → ${stocks.size} stocks (excluded: ${sectors.joinToString(", ")})")
    }

    if (stocks.isEmpty()) {
      logger.warn("No stocks found in database. Use Data Manager to refresh stocks first.")
      return null
    }

    if (!request.stockSymbols.isNullOrEmpty() && stocks.size < request.stockSymbols.size) {
      val foundSymbols = stocks.map { it.symbol }.toSet()
      val missingSymbols = request.stockSymbols.filter { !foundSymbols.contains(it.uppercase()) }
      logger.warn(
        "${request.stockSymbols.size} symbols requested but only ${stocks.size} found. " +
          "Missing: ${missingSymbols.joinToString(", ")}.",
      )
    }

    logger.info("${stocks.size} stocks fetched")
    return stocks
  }

  private fun executeBacktest(
    entryStrategy: EntryStrategy,
    exitStrategy: ExitStrategy,
    stocks: List<Stock>,
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
          stocks,
          start,
          end,
          request.maxPositions,
          ranker,
          request.useUnderlyingAssets,
          request.customUnderlyingMap,
          request.cooldownDays,
        )

      logger.info(
        "Backtest complete: ${backtestReport.trades.size} trades, " +
          "Win rate: ${String.format("%.2f", backtestReport.winRate * 100)}%, " +
          "Edge: ${String.format("%.2f", backtestReport.edge)}%",
      )

      val backtestId = backtestResultStore.store(backtestReport)
      ResponseEntity.ok(backtestReport.toResponseDto(backtestId))
    } catch (e: IllegalArgumentException) {
      logger.error("Backtest validation failed: ${e.message}", e)
      ResponseEntity.badRequest().build()
    } catch (e: Exception) {
      logger.error("Unexpected error during backtest: ${e.message}", e)
      ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
    }

  private fun <T> logAndBadRequest(message: String): ResponseEntity<T> {
    logger.error(message)
    return ResponseEntity.badRequest().build()
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
