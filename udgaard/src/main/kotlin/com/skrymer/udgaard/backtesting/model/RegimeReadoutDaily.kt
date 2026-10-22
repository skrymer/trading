package com.skrymer.udgaard.backtesting.model

import java.time.LocalDate

/**
 * One trading day's regime read. [rawLabel] is the instantaneous decision-table result;
 * [publishedLabel] is what an operator consulting the read-out live would have seen (raw debounced
 * by the dwell). Both are null on a day with no defensible read (fail-closed unlabeled).
 * [axes] carries the raw readings the label was decided on — observability for the
 * current-regime line and the spec-calibration diagnostics, never an input to anything.
 */
data class RegimeReadoutDaily(
  val quoteDate: LocalDate,
  val rawLabel: RegimeLabel?,
  val publishedLabel: RegimeLabel?,
  val axes: RegimeAxes? = null,
)

/**
 * The raw per-day axis readings behind a regime read. Nullable fields are undefined on the day
 * (e.g. no breadth row, vol window unseeded) — exposed as-is rather than defaulted, so a
 * diagnostic can tell "zero" from "absent".
 */
data class RegimeAxes(
  val breadthLevel: Double?,
  val breadthSlope: Double?,
  val gapSmoothed: Double?,
  val gapStandardError: Double?,
  val gapContributingN: Int?,
  val gapTrustworthy: Boolean?,
  val ewMedianReturn: Double?,
  val ewIqr: Double?,
  val realizedVol: Double?,
  val direction: Double?,
  val washoutActive: Boolean,
  val drawdownFrom252High: Double?,
  // The v3 trend-efficiency leg (ADR 0028) — null on the v2 production read-out, populated only on
  // the v3 candidate series. [choppinessIndex30] drives the GRIND-demotion overlay; [efficiencyRatio30]
  // (Kaufman ER) is advisory observability only, never an input to a label.
  val choppinessIndex30: Double? = null,
  val efficiencyRatio30: Double? = null,
)
