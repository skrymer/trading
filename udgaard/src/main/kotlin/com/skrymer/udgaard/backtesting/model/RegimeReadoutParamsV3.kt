package com.skrymer.udgaard.backtesting.model

/**
 * Pre-registered, frozen parameters of the v3 trend-efficiency overlay (ADR 0028). These tune the
 * *only* thing v3 adds over the frozen-and-live v2 classifier ([RegimeReadoutParams]): a multi-week
 * SPY Choppiness-Index read that demotes whole low-efficiency v2-GRIND spells to CHOP. The v2
 * backbone runs untouched; these values never feed it. Frozen 2026-06-16 before any coverage was
 * computed — changing any value is a new methodology pre-registration, not a config knob.
 *
 * @param efficiencyWindowBars trailing SPY bars over which the Choppiness Index is taken — the
 *   midpoint of the research-cited 20-60d multi-week band, deliberately distinct from the backbone's
 *   [RegimeReadoutParams.lookbackBars] so the axis stays decoupled. N=14 (the index's usual default)
 *   is ruled out as below the multi-week floor at which daily efficiency stops being white noise.
 * @param choppinessEfficientBand Choppiness at or below which the tape reads trend-EFFICIENT
 *   (GRIND-eligible) — the literature-fixed 38.2, never tuned. A reading in the indeterminate band
 *   above this (and a null reading) counts as NOT efficient (fail-closed): efficiency can only
 *   confirm an up-tape GRIND the v2 directional legs already claimed, never source a label.
 * @param choppinessChoppyBand Choppiness at or above which the tape reads choppy — the
 *   literature-fixed 61.8. Surfaced for the report-only band histogram; the overlay rule keys only
 *   off [choppinessEfficientBand] (low-Choppiness is direction-blind, so only the efficient cut
 *   gates a label).
 * @param grindMajorityThreshold fraction of a v2-GRIND spell's days that must read efficient for the
 *   spell to stay GRIND; at or below it the whole spell is relabeled CHOP. A strict majority — ties
 *   demote — so a spell with no efficient edge cannot survive on a coin-flip.
 */
data class RegimeReadoutParamsV3(
  val efficiencyWindowBars: Int = 30,
  val choppinessEfficientBand: Double = 38.2,
  val choppinessChoppyBand: Double = 61.8,
  val grindMajorityThreshold: Double = 0.50,
) {
  companion object {
    /** The quant-signed v3 trend-efficiency pre-registration (ADR 0028). */
    val FROZEN = RegimeReadoutParamsV3()
  }
}

/**
 * Which regime read-out spec a request wants. [V2] is the frozen-and-live production classifier
 * (ADR 0024); [V3] is the provisional trend-efficiency candidate (ADR 0028) — a transitional switch
 * exposed only for validation, removed once the integrated v3 is accepted or rejected (#168).
 */
enum class RegimeReadoutVersion {
  V2,
  V3,
}
