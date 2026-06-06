package com.skrymer.udgaard.backtesting.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Probabilistic / Deflated Sharpe Ratio (Bailey–López de Prado) — the multiple-testing
 * readout behind the firewall-stage Deflated-Sharpe flag (ADR 0014).
 *
 * All Sharpe inputs are PER-OBSERVATION (non-annualized). The caller de-annualizes an
 * annualized Sharpe by √(periodsPerYear) and its variance by periodsPerYear before calling —
 * the formulae are frequency-dependent (the skew/kurtosis adjustment is not scale-invariant).
 */
class RiskMetricsServiceDsrTest {
  private val service = RiskMetricsService()

  @Test
  fun `probabilistic Sharpe of a normal series against a zero threshold`() {
    // Given a per-observation Sharpe of 0.1 over 101 observations, Gaussian returns
    // (skew 0, kurtosis 3), benchmarked against SR* = 0
    // Then PSR = Φ( (0.1 - 0) * √100 / √(1 + ((3-1)/4) * 0.1²) ) = Φ(0.99751) ≈ 0.8407
    val psr = service.probabilisticSharpe(
      observedSharpe = 0.1,
      benchmarkSharpe = 0.0,
      skew = 0.0,
      kurtosis = 3.0,
      nObs = 101,
    )

    assertEquals(0.8407, psr, 0.001)
  }

  @Test
  fun `deflated Sharpe with a single trial applies no deflation`() {
    // Given a single firewall trial (nEff = 1), there is no best-of-search inflation to remove
    // Then DSR collapses to the plain PSR against a zero threshold
    val dsr = service.deflatedSharpe(
      observedSharpe = 0.1,
      nEff = 1.0,
      trialSharpeVariance = 0.0004,
      skew = 0.0,
      kurtosis = 3.0,
      nObs = 101,
    )

    val undeflated = service.probabilisticSharpe(0.1, 0.0, 0.0, 3.0, 101)
    assertEquals(undeflated, dsr, 1e-9)
  }

  @Test
  fun `deflated Sharpe decreases monotonically as the trial count grows`() {
    // Given the same survivor Sharpe and cross-trial variance, evaluated at growing trial counts
    // Then more searched trials means a higher expected-max null, so the DSR can only fall
    val dsrs = listOf(2.0, 5.0, 20.0, 100.0).map { n ->
      service.deflatedSharpe(
        observedSharpe = 0.2,
        nEff = n,
        trialSharpeVariance = 0.0025,
        skew = 0.0,
        kurtosis = 3.0,
        nObs = 1000,
      )
    }

    dsrs.zipWithNext { higher, lower -> assertTrue(lower < higher, "DSR should fall as N grows: $dsrs") }
  }

  @Test
  fun `negative skew lowers the probabilistic Sharpe`() {
    // Given the same Sharpe estimate, once Gaussian and once with a left-skewed return tail
    val gaussian = service.probabilisticSharpe(0.15, 0.0, skew = 0.0, kurtosis = 3.0, nObs = 500)
    val leftSkewed = service.probabilisticSharpe(0.15, 0.0, skew = -1.0, kurtosis = 3.0, nObs = 500)

    // Then negative skew inflates the Sharpe's estimation error, so confidence drops
    assertTrue(leftSkewed < gaussian, "negative skew should lower PSR: $leftSkewed vs $gaussian")
  }

  @Test
  fun `excess kurtosis lowers the probabilistic Sharpe`() {
    // Given the same Sharpe estimate, once Gaussian and once with fat tails (kurtosis > 3)
    val gaussian = service.probabilisticSharpe(0.15, 0.0, skew = 0.0, kurtosis = 3.0, nObs = 500)
    val fatTailed = service.probabilisticSharpe(0.15, 0.0, skew = 0.0, kurtosis = 9.0, nObs = 500)

    // Then heavier tails inflate the Sharpe's estimation error, so confidence drops
    assertTrue(fatTailed < gaussian, "excess kurtosis should lower PSR: $fatTailed vs $gaussian")
  }

  @Test
  fun `zero cross-trial variance applies no deflation regardless of trial count`() {
    // Given many trials but no spread among their Sharpes, the expected-max null collapses to 0
    val dsr = service.deflatedSharpe(
      observedSharpe = 0.1,
      nEff = 50.0,
      trialSharpeVariance = 0.0,
      skew = 0.0,
      kurtosis = 3.0,
      nObs = 101,
    )

    // Then DSR collapses to the undeflated PSR against a zero threshold
    val undeflated = service.probabilisticSharpe(0.1, 0.0, 0.0, 3.0, 101)
    assertEquals(undeflated, dsr, 1e-9)
  }

  @Test
  fun `expected-max null is never negative for a fractional trial count below two`() {
    // Given a fractional effective-N between 1 and 2 (a correlation-haircut endpoint), where the
    // Bailey-LdP E[max] approximation would otherwise dip negative
    listOf(1.2, 1.5, 1.9).forEach { n ->
      // Then the benchmark is floored at 0 — a deflation null must never CREDIT the candidate
      val benchmark = service.expectedMaxSharpe(nEff = n, trialSharpeVariance = 0.0025)
      assertTrue(benchmark >= 0.0, "E[max] must be >= 0 at nEff=$n, got $benchmark")
    }

    // And the DSR there cannot exceed the undeflated PSR (deflation only ever lowers confidence)
    val dsr = service.deflatedSharpe(0.1, 1.5, 0.0025, 0.0, 3.0, 101)
    val undeflated = service.probabilisticSharpe(0.1, 0.0, 0.0, 3.0, 101)
    assertTrue(dsr <= undeflated + 1e-12, "DSR $dsr should not exceed undeflated PSR $undeflated")
  }
}
