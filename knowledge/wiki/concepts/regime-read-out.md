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
legitimate v3 is a **from-scratch new pre-registration**: a structurally new axis (cross-sectional
dispersion/correlation, sector participation, vol term-structure), parameters fixed before any
coverage is computed, validated on **uncontaminated ground truth** — the 19 anchor spans are burned
as a primary acceptance gate.

**The v3 research is done** (deep-research 2026-06-14, [[2026-06-14-regime-classification-v3-research]],
tracked as issue #168). Its decisive finding *corroborates* this page: a return+vol classifier
(HMM/HSMM/Markov-switching) is **structurally incapable** of resolving the GRIND/NARROW/CHOP trichotomy —
NARROW is a cross-sectional/breadth fact an index-return model has no channel to see. The pre-registrable
path is **two orthogonal strategy-blind axes layered over the CRISIS/THRUST backbone**: a cross-sectional
**concentration** axis (top/bottom-half return dispersion, built from constituent prices) for NARROW, and
a **multi-week trend-efficiency** axis (Kaufman Efficiency Ratio / Choppiness Index — never daily; daily
Hurst is white noise) for GRIND-vs-CHOP. Validate against a random-walk surrogate + frozen-parameter OOS
on label stability + external Bry-Boschan/Lunde-Timmermann dating; GRIND/NARROW/CHOP stay
descriptive-only until those axes clear validation. Axis/threshold/window choices are quant-domain.
