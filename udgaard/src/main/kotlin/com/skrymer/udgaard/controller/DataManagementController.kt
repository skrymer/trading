package com.skrymer.udgaard.controller

import com.skrymer.udgaard.controller.dto.*
import com.skrymer.udgaard.repository.StockRepository
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
    private val stockRepository: StockRepository
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
     * Get current rate limit status
     */
    @GetMapping("/rate-limit")
    fun getRateLimitStatus(): RateLimitStats {
        logger.info("Getting rate limit status")
        return rateLimiter.getUsageStats()
    }

    /**
     * Get rate limit configuration
     */
    @GetMapping("/rate-limit/config")
    fun getRateLimitConfig(): RateLimitConfigDto {
        logger.info("Getting rate limit configuration")
        val stats = rateLimiter.getUsageStats()
        val tier = when {
            stats.minuteLimit <= 5 -> "FREE"
            stats.minuteLimit <= 75 -> "PREMIUM"
            else -> "ULTIMATE"
        }
        return RateLimitConfigDto(
            requestsPerMinute = stats.minuteLimit,
            requestsPerDay = stats.dailyLimit,
            tier = tier
        )
    }

    /**
     * Queue specific stocks for refresh
     */
    @PostMapping("/refresh/stocks")
    fun refreshStocks(@RequestBody symbols: List<String>): RefreshResponse {
        logger.info("Queueing ${symbols.size} stocks for refresh")
        dataRefreshService.queueStockRefresh(symbols)
        return RefreshResponse(
            queued = symbols.size,
            message = "Queued ${symbols.size} stocks for refresh"
        )
    }

    /**
     * Queue all stocks for refresh
     */
    @PostMapping("/refresh/all-stocks")
    fun refreshAllStocks(): RefreshResponse {
        logger.info("Queueing all stocks for refresh")
        val allSymbols = stockRepository.findAll().mapNotNull { it.symbol }
        dataRefreshService.queueStockRefresh(allSymbols)
        return RefreshResponse(
            queued = allSymbols.size,
            message = "Queued ${allSymbols.size} stocks for refresh"
        )
    }

    /**
     * Queue breadth data for refresh
     */
    @PostMapping("/refresh/breadth")
    fun refreshBreadth(): RefreshResponse {
        logger.info("Queueing breadth data for refresh")
        val symbols = listOf("SPY", "QQQ", "IWM")
        dataRefreshService.queueBreadthRefresh(symbols)
        return RefreshResponse(
            queued = symbols.size,
            message = "Queued ${symbols.size} breadth symbols for refresh"
        )
    }

    /**
     * Get current refresh progress
     */
    @GetMapping("/refresh/progress")
    fun getRefreshProgress(): RefreshProgress {
        logger.debug("Getting refresh progress")
        return dataRefreshService.getProgress()
    }

    /**
     * Pause refresh processing
     */
    @PostMapping("/refresh/pause")
    fun pauseRefresh(): ResponseEntity<String> {
        logger.info("Pausing refresh processing")
        dataRefreshService.pauseProcessing()
        return ResponseEntity.ok("Refresh paused")
    }

    /**
     * Resume refresh processing
     */
    @PostMapping("/refresh/resume")
    fun resumeRefresh(): ResponseEntity<String> {
        logger.info("Resuming refresh processing")
        dataRefreshService.resumeProcessing()
        return ResponseEntity.ok("Refresh resumed")
    }

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
