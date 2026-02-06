package com.skrymer.udgaard.controller

import com.skrymer.udgaard.controller.dto.*
import com.skrymer.udgaard.model.StockSymbol
import com.skrymer.udgaard.service.DataRefreshService
import com.skrymer.udgaard.service.DataStatsService
import com.skrymer.udgaard.service.RateLimiterService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/data-management")
class DataManagementController(
  private val dataStatsService: DataStatsService,
  private val dataRefreshService: DataRefreshService,
  private val rateLimiter: RateLimiterService,
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
  fun getAllProvidersRateLimitStatus(): Map<String, com.skrymer.udgaard.service.ProviderRateLimitStats> =
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
    @RequestParam(required = false, defaultValue = "false") skipOvtlyr: Boolean,
  ): RefreshResponse {
    logger.info("Queueing ${symbols.size} stocks for refresh (skipOvtlyr=$skipOvtlyr)")
    dataRefreshService.queueStockRefresh(symbols, skipOvtlyrEnrichment = skipOvtlyr)
    return RefreshResponse(
      queued = symbols.size,
      message = "Queued ${symbols.size} stocks for refresh" + if (skipOvtlyr) " (Ovtlyr enrichment skipped)" else "",
    )
  }

  /**
   * Queue all stocks for refresh (from StockSymbol enum - S&P 500 constituents)
   */
  @PostMapping("/refresh/all-stocks")
  fun refreshAllStocks(
    @RequestParam(required = false, defaultValue = "false") skipOvtlyr: Boolean,
  ): RefreshResponse {
    logger.info("Queueing all stocks for refresh (skipOvtlyr=$skipOvtlyr)")
    val allSymbols = StockSymbol.entries.map { it.symbol }
    dataRefreshService.queueStockRefresh(allSymbols, skipOvtlyrEnrichment = skipOvtlyr)
    return RefreshResponse(
      queued = allSymbols.size,
      message = "Queued ${allSymbols.size} stocks for refresh" + if (skipOvtlyr) " (Ovtlyr enrichment skipped)" else "",
    )
  }

  /**
   * Queue breadth data for refresh (market breadth + all sector breadth)
   */
  @PostMapping("/refresh/breadth")
  fun refreshBreadth(): RefreshResponse {
    logger.info("Queueing breadth data for refresh")
    // Get all breadth symbols (FULLSTOCK market + all sectors)
    val breadthSymbols = com.skrymer.udgaard.model.BreadthSymbol
      .all()
    val identifiers = breadthSymbols.map { it.toIdentifier() }
    dataRefreshService.queueBreadthRefresh(identifiers)
    return RefreshResponse(
      queued = identifiers.size,
      message = "Queued ${identifiers.size} breadth symbols for refresh (1 market + ${identifiers.size - 1} sectors)",
    )
  }

  /**
   * Get current refresh progress
   */
  @GetMapping("/refresh/progress")
  fun getRefreshProgress(): RefreshProgress = dataRefreshService.getProgress()

  /**
   * Clear refresh queue
   */
  @PostMapping("/refresh/clear")
  fun clearRefreshQueue(): ResponseEntity<String> {
    logger.info("Clearing refresh queue")
    dataRefreshService.clearQueue()
    return ResponseEntity.ok("Refresh queue cleared")
  }
}
