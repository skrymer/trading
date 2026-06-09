package com.skrymer.udgaard.backtesting.model

/**
 * Observability summary of a leadership-gap regime series over a window (issue #83 §A4). Lets the
 * R1 diagnostic check regime-switch integrity (flip count, spell lengths — Gate-0) and the named-window
 * cash expectation (per-year deploy fraction — Gate-1) without re-deriving them from the raw series.
 */
data class LeadershipRegimeDiagnostics(
  val onFraction: Double,
  val flipCount: Int,
  val medianOnSpellDays: Double,
  val medianOffSpellDays: Double,
  val onFractionByYear: Map<Int, Double>,
  val untrustworthyDays: Int,
  val minContributingN: Int,
)
