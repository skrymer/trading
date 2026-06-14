---
type: concept
title: Regime read-out — the v2 classifier, accepted with limitations
summary: The frozen v2 classifier (ADR 0023/0024) — CRISIS trustworthy + THRUST precision-only (gateable); GRIND/NARROW/CHOP below axis resolving power, descriptive-only, ungateable. No v3 by iteration.
status: stable
tags: [methodology, regime, pre-registration, classifier]
sources: ["docs/adr/0023-regime-read-out-revived-as-pre-registered-gate-able-series.md", "docs/adr/0024-regime-read-out-v2-accepted-with-limitations.md", "knowledge/wiki/sources/2026-06-13-regime-readout-v1-fail-v2-adjudication.md", "udgaard/src/main/kotlin/com/skrymer/udgaard/backtesting/model/RegimeReadoutParams.kt"]
related: ["[[strategy-assessment]]", "[[regime-conditional-portfolio]]", "[[aliased-regime-sensitivity]]", "[[component-firewall]]", "[[2026-06-14-regime-classification-v3-research]]"]
updated: 2026-06-14
---

# Regime read-out — v2, accepted with limitations (ADR 0024)

The built, frozen 5-label daily classifier (`RegimeReadoutService`, `RegimeReadoutParams.FROZEN`).
v1 failed its first-compute anchors (18/19); the §5 revision loop produced v2, which the cycle-2
anchors adjudicated **ACCEPT-WITH-LIMITATIONS** — the full arc and durable findings live in
[[2026-06-13-regime-readout-v1-fail-v2-adjudication]]. A **research instrument, not an
auto-switcher**; the regime-conditional portfolio program stays abandoned.

## What it is, honestly compressed

**A −20%-drawdown/washout CRISIS detector with a precision-only THRUST; the GRIND/NARROW/CHOP
trichotomy is below the resolving power of three cheap daily axes and is research-descriptive
only.** Consume the stream as CRISIS-vs-not plus a precision-only THRUST — never as a clean
five-state series.

## Per-label trust grades (the consumer permission matrix)

| Label | Grade | Gateable? | Caveat |
|---|---|---|---|
| CRISIS | A — trustworthy (5/7 anchors) | **Yes** | A *confirmation* of "in or recovering from a ≥20% drawdown / sustained washout" — never an early warning; it lags topping phases (the 2000-09 caveated FAIL) |
| THRUST | B — precision-only (real where unmasked: 2003 71%) | Yes, author-beware | **The drawdown-recovery blind spot**: structurally suppressed ~12 months post-crash (2009-Q2/Q3 published CRISIS at 0% THRUST). Deploy-in-uptrend intent belongs to the leadership-gap regime (ADR 0010), not THRUST |
| GRIND | D — unreliable (0–29%) | **No — rejected at build time** | Not separable from chop on these axes |
| NARROW | D — unreliable (29–48%) | **No — rejected at build time** | Not separable from chop on these axes |
| CHOP | D — residual | **No — rejected at build time** | Means "unclassified" (49% of days, 36% direction-flat), never "the tape is choppy" |

The fence lives at the consumer boundary (`RegimeLabelCondition.GATEABLE_LABELS` — construction and
config parsing throw on D-grade labels); the producer keeps emitting all five labels + axes for
diagnostics and the descriptive table. In the [[strategy-assessment]]: D-grade rows render only
under the fixed reliability banner; the current-regime line reports CRISIS authoritatively, THRUST
with its caveat, and collapses the rest to "uptrend — fine-grain label unreliable".

## The frozen v2 spec (params in `RegimeReadoutParams.FROZEN` — no further iteration)

- **Gap leg**: SPY 20-bar return − **median** per-name 20-bar return (full STOCK universe; the mean
  is tail-contaminated and drift-non-stationary), EMA10-smoothed; validated at 80.7% sign-agreement
  vs SPY−RSP. Cut at asymmetric terciles frozen from the clean Block-A distribution:
  **NEG ≤ −0.007746 (p33), POS ≥ +0.003167 (p67)** — read once by a pre-registered rule, never re-read.
