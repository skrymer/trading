---
type: entity
title: R1 — Leadership-Gap Breakout
summary: Minervini breakout + a #83 leadership-gap regime gate; ABANDONED 2026-06-09 — deploy signal orthogonal to the edge (corr≈0); a market-level gate can't rescue participate-and-lose.
status: stable
tags: [candidate, breakout, regime-gate, abandoned]
sources: ["knowledge/wiki/entities/r1-leadership-gap-breakout.request.json", "strategy_exploration/dossier/"]
related: ["[[minervini-vcp-breakout]]", "[[participate-and-lose]]", "[[thinning-not-selecting]]", "[[regime-conditional-portfolio]]", "[[2026-06-09-r1-leadership-gap-breakout-abandon]]", "[[aliased-regime-sensitivity]]", "[[purpose]]"]
updated: 2026-06-09
---

# R1 — Leadership-Gap Breakout

The explicit attempt to rescue the [[minervini-vcp-breakout]] by the route its own post-mortem
prescribed: a **breadth-confirmed regime-transition layer introduced as a NEW candidate** (#83), not a
post-hoc patch. R1 = the breakout's validated 11-condition entry stack (10 base, byte-identical) **AND-ed**
with a market-level `leadershipGapRegimeOn` gate that deploys only when the equal-weight universe leads
the cap-weighted market. The gate is built, tested, and deployed (the engineering is sound); the
**premise pairing** is what this candidate killed.

> ⚠ Distinct from [[minervini-vcp-breakout]] (the ungated base, REJECTED 2026-06-03) and from the
> look-ahead-bug VCP. R1 is the breakout + the #83 regime gate, a new `config_hash`.

## Status

**ABANDONED — pre-registered diagnostic, 2026-06-09** (decisive, not a near-miss). Fails Gate-1 (named
window deployed-and-bleeding) and the Gate-2 MASTER (in-market Calmar) independently. Pre-registration
forbids tuning the gate after seeing the outcome ([[aliased-regime-sensitivity]]). Full run +
quant post-mortem: [[2026-06-09-r1-leadership-gap-breakout-abandon]].

## Diagnostic verdict (single 25y `/backtest`, 300-sym, unlevered)

| Gate | Result | Verdict |
|---|---|---|
| Gate-0 regime integrity (median ON/OFF spell ≥15d) | ON 34d / OFF 30d; 131 flips | PASS |
| Gate-1 named windows predominantly cash; abandon if deployed-and-bleeding | **2021: 3 trades, 0% win, −6.98%** | **FAIL** |
| Gate-2 MASTER in-market Calmar ≥3.0 | **0.32** (CAGR 7.61%, DD 23.81%) | **FAIL** (~9× short) |

138 trades / 25y, win 32.6%, blended CAGR 3.93%, blended Calmar 0.17. The in-market Calmar **0.32 ≈ the
ungated 0.42** — the gate did not improve the risk-adjusted profile.

## Why it died

**The deploy signal is orthogonal to the edge.** Mean gate ON-fraction is identical in losing years
(0.593) and winning years (0.599); `corr(ON-fraction, annual edge) ≈ +0.05`. It was ON 48% of 2021, 48%
of 2023, 67% of 2024 — it never identified the narrow-leadership years as cash. Three mechanisms
(quant): (1) `SPY_20d − EW_20d` is a momentum-dispersion oscillator that **horizon-aliases** a
multi-quarter regime with a multi-week instrument; (2) equal-weight-vs-cap-weight is the wrong
concentration proxy (size-factor wiggle, not monotone in concentration); (3) deeper — a market-level gate
acts on the **calendar** while the breakout's loss lives in the **cross-section** (individual breakouts
fail at fresh highs in thin tape), one level of aggregation above where the alpha decays, so
[[participate-and-lose]] is structurally immune to regime overlays. See [[thinning-not-selecting]].

## Failure modes hit

- [[participate-and-lose]] — the binding cause, now **mechanistically proven un-gateable** by a
  market-level layer (R1 is the proof, not just another instance).
- [[thinning-not-selecting]] — a scalar/market gate has zero cross-sectional resolution; an
  edge-orthogonal gate thins (and here mildly *worsens*) rather than selects.

## Disposition

- **EARNED-DEAD:** `SPY − equal-weight` trailing-return-gap as a leadership/concentration regime signal,
  at any parameter setting (dead by mechanism, not backtest). Added to the deprecated classes in [[purpose]].
- **STILL-OPEN:** a direct, low-frequency concentration signal (% at new highs, A-D slope, % above own
  200-EMA, weight Herfindahl) — but only on a market-timing-level premise, **never on a breakout**.

## Reusable findings (durable capital)

- The **regime-gate engine is a clean, reusable asset** — full-universe equal-weight-return aggregate,
  causal EMA/Schmitt/washout precompute, warm-up-buffered, gated on the condition's presence, with a
  loud all-cash WARN and `leadershipRegimeDiagnostics` observability. Correct and lookahead-free
  (Gate-0 well-behaved); only the *signal it computes* is the wrong axis. Any future direct-concentration
  signal can reuse the same machinery.
- **Diagnostic recipe:** cross-tab the gate's per-year deploy fraction against per-year edge *before*
  reading the headline — `corr≈0` is the kill tell, and it would have been visible without the full
  Gate-2 reconstruction.
- The breakout base + its G14-promoted conditions ([[minervini-vcp-breakout]]) remain shelved assets.

## Related

[[minervini-vcp-breakout]] · [[participate-and-lose]] · [[thinning-not-selecting]] ·
[[regime-conditional-portfolio]] · [[2026-06-09-r1-leadership-gap-breakout-abandon]] ·
[[aliased-regime-sensitivity]] · [[the-funnel]] · [[purpose]]
