package com.skrymer.udgaard.controller

import com.skrymer.udgaard.controller.dto.EtfStatsResponse
import com.skrymer.udgaard.model.EtfSymbol
import com.skrymer.udgaard.service.EtfStatsService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * REST controller for ETF statistics and analysis.
 *
 * Handles:
 * - Retrieving ETF statistics and membership data
 */
@RestController
@RequestMapping("/api/etf")
@CrossOrigin(origins = ["http://localhost:3000", "http://localhost:8080"])
class EtfController(
  private val etfStatsService: EtfStatsService,
) {
  companion object {
    private val logger: Logger = LoggerFactory.getLogger(EtfController::class.java)
  }

  /**
   * Get statistics for a specific ETF.
   *
   * Example: GET /api/etf/SPY/stats?fromDate=2024-01-01&toDate=2024-12-31&refresh=true
   *
   * @param symbol ETF symbol (e.g., SPY, QQQ, IWM)
   * @param fromDate Start date for statistics (optional, defaults to 3 months ago)
   * @param toDate End date for statistics (optional, defaults to now)
   * @param refresh Force refresh from external source
   * @return ETF statistics including membership data and historical performance
   */
  @GetMapping("/{symbol}/stats")
  @Transactional(readOnly = true)
  fun getEtfStats(
    @PathVariable symbol: String,
    @RequestParam(required = false) fromDate: String?,
    @RequestParam(required = false) toDate: String?,
    @RequestParam(defaultValue = "false") refresh: Boolean,
  ): ResponseEntity<EtfStatsResponse> {
    logger.info("Getting ETF stats for: $symbol (refresh=$refresh, fromDate=$fromDate, toDate=$toDate)")

    val etf =
      EtfSymbol.fromString(symbol)
        ?: run {
          logger.error("Invalid ETF symbol: $symbol")
          return ResponseEntity.badRequest().build()
        }

    val start = fromDate?.let { LocalDate.parse(it) } ?: LocalDate.now().minusMonths(3)
    val end = toDate?.let { LocalDate.parse(it) } ?: LocalDate.now()
    logger.info("ETF stats date range: $start to $end")

    val stats = etfStatsService.getEtfStats(etf, start, end, refresh)
    logger.info("ETF stats retrieved successfully for: $symbol")
    return ResponseEntity.ok(stats)
  }
}
