package com.skrymer.udgaard.controller

import com.skrymer.udgaard.controller.dto.*
import com.skrymer.udgaard.model.BacktestReport
import com.skrymer.udgaard.model.strategy.*
import com.skrymer.udgaard.service.BacktestService
import com.skrymer.udgaard.service.DynamicStrategyBuilder
import com.skrymer.udgaard.service.StockService
import com.skrymer.udgaard.service.StrategyRegistry
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
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
    private val backtestService: BacktestService,
    private val strategyRegistry: StrategyRegistry,
    private val dynamicStrategyBuilder: DynamicStrategyBuilder
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(BacktestController::class.java)
        private const val DEFAULT_START_DATE = "2020-01-01"
        private const val DEFAULT_ENTRY_STRATEGY = "PlanAlpha"
        private const val DEFAULT_EXIT_STRATEGY = "PlanMoney"
        private const val DEFAULT_RANKER = "Heatmap"
    }

    /**
     * Run backtest with query parameters (simple interface).
     *
     * Example: GET /api/backtest?stockSymbols=AAPL,GOOGL&entryStrategy=PlanAlpha&exitStrategy=PlanMoney
     */
    @GetMapping
    fun runBacktest(
        @RequestParam(required = false) stockSymbols: List<String>?,
        @RequestParam(required = false, defaultValue = DEFAULT_ENTRY_STRATEGY) entryStrategy: String,
        @RequestParam(required = false, defaultValue = DEFAULT_EXIT_STRATEGY) exitStrategy: String,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?,
        @RequestParam(required = false) maxPositions: Int?,
        @RequestParam(required = false, defaultValue = DEFAULT_RANKER) ranker: String,
        @RequestParam(defaultValue = "false") refresh: Boolean,
        @RequestParam(defaultValue = "0") cooldownDays: Int
    ): ResponseEntity<BacktestReport> {
        logger.info("Running backtest: entry=$entryStrategy, exit=$exitStrategy, symbols=${stockSymbols?.joinToString(",") ?: "all"}, " +
            "startDate=$startDate, endDate=$endDate, maxPositions=$maxPositions, ranker=$ranker, cooldownDays=$cooldownDays")

        // Get entry strategy
        val entryStrategyInstance = strategyRegistry.createEntryStrategy(entryStrategy)
            ?: run {
                logger.error("Failed to create entry strategy: $entryStrategy")
                return ResponseEntity.badRequest().build()
            }

        // Get exit strategy
        val exitStrategyInstance = strategyRegistry.createExitStrategy(exitStrategy)
            ?: run {
                logger.error("Failed to create exit strategy: $exitStrategy")
                return ResponseEntity.badRequest().build()
            }

        // Get stocks (using cached method for better performance)
        val stocks = if (!stockSymbols.isNullOrEmpty()) {
            stockService.getStocksBySymbols(stockSymbols.map { it.uppercase() }, refresh)
        } else {
            stockService.getAllStocks()
        }

        if (stocks.isEmpty()) {
            logger.warn("No stocks found")
            return ResponseEntity.badRequest().build()
        }

        logger.info("${stocks.size} stocks fetched")

        // Parse dates or use defaults
        val start = startDate?.let { LocalDate.parse(it) } ?: LocalDate.parse(DEFAULT_START_DATE)
        val end = endDate?.let { LocalDate.parse(it) } ?: LocalDate.now()
        logger.info("Date range: $start to $end")

        // Run backtest
        val rankerInstance = getRankerInstance(ranker) ?: run {
            logger.error("Failed to get ranker instance: $ranker")
            return ResponseEntity.badRequest().build()
        }

        logger.info("Starting backtest execution...")
        val backtestReport = backtestService.backtest(
            entryStrategyInstance,
            exitStrategyInstance,
            stocks,
            start,
            end,
            maxPositions,
            rankerInstance,
            cooldownDays = cooldownDays
        )

        logger.info("Backtest complete: ${backtestReport.trades.size} trades, " +
            "Win rate: ${String.format("%.2f", backtestReport.winRate * 100)}%, " +
            "Wins: ${backtestReport.numberOfWinningTrades}, Losses: ${backtestReport.numberOfLosingTrades}, " +
            "Edge: ${String.format("%.2f", backtestReport.edge)}%")
        return ResponseEntity.ok(backtestReport)
    }

    /**
     * Run backtest with request body (advanced interface).
     * Supports both predefined and custom strategies.
     *
     * Example: POST /api/backtest with JSON body
     */
    @PostMapping
    fun runBacktestWithConfig(
        @RequestBody request: BacktestRequest
    ): ResponseEntity<BacktestReport> {
        logger.info("Running backtest with config: entry=${request.entryStrategy}, exit=${request.exitStrategy}, " +
            "symbols=${request.stockSymbols?.joinToString(",") ?: "all"}, startDate=${request.startDate}, " +
            "endDate=${request.endDate}, maxPositions=${request.maxPositions}, ranker=${request.ranker}, " +
            "useUnderlyingAssets=${request.useUnderlyingAssets}, cooldownDays=${request.cooldownDays}")

        // Build entry strategy
        val entryStrategy = dynamicStrategyBuilder.buildEntryStrategy(request.entryStrategy)
            ?: run {
                logger.error("Failed to build entry strategy from config: ${request.entryStrategy}")
                return ResponseEntity.badRequest().build()
            }

        // Build exit strategy
        val exitStrategy = dynamicStrategyBuilder.buildExitStrategy(request.exitStrategy)
            ?: run {
                logger.error("Failed to build exit strategy from config: ${request.exitStrategy}")
                return ResponseEntity.badRequest().build()
            }

        // Get stocks (using cached method for better performance)
        val stocks = if (!request.stockSymbols.isNullOrEmpty()) {
            stockService.getStocksBySymbols(request.stockSymbols.map { it.uppercase() }, request.refresh)
        } else {
            stockService.getAllStocks()
        }

        if (stocks.isEmpty()) {
            logger.warn("No stocks found")
            return ResponseEntity.badRequest().build()
        }

        logger.info("${stocks.size} stocks fetched")

        // Parse dates or use defaults
        val start = request.startDate?.let { LocalDate.parse(it) } ?: LocalDate.parse(DEFAULT_START_DATE)
        val end = request.endDate?.let { LocalDate.parse(it) } ?: LocalDate.now()
        logger.info("Date range: $start to $end")

        // Run backtest
        val rankerInstance = getRankerInstance(request.ranker) ?: run {
            logger.error("Failed to get ranker instance: ${request.ranker}")
            return ResponseEntity.badRequest().build()
        }

        try {
            logger.info("Starting backtest execution...")
            val backtestReport = backtestService.backtest(
                entryStrategy,
                exitStrategy,
                stocks,
                start,
                end,
                request.maxPositions,
                rankerInstance,
                request.useUnderlyingAssets,
                request.customUnderlyingMap,
                request.cooldownDays
            )

            logger.info("Backtest complete: ${backtestReport.trades.size} trades, " +
                "Win rate: ${String.format("%.2f", backtestReport.winRate * 100)}%, " +
                "Wins: ${backtestReport.numberOfWinningTrades}, Losses: ${backtestReport.numberOfLosingTrades}, " +
                "Edge: ${String.format("%.2f", backtestReport.edge)}%")
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
        val strategies = mapOf(
            "entryStrategies" to strategyRegistry.getAvailableEntryStrategies(),
            "exitStrategies" to strategyRegistry.getAvailableExitStrategies()
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
        val rankers = listOf(
            "Heatmap",
            "RelativeStrength",
            "Volatility",
            "DistanceFrom10Ema",
            "Composite",
            "SectorStrength",
            "Random",
            "Adaptive"
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
        val conditions = dynamicStrategyBuilder.getAvailableConditions()
        logger.info("Returning available conditions")
        return ResponseEntity.ok(conditions)
    }

    /**
     * Helper method to create ranker instance from name.
     */
    private fun getRankerInstance(rankerName: String): StockRanker? {
        return when (rankerName.lowercase()) {
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
}
