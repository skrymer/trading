package com.skrymer.udgaard.backtesting.controller

import com.skrymer.udgaard.backtesting.dto.DeflatedSharpeRequest
import com.skrymer.udgaard.backtesting.service.RiskMetricsService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class RiskControllerTest {
  private val service = RiskMetricsService()
  private val controller = RiskController(service)

  @Test
  fun `deflated-sharpe endpoint returns the engine DSR for a valid request`() {
    // Given a firewall survivor's Sharpe and a multi-trial search context
    val request = DeflatedSharpeRequest(
      observedSharpe = 0.2,
      nEff = 8.0,
      trialSharpeVariance = 0.0025,
      skew = 0.0,
      kurtosis = 3.0,
      nObs = 1000,
    )

    // When the endpoint is called
    val response = controller.deflatedSharpe(request)

    // Then it echoes the engine's DSR (deflated against the expected-max-over-8-trials null)
    val body = requireNotNull(response.body)
    val expected = service.deflatedSharpe(0.2, 8.0, 0.0025, 0.0, 3.0, 1000)
    assertEquals(expected, body.deflatedSharpe, 1e-9)
  }

  @Test
  fun `deflated-sharpe endpoint rejects a request with too few observations`() {
    // Given a Sharpe estimate backed by a single observation (no estimation error is defined)
    val request = DeflatedSharpeRequest(
      observedSharpe = 0.2,
      nEff = 8.0,
      trialSharpeVariance = 0.0025,
      skew = 0.0,
      kurtosis = 3.0,
      nObs = 1,
    )

    // When the endpoint is called, Then it is rejected with 400 rather than throwing
    val response = controller.deflatedSharpe(request)
    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
  }

  @Test
  fun `deflated-sharpe endpoint rejects a negative cross-trial variance`() {
    // Given a negative trial-Sharpe variance (impossible by definition — a caller bug)
    val request = DeflatedSharpeRequest(0.2, 8.0, trialSharpeVariance = -0.01, nObs = 1000)

    // When called, Then 400 rather than a NaN body (sqrt of a negative)
    assertEquals(HttpStatus.BAD_REQUEST, controller.deflatedSharpe(request).statusCode)
  }

  @Test
  fun `deflated-sharpe endpoint rejects a non-finite observed Sharpe`() {
    // Given a non-finite Sharpe (Jackson accepts NaN/Infinity tokens on input)
    val request = DeflatedSharpeRequest(Double.NaN, 8.0, 0.0025, nObs = 1000)

    // When called, Then 400 rather than emitting an invalid-JSON NaN response token
    assertEquals(HttpStatus.BAD_REQUEST, controller.deflatedSharpe(request).statusCode)
  }

  @Test
  fun `deflated-sharpe endpoint rejects a degenerate variance term from extreme skew`() {
    // Given skew so extreme the Sharpe-estimator variance term goes non-positive
    val request = DeflatedSharpeRequest(0.5, 8.0, 0.0025, skew = 6.0, nObs = 1000)

    // When called, Then 400 rather than a NaN body (sqrt of a non-positive variance term)
    assertEquals(HttpStatus.BAD_REQUEST, controller.deflatedSharpe(request).statusCode)
  }
}
