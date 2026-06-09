package com.skrymer.udgaard.backtesting.model

/**
 * Pre-registered, frozen parameters of the leadership-gap regime gate. These values are fixed by
 * design (not tuned to any backtest result) — the regime is computed once at setup, never per
 * config, so a strategy cannot override them. Changing any value is a methodology change, not a
 * config knob.
 *
 * @param lookbackBars trailing simple-return horizon for both the SPY and equal-weight legs (bars).
 * @param emaPeriod smoothing applied to the raw gap before the Schmitt trigger.
 * @param deadBand Schmitt half-width: ON when smoothed gap < -deadBand, OFF when > +deadBand, else hold.
 * @param washoutThreshold breadth bull-% at or below which a reading counts toward a crisis washout.
 * @param washoutConsecutiveDays consecutive sub-threshold breadth readings that constitute a washout.
 * @param washoutLookbackDays trailing breadth readings scanned for the sustained-washout veto.
 * @param minTrustworthyN contributing names below which a regime read is flagged untrustworthy.
 * @param maxTrustworthyStandardError standard-error ceiling (of the equal-weight mean) for a trustworthy read.
 */
data class LeadershipRegimeParams(
  val lookbackBars: Int = 20,
  val emaPeriod: Int = 10,
  val deadBand: Double = 0.005,
  val washoutThreshold: Double = 15.0,
  val washoutConsecutiveDays: Int = 10,
  val washoutLookbackDays: Int = 40,
  val minTrustworthyN: Int = 200,
  val maxTrustworthyStandardError: Double = 0.005,
) {
  companion object {
    /** The frozen, pre-registered gate spec (issue #83). */
    val FROZEN = LeadershipRegimeParams()
  }
}
