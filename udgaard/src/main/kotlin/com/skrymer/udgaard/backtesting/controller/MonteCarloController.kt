package com.skrymer.udgaard.backtesting.controller

import com.skrymer.udgaard.backtesting.dto.MonteCarloRequestDto
import com.skrymer.udgaard.backtesting.model.BacktestReport
import com.skrymer.udgaard.backtesting.model.MonteCarloRequest
import com.skrymer.udgaard.backtesting.model.MonteCarloResult
import com.skrymer.udgaard.backtesting.service.BacktestResultStore
import com.skrymer.udgaard.backtesting.service.MonteCarloService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for Monte Carlo simulation operations.
 *
 * Handles running Monte Carlo simulations on backtest results to validate strategy edge
 * and understand probability distributions of outcomes.
 */
@RestController
@RequestMapping("/api/monte-carlo")
@CrossOrigin(origins = ["http://localhost:3000", "http://localhost:8080"])
class MonteCarloController(
  private val monteCarloService: MonteCarloService,
  private val backtestResultStore: BacktestResultStore,
) {
  /**
   * Run Monte Carlo simulation on a cached backtest result.
   *
   * Example: POST /api/monte-carlo/simulate with JSON body containing backtestId
   */
  @PostMapping("/simulate")
  fun runSimulation(
    @RequestBody requestDto: MonteCarloRequestDto,
  ): ResponseEntity<MonteCarloResult> {
    logger.info(
      "Running Monte Carlo simulation: backtestId=${requestDto.backtestId}, technique=${requestDto.technique}, " +
        "iterations=${requestDto.iterations}, includeAllCurves=${requestDto.includeAllEquityCurves}",
    )

    try {
      // Validate request
      if (requestDto.iterations < 100) {
        logger.warn("Iterations too low: ${requestDto.iterations}")
        return ResponseEntity.badRequest().build()
      }

      if (requestDto.iterations > 100000) {
        logger.warn("Iterations too high: ${requestDto.iterations}")
        return ResponseEntity.badRequest().build()
      }

      // Retrieve cached backtest result
      val cachedReport = backtestResultStore.get(requestDto.backtestId)
      if (cachedReport == null) {
        logger.warn("Backtest result not found: ${requestDto.backtestId}")
        return ResponseEntity.notFound().build()
      }

      if (cachedReport.trades.isEmpty()) {
        logger.warn("No trades in cached backtest result: ${requestDto.backtestId}")
        return ResponseEntity.badRequest().build()
      }

      // Construct BacktestReport for Monte Carlo (use the cached one directly)
      val backtestReport =
        BacktestReport(
          winningTrades = cachedReport.winningTrades,
          losingTrades = cachedReport.losingTrades,
        )

      // Convert DTO to request
      val request =
        MonteCarloRequest(
          backtestResult = backtestReport,
          techniqueType = requestDto.technique,
          iterations = requestDto.iterations,
          seed = requestDto.seed,
          includeAllEquityCurves = requestDto.includeAllEquityCurves,
          positionSizing = requestDto.positionSizing,
        )

      logger.info("Starting Monte Carlo simulation...")
      val result = monteCarloService.runSimulation(request)

      logger.info(
        "Monte Carlo simulation complete: " +
          "technique=${result.technique}, " +
          "iterations=${result.iterations}, " +
          "executionTime=${result.executionTimeMs}ms, " +
          "meanReturn=${String.format("%.2f", result.statistics.meanReturnPercentage)}%, " +
          "probabilityOfProfit=${String.format("%.2f", result.statistics.probabilityOfProfit)}%",
      )

      return ResponseEntity.ok(result)
    } catch (e: IllegalArgumentException) {
      logger.error("Monte Carlo simulation validation failed: ${e.message}", e)
      return ResponseEntity.badRequest().build()
    } catch (e: IllegalStateException) {
      logger.error("Monte Carlo simulation state error: ${e.message}", e)
      return ResponseEntity.badRequest().build()
    } catch (e: Exception) {
      logger.error("Unexpected error during Monte Carlo simulation: ${e.message}", e)
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
    }
  }

  companion object {
    private val logger: Logger = LoggerFactory.getLogger(MonteCarloController::class.java)
  }
}
