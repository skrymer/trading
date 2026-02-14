package com.skrymer.udgaard.backtesting.controller

import com.skrymer.udgaard.backtesting.dto.*
import com.skrymer.udgaard.backtesting.model.BacktestReport
import com.skrymer.udgaard.backtesting.service.BacktestService
import com.skrymer.udgaard.backtesting.service.ConditionRegistry
import com.skrymer.udgaard.backtesting.service.DynamicStrategyBuilder
import com.skrymer.udgaard.backtesting.service.StrategyRegistry
import com.skrymer.udgaard.backtesting.strategy.*
import com.skrymer.udgaard.data.model.AssetType
import com.skrymer.udgaard.data.service.StockService
import com.skrymer.udgaard.data.service.SymbolService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
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
) {
  companion object {
    private val logger: Logger = LoggerFactory.getLogger(BacktestController::class.java)
  }

  /**
   * Run backtest with request body.
   * Supports both predefined and custom strategies.
   *
   * Example: POST /api/backtest with JSON body
   */
  @PostMapping
  fun runBacktestWithConfig(
    @RequestBody request: BacktestRequest,
  ): ResponseEntity<BacktestReport> {
    logger.info(
      "Running backtest with config: entry=${request.entryStrategy}, exit=${request.exitStrategy}, " +
        "symbols=${request.stockSymbols?.joinToString(",") ?: "all"}, " +
        "assetTypes=${request.assetTypes?.joinToString(",") ?: "all"}, startDate=${request.startDate}, " +
        "endDate=${request.endDate}, maxPositions=${request.maxPositions}, ranker=${request.ranker}, " +
        "useUnderlyingAssets=${request.useUnderlyingAssets}, cooldownDays=${request.cooldownDays}",
    )

    // Build entry strategy
    val entryStrategy =
      dynamicStrategyBuilder.buildEntryStrategy(request.entryStrategy)
        ?: run {
          logger.error("Failed to build entry strategy from config: ${request.entryStrategy}")
          return ResponseEntity.badRequest().build()
        }

    // Build exit strategy
    val exitStrategy =
      dynamicStrategyBuilder.buildExitStrategy(request.exitStrategy)
        ?: run {
          logger.error("Failed to build exit strategy from config: ${request.exitStrategy}")
          return ResponseEntity.badRequest().build()
        }

    // Get stocks from database (use Data Manager to refresh stocks before running backtest)
    val stocks =
      if (!request.stockSymbols.isNullOrEmpty()) {
        // Specific symbols provided - filter from DB
        stockService.getStocksBySymbols(request.stockSymbols.map { it.uppercase() }, forceFetch = false)
      } else if (!request.assetTypes.isNullOrEmpty()) {
        // Filter by asset type
        val assetTypeEnums = request.assetTypes.map { AssetType.valueOf(it) }
        val symbols =
          symbolService
            .getAll()
            .filter { it.assetType in assetTypeEnums }
            .map { it.symbol }
        logger.info("Filtered to ${symbols.size} symbols matching asset types: ${request.assetTypes.joinToString(", ")}")
        stockService.getStocksBySymbols(symbols, forceFetch = false)
      } else {
        // "All stocks" selected - use all stocks in DB
        stockService.getAllStocks()
      }

    if (stocks.isEmpty()) {
      logger.warn("No stocks found in database. Use Data Manager to refresh stocks first.")
      return ResponseEntity.badRequest().build()
    }

    // Warn if some requested symbols were not found in database
    if (!request.stockSymbols.isNullOrEmpty() && stocks.size < request.stockSymbols.size) {
      val missingCount = request.stockSymbols.size - stocks.size
      val foundSymbols = stocks.map { it.symbol }.toSet()
      val missingSymbols = request.stockSymbols.filter { !foundSymbols.contains(it.uppercase()) }
      logger.warn(
        "WARNING: ${request.stockSymbols.size} symbols requested but only ${stocks.size} found in database. " +
          "$missingCount missing: ${missingSymbols.joinToString(", ")}. Use Data Manager to refresh missing stocks.",
      )
    }

    logger.info("${stocks.size} stocks fetched")

    // Parse dates or use defaults
    val start = request.startDate?.let { LocalDate.parse(it) } ?: LocalDate.parse("2020-01-01")
    val end = request.endDate?.let { LocalDate.parse(it) } ?: LocalDate.now()
    logger.info("Date range: $start to $end")

    // Run backtest
    val rankerInstance =
      getRankerInstance(request.ranker) ?: run {
        logger.error("Failed to get ranker instance: ${request.ranker}")
        return ResponseEntity.badRequest().build()
      }

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
          rankerInstance,
          request.useUnderlyingAssets,
          request.customUnderlyingMap,
          request.cooldownDays,
        )

      logger.info(
        "Backtest complete: ${backtestReport.trades.size} trades, " +
          "Win rate: ${String.format("%.2f", backtestReport.winRate * 100)}%, " +
          "Wins: ${backtestReport.numberOfWinningTrades}, Losses: ${backtestReport.numberOfLosingTrades}, " +
          "Edge: ${String.format("%.2f", backtestReport.edge)}%",
      )
      return ResponseEntity.ok(backtestReport)
    } catch (e: IllegalArgumentException) {
      logger.error("Backtest validation failed: ${e.message}", e)
      return ResponseEntity.badRequest().build()
    } catch (e: Exception) {
      logger.error("Unexpected error during backtest: ${e.message}", e)
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
    }
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
        "Heatmap",
        "RelativeStrength",
        "Volatility",
        "DistanceFrom10Ema",
        "Composite",
        "SectorStrength",
        "Random",
        "Adaptive",
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

  /**
   * Helper method to create ranker instance from name.
   */
  private fun getRankerInstance(rankerName: String): StockRanker? =
    when (rankerName.lowercase()) {
      "heatmap" -> HeatmapRanker()
      "relativestrength" -> RelativeStrengthRanker()
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
}
