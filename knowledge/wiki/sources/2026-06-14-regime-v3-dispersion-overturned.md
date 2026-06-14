---
type: source
title: Regime v3 NARROW axis — dispersion overturned; trend-efficiency + cap-weighted concentration is the path
summary: Grill + 3 quant reads (#168) killed the dispersion NARROW axis (cap-blind, collinear, fail-blind); corrected v3 = SPY trend-efficiency for GRIND/CHOP + cap-weighted top-N concentration for NARROW.
status: stable
tags: [methodology, regime, classifier, pre-registration, breadth, trend-efficiency, universe]
sources: ["docs/adr/0024-regime-read-out-v2-accepted-with-limitations.md", "knowledge/wiki/concepts/regime-read-out.md", "https://github.com/skrymer/trading/issues/168"]
related: ["[[2026-06-14-regime-classification-v3-research]]", "[[regime-read-out]]", "[[regime-classification-v3-research-brief]]", "[[aliased-regime-sensitivity]]", "[[minervini-vcp-breakout]]"]
updated: 2026-06-14
---

# Regime v3 NARROW axis — dispersion overturned

A `/grill-with-docs` session + three independent quant-analyst reads (2026-06-14, issue #168) reviewed the
deep-research recommendation ([[2026-06-14-regime-classification-v3-research]], "Design 1 / Axis A")
**before any code** and overturned its NARROW axis. This page is the correction-of-record; the
deep-research page keeps its CORRECTION callout and its appendix source claims (the error was in the
*inference* that dispersion isolates NARROW, not in the claims).

## What was run

The deep-research synthesis recommended resolving NARROW with a cross-sectional **dispersion** axis
(top-half-mean − bottom-half-mean return). The grill challenged it against CONTEXT.md + ADR 0024; three
quant reads — an adjudication, an adversarial steelman-then-refute, and a universe-root-cause probe — all
rejected it and converged.

## Why Axis A (dispersion) is dead

1. **Cap-blind.** A small-cap surge (broad thrust) and a mega-cap surge (narrow leadership) produce an
   *identical* top−bottom dispersion but *opposite* regimes. Cap-identity is the entire NARROW signal; a
   cap-blind spread discards it. CONTEXT.md already names the *signed* leadership gap "the only clean
   thrust-vs-narrow discriminator."
2. **Collinear with the ruled-out stdev.** For a roughly symmetric cross-section, top−bottom ≈ 1.60σ —
   proportional to the full-universe stdev CONTEXT.md ruled out 2026-06-03 as "collinear, changes no
   stance." The deep-research never engaged that prior ruling.
3. **Fail-blind on our own data.** v1's cross-sectional dispersion trust guard fired *symmetrically* in
   crashes and recoveries (~30% of every year) and was demoted to a non-gating advisory thin-N flag —
   direct in-house evidence that dispersion magnitude is sign-blind.

The deep-research's load-bearing premise — "a return+vol model is structurally blind to NARROW" — is true
but aimed at a **strawman v2**: v2 is not return-only; it already carries breadth + a *signed* concentration
gap, and NARROW still graded D. So "add a concentration axis" cannot be the fix. `^[inferred]`

## The root cause: the universe `^[inferred]`

A count-equal ~5,098-name universe (median name ≈ rank 2,549, deep small-cap) is **structurally blind to
mega-cap concentration on any axis** — each mega-cap carries ~0.02% weight (≈500× diluted vs its cap
weight). NARROW is a *cap-weighted* fact; no statistic over a count-equal cross-section can see it. A
weighting-basis mismatch, not an axis-choice failure — which is why this spun out the three-population
universe redesign (#173) + the market-cap-sourcing primitive (#174).

## The corrected v3 design

- **GRIND-vs-CHOP** — a multi-week **SPY trend-efficiency** axis (Choppiness Index / Kaufman ER, frozen
  20–60d window, SPY-close only; never daily — daily Hurst is white noise). The genuine missing channel
  (v2's only directional input was a ±2% dead-band on a single 20-bar return). **Zero new data.** The clean
  v3 core.
- **NARROW** — a **cap-weighted top-N return-concentration** axis (Herfindahl / top-N share of cap-weighted
  return contribution, conditioned on index-up + breadth-deteriorating) over a point-in-time
  **top-N-by-cap** universe. Needs computed market cap (`close × shares_outstanding`; EODHD
  outstanding-shares is deep enough for the major names, while direct EODHD historical market cap is 2020+
  only) — issue #174.
- **Dispersion is dropped entirely.**
- Both axes stay descriptive-only / non-gateable until they clear a random-walk surrogate + frozen-param OOS
  on label stability + the 19 anchor spans; the NARROW axis is a *clean new pre-registration*, never
  "fixing v2's NARROW label" (the forbidden v3-by-iteration — [[aliased-regime-sensitivity]]).

## What it updated

ADR 0024 (dated amendment — bare "dispersion" superseded for NARROW; the v2 decision untouched),
[[regime-read-out]] (corrected the v3 paragraph + the no-v3 example), the deep-research source page
(CORRECTION callout), [[regime-classification-v3-research-brief]] (ANSWERED callout), and
[[minervini-vcp-breakout]] (the regime-specialist open-dependency line). Committed `eb88ca4` + this ingest.
