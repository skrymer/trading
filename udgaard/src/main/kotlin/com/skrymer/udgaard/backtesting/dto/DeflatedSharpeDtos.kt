package com.skrymer.udgaard.backtesting.dto

/**
 * Inputs to the Deflated-Sharpe endpoint (ADR 0014). Search-agnostic: [nEff] and
 * [trialSharpeVariance] describe the multiple-testing context and are supplied by the caller
 * (the state-machine), never inferred by the engine.
 *
 * All Sharpe inputs are PER-OBSERVATION (non-annualized) — de-annualize an annualized Sharpe by
 * √periodsPerYear and its variance by periodsPerYear before calling.
 *
 * @param observedSharpe the candidate's own per-observation Sharpe (from its firewall run)
 * @param nEff effective trial count; may be fractional. `≤ 1` ⇒ no deflation
 * @param trialSharpeVariance cross-trial variance of the per-observation trial Sharpes
 * @param skew skewness of the candidate's returns (default 0 = symmetric)
 * @param kurtosis non-excess kurtosis of the candidate's returns (default 3 = Gaussian)
 * @param nObs number of return observations behind the Sharpe estimate (≥ 2)
 */
data class DeflatedSharpeRequest(
  val observedSharpe: Double,
  val nEff: Double,
  val trialSharpeVariance: Double,
  val skew: Double = 0.0,
  val kurtosis: Double = 3.0,
  val nObs: Int,
)

/**
 * Deflated-Sharpe result. [deflatedSharpe] is the probability the true Sharpe beats the
 * expected-max-over-`nEff`-trials null; the firewall flag is AMBER when it drops below 0.95.
 * [probabilisticSharpe] is the undeflated reference (vs a zero threshold) and [expectedMaxSharpe]
 * is the null benchmark that was subtracted — both published for transparency.
 */
data class DeflatedSharpeResponse(
  val deflatedSharpe: Double,
  val probabilisticSharpe: Double,
  val expectedMaxSharpe: Double,
  val nEff: Double,
)
