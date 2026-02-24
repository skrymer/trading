package com.skrymer.udgaard.portfolio.controller

import com.skrymer.udgaard.portfolio.model.OptionType
import com.skrymer.udgaard.portfolio.service.OptionPricePoint
import com.skrymer.udgaard.portfolio.service.OptionPriceService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * REST controller for option-related endpoints.
 * Provides historical option pricing data for visualization.
 */
@RestController
@RequestMapping("/api/options")
class OptionController(
  private val optionPriceService: OptionPriceService,
) {
  /**
   * Fetch historical option prices for a date range (for chart display).
   *
   * Example:
   * GET /api/options/historical-prices?symbol=SPY&strike=600
   *     &expiration=2025-12-19&type=CALL&startDate=2025-11-01&endDate=2025-12-04
   *
   * @param symbol Underlying stock symbol (e.g., "SPY")
   * @param strike Strike price
   * @param expiration Expiration date (yyyy-MM-dd format)
   * @param type Option type (CALL or PUT)
   * @param startDate Start of date range (yyyy-MM-dd format)
   * @param endDate End of date range (yyyy-MM-dd format)
   * @return List of option price points for each trading day in the range
   */
  @GetMapping("/historical-prices")
  fun getHistoricalPrices(
    @RequestParam symbol: String,
    @RequestParam strike: Double,
    @RequestParam expiration: String,
    @RequestParam type: String,
    @RequestParam startDate: String,
    @RequestParam endDate: String,
  ): ResponseEntity<List<OptionPricePoint>> {
    logger.info(
      "Fetching historical option prices for $symbol $strike $type " +
        "exp=$expiration from $startDate to $endDate",
    )

    val optionType =
      try {
        OptionType.valueOf(type.uppercase())
      } catch (e: IllegalArgumentException) {
        logger.warn("Invalid option type: $type", e)
        return ResponseEntity.badRequest().build()
      }

    val expirationDate =
      try {
        LocalDate.parse(expiration)
      } catch (e: Exception) {
        logger.warn("Invalid expiration date: $expiration", e)
        return ResponseEntity.badRequest().build()
      }

    val start =
      try {
        LocalDate.parse(startDate)
      } catch (e: Exception) {
        logger.warn("Invalid start date: $startDate", e)
        return ResponseEntity.badRequest().build()
      }

    val end =
      try {
        LocalDate.parse(endDate)
      } catch (e: Exception) {
        logger.warn("Invalid end date: $endDate", e)
        return ResponseEntity.badRequest().build()
      }

    val prices =
      optionPriceService.getHistoricalOptionPrices(
        underlyingSymbol = symbol,
        strike = strike,
        expiration = expirationDate,
        optionType = optionType,
        startDate = start,
        endDate = end,
      )

    logger.info("Returning ${prices.size} price points")
    return ResponseEntity.ok(prices)
  }

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(OptionController::class.java)
  }
}
