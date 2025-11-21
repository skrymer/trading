package com.skrymer.udgaard.controller

import com.skrymer.udgaard.model.MarketBreadth
import com.skrymer.udgaard.model.MarketSymbol
import com.skrymer.udgaard.model.valueOf
import com.skrymer.udgaard.service.MarketBreadthService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * REST controller for market breadth operations.
 *
 * Handles:
 * - Retrieving market breadth data for specific sectors
 * - Refreshing all sector breadth data
 */
@RestController
@RequestMapping("/api/market-breadth")
@CrossOrigin(origins = ["http://localhost:3000", "http://localhost:8080"])
class MarketBreadthController(
    private val marketBreadthService: MarketBreadthService
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(MarketBreadthController::class.java)
    }

    /**
     * Get market breadth data for a specific market symbol.
     *
     * Example: GET /api/market-breadth?marketSymbol=SPY&refresh=true
     *
     * @param marketSymbol Market/sector symbol (e.g., SPY, XLK, XLF)
     * @param refresh Force refresh from external source
     * @return Market breadth data including historical quotes
     */
    @GetMapping
    fun getMarketBreadth(
        @RequestParam marketSymbol: String,
        @RequestParam(defaultValue = "false") refresh: Boolean
    ): ResponseEntity<MarketBreadth> {
        logger.info("Getting market breadth for: $marketSymbol (refresh=$refresh)")
        val marketBreadth = marketBreadthService.getMarketBreadth(
            marketSymbol = MarketSymbol.valueOf(marketSymbol),
            fromDate = LocalDate.now().minusMonths(3),
            toDate = LocalDate.now(),
            refresh = refresh
        )
        logger.info("Market breadth retrieved successfully for: $marketSymbol")
        return ResponseEntity.ok(marketBreadth)
    }

    /**
     * Refresh market breadth data for all sectors.
     *
     * Example: POST /api/market-breadth/refresh-all
     *
     * @return Status map with refresh results
     */
    @PostMapping("/refresh-all")
    fun refreshAllSectors(): ResponseEntity<Map<String, String>> {
        logger.info("Refreshing all market sectors")
        val result = marketBreadthService.refreshAllSectors()
        logger.info("All sectors refreshed successfully")
        return ResponseEntity.ok(result)
    }
}
