package com.skrymer.udgaard.backtesting.model

import java.time.LocalDate

/**
 * One trading day's leadership-gap regime read. [regimeOn] is the gate the strategy consults
 * (`true` = deploy, `false` = stay in cash); the remaining fields are observability that explain
 * the read and flag when it should not be trusted ([trustworthy] = thin/noisy cross-section).
 *
 * @param gap raw SPY 20-bar return minus the equal-weight universe mean 20-bar return (negative = broad thrust).
 * @param gapSmoothed EMA of [gap] feeding the Schmitt trigger.
 * @param schmittOn the hysteresis state before the crisis veto is applied.
 * @param washoutActive whether a sustained breadth washout vetoes deployment on this bar.
 * @param regimeOn final gate: [schmittOn] AND NOT [washoutActive].
 * @param contributingN names contributing to the equal-weight mean on this bar.
 * @param standardError standard error of the equal-weight mean (cross-sectional stdev / sqrt N).
 * @param trustworthy false when the read rests on too few names or too wide a standard error.
 */
data class LeadershipRegimeDaily(
  val quoteDate: LocalDate,
  val gap: Double,
  val gapSmoothed: Double,
  val schmittOn: Boolean,
  val washoutActive: Boolean,
  val regimeOn: Boolean,
  val contributingN: Int,
  val standardError: Double,
  val trustworthy: Boolean,
)
