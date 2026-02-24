package com.skrymer.udgaard.data.controller

import com.skrymer.udgaard.data.model.MarketBreadthDaily
import com.skrymer.udgaard.data.model.SectorBreadthDaily
import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import com.skrymer.udgaard.data.repository.SectorBreadthRepository
import com.skrymer.udgaard.data.service.MarketBreadthService
import com.skrymer.udgaard.data.service.SectorBreadthService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for breadth data operations.
 * Provides market breadth (all stocks) and sector breadth (individual sectors)
 * calculated from local stock quote data.
 *
 * Endpoints:
 * - GET /api/breadth/market-daily - Get daily market breadth
 * - GET /api/breadth/sector-daily/{symbol} - Get daily sector breadth
 */
@RestController
@RequestMapping("/api/breadth")
class BreadthController(
  private val marketBreadthRepository: MarketBreadthRepository,
  private val sectorBreadthRepository: SectorBreadthRepository,
  private val marketBreadthService: MarketBreadthService,
  private val sectorBreadthService: SectorBreadthService,
) {
  @GetMapping("/market-daily")
  fun getMarketBreadthDaily(): ResponseEntity<List<MarketBreadthDaily>> {
    var data = marketBreadthRepository.findAllAsMap().values.sortedBy { it.quoteDate }
    if (data.isEmpty()) {
      logger.info("Market breadth daily table empty, calculating from stock data")
      marketBreadthRepository.refreshBreadthDaily()
      marketBreadthService.refreshMarketBreadth()
      data = marketBreadthRepository.findAllAsMap().values.sortedBy { it.quoteDate }
    }
    return ResponseEntity.ok(data)
  }

  @GetMapping("/sector-daily/{symbol}")
  fun getSectorBreadthDaily(
    @PathVariable symbol: String,
  ): ResponseEntity<List<SectorBreadthDaily>> {
    var data = sectorBreadthRepository.findBySector(symbol)
    if (data.isEmpty()) {
      logger.info("Sector breadth daily table empty for $symbol, calculating from stock data")
      sectorBreadthService.refreshSectorBreadth()
      data = sectorBreadthRepository.findBySector(symbol)
    }
    return ResponseEntity.ok(data)
  }

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(BreadthController::class.java)
  }
}
