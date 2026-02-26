package com.skrymer.udgaard.controller

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST Controller for cache management
 *
 * Provides endpoints to manage caching for the application.
 */
@RestController
@RequestMapping("/api/cache")
class CacheController {
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
        "dataSource" to
          mapOf(
            "provider" to "midgaard",
            "note" to "Stock data and indicators provided by Midgaard reference data service",
          ),
      ),
    )
  }

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(CacheController::class.java)
  }
}
