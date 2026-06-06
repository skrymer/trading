package com.skrymer.udgaard.backtesting.controller

import com.skrymer.udgaard.backtesting.dto.DeflatedSharpeRequest
import com.skrymer.udgaard.backtesting.dto.DeflatedSharpeResponse
import com.skrymer.udgaard.backtesting.service.RiskMetricsService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Thin wrapper over the engine's Deflated / Probabilistic Sharpe functions (ADR 0014). It performs
 * no search bookkeeping — the multiple-testing context (`nEff`, `trialSharpeVariance`) arrives in
 * the request; the state-machine that owns the trial register is the only caller.
 *
 * Invalid inputs (fewer than 2 observations) return 400.
 */
@RestController
@RequestMapping("/api/risk")
class RiskController(
  private val riskMetricsService: RiskMetricsService,
) {
  private val logger = LoggerFactory.getLogger(RiskController::class.java)

  @PostMapping("/deflated-sharpe")
  fun deflatedSharpe(
    @RequestBody request: DeflatedSharpeRequest,
  ): ResponseEntity<DeflatedSharpeResponse> = try {
    val dsr = riskMetricsService.deflatedSharpe(
      observedSharpe = request.observedSharpe,
      nEff = request.nEff,
      trialSharpeVariance = request.trialSharpeVariance,
      skew = request.skew,
      kurtosis = request.kurtosis,
      nObs = request.nObs,
    )
    val expectedMax = riskMetricsService.expectedMaxSharpe(request.nEff, request.trialSharpeVariance)
    val psr = riskMetricsService.probabilisticSharpe(
      observedSharpe = request.observedSharpe,
      benchmarkSharpe = 0.0,
      skew = request.skew,
      kurtosis = request.kurtosis,
      nObs = request.nObs,
    )
    ResponseEntity.ok(
      DeflatedSharpeResponse(
        deflatedSharpe = dsr,
        probabilisticSharpe = psr,
        expectedMaxSharpe = expectedMax,
        nEff = request.nEff,
      ),
    )
  } catch (e: IllegalArgumentException) {
    logger.warn("Rejected deflated-sharpe request: ${e.message}")
    ResponseEntity.badRequest().build()
  }
}
