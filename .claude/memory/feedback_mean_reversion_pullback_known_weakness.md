---
name: mean-reversion-pullback-known-weakness
description: "Mean-reversion-on-pullback strategies (long entry on dip-toward-EMA in uptrend) have a known weakness in low-breadth narrow-leadership trending markets like 2024. Per-trade edge inverts when flow rotates away from laggards before mean-reversion fires and leaders' pullbacks stay too shallow. Don't iterate VZ3-shaped variants by adding regime filters — that's IS-fitting to a single OOS window. Structurally different entry premise required."
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 7571f7f6-71bf-476b-8e88-d8d2a44467a6
---

**Rule**: Mean-reversion-on-pullback strategies (e.g. "long entry when price pulls back near an EMA in confirmed uptrend") are **DOUBLY-CONDEMNED** and **deprecated for the current macro regime** (post-2020 narrow-leadership equity tape). Do NOT explore this premise class further. Revisit only if market structure returns to broad-participation bull markets (measurable via market breadth / advance-decline trend).

**Why** (two-strike Bayesian update per quant 11th consultation 2026-05-29):

**Strike 1 — Regime-weak in narrow-leadership tape** (2026-05-28 finding):
- Quant analysis on VZ3-s3 (pullback-to-EMA20 in marketUptrend gate): passed Block A (36% CAGR, +0.62% per-trade edge) and Block B-corrected (36% CAGR, +0.48% edge), but **per-trade edge inverted sign to −0.11% in Block C** (2024 only).
- Mechanism: narrow-leadership / Mag-7-concentrated tape breaks the pullback premise. Flow rotates away from laggards before mean-reversion can fire (the laggards just keep drifting). Leaders' pullbacks are too shallow to touch the entry EMA. The strategy is left buying dips that don't bounce.
- The sign-flip (~2.5-3σ on 245 trades, combined with Sharpe collapsing 2.32 → 0.62) is diagnostic, not sample noise.
- Sister candidate MR3 (3-day-decline-in-uptrend) was REJECTED on Block A with three correlated tight failures (G3+G4+G5) — different failure mode, same search area.

**Strike 2 — Parameter-brittle at the condition-design level** (2026-05-29 finding):
- Idunn (promoted VZ3-s3) brittleness sweep on lookbackDays ∈ {8, 9, 10, 11}: pass/fail across the parameter neighborhood is non-monotone ({8: PASS, 9: FAIL G6, 10: FAIL G1+G5+G7, 11: PASS}). Per-window edges flip sign at regime gates (G6 2020, G7 2018-Q4) under 1-day shifts. Classic Aliased Regime Sensitivity (ARS).
- Per quant: "today's low vs N-bars-ago low" is **structurally an anti-pattern** for noisy regime-variant data. Point estimates of support amplify noise. The pullback-detection sub-condition itself can't be made parameter-robust within this design.

**Two independent failure modes pointing the same direction** (regime gate failure AND parameter dimension failure) = the alpha hypothesis is wrong for current market structure, not the encoding.

**How to apply**:
- **Do not iterate.** Mean-reversion-on-pullback (VZ3, MR3, DV1, Idunn, and any conceptual cousin) is deprecated for the current macro regime. Stop exploring.
- When a mean-reversion-on-pullback candidate fails Block C in a narrow-leadership regime, DO NOT propose adding a breadth-divergence / sector-rotation / leadership-concentration filter and re-testing on the same Block C. That's IS-fitting to the single OOS window the firewall gives you.
- DO NOT propose redesigning the pullback-detection sub-condition (multi-bar minimum, percentile, EMA-support proxy, etc.). The premise class is the problem, not the encoding (Strike 2 finding).
- Move to a different premise class entirely: momentum-persistence with broad-leadership filter, volatility-contraction (already have VCP), earnings-drift, post-event reversion, or structural patterns we haven't explored yet.
- **Exception (narrow)**: a strategy that **explicitly incorporates the narrow-leadership regime into its entry logic** (e.g. requires leadership concentration above a threshold AND pulls back) is a structurally different premise and would re-enter the firewall via `/strategy-screen` fairly. But don't treat this as the default escape hatch — most "with regime filter" variants will be IS-fitting.
- **Revisit trigger**: market structure returns to broad-participation bull markets (objective measure: market breadth EMA10 > 60% sustained for 6+ months OR advance-decline line in new highs sustained). Until then, parking this premise class.

Related: [[aliased-regime-sensitivity]], [[parameter-fragility-must-be-verified]], [[use-strategy-screen-for-scanning]], [[never-iterate-rejected-candidate]], [[v3-goal-search-outcome]].
