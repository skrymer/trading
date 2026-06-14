package com.skrymer.udgaard.backtesting.dto

import java.time.LocalDate

/**
 * Request for the diagnostic condition screen (`POST /api/conditions/screen`).
 *
 * Screens an entry condition stack (incl. inline `script`) for forward-return lift, firing
 * behaviour, parameter sensitivity (ARS), and overlap with reference conditions — before the
 * condition is wired into a strategy. Diagnostic only: produces no verdict.
 *
 * `endDate` is hard-capped at Block C's start (2021-01-01) so screening cannot leak the firewall's
 * only true out-of-sample block (ADR 0007). `startDate` may be moved earlier freely.
 */
data class ConditionScreenRequest(
  val conditions: List<ConditionConfig>,
  val operator: String = "AND",
  val symbols: List<String>? = null,
  val assetTypes: List<String> = listOf("STOCK"),
  val startDate: LocalDate? = null,
  val endDate: LocalDate? = null,
  val entryDelayDays: Int = 1,
  val horizons: List<Int> = listOf(5, 10, 20),
  val scriptSweeps: List<ScriptSweepSpec> = emptyList(),
  val referenceConditions: List<ReferenceConditionSpec> = emptyList(),
  // Restrict the screen (firings AND the all-bars baseline) to the tradable universe (point-in-time
  // price/liquidity/age, ADR 0026). Default ON; false reproduces the pre-#173 full-universe screen.
  val applyLiquidityFilter: Boolean = true,
)

/**
 * A sweep declaration for a tunable that lives inside an inline `script` body. The script must
 * contain the placeholder `{{name}}`; the screen substitutes `center - step`, `center`, and
 * `center + step` and recompiles each to produce the ARS sensitivity cells. Registered (non-script)
 * conditions are swept automatically from their parameter metadata and need no declaration here.
 */
data class ScriptSweepSpec(
  val name: String,
  val center: Double,
  val step: Double,
)

/** A named reference condition stack to measure firing overlap (Jaccard) against. */
data class ReferenceConditionSpec(
  val label: String,
  val conditions: List<ConditionConfig>,
  val operator: String = "AND",
)

/** The full diagnostic report. Carries raw statistics only — flag thresholds are the analyst's. */
data class ConditionScreenReport(
  val diagnosticNotice: String,
  val window: ScreenWindow,
  val universe: ScreenUniverse,
  val firing: FiringReport,
  val forwardReturns: List<HorizonReport>,
  val signalToFillGap: GapReport,
  val spyRegime: List<RegimeHorizonReport>,
  val parameterSweep: List<SweptParameter>,
  val jaccard: List<ReferenceOverlapReport>,
  val notes: List<String>,
)

data class ScreenWindow(
  val startDate: LocalDate,
  val endDate: LocalDate,
  val entryDelayDays: Int,
)

data class ScreenUniverse(
  val symbolCount: Int,
  val totalEligibleBars: Int,
)

data class FiringReport(
  val totalSignals: Int,
  val overallFiringRate: Double,
  val byYear: List<FiringYearReport>,
)

data class FiringYearReport(
  val year: Int,
  val signals: Int,
  val eligibleBars: Int,
  val firingRate: Double,
)

data class HorizonReport(
  val horizonDays: Int,
  val condition: SummaryReport,
  val universe: SummaryReport,
  val meanLift: Double,
  val hitRateLift: Double,
)

data class SummaryReport(
  val signalCount: Int,
  val dateCount: Int,
  val droppedCount: Int,
  val mean: Double,
  val median: Double,
  val std: Double,
  val skew: Double,
  val hitRate: Double,
  val clusteredMean: Double,
  val clusteredStdError: Double,
)

data class GapReport(
  val meanGap: Double,
  val medianGap: Double,
  val positiveRate: Double,
  val n: Int,
)

data class RegimeHorizonReport(
  val horizonDays: Int,
  val down: RegimeBucketReport,
  val flat: RegimeBucketReport,
  val up: RegimeBucketReport,
)

data class RegimeBucketReport(
  val firingRate: Double,
  val meanLift: Double?,
  val nSignals: Int,
)

data class SweptParameter(
  val source: String,
  val parameterName: String,
  val centerValue: Double,
  val cells: List<SweepCell>,
)

data class SweepCell(
  val parameterValue: Double,
  val isCenter: Boolean,
  val relativeStep: Double,
  val firingRate: Double,
  val liftByHorizon: Map<Int, Double>,
  val clusteredStdErrorByHorizon: Map<Int, Double>,
)

data class ReferenceOverlapReport(
  val label: String,
  val byYear: Map<Int, Double>,
  val pooled: Double?,
)
