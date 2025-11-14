package com.skrymer.udgaard.controller

import com.skrymer.udgaard.controller.dto.*
import com.skrymer.udgaard.integration.ovtlyr.DataLoader
import com.skrymer.udgaard.model.BacktestReport
import com.skrymer.udgaard.model.MarketBreadth
import com.skrymer.udgaard.model.MarketSymbol
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockSymbol
import com.skrymer.udgaard.model.strategy.*
import com.skrymer.udgaard.model.valueOf
import com.skrymer.udgaard.service.DynamicStrategyBuilder
import com.skrymer.udgaard.service.MarketBreadthService
import com.skrymer.udgaard.service.StockService
import com.skrymer.udgaard.service.StrategyRegistry
import kotlinx.coroutines.runBlocking
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

@RestController()
@RequestMapping("/api")
@CrossOrigin(origins = ["http://localhost:3000", "http://localhost:8080"])
class UdgaardController(
  val stockService: StockService,
  val marketBreadthService: MarketBreadthService,
  val dataLoader: DataLoader,
  val strategyRegistry: StrategyRegistry,
  val dynamicStrategyBuilder: DynamicStrategyBuilder
) {

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(UdgaardController::class.java)
    private const val DEFAULT_START_DATE = "2020-01-01"
    private const val DEFAULT_ENTRY_STRATEGY = "PlanAlpha"
    private const val DEFAULT_EXIT_STRATEGY = "PlanMoney"
    private const val DEFAULT_RANKER = "Heatmap"
  }

  @GetMapping("/backtest")
  fun runBacktest(
    @RequestParam(required = false) stockSymbols: List<String>?,
    @RequestParam(required = false, defaultValue = DEFAULT_ENTRY_STRATEGY) entryStrategy: String,
    @RequestParam(required = false, defaultValue = DEFAULT_EXIT_STRATEGY) exitStrategy: String,
    @RequestParam(required = false) startDate: String?,
    @RequestParam(required = false) endDate: String?,
    @RequestParam(required = false) maxPositions: Int?,
    @RequestParam(required = false, defaultValue = DEFAULT_RANKER) ranker: String,
    @RequestParam(defaultValue = "false") refresh: Boolean
  ): ResponseEntity<BacktestReport> {
    logger.info("Running backtest: entry=$entryStrategy, exit=$exitStrategy, symbols=${stockSymbols?.joinToString(",") ?: "all"}")

    // Get entry strategy
    val entryStrategyInstance = strategyRegistry.createEntryStrategy(entryStrategy)
      ?: return ResponseEntity.badRequest().build()

    // Get exit strategy
    val exitStrategyInstance = strategyRegistry.createExitStrategy(exitStrategy)
      ?: return ResponseEntity.badRequest().build()

    // Get stocks
    val stocks = if (!stockSymbols.isNullOrEmpty()) {
      runBlocking {
        stockService.getStocks(stockSymbols.map { it.uppercase() }, refresh)
      }
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

    // Run backtest
    val rankerInstance = getRankerInstance(ranker) ?: return ResponseEntity.badRequest().build()
    val backtestReport = stockService.backtest(
      entryStrategyInstance,
      exitStrategyInstance,
      stocks,
      start,
      end,
      maxPositions,
      rankerInstance
    )

    logger.info("Backtest complete: ${backtestReport.trades.size} trades")
    return ResponseEntity.ok(backtestReport)
  }

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

  @GetMapping("/market-breadth")
  fun getMarketBreadth(
    @RequestParam marketSymbol: String,
    @RequestParam(defaultValue = "false") refresh: Boolean
  ): ResponseEntity<MarketBreadth> {
    val marketBreadth = marketBreadthService.getMarketBreadth(
      marketSymbol = MarketSymbol.valueOf(marketSymbol),
      fromDate = LocalDate.now().minusMonths(3),
      toDate = LocalDate.now(),
      refresh = refresh
    )
    return ResponseEntity.ok(marketBreadth)
  }

  @GetMapping("/stocks/{symbol}")
  fun getStock(
    @PathVariable symbol: String,
    @RequestParam(defaultValue = "false") refresh: Boolean
  ): ResponseEntity<Stock> {
    val stock = stockService.getStock(symbol, refresh)
    return ResponseEntity.ok(stock)
  }

  @GetMapping("/stocks")
  fun getStockSymbols(): ResponseEntity<List<String>> {
    val symbols = StockSymbol.entries.map { it.symbol }
    return ResponseEntity.ok(symbols)
  }

  @PostMapping("/stocks/refresh")
  fun refreshStocks(
    @RequestBody symbols: List<String>
  ): ResponseEntity<Map<String, String>> {
    logger.info("Refreshing ${symbols.size} stocks: ${symbols.joinToString(", ")}")
    runBlocking {
      stockService.getStocks(symbols.map { it.uppercase() }, forceFetch = true)
    }
    logger.info("Stocks refreshed successfully")
    return ResponseEntity.ok(
      mapOf("status" to "success", "message" to "${symbols.size} stocks refreshed successfully")
    )
  }

  @GetMapping("/strategies")
  fun getAvailableStrategies(): ResponseEntity<Map<String, List<String>>> {
    return ResponseEntity.ok(
      mapOf(
        "entryStrategies" to strategyRegistry.getAvailableEntryStrategies(),
        "exitStrategies" to strategyRegistry.getAvailableExitStrategies()
      )
    )
  }

  @GetMapping("/rankers")
  fun getAvailableRankers(): ResponseEntity<List<String>> {
    return ResponseEntity.ok(
      listOf(
        "Heatmap",
        "RelativeStrength",
        "Volatility",
        "DistanceFrom10Ema",
        "Composite",
        "SectorStrength",
        "Random",
        "Adaptive"
      )
    )
  }

  @GetMapping("/data/load")
  fun loadData(): ResponseEntity<Map<String, String>> {
    logger.info("Loading data started")
    dataLoader.loadData()
    logger.info("Data loading completed")
    return ResponseEntity.ok(
      mapOf("status" to "success", "message" to "Data loaded successfully")
    )
  }

  /**
   * NEW: POST endpoint for backtesting with request body
   * Supports both predefined and custom strategies
   */
  @PostMapping("/backtest")
  fun runBacktestWithConfig(
    @RequestBody request: BacktestRequest
  ): ResponseEntity<BacktestReport> {
    logger.info("Running backtest with config: entry=${request.entryStrategy}, exit=${request.exitStrategy}")

    // Build entry strategy
    val entryStrategy = dynamicStrategyBuilder.buildEntryStrategy(request.entryStrategy)
      ?: return ResponseEntity.badRequest().build()

    // Build exit strategy
    val exitStrategy = dynamicStrategyBuilder.buildExitStrategy(request.exitStrategy)
      ?: return ResponseEntity.badRequest().build()

    // Get stocks
    val stocks = if (!request.stockSymbols.isNullOrEmpty()) {
      runBlocking {
        stockService.getStocks(request.stockSymbols.map { it.uppercase() }, request.refresh)
      }
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

    // Run backtest
    val rankerInstance = getRankerInstance(request.ranker) ?: return ResponseEntity.badRequest().build()

    try {
      val backtestReport = stockService.backtest(
        entryStrategy,
        exitStrategy,
        stocks,
        start,
        end,
        request.maxPositions,
        rankerInstance,
        request.useUnderlyingAssets,
        request.customUnderlyingMap
      )

      logger.info("Backtest complete: ${backtestReport.trades.size} trades")
      return ResponseEntity.ok(backtestReport)
    } catch (e: IllegalArgumentException) {
      logger.error("Backtest validation failed: ${e.message}")
      return ResponseEntity.badRequest().build()
    }
  }

  /**
   * NEW: Get available conditions for UI strategy builder
   */
  @GetMapping("/conditions")
  fun getAvailableConditions(): ResponseEntity<AvailableConditionsResponse> {
    return ResponseEntity.ok(dynamicStrategyBuilder.getAvailableConditions())
  }
}