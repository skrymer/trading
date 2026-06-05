package com.skrymer.udgaard.backtesting.model

/**
 * Strategy performance vs a benchmark (currently SPY). Field names are benchmark-agnostic;
 * the actual benchmark symbol travels in `benchmarkSymbol` so future non-SPY benchmarks
 * don't require a rename.
 *
 * `activeReturnVsBenchmark` is r_strategy_ann − β · r_benchmark_ann. This is **active return**
 * (excess of beta-implied return), NOT Jensen's alpha — Jensen's α requires subtracting RF
 * from both legs and is intentionally not computed here. Documented mismatch to avoid the
 * literature-comparison trap.
 *
 * `benchmarkCagr` / `benchmarkMaxDrawdownPct` / `benchmarkCalmar` / `benchmarkSharpe` are the
 * benchmark's OWN standalone risk-adjusted metrics over the overlap support — the diagnostic
 * leg of the SPY buy-and-hold baseline gate (ADR 0013). They describe "what holding the
 * benchmark alone would have done" on the same days, independent of the strategy.
 *
 * All metric fields are nullable: `null` when overlap with benchmark is below the
 * statistical-significance floor (60 days) or benchmark has zero variance.
 */
data class BenchmarkComparison(
  val benchmarkSymbol: String,
  val correlation: Double?,
  val beta: Double?,
  val activeReturnVsBenchmark: Double?,
  val benchmarkCagr: Double? = null,
  val benchmarkMaxDrawdownPct: Double? = null,
  val benchmarkCalmar: Double? = null,
  val benchmarkSharpe: Double? = null,
)
