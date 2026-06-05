package com.skrymer.udgaard.backtesting.model

/**
 * The SPY buy-and-hold Calmar baseline gate verdict for a walk-forward run (ADR 0013).
 *
 * - PASS: the strategy's stitched-OOS Calmar is at least the benchmark's over the identical
 *   OOS support — the candidate beats just holding the index, risk-adjusted.
 * - FAIL: the strategy's stitched-OOS Calmar is below the benchmark's — its return is index beta.
 * - INCONCLUSIVE: the block does not bind (and never auto-fails). Fired when the stitched OOS
 *   series is too short (< 60 trading days) or the strategy's stitched maxDD is trivially small
 *   (a tiny denominator manufactures an explosive Calmar that would falsely "beat" SPY).
 */
enum class SpyBaselineVerdict { PASS, FAIL, INCONCLUSIVE }

/**
 * Strategy-vs-SPY Calmar comparison for a single walk-forward run, computed over the
 * stitched-OOS support (per ADR 0013 / ADR 0005). The engine emits one verdict per run; whether
 * that run binds (Block A / Block B / 25y aggregate) or is informational (Block C) is the
 * caller's framing — the engine does not know which block a date range represents.
 *
 * `benchmark*` fields are the SPY leg's stitched-OOS metrics. `strategyCalmar` is repeated here
 * (it also lives on `aggregateOosRiskMetrics`) so the comparison is self-contained.
 * `inconclusiveReason` is null unless `verdict == INCONCLUSIVE`.
 */
data class SpyBaselineComparison(
  val verdict: SpyBaselineVerdict,
  val strategyCalmar: Double?,
  val benchmarkCalmar: Double?,
  val benchmarkCagr: Double?,
  val benchmarkMaxDrawdownPct: Double?,
  val benchmarkSharpe: Double?,
  val inconclusiveReason: String? = null,
)
