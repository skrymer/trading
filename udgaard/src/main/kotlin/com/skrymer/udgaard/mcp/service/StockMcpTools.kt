package com.skrymer.udgaard.mcp.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.skrymer.udgaard.backtesting.dto.AvailableConditionsResponse
import com.skrymer.udgaard.backtesting.service.ConditionRegistry
import com.skrymer.udgaard.backtesting.service.StrategyRegistry
import com.skrymer.udgaard.data.repository.StockJooqRepository
import com.skrymer.udgaard.data.service.StockService
import com.skrymer.udgaard.data.service.SymbolService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.ai.tool.annotation.Tool
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * MCP Tools for strategy and stock symbol discovery.
 * These tools provide information about available strategies, conditions, and stock symbols
 * for use in backtesting via the REST API.
 */
@Service
class StockMcpTools(
  private val strategyRegistry: StrategyRegistry,
  private val conditionRegistry: ConditionRegistry,
  private val stockRepository: StockJooqRepository,
  private val stockService: StockService,
  private val cacheManager: CacheManager,
  private val objectMapper: ObjectMapper,
  private val symbolService: SymbolService,
) {
  companion object {
    private val logger: Logger = LoggerFactory.getLogger("MCP tools")
  }

  @Tool(
    description = """Get list of all available stock symbols that have historical data in the system.
      Returns an array of stock info objects with symbol, sector, assetType, quoteCount,
      lastQuoteDate, and hasData fields. Use this to discover which stocks are available
      for backtesting and what data coverage they have.

      Returns:
      - count: Total number of stocks with data
      - symbols: Array of stock info objects sorted by symbol""",
  )
  fun getAvailableSymbols(): String {
    val stocks = stockService.getAllStocksSimple()
    val symbolRecords = symbolService.getAll().associateBy { it.symbol }
    return objectMapper
      .writerWithDefaultPrettyPrinter()
      .writeValueAsString(
        mapOf(
          "count" to stocks.size,
          "symbols" to
            stocks.map { stock ->
              mapOf(
                "symbol" to stock.symbol,
                "sector" to stock.sector,
                "assetType" to (symbolRecords[stock.symbol]?.assetType?.name ?: "UNKNOWN"),
                "quoteCount" to stock.quoteCount,
                "lastQuoteDate" to stock.lastQuoteDate?.toString(),
                "hasData" to stock.hasData,
              )
            },
        ),
      )
  }

  @Tool(
    description = """Get list of all available entry and exit strategies in the system.
      Returns:
      - entryStrategies: List of available entry strategy names
      - exitStrategies: List of available exit strategy names

      These strategy names can be used when configuring backtests via the REST API.
      Each strategy has specific entry/exit conditions optimized for different market scenarios.""",
  )
  fun getAvailableStrategies(): String =
    objectMapper
      .writerWithDefaultPrettyPrinter()
      .writeValueAsString(
        mapOf(
          "entryStrategies" to strategyRegistry.getAvailableEntryStrategies(),
          "exitStrategies" to strategyRegistry.getAvailableExitStrategies(),
        ),
      )

  @Tool(
    description = """Get list of all available rankers for stock selection in position-limited backtests.
      Returns a list of ranker names that can be used to prioritize stocks when multiple stocks
      trigger entry signals on the same day and position limits are enabled.

      Available rankers typically include:
      - Heatmap: Ranks by sentiment/heatmap values
      - RelativeStrength: Ranks by relative strength indicators
      - Volatility: Ranks by ATR-based volatility
      - DistanceFrom10Ema: Ranks by distance from 10-period EMA
      - Composite: Combined ranking using multiple factors
      - SectorStrength: Ranks by sector performance
      - Random: Random selection (for baseline comparison)
      - Adaptive: Dynamically adapts ranking based on market conditions

      Use these ranker names in the backtest API request.""",
  )
  fun getAvailableRankers(): String {
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
    return objectMapper
      .writerWithDefaultPrettyPrinter()
      .writeValueAsString(
        mapOf(
          "rankers" to rankers,
          "count" to rankers.size,
        ),
      )
  }

  @Tool(
    description = """Get available conditions for building custom entry and exit strategies.
      Returns detailed metadata about all available trading conditions including:
      - type: Unique identifier for the condition
      - displayName: Human-readable name
      - description: What the condition checks for
      - parameters: Required/optional parameters with types and defaults
      - category: Condition category (Stock, Market, SPY, Sector, etc.)

      Use this information to:
      1. Discover what conditions are available for custom strategy building
      2. Understand parameter requirements for each condition
      3. Build custom strategies by combining conditions

      Returns separate lists for entry conditions and exit conditions.""",
  )
  fun getAvailableConditions(): String {
    val conditions =
      AvailableConditionsResponse(
        entryConditions = conditionRegistry.getEntryConditionMetadata(),
        exitConditions = conditionRegistry.getExitConditionMetadata(),
      )
    return objectMapper
      .writerWithDefaultPrettyPrinter()
      .writeValueAsString(conditions)
  }

  @Tool(
    description = """Get detailed information about a specific trading strategy.
      Provides the strategy description from its implementation.

      Parameters:
      - strategyName: Name of the strategy (e.g., 'PlanAlpha', 'PlanMoney')
      - strategyType: Type of strategy - 'entry' or 'exit'

      Returns:
      - name: Strategy name
      - type: Entry or exit strategy
      - description: What the strategy does and its conditions
      - available: Whether the strategy exists in the system

      Use getAvailableStrategies first to discover valid strategy names.""",
  )
  fun getStrategyDetails(
    strategyName: String,
    strategyType: String,
  ): String {
    logger.info("Getting strategy details for: $strategyName ($strategyType)")

    val type = strategyType.lowercase()
    val isEntry = type == "entry"
    val isExit = type == "exit"

    if (!isEntry && !isExit) {
      return objectMapper
        .writerWithDefaultPrettyPrinter()
        .writeValueAsString(
          mapOf(
            "error" to "Invalid strategy type. Use 'entry' or 'exit'",
            "validTypes" to mutableListOf("entry", "exit"),
          ),
        )
    }

    // Check if strategy exists
    val availableStrategies =
      if (isEntry) {
        strategyRegistry.getAvailableEntryStrategies()
      } else {
        strategyRegistry.getAvailableExitStrategies()
      }

    if (!availableStrategies.contains(strategyName)) {
      return objectMapper
        .writerWithDefaultPrettyPrinter()
        .writeValueAsString(
          mapOf(
            "error" to "Strategy '$strategyName' not found",
            "availableStrategies" to availableStrategies,
            "strategyType" to strategyType,
          ),
        )
    }

    // Get strategy description
    val description =
      try {
        if (isEntry) {
          strategyRegistry.createEntryStrategy(strategyName)?.description()
        } else {
          strategyRegistry.createExitStrategy(strategyName)?.description()
        }
      } catch (e: Exception) {
        null
      } ?: "No description available"

    val result =
      mapOf(
        "name" to strategyName,
        "type" to strategyType,
        "available" to true,
        "description" to description,
      )

    return objectMapper
      .writerWithDefaultPrettyPrinter()
      .writeValueAsString(result)
  }

  @Tool(
    description = """Explain what backtest performance metrics mean and how to interpret them.
      Helps understand backtest results by providing definitions, interpretations, and benchmarks.

      Parameters:
      - metrics: Optional comma-separated list of specific metrics to explain.
                 If not provided, explains all common metrics.
                 Examples: 'winRate', 'edge', 'averageWin'

      Returns detailed explanations including:
      - Definition: What the metric measures
      - Interpretation: How to read the value
      - Benchmark: What's considered good/bad
      - Context: When the metric matters most

      Use this to understand backtest results and make informed decisions.""",
  )
  fun explainBacktestMetrics(metrics: String?): String {
    logger.info("Explaining backtest metrics: ${metrics ?: "all"}")

    val allMetrics =
      mapOf(
        "winRate" to
          mapOf(
            "definition" to "Percentage of trades that were profitable",
            "formula" to "(Winning Trades / Total Trades) × 100",
            "interpretation" to "Higher is generally better, but must be considered with edge and average win/loss",
            "benchmark" to
              mapOf(
                "poor" to "< 40%",
                "average" to "40-55%",
                "good" to "55-65%",
                "excellent" to "> 65%",
              ),
            "context" to
              "Random trading gives ~50% win rate. A strategy with 40% win rate can be profitable if wins are much larger than losses.",
            "warnings" to
              mutableListOf(
                "High win rate doesn't guarantee profitability",
                "Consider win rate together with edge and average win/loss",
              ),
          ),
        "lossRate" to
          mapOf(
            "definition" to "Percentage of trades that lost money",
            "formula" to "(Losing Trades / Total Trades) × 100",
            "interpretation" to "Complement of win rate (lossRate = 100 - winRate)",
            "note" to "Focuses on the frequency of losses",
          ),
        "edge" to
          mapOf(
            "definition" to "Expected profit percentage per trade over the long run",
            "formula" to "(Win Rate × Avg Win %) - (Loss Rate × Avg Loss %)",
            "interpretation" to "The most important metric. Positive edge = profitable strategy over time",
            "benchmark" to
              mapOf(
                "unprofitable" to "< 0%",
                "marginal" to "0-1%",
                "good" to "1-3%",
                "excellent" to "3-5%",
                "exceptional" to "> 5%",
              ),
            "context" to
              "Edge tells you your average expected return per trade. A 2% edge means you expect to make 2% per trade on average.",
            "warnings" to
              mutableListOf(
                "Edge can be inflated by a few outlier wins",
                "Consider consistency and drawdowns too",
              ),
          ),
        "averageWin" to
          mapOf(
            "definition" to "Average profit amount across all winning trades",
            "interpretation" to "Shows typical profit when trades succeed",
            "context" to "Compare with average loss to understand risk/reward ratio",
          ),
        "averageWinPercent" to
          mapOf(
            "definition" to "Average profit percentage across winning trades",
            "interpretation" to "Percentage gain on winning positions",
            "benchmark" to "5-15% is typical for swing trading, depends on holding period",
            "context" to "Higher is better, but balance with win rate",
          ),
        "averageLoss" to
          mapOf(
            "definition" to "Average loss amount across all losing trades",
            "interpretation" to "Shows typical loss when trades fail",
            "context" to "Should be smaller than average win for profitable strategy",
            "goalRatio" to "Average Win / Average Loss should be > 1.5 ideally",
          ),
        "averageLossPercent" to
          mapOf(
            "definition" to "Average loss percentage across losing trades",
            "interpretation" to "Percentage loss on losing positions",
            "benchmark" to "-2% to -5% is typical with good risk management",
            "context" to "Controlled losses are key to long-term profitability",
          ),
        "totalTrades" to
          mapOf(
            "definition" to "Total number of trades executed in the backtest",
            "interpretation" to "More trades generally means more statistical significance",
            "benchmark" to
              mapOf(
                "insufficient" to "< 30 trades",
                "minimum" to "30-100 trades",
                "good" to "100-300 trades",
                "excellent" to "> 300 trades",
              ),
            "context" to "Need enough trades for statistical significance. Too few trades make results unreliable.",
            "warnings" to
              mutableListOf(
                "Strategies with < 30 trades are statistically questionable",
                "Could be random luck rather than edge",
              ),
          ),
        "profitFactor" to
          mapOf(
            "definition" to "Ratio of gross profit to gross loss",
            "formula" to "Total Profit from Wins / Total Loss from Losses",
            "interpretation" to "How many dollars you make for each dollar you lose",
            "benchmark" to
              mapOf(
                "unprofitable" to "< 1.0",
                "marginal" to "1.0-1.5",
                "good" to "1.5-2.5",
                "excellent" to "> 2.5",
              ),
            "context" to "A profit factor of 2.0 means you make $2 for every $1 you lose",
          ),
        "maxDrawdown" to
          mapOf(
            "definition" to "Largest peak-to-trough decline in equity during the backtest",
            "interpretation" to "Worst losing streak experienced",
            "benchmark" to "< 20% is good, > 50% is concerning",
            "context" to "Critical for risk management. Can you psychologically handle this drawdown?",
            "warnings" to
              mutableListOf(
                "Past drawdown doesn't predict future max drawdown",
                "Real drawdowns often exceed backtest drawdowns",
              ),
          ),
      )

    // Filter metrics if specific ones requested
    val requestedMetrics = metrics?.split(",")?.map { it.trim().lowercase() }
    val metricsToReturn =
      if (requestedMetrics != null) {
        allMetrics.filterKeys { key -> requestedMetrics.contains(key.lowercase()) }
      } else {
        allMetrics
      }

    val result =
      mapOf(
        "metrics" to metricsToReturn,
        "overallGuidance" to
          mapOf(
            "essentialMetrics" to mutableListOf("edge", "winRate", "totalTrades"),
            "analysisOrder" to
              mutableListOf(
                "1. Check total trades (need statistical significance)",
                "2. Check edge (must be positive)",
                "3. Check win rate and avg win/loss ratio",
                "4. Verify results make logical sense",
              ),
            "redFlags" to
              listOf(
                "Very high win rate (>80%) - might be overfitted",
                "Very few trades (<30) - not statistically significant",
                "Negative edge - unprofitable strategy",
                "Huge average win with many small losses - might be due to outliers",
              ),
          ),
      )

    return objectMapper
      .writerWithDefaultPrettyPrinter()
      .writeValueAsString(result)
  }

  @Tool(
    description = """Get system status and readiness for backtesting.
      Provides a health check of the backtesting system including database connectivity,
      data availability, cache status, and overall readiness.

      Returns:
      - status: Overall system status ('ready', 'degraded', 'error')
      - stockCount: Number of stocks with data available
      - strategyCount: Number of available strategies
      - cacheStatus: Whether cache is warm or cold
      - readyForBacktest: Boolean indicating if system is ready
      - warnings: Any warnings or issues

      Use this to verify the system is ready before running backtests.""",
  )
  fun getSystemStatus(): String {
    logger.info("Checking system status")

    var status = "ready"
    val warnings = mutableListOf<String>()

    // Check stock count
    val stockCount =
      try {
        stockRepository.count()
      } catch (e: Exception) {
        warnings.add("Unable to connect to database: ${e.message}")
        status = "error"
        0
      }

    if (stockCount == 0L) {
      warnings.add("No stocks found in database")
      status = "error"
    }

    // Check strategies
    val entryStrategies = strategyRegistry.getAvailableEntryStrategies()
    val exitStrategies = strategyRegistry.getAvailableExitStrategies()
    val totalStrategies = entryStrategies.size + exitStrategies.size

    if (totalStrategies == 0) {
      warnings.add("No strategies registered")
      status = "degraded"
    }

    // Check cache status
    val cacheStatus =
      try {
        val stocksCache = cacheManager.getCache("stocks")
        if (stocksCache != null) {
          val allStocksInCache = stocksCache.get("allStocks") != null
          if (allStocksInCache) "warm" else "cold"
        } else {
          warnings.add("Cache not configured")
          "unavailable"
        }
      } catch (e: Exception) {
        warnings.add("Unable to check cache status: ${e.message}")
        "unknown"
      }

    // Determine overall readiness
    val readyForBacktest = status == "ready" && stockCount > 0 && totalStrategies > 0

    // Build result
    val result =
      mapOf(
        "status" to status,
        "readyForBacktest" to readyForBacktest,
        "timestamp" to LocalDate.now().toString(),
        "database" to
          mapOf(
            "connected" to (status != "error"),
            "stockCount" to stockCount,
          ),
        "strategies" to
          mapOf(
            "entryStrategies" to entryStrategies.size,
            "exitStrategies" to exitStrategies.size,
            "total" to totalStrategies,
            "available" to
              listOf(
                "Entry: ${entryStrategies.joinToString(", ")}",
                "Exit: ${exitStrategies.joinToString(", ")}",
              ),
          ),
        "rankers" to
          mapOf(
            "count" to 8,
            "available" to true,
          ),
        "cache" to
          mapOf(
            "status" to cacheStatus,
            "description" to
              when (cacheStatus) {
                "warm" -> "Stock data cached, backtests will be faster"
                "cold" -> "Cache empty, first backtest will populate cache"
                "unavailable" -> "Cache not configured, backtests may be slower"
                else -> "Cache status unknown"
              },
          ),
        "warnings" to warnings,
        "recommendation" to
          if (readyForBacktest) {
            "System is ready for backtesting"
          } else {
            "System has issues that need to be resolved: ${warnings.joinToString("; ")}"
          },
      )

    return objectMapper
      .writerWithDefaultPrettyPrinter()
      .writeValueAsString(result)
  }
}
