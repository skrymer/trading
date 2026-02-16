package com.skrymer.udgaard.data.controller

import com.skrymer.udgaard.data.dto.BreadthCoverageStats
import com.skrymer.udgaard.data.dto.DatabaseStats
import com.skrymer.udgaard.data.dto.RateLimitConfigDto
import com.skrymer.udgaard.data.dto.RateLimitStats
import com.skrymer.udgaard.data.dto.RefreshProgress
import com.skrymer.udgaard.data.dto.RefreshResponse
import com.skrymer.udgaard.data.dto.SectorStockCount
import com.skrymer.udgaard.data.repository.MarketBreadthRepository
import com.skrymer.udgaard.data.repository.SectorBreadthRepository
import com.skrymer.udgaard.data.repository.StockJooqRepository
import com.skrymer.udgaard.data.service.DataStatsService
import com.skrymer.udgaard.data.service.MarketBreadthService
import com.skrymer.udgaard.data.service.RateLimiterService
import com.skrymer.udgaard.data.service.SectorBreadthService
import com.skrymer.udgaard.data.service.StockIngestionService
import com.skrymer.udgaard.data.service.SymbolService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/data-management")
class DataManagementController(
  private val dataStatsService: DataStatsService,
  private val stockIngestionService: StockIngestionService,
  private val rateLimiter: RateLimiterService,
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
   * Get current rate limit status for the primary provider (AlphaVantage)
   * Returns stats in the format expected by the frontend
   */
  @GetMapping("/rate-limit")
  fun getRateLimitStatus(): RateLimitStats {
    val stats = rateLimiter.getProviderStats("alphavantage")
      ?: return RateLimitStats(
        requestsLastMinute = 0,
        requestsLastDay = 0,
        remainingMinute = 0,
        remainingDaily = 0,
        minuteLimit = 0,
        dailyLimit = 0,
        resetMinute = 0,
        resetDaily = 0,
      )

    // Calculate approximate reset times
    // For rolling windows, we estimate based on when the oldest request will fall out of the window
    // This is a simplification; actual reset time depends on when the oldest request was made
    val resetMinute = 60L // Rolling minute window resets continuously
    val resetDaily = 86400L // Rolling day window resets continuously

    return RateLimitStats(
      requestsLastMinute = stats.requestsLastMinute,
      requestsLastDay = stats.requestsLastDay,
      remainingMinute = stats.remainingMinute,
      remainingDaily = stats.remainingDaily,
      minuteLimit = stats.minuteLimit,
      dailyLimit = stats.dailyLimit,
      resetMinute = resetMinute,
      resetDaily = resetDaily,
    )
  }

  /**
   * Get rate limit status for all registered providers
   * Returns a map of provider ID to their stats
   */
  @GetMapping("/rate-limit/all")
  fun getAllProvidersRateLimitStatus(): Map<String, com.skrymer.udgaard.data.service.ProviderRateLimitStats> =
    rateLimiter.getAllProviderStats()

  /**
   * Get rate limit configuration
   */
  @GetMapping("/rate-limit/config")
  fun getRateLimitConfig(): RateLimitConfigDto {
    logger.info("Getting rate limit configuration")
    // Get stats for the primary provider (AlphaVantage)
    val stats = rateLimiter.getProviderStats("alphavantage")
      ?: return RateLimitConfigDto(tier = "unknown", requestsPerMinute = 0, requestsPerDay = 0)
    val tier =
      when {
        stats.minuteLimit <= 5 -> "FREE"
        stats.minuteLimit <= 75 -> "PREMIUM"
        else -> "ULTIMATE"
      }
    return RateLimitConfigDto(
      requestsPerMinute = stats.minuteLimit,
      requestsPerDay = stats.dailyLimit,
      tier = tier,
    )
  }

  /**
   * Queue specific stocks for refresh
   */
  @PostMapping("/refresh/stocks")
  fun refreshStocks(
    @RequestBody symbols: List<String>,
    @RequestParam(required = false, defaultValue = "2020-01-01") minDate: String,
  ): RefreshResponse {
    val parsedMinDate = LocalDate.parse(minDate)
    logger.info("Queueing ${symbols.size} stocks for refresh (minDate=$parsedMinDate)")
    stockIngestionService.queueStockRefresh(symbols, minDate = parsedMinDate)
    return RefreshResponse(
      queued = symbols.size,
      message = "Queued ${symbols.size} stocks for refresh",
    )
  }

  /**
   * Queue all stocks for refresh (from symbols table)
   */
  @PostMapping("/refresh/all-stocks")
  fun refreshAllStocks(
    @RequestParam(required = false, defaultValue = "2020-01-01") minDate: String,
  ): RefreshResponse {
    val parsedMinDate = LocalDate.parse(minDate)
    logger.info("Queueing all stocks for refresh (minDate=$parsedMinDate)")
    val allSymbols = symbolService.getAll().map { it.symbol }
    stockIngestionService.queueStockRefresh(allSymbols, minDate = parsedMinDate)
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
