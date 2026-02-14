package com.skrymer.udgaard.data.controller

import com.skrymer.udgaard.data.integration.ovtlyr.DataLoader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for data loading and management operations.
 *
 * Handles:
 * - Loading/initializing data from external sources
 */
@RestController
@RequestMapping("/api/data")
@CrossOrigin(origins = ["http://localhost:3000", "http://localhost:8080"])
class DataController(
  private val dataLoader: DataLoader,
) {
  companion object {
    private val logger: Logger = LoggerFactory.getLogger(DataController::class.java)
  }

  /**
   * Load/initialize data from external sources.
   *
   * This endpoint triggers data loading from Ovtlyr and other configured sources.
   * Use POST (not GET) as this operation modifies state.
   *
   * Example: POST /api/data/load
   *
   * @return Status message
   */
  @PostMapping("/load")
  fun loadData(): ResponseEntity<Map<String, String>> {
    logger.info("Loading data started")
    dataLoader.loadData()
    logger.info("Data loading completed")
    return ResponseEntity.ok(
      mapOf("status" to "success", "message" to "Data loaded successfully"),
    )
  }
}
