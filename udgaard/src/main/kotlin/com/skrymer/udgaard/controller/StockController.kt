package com.skrymer.udgaard.controller

import com.skrymer.udgaard.controller.dto.EntrySignalDetails
import com.skrymer.udgaard.controller.dto.SimpleStockInfo
import com.skrymer.udgaard.controller.dto.StockRefreshResult
import com.skrymer.udgaard.controller.dto.StockWithSignals
import com.skrymer.udgaard.domain.StockDomain
import com.skrymer.udgaard.model.StockSymbol
import com.skrymer.udgaard.service.StockService
import com.skrymer.udgaard.service.StrategySignalService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.CrossOrigin
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
@CrossOrigin(origins = ["http://localhost:3000", "http://localhost:8080"])
class StockController(
  private val stockService: StockService,
  private val strategySignalService: StrategySignalService,
) {
  companion object {
    private val logger: Logger = LoggerFactory.getLogger(StockController::class.java)
  }

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
   * Get all available stock symbols from StockSymbol enum.
   * This returns ALL possible symbols (1,433), not just stocks with data loaded.
   *
   * Example: GET /api/stocks/symbols
   *
   * @return List of all stock symbols defined in StockSymbol enum
   */
  @GetMapping("/symbols")
  fun getAllStockSymbols(): ResponseEntity<List<String>> {
    logger.info("Retrieving all stock symbols from enum")
    val symbols = StockSymbol.entries.map { it.symbol }
    logger.info("Returning ${symbols.size} stock symbols from enum")
    return ResponseEntity.ok(symbols)
  }

  /**
   * Get stock data for a specific symbol.
   *
   * Example: GET /api/stocks/AAPL?refresh=true&skipOvtlyr=true
   *
   * @param symbol Stock symbol (e.g., AAPL, GOOGL)
   * @param refresh Force refresh from external source
   * @param skipOvtlyr Skip Ovtlyr enrichment when refreshing
   * @return Stock data including quotes and order blocks
   */
  @GetMapping("/{symbol}")
  @Transactional(readOnly = true)
  suspend fun getStock(
    @PathVariable symbol: String,
    @RequestParam(defaultValue = "false") refresh: Boolean,
    @RequestParam(defaultValue = "false") skipOvtlyr: Boolean,
  ): ResponseEntity<StockDomain> {
    logger.info("Getting stock data for: $symbol (refresh=$refresh, skipOvtlyr=$skipOvtlyr)")
    val stock = stockService.getStock(symbol, refresh, skipOvtlyr)
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
  @Transactional(readOnly = true)
  suspend fun getStockWithSignals(
    @PathVariable symbol: String,
    @RequestParam entryStrategy: String,
    @RequestParam exitStrategy: String,
    @RequestParam(defaultValue = "0") cooldownDays: Int,
    @RequestParam(defaultValue = "false") refresh: Boolean,
  ): ResponseEntity<StockWithSignals> {
    logger.info(
      "Getting stock $symbol with signals for entry=$entryStrategy, exit=$exitStrategy, cooldown=$cooldownDays (refresh=$refresh)",
    )

    // Get stock data
    val stock =
      stockService.getStock(symbol, refresh) ?: run {
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
   * Refresh stock data for multiple symbols.
   *
   * Example: POST /api/stocks/refresh
   * Body: ["AAPL", "GOOGL", "MSFT"]
   *
   * @param symbols List of stock symbols to refresh
   * @return Detailed refresh result with status, counts, successful stocks, and failed stocks with errors
   */
  @PostMapping("/refresh")
  fun refreshStocks(
    @RequestBody symbols: List<String>,
  ): ResponseEntity<StockRefreshResult> {
    logger.info("Refreshing ${symbols.size} stocks: ${symbols.joinToString(", ")}")
    val result = stockService.refreshStocksWithDetails(symbols.map { it.uppercase() })
    logger.info(
      "Stock refresh complete: ${result.succeeded}/${result.total} succeeded, ${result.failed} failed (${result.status})",
    )
    return ResponseEntity.ok(result)
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
  suspend fun evaluateConditionsForDate(
    @PathVariable symbol: String,
    @PathVariable date: String,
    @RequestParam entryStrategy: String,
  ): ResponseEntity<EntrySignalDetails> {
    logger.info("Evaluating entry conditions for $symbol on date=$date with strategy=$entryStrategy")

    // Get stock data
    val stock =
      stockService.getStock(symbol, false) ?: run {
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
}
