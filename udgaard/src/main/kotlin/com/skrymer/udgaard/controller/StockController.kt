package com.skrymer.udgaard.controller

import com.skrymer.udgaard.controller.dto.StockWithSignals
import com.skrymer.udgaard.model.Stock
import com.skrymer.udgaard.model.StockSymbol
import com.skrymer.udgaard.service.StockService
import com.skrymer.udgaard.service.StrategySignalService
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
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
    private val strategySignalService: StrategySignalService
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(StockController::class.java)
    }

    /**
     * Get all available stock symbols.
     *
     * Example: GET /api/stocks
     *
     * @return List of stock symbols
     */
    @GetMapping
    fun getStockSymbols(): ResponseEntity<List<String>> {
        logger.info("Retrieving all stock symbols")
        val symbols = StockSymbol.entries.map { it.symbol }
        logger.info("Returning ${symbols.size} stock symbols")
        return ResponseEntity.ok(symbols)
    }

    /**
     * Get stock data for a specific symbol.
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
        @RequestParam(defaultValue = "false") refresh: Boolean
    ): ResponseEntity<Stock> {
        logger.info("Getting stock data for: $symbol (refresh=$refresh)")
        val stock = stockService.getStock(symbol, refresh)
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
        @RequestParam(defaultValue = "false") refresh: Boolean
    ): ResponseEntity<StockWithSignals> {
        logger.info("Getting stock $symbol with signals for entry=$entryStrategy, exit=$exitStrategy, cooldown=$cooldownDays (refresh=$refresh)")

        // Get stock data
        val stock = stockService.getStock(symbol, refresh) ?: run {
            logger.error("Stock not found: $symbol")
            return ResponseEntity.notFound().build()
        }

        // Evaluate strategies and generate signals
        val stockWithSignals = strategySignalService.evaluateStrategies(stock, entryStrategy, exitStrategy, cooldownDays)
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
     * @return Status message
     */
    @PostMapping("/refresh")
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
}
