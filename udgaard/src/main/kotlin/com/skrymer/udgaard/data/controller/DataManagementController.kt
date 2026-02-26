package com.skrymer.udgaard.data.controller

import com.skrymer.udgaard.data.dto.BreadthCoverageStats
import com.skrymer.udgaard.data.dto.DatabaseStats
import com.skrymer.udgaard.data.dto.RefreshProgress
import com.skrymer.udgaard.data.dto.RefreshResponse
import com.skrymer.udgaard.data.dto.SectorStockCount
import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import com.skrymer.udgaard.data.repository.SectorBreadthRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import com.skrymer.udgaard.data.service.DataStatsService
import com.skrymer.udgaard.data.service.MarketBreadthService
import com.skrymer.udgaard.data.service.SectorBreadthService
import com.skrymer.udgaard.data.service.StockIngestionService
import com.skrymer.udgaard.data.service.SymbolService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/data-management")
class DataManagementController(
  private val dataStatsService: DataStatsService,
  private val stockIngestionService: StockIngestionService,
  private val symbolService: SymbolService,
  private val sectorBreadthService: SectorBreadthService,
  private val marketBreadthService: MarketBreadthService,
  private val marketBreadthRepository: MarketBreadthRepository,
  private val stockRepository: StockJooqRepository,
  private val sectorBreadthRepository: SectorBreadthRepository,
) {
  private val logger = LoggerFactory.getLogger(DataManagementController::class.java)

  /**
   * Get comprehensive database statistics
   */
  @GetMapping("/stats")
  fun getDatabaseStats(): DatabaseStats {
    logger.info("Getting database statistics")
    return dataStatsService.calculateStats()
  }

  /**
   * Queue specific stocks for refresh
   */
  @PostMapping("/refresh/stocks")
  fun refreshStocks(
    @RequestBody symbols: List<String>,
  ): RefreshResponse {
    logger.info("Queueing ${symbols.size} stocks for refresh")
    stockIngestionService.queueStockRefresh(symbols)
    return RefreshResponse(
      queued = symbols.size,
      message = "Queued ${symbols.size} stocks for refresh",
    )
  }

  /**
   * Queue all stocks for refresh (from symbols table)
   */
  @PostMapping("/refresh/all-stocks")
  fun refreshAllStocks(): RefreshResponse {
    val allSymbols = symbolService.getAll().map { it.symbol }
    logger.info("Queueing all ${allSymbols.size} stocks for refresh")
    stockIngestionService.queueStockRefresh(allSymbols)
    return RefreshResponse(
      queued = allSymbols.size,
      message = "Queued ${allSymbols.size} stocks for refresh",
    )
  }

  /**
   * Recalculate market and sector breadth from stock quotes.
   * This recomputes breadth percentages and EMAs from existing stock data.
   */
  @PostMapping("/refresh/recalculate-breadth")
  fun recalculateBreadth(): Map<String, String> {
    logger.info("Recalculating market and sector breadth from stock quotes")
    marketBreadthRepository.refreshBreadthDaily()
    marketBreadthService.refreshMarketBreadth()
    sectorBreadthService.refreshSectorBreadth()
    logger.info("Market and sector breadth recalculation complete")
    return mapOf("status" to "Market and sector breadth recalculated successfully")
  }

  /**
   * Get breadth coverage statistics (how many stocks are included in breadth calculations)
   */
  @GetMapping("/breadth-coverage")
  fun getBreadthCoverage(): BreadthCoverageStats {
    val totalStocks = stockRepository.countDistinctStocksWithQuotes()
    val sectorCounts = sectorBreadthRepository.getLatestSectorCounts()

    return BreadthCoverageStats(
      totalStocks = totalStocks,
      sectors = sectorCounts.map { (symbol, count) ->
        SectorStockCount(sectorSymbol = symbol, totalStocks = count)
      },
    )
  }

  /**
   * Get current refresh progress
   */
  @GetMapping("/refresh/progress")
  fun getRefreshProgress(): RefreshProgress = stockIngestionService.getProgress()

  /**
   * Clear refresh queue
   */
  @PostMapping("/refresh/clear")
  fun clearRefreshQueue(): ResponseEntity<String> {
    logger.info("Clearing refresh queue")
    stockIngestionService.clearQueue()
    return ResponseEntity.ok("Refresh queue cleared")
  }

}