- **CRISIS** = sustained washout (breadth ≤ 15 for ≥ 10 consecutive days in trailing 40; immediate
  publication) **OR** close-basis drawdown ≤ −20% from the trailing 252-bar high (dwell-debounced
  entry). Warmup 400 calendar days (the drawdown leg needs a year of SPY history on day 1).
- **THRUST** = (breadth HIGH ≥50 OR slope RISING ≥+3/5 bars) AND gap NEG.
- **NARROW** = direction not-DOWN AND gap POS AND breadth (WEAK ≤35 OR FALLING) — `not-DOWN` rather
  than strict-up: melt-ups grind inside the ±2% band, while the not-DOWN guard refuses the
  bear-masquerade (mega-caps falling less than the median stock in a declining tape).
- **GRIND** = gap NEUTRAL AND vol LOW (≤12%) AND breadth > WEAK AND not-falling AND not-DOWN.
- **CHOP** = residual. Dwell 5 days; fail-closed unlabeled only for genuinely-undefined days
  (un-seeded gap EMA, missing breadth). The dispersion trust guard is an **advisory thin-N flag**
  (N < 200) — fail-closed it was fail-blind.

## The no-v3 line

Shifting bands, dwell, precedence, or adding sub-filters to chase the failing labels is IS-fitting
to anchors now seen — forbidden permanently ([[aliased-regime-sensitivity]] discipline). A
legitimate v3 is a **from-scratch new pre-registration**: a structurally new axis (multi-week
trend-efficiency, cap-weighted concentration, cross-sectional correlation, sector participation, vol
term-structure — **not** bare cross-sectional *dispersion*, which is cap-blind for NARROW, see
[[2026-06-14-regime-v3-dispersion-overturned]]), parameters fixed before any coverage is computed,
validated on **uncontaminated ground truth** — the 19 anchor spans are burned as a primary acceptance gate.

**The v3 research is done** (deep-research 2026-06-14, [[2026-06-14-regime-classification-v3-research]],
tracked as issue #168) — but its *recommended* design was **corrected on quant review** (`/grill-with-docs`
+ 3 independent quant reads, 2026-06-14). The research's load-bearing premise ("a return+vol classifier
can't see NARROW") attacked a **strawman**: v2 is not return-only — it already carries breadth **and** a
signed concentration gap, and NARROW still graded D. The research's proposed NARROW axis — **cross-sectional
dispersion (top/bottom-half return)** — is **rejected**: it is the 2026-06-03 "ruled-out" cross-sectional
spread relabeled (cap-blind, ~1.60σ-collinear with the ruled-out stdev, and already shown *fail-blind* on
our own data — v1's dispersion guard fired symmetrically in crashes **and** recoveries). The corrected v3:

- **GRIND-vs-CHOP** — a **multi-week SPY trend-efficiency** axis (Choppiness Index / Kaufman ER; frozen
  20–60d window; SPY-close only; never daily — daily Hurst is white noise). The genuine missing channel;
  **zero new data**. The clean v3 core.
- **NARROW** — a **cap-weighted top-N return-concentration** axis (top-N share / Herfindahl of cap-weighted
  return contribution, conditioned on index-up + breadth-deteriorating) over a point-in-time **top-N-by-cap**
  universe — **not** cross-sectional dispersion. The blocker is the *universe*: a count-equal ~5,098-name
  pool (median name ≈ rank 2,549) is structurally blind to mega-cap concentration on any axis. Needs the
  computed market-cap primitive (`close × shares_outstanding`) of issue #174; spun out with the
  three-population universe redesign (issue #173).

Both axes stay descriptive-only until they clear a random-walk surrogate + frozen-parameter OOS on label
stability + the 19 anchor spans; the NARROW axis is a **clean new pre-registration**, never "fixing v2's
NARROW label". Axis/threshold/window choices are quant-domain.
