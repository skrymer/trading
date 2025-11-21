package com.skrymer.udgaard.controller

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST Controller for cache management
 *
 * Provides endpoints to manage caching for external API integrations
 * like Alpha Vantage to optimize API usage and stay within rate limits.
 *
 * Note: AlphaVantage caching has been disabled to prevent stale data issues.
 */
@RestController
@RequestMapping("/api/cache")
@CrossOrigin(origins = ["http://localhost:3000", "http://localhost:8080"])
class CacheController {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(CacheController::class.java)
    }

    /**
     * Get cache status information
     *
     * @return Information about cache configuration
     */
    @GetMapping("/status")
    fun getCacheStatus(): ResponseEntity<Map<String, Any>> {
        logger.info("Request for cache status")
        return ResponseEntity.ok(
            mapOf(
                "alphaVantage" to mapOf(
                    "caching" to "disabled",
                    "note" to "AlphaVantage caching has been disabled to ensure fresh data"
                ),
                "rateLimits" to mapOf(
                    "daily" to 25,
                    "perMinute" to 5,
                    "tier" to "free"
                )
            )
        )
    }
}
