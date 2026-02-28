package com.skrymer.udgaard.data.controller

import com.skrymer.udgaard.backtesting.dto.ConditionEvaluationRequest
import com.skrymer.udgaard.backtesting.dto.EntrySignalDetails
import com.skrymer.udgaard.backtesting.dto.ExitSignalDetails
import com.skrymer.udgaard.backtesting.dto.StockConditionSignals
import com.skrymer.udgaard.backtesting.dto.StockWithSignals
import com.skrymer.udgaard.backtesting.service.StrategySignalService
import com.skrymer.udgaard.data.dto.SimpleStockInfo
import com.skrymer.udgaard.data.model.Stock
import com.skrymer.udgaard.data.service.StockIngestionService
import com.skrymer.udgaard.data.service.StockService
import com.skrymer.udgaard.data.service.SymbolService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for stock data operations.
 *
 * Handles:
 * - Retrieving stock data (single or all symbols)
 * - Retrieving stock data with strategy signals
 * - Refreshing stock data from external sources
 */
@RestController
@RequestMapping("/api/stocks")
class StockController(
  private val stockService: StockService,
  private val stockIngestionService: StockIngestionService,
  private val strategySignalService: StrategySignalService,
  private val symbolService: SymbolService,
) {
  /**
   * Get all stocks that have been loaded into the database.
   * Returns simple stock information without loading full quote/order block data.
   *
   * Example: GET /api/stocks
   *
   * @return List of stocks with basic info (symbol, sector, quote count, last updated)
   */
  @GetMapping
  fun getStocksInDatabase(): ResponseEntity<List<SimpleStockInfo>> {
    logger.info("Retrieving all stocks from database")
    val stocks = stockService.getAllStocksSimple()
    logger.info("Returning ${stocks.size} stocks from database (${stocks.count { it.hasData }} with quote data)")
    return ResponseEntity.ok(stocks)
  }

  /**
   * Get all available stock symbols from the symbols table.
   * This returns ALL possible symbols, not just stocks with data loaded.
   *
   * Example: GET /api/stocks/symbols
   *
   * @return List of all stock symbols
   */
  @GetMapping("/symbols")
  fun getAllStockSymbols(): ResponseEntity<List<String>> {
    logger.info("Retrieving all stock symbols")
    val symbols = symbolService.getAll().map { it.symbol }
    logger.info("Returning ${symbols.size} stock symbols")
    return ResponseEntity.ok(symbols)
  }

  /**
   * Search stock symbols by prefix.
   *
   * Example: GET /api/stocks/symbols/search?query=AA&limit=5
   *
   * @param query Search prefix (minimum 2 characters)
   * @param limit Maximum number of results (default 20)
   * @return List of matching stock symbols
   */
  @GetMapping("/symbols/search")
  fun searchSymbols(
    @RequestParam query: String,
    @RequestParam(defaultValue = "20") limit: Int,
  ): List<String> = symbolService.search(query, limit)

  /**
   * Get stock data for a specific symbol.
   * If refresh=true, refreshes from API first. If the stock is not in the DB,
   * auto-populates it via the ingestion service.
   *
   * Example: GET /api/stocks/AAPL?refresh=true
   *
   * @param symbol Stock symbol (e.g., AAPL, GOOGL)
   * @param refresh Force refresh from external source
   * @return Stock data including quotes and order blocks
   */
  @GetMapping("/{symbol}")
  fun getStock(
    @PathVariable symbol: String,
    @RequestParam(defaultValue = "false") refresh: Boolean,
  ): ResponseEntity<Stock> {
    logger.info("Getting stock data for: $symbol (refresh=$refresh)")

    if (refresh) {
      stockIngestionService.refreshStock(symbol)
    }

    var stock = stockService.getStock(symbol)

    // Auto-populate if not found and no refresh was requested
    if (stock == null && !refresh) {
      stockIngestionService.refreshStock(symbol)
      stock = stockService.getStock(symbol)
    }

    if (stock == null) {
      return ResponseEntity.notFound().build()
    }

    logger.info("Stock data retrieved successfully for: $symbol")
    return ResponseEntity.ok(stock)
  }

  /**
   * Get stock data with entry/exit signals for specific strategies.
   *
   * Example: GET /api/stocks/TQQQ/signals?entryStrategy=VegardPlanEtf&exitStrategy=VegardPlanEtf&cooldownDays=10
   *
   * @param symbol Stock symbol (e.g., TQQQ, SPY)
   * @param entryStrategy Entry strategy name (e.g., VegardPlanEtf, OvtlyrPlanEtf)
   * @param exitStrategy Exit strategy name (e.g., VegardPlanEtf, OvtlyrPlanEtf)
   * @param cooldownDays Number of trading days to wait after exit before allowing new entry (default: 0)
   * @param refresh Force refresh stock data from external source
   * @return Stock data with entry/exit signals annotated on each quote
   */
  @GetMapping("/{symbol}/signals")
  fun getStockWithSignals(
    @PathVariable symbol: String,
    @RequestParam entryStrategy: String,
    @RequestParam exitStrategy: String,
    @RequestParam(defaultValue = "0") cooldownDays: Int,
    @RequestParam(defaultValue = "false") refresh: Boolean,
  ): ResponseEntity<StockWithSignals> {
    logger.info(
      "Getting stock $symbol with signals for entry=$entryStrategy, exit=$exitStrategy, cooldown=$cooldownDays (refresh=$refresh)",
    )

    if (refresh) {
      stockIngestionService.refreshStock(symbol)
    }

    // Get stock data
    val stock =
      stockService.getStock(symbol) ?: run {
        logger.error("Stock not found: $symbol")
        return ResponseEntity.notFound().build()
      }

    // Evaluate strategies and generate signals
    val stockWithSignals =
      strategySignalService.evaluateStrategies(stock, entryStrategy, exitStrategy, cooldownDays)
        ?: run {
          logger.error("Failed to evaluate strategies entry=$entryStrategy, exit=$exitStrategy on stock $symbol")
          return ResponseEntity.badRequest().build()
        }

    logger.info("Stock data with signals retrieved successfully for: $symbol")
    return ResponseEntity.ok(stockWithSignals)
  }

  /**
   * Evaluate entry strategy conditions for a specific date.
   * Returns condition details showing why a strategy did or did not trigger.
   *
   * Example: GET /api/stocks/TQQQ/evaluate-date/2024-01-15?entryStrategy=VegardPlanEtf
   *
   * @param symbol Stock symbol (e.g., TQQQ, SPY)
   * @param date Date to evaluate (format: YYYY-MM-DD)
   * @param entryStrategy Entry strategy name (e.g., VegardPlanEtf)
   * @return Entry signal details with all condition evaluations
   */
  @GetMapping("/{symbol}/evaluate-date/{date}")
  @Transactional(readOnly = true)
  fun evaluateConditionsForDate(
    @PathVariable symbol: String,
    @PathVariable date: String,
    @RequestParam entryStrategy: String,
  ): ResponseEntity<EntrySignalDetails> {
    logger.info("Evaluating entry conditions for $symbol on date=$date with strategy=$entryStrategy")

    // Get stock data
    val stock =
      stockService.getStock(symbol) ?: run {
        logger.error("Stock not found: $symbol")
        return ResponseEntity.notFound().build()
      }

    // Evaluate conditions for the specific date
    val details =
      strategySignalService.evaluateConditionsForDate(stock, date, entryStrategy)
        ?: run {
          logger.error("Failed to evaluate conditions for $symbol on $date with strategy=$entryStrategy")
          return ResponseEntity.badRequest().build()
        }

    logger.info("Evaluated conditions for $symbol on $date: allConditionsMet=${details.allConditionsMet}")
    return ResponseEntity.ok(details)
  }

  /**
   * Evaluate exit strategy conditions for a specific date.
   * Returns condition details showing why a strategy did or did not trigger an exit.
   *
   * Example: GET /api/stocks/AAPL/evaluate-exit/2024-01-15?exitStrategy=ProjectXExitStrategy&entryDate=2024-01-10
   *
   * @param symbol Stock symbol (e.g., AAPL, TQQQ)
   * @param date Date to evaluate exit conditions on (format: YYYY-MM-DD)
   * @param exitStrategy Exit strategy name (e.g., ProjectXExitStrategy)
   * @param entryDate The entry date for the trade (format: YYYY-MM-DD)
   * @return Exit signal details with all condition evaluations
   */
  @GetMapping("/{symbol}/evaluate-exit/{date}")
  @Transactional(readOnly = true)
  fun evaluateExitConditionsForDate(
    @PathVariable symbol: String,
    @PathVariable date: String,
    @RequestParam exitStrategy: String,
    @RequestParam entryDate: String,
  ): ResponseEntity<ExitSignalDetails> {
    logger.info("Evaluating exit conditions for $symbol on date=$date (entry=$entryDate) with strategy=$exitStrategy")

    val stock =
      stockService.getStock(symbol) ?: run {
        logger.error("Stock not found: $symbol")
        return ResponseEntity.notFound().build()
      }

    val details =
      strategySignalService.evaluateExitConditionsForDate(stock, date, entryDate, exitStrategy)
        ?: run {
          logger.error("Failed to evaluate exit conditions for $symbol on $date with strategy=$exitStrategy")
          return ResponseEntity.badRequest().build()
        }

    logger.info("Evaluated exit conditions for $symbol on $date: anyConditionMet=${details.anyConditionMet}")
    return ResponseEntity.ok(details)
  }

  /**
   * Evaluate entry conditions on a stock's data and return matching quotes.
   * Unlike the signals endpoint, this does not require an exit strategy and
   * evaluates conditions independently on every quote.
   *
   * Example: POST /api/stocks/TQQQ/condition-signals
   * Body: { "conditions": [{"type": "uptrend"}, {"type": "priceAboveEma", "parameters": {"emaPeriod": 20}}], "operator": "AND" }
   *
   * @param symbol Stock symbol
   * @param request Condition evaluation request with conditions and operator
   * @return Quotes where conditions match, with detailed evaluation results
   */
  @PostMapping("/{symbol}/condition-signals")
  @Transactional(readOnly = true)
  fun evaluateConditions(
    @PathVariable symbol: String,
    @RequestBody request: ConditionEvaluationRequest,
  ): ResponseEntity<StockConditionSignals> {
    logger.info("Evaluating ${request.conditions.size} conditions on stock $symbol with operator=${request.operator}")

    if (request.conditions.isEmpty()) {
      return ResponseEntity.badRequest().build()
    }

    val stock =
      stockService.getStock(symbol) ?: run {
        logger.error("Stock not found: $symbol")
        return ResponseEntity.notFound().build()
      }

    return try {
      val result = strategySignalService.evaluateConditions(stock, request.conditions, request.operator)
      logger.info("Condition evaluation for $symbol: ${result.matchingQuotes}/${result.totalQuotes} quotes matched")
      ResponseEntity.ok(result)
    } catch (e: IllegalArgumentException) {
      logger.error("Invalid condition configuration: ${e.message}", e)
      ResponseEntity.badRequest().build()
    }
  }

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(StockController::class.java)
  }
}
