package com.skrymer.udgaard.backtesting.controller

import com.skrymer.udgaard.backtesting.dto.ConditionScreenReport
import com.skrymer.udgaard.backtesting.dto.ConditionScreenRequest
import com.skrymer.udgaard.backtesting.service.ConditionScreenService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Diagnostic condition screen endpoint. See [ConditionScreenService] and ADR 0007.
 *
 * Invalid requests — empty stack, a date range past Block C, an unknown condition type — return
 * 400 with the failure reason; the message names Block C when the leakage guard trips.
 */
@RestController
@RequestMapping("/api/conditions")
class ConditionScreenController(
  private val conditionScreenService: ConditionScreenService,
) {
  private val logger = LoggerFactory.getLogger(ConditionScreenController::class.java)

  // No surrounding @Transactional: the screen is minutes of in-memory CPU work over data fetched up
  // front via independent eager reads; holding a read transaction open would pin a pooled connection
  // for the whole computation with no consistency benefit.
  @PostMapping("/screen")
  fun screen(
    @RequestBody request: ConditionScreenRequest,
  ): ResponseEntity<ConditionScreenReport> {
    logger.info(
      "Condition screen: ${request.conditions.size} conditions, operator=${request.operator}, " +
        "range=${request.startDate}..${request.endDate}, horizons=${request.horizons}",
    )
    return try {
      ResponseEntity.ok(conditionScreenService.screen(request))
    } catch (e: IllegalArgumentException) {
      logger.warn("Rejected condition screen request: ${e.message}")
      ResponseEntity.badRequest().build()
    }
  }
}
