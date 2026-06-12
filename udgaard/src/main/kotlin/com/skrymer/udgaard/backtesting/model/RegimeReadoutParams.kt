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
 * @param gapNegBand smoothed gap at or below which the cut reads NEG — frozen at the p33 of the
 *   clean (median-leg) gap over the design-safe window; the gap is left-skewed, so the bands are
 *   deliberately asymmetric percentile terciles, never a symmetric zone around zero.
 * @param gapPosBand smoothed gap at or above which the cut reads POS — frozen at the p67 of the same
 *   distribution.
 * @param volLowBand annualized 20-bar realized vol at or below which vol reads LOW.
 * @param volHighBand annualized 20-bar realized vol at or above which vol reads HIGH.
 * @param directionDeadBand SPY [lookbackBars]-bar return magnitude below which direction reads FLAT.
 * @param dwellDays consecutive raw days a new label must persist before the published label switches
 *   (entry into washout-CRISIS publishes immediately — it is already a sustained condition; entry
 *   via the drawdown leg, and every exit, honors the dwell).
 * @param ddCrisisThreshold close-basis drawdown from the trailing [ddLookbackBars]-bar high at or
 *   below which a day is CRISIS regardless of the washout — the correlated-decline signature of a
 *   slow bear the velocity-sensing washout structurally misses.
 * @param ddLookbackBars trailing bars defining the drawdown reference high (~one trading year).
 * @param washoutThreshold breadth bull-% at or below which a reading counts toward a crisis washout.
 * @param washoutConsecutiveDays consecutive sub-threshold breadth readings that constitute a washout.
 * @param washoutLookbackDays trailing breadth readings scanned for the sustained washout.
 * @param minTrustworthyN contributing names below which the day's advisory gapTrustworthy flag is
 *   false — thin-N only; a dispersion ceiling proved fail-blind and the median gap leg is
 *   dispersion-robust. The flag never gates a label.
 */
data class RegimeReadoutParams(
  val breadthHighBand: Double = 50.0,
  val breadthWeakBand: Double = 35.0,
  val slopeBars: Int = 5,
  val slopeBand: Double = 3.0,
  val lookbackBars: Int = 20,
  val emaPeriod: Int = 10,
  val gapNegBand: Double = -0.007746,
  val gapPosBand: Double = 0.003167,
  val volLowBand: Double = 0.12,
  val volHighBand: Double = 0.22,
  val directionDeadBand: Double = 0.02,
  val dwellDays: Int = 5,
  val ddCrisisThreshold: Double = -0.20,
  val ddLookbackBars: Int = 252,
  val washoutThreshold: Double = 15.0,
  val washoutConsecutiveDays: Int = 10,
  val washoutLookbackDays: Int = 40,
  val minTrustworthyN: Int = 200,
) {
  companion object {
    /**
     * The quant-signed pre-registration v2 (ADR 0023). The gap bands are the p33/p67 of the clean
     * median-leg gap over the design-safe window (n=3773, read once on 2026-06-12 per the
     * pre-registered derivation rule, then frozen — never re-read on later data).
     */
    val FROZEN = RegimeReadoutParams()
  }
}
