package com.skrymer.udgaard.backtesting.model

/**
 * Pre-registered, frozen parameters of the 5-label regime read-out (ADR 0023). These values are
 * fixed by design (not tuned to any backtest result) — one canonical parameterisation, computed
 * once, never per config. Changing any value is a methodology change, not a config knob.
 *
 * @param breadthHighBand breadth EMA10 level at or above which participation reads HIGH.
 * @param breadthWeakBand breadth EMA10 level at or below which participation reads WEAK.
 * @param slopeBars trailing bars over which the breadth-level slope is taken.
 * @param slopeBand breadth-level points over [slopeBars] beyond which slope reads RISING/FALLING.
 * @param lookbackBars trailing simple-return horizon shared by the gap legs, realized vol, and direction.
 * @param emaPeriod smoothing applied to the raw gap before the stateless three-way cut.
 * @param gapDeadBand half-width of the gap's NEUTRAL zone: NEG at or below -band, POS at or above +band.
 * @param volLowBand annualized 20-bar realized vol at or below which vol reads LOW.
 * @param volHighBand annualized 20-bar realized vol at or above which vol reads HIGH.
 * @param directionDeadBand SPY [lookbackBars]-bar return magnitude below which direction reads FLAT.
 * @param dwellDays consecutive raw days a new label must persist before the published label switches
 *   (entry into CRISIS publishes immediately; exit from CRISIS honors the dwell).
 * @param washoutThreshold breadth bull-% at or below which a reading counts toward a crisis washout.
 * @param washoutConsecutiveDays consecutive sub-threshold breadth readings that constitute a washout.
 * @param washoutLookbackDays trailing breadth readings scanned for the sustained washout.
 * @param minTrustworthyN contributing names below which the gap read fails closed (unlabeled).
 * @param maxTrustworthyStandardError standard-error ceiling (of the equal-weight mean) for a trustworthy read.
 */
data class RegimeReadoutParams(
  val breadthHighBand: Double = 50.0,
  val breadthWeakBand: Double = 35.0,
  val slopeBars: Int = 5,
  val slopeBand: Double = 3.0,
  val lookbackBars: Int = 20,
  val emaPeriod: Int = 10,
  val gapDeadBand: Double = 0.005,
  val volLowBand: Double = 0.12,
  val volHighBand: Double = 0.22,
  val directionDeadBand: Double = 0.02,
  val dwellDays: Int = 5,
  val washoutThreshold: Double = 15.0,
  val washoutConsecutiveDays: Int = 10,
  val washoutLookbackDays: Int = 40,
  val minTrustworthyN: Int = 200,
  val maxTrustworthyStandardError: Double = 0.005,
) {
  companion object {
    /** The quant-signed pre-registration v1 (ADR 0023). */
    val FROZEN = RegimeReadoutParams()
  }
}
