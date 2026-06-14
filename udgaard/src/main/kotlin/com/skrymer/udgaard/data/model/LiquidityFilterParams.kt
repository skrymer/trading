package com.skrymer.udgaard.data.model

/**
 * Frozen, pre-registered thresholds of the tradable-universe liquidity filter (ADR 0026). Chosen from
 * real-world rationale (marginability/penny floor, retail-book fill scale, warmup), never tuned to a
 * backtest result — one canonical parameterisation, not a per-config knob. Changing a value is a
 * methodology change (a new "universe epoch"), not a config setting.
 *
 * @param minClose close at or above which the price floor passes (marginability / penny floor).
 * @param minMedianDollarVolume trailing-[lookbackBars] median dollar-volume (close x volume) at or
 *   above which the liquidity floor passes — the retail-book fill scale.
 * @param lookbackBars trailing bars over which the median dollar-volume is taken.
 * @param minBars bars of history a name must have as of the decision bar before it is tradable.
 */
data class LiquidityFilterParams(
  val minClose: Double = 5.0,
  val minMedianDollarVolume: Double = 1_000_000.0,
  val lookbackBars: Int = 20,
  val minBars: Int = 252,
) {
  companion object {
    /** The canonical frozen parameterisation (ADR 0026). */
    val FROZEN = LiquidityFilterParams()

    /** Documented sensitivity-stress / future-growth variant — used only for A/B robustness, never tuned. */
    val STRESS_5M = FROZEN.copy(minMedianDollarVolume = 5_000_000.0)
  }
}
