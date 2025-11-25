package com.skrymer.udgaard.controller

import com.skrymer.udgaard.model.Breadth
import com.skrymer.udgaard.model.SectorSymbol
import com.skrymer.udgaard.service.BreadthService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * REST controller for breadth data operations.
 * Provides clear separation between market breadth (all stocks) and sector breadth (individual sectors).
 *
 * Endpoints:
 * - GET /api/breadth/market - Get market breadth (FULLSTOCK)
 * - GET /api/breadth/sector/{symbol} - Get specific sector breadth
 * - POST /api/breadth/refresh-all - Refresh all breadth data
 */
@RestController
@RequestMapping("/api/breadth")
@CrossOrigin(origins = ["http://localhost:3000", "http://localhost:8080"])
class BreadthController(
    private val breadthService: BreadthService
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(BreadthController::class.java)
    }

    /**
     * Get market breadth data (all stocks combined).
     *
     * Example: GET /api/breadth/market?refresh=true
     *
     * @param refresh Force refresh from external source
     * @return Market breadth data including historical quotes
     */
    @GetMapping("/market")
    fun getMarketBreadth(
        @RequestParam(defaultValue = "false") refresh: Boolean
    ): ResponseEntity<Breadth> {
        logger.info("Getting market breadth (refresh=$refresh)")
        val breadth = breadthService.getMarketBreadth(
            fromDate = LocalDate.now().minusMonths(3),
            toDate = LocalDate.now(),
            refresh = refresh
        )
        logger.info("Market breadth retrieved successfully")
        return ResponseEntity.ok(breadth)
    }

    /**
     * Get sector breadth data for a specific sector.
     *
     * Example: GET /api/breadth/sector/XLK?refresh=true
     *
     * @param symbol Sector symbol (XLE, XLV, XLB, XLC, XLK, XLRE, XLI, XLF, XLY, XLP, XLU)
     * @param refresh Force refresh from external source
     * @return Sector breadth data including historical quotes
     */
    @GetMapping("/sector/{symbol}")
    fun getSectorBreadth(
        @PathVariable symbol: String,
        @RequestParam(defaultValue = "false") refresh: Boolean
    ): ResponseEntity<Breadth> {
        logger.info("Getting sector breadth for: $symbol (refresh=$refresh)")
        val sectorSymbol = SectorSymbol.fromString(symbol)
            ?: return ResponseEntity.badRequest().build()

        val breadth = breadthService.getSectorBreadth(
            sector = sectorSymbol,
            fromDate = LocalDate.now().minusMonths(3),
            toDate = LocalDate.now(),
            refresh = refresh
        )
        logger.info("Sector breadth retrieved successfully for: $symbol")
        return ResponseEntity.ok(breadth)
    }

    /**
     * Refresh breadth data for all symbols (market + all sectors).
     *
     * Example: POST /api/breadth/refresh-all
     *
     * @return Status map with refresh results
     */
    @PostMapping("/refresh-all")
    fun refreshAll(): ResponseEntity<Map<String, String>> {
        logger.info("Refreshing all breadth data (market + all sectors)")
        val result = breadthService.refreshAll()
        logger.info("All breadth data refreshed successfully")
        return ResponseEntity.ok(result)
    }
}
