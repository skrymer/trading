---
type: concept
title: Regime read-out — the signed 5-label classifier pre-registration
summary: Quant-signed (2026-06-12) pre-registration of the 3-axis daily classifier (THRUST/GRIND/NARROW/CHOP/CRISIS) — frozen constants, decision table, hysteresis, validation anchors. To-build.
status: stable
tags: [methodology, regime, pre-registration, classifier]
sources: ["docs/adr/0023-regime-read-out-revived-as-pre-registered-gate-able-series.md", "knowledge/wiki/sources/2026-06-12-strategy-assessment-design-and-regime-readout-prereg.md", "udgaard/src/main/kotlin/com/skrymer/udgaard/backtesting/model/LeadershipRegimeParams.kt"]
related: ["[[strategy-assessment]]", "[[regime-conditional-portfolio]]", "[[aliased-regime-sensitivity]]", "[[component-firewall]]"]
updated: 2026-06-12
---

# Regime read-out — pre-registration v1 (quant-signed 2026-06-12)

The operative spec for the to-build 5-label daily regime classifier (ADR 0023). This page is the
methodology home per the quant's placement ruling; the binding numeric constants additionally land in
code as `RegimeReadoutParams.FROZEN` (mirroring `LeadershipRegimeParams.FROZEN`) at build time. The
shelved 2026-06-03 ancestor spec lives in [[regime-conditional-portfolio]] — its revival clause
("revive only if regime-attribution is ever wanted as a research instrument") is met by ADR 0023.

## Purpose & boundary

A pre-registered, market-defined, **strategy-blind** daily classifier assigning each NYSE trading day
from 2000-01-01 one of: **THRUST / GRIND / NARROW / CHOP / CRISIS** (CONTEXT.md canonical definitions).
A read-out the operator consults plus a frozen condition palette — **never an auto-switcher**; the
regime-conditional portfolio program remains abandoned. Changing any value is a methodology change, not
a config knob. Never fit to any strategy's good/bad years ([[aliased-regime-sensitivity]] discipline).
Uses: the [[strategy-assessment]] regime table, the current-regime line, frozen-param conditions under
ADR 0023's **rescue-forbidden** boundary, and the regime×sector consumers (below).

## Axes (daily inputs; all smoothing is input-stage EMA10 already present in the platform)

**A1 — Breadth participation** (breadth only; MUST NOT reference SPY price):
`L(t) = MarketBreadthDaily.ema10`. Bands: **HIGH if L ≥ 50; WEAK if L ≤ 35; else MID**
(anchor: breadthPercent mean ≈ 42; 50 ≈ majority-of-universe participation).
`S(t) = L(t) − L(t−5 bars)`. **RISING if S ≥ +3.0; FALLING if S ≤ −3.0; else FLAT**
(anchor: ±3 pts/week on the EMA10 level clears the smoother's sub-point daily noise floor).

**A2 — Leadership-concentration gap**: the existing frozen pipeline **verbatim** (20-bar SPY return −
EW-universe mean, EMA10-smoothed, trust guards minN=200 / maxSE=0.005). **Stateless** three-way cut on
`gapSmoothed`: **NEG ≤ −0.005; POS ≥ +0.005; else NEUTRAL**. The Schmitt latch (`schmittOn`) is NOT
used — no axis-stage hysteresis (using the latch would collapse NEUTRAL and make NARROW/GRIND
unreachable — the v0 blocking defect).

**A3 — Realized vol**: `σ(t) = stdev(SPY daily simple returns, 20 bars) × √252`. **LOW ≤ 12%;
HIGH ≥ 22%; else MID** (anchors: sub-12% = the documented low-vol-grind floor — 2013/2017/2019-H1;
≥22% isolates genuine stress windows; sanity-checked against SPY percentiles on the design-safe window
**2000–2014, Block A only**, at freeze time; never re-read on Block B/C data).

**A4 — Direction**: `D(t) = SPY 20-bar simple return`. **UP ≥ +2%; DOWN ≤ −2%; else FLAT** (a 0%
dead-band flips on noise and starves FLAT — the other v0 blocking defect). A4 is the *direction* axis,
deliberately distinct from A1 *participation*; D never back-confirms A1. All multi-week reads share the
20-bar horizon (no cross-horizon aliasing).

## Label decision table (raw daily label; precedence top-down, first match wins)

Mutually exclusive at the gap backbone (NEG/POS/NEUTRAL):

1. **CRISIS** — sustained breadth washout, verbatim the frozen veto: `breadthPercent ≤ 15.0 for ≥ 10
   consecutive days within trailing 40`. Washout-only — no vol/direction OR-leg (each new leg = a fresh
   ARS surface); cadence ~0.65 episodes/yr is correct for correlated risk-off.
2. **THRUST** — `(L HIGH OR S RISING) AND gap NEG`. The inner OR catches early thrust off a washout
   (rising-not-yet-high breadth); gap NEG is the mandatory thrust/narrow discriminator.
3. **NARROW** — `D UP AND gap POS AND (L WEAK OR S FALLING)`.
4. **GRIND** — `gap NEUTRAL AND σ LOW AND (L MID OR HIGH) AND S ≠ FALLING AND D ≠ DOWN`.
5. **CHOP** — residual. Diagnostic (validation-stage, not a label rule): log the fraction of CHOP days
   with D = FLAT; if < 70% the residual is silently a miscellaneous-decline bucket → revise the SPEC
   before any strategy is scored.

## Hysteresis / stability (exactly one debounce stage)

Published label = raw label debounced by a **5-consecutive-raw-day dwell**, EXCEPT entry into CRISIS
(publishes immediately; exit from CRISIS honors the dwell). No other latches anywhere. Stability
validation targets (read once after first compute, never tuned per strategy): median published-spell
≥ 15 trading days; ≤ 12 published label-changes/year.

## Undefined / trust (fail-closed)

Null label ("unlabeled") when: date < 2000-01-01 (breadth trust floor); 180-calendar-day warmup
unseeded; A2 trust guards breached; A3 window < 20 bars; any input series missing. Trades entering on
null-label days bucket as "unlabeled" in the assessment regime table.

## Validation anchors (fixed ex ante; market-consensus dates, never from where the classifier fires, never from any strategy's P&L)

Pass requires expected-label coverage **≥ 60%** of days in each span:

- **CRISIS** ⊇ {2000-09→2001-03, 2002-06→2002-10, 2008-09→2009-03, 2011-08, 2018-12,
  2020-02-20→2020-04-15, 2022-H1 leg}
- **THRUST** ⊇ {2003-Q2→Q4, 2009-Q2→Q3, 2020-04→2020-08}
- **GRIND** ⊇ {2013, 2017, 2019-H1}
- **NARROW** ⊇ {2021-H2, 2023, 2024}
- **CHOP** ⊇ {2011-H1, 2015-H1, 2015-H2→2016-Q1}

Plus the CHOP D-FLAT diagnostic (≥70%) and the stability targets. If any check fails, the SPEC is
revised and re-frozen BEFORE any strategy is ever scored against it (legal: no strategy has seen it).
The 2021–25 anchors label **market data only** — outside ADR 0007's leak surface.

## Implementation (informational)

Udgaard `RegimeReadoutService` mirroring `LeadershipRegimeService` (pure `computeSeries` + impure
loader); daily-series + current-label endpoint; frozen-param entry/exit conditions; regime-decomposition
endpoint (per-regime edge ± date-clustered SE, insufficient-N floor ~30 trades, **published-label**
bucketing with a raw-label diagnostic column). Scope additions (label consumers, no re-freeze):
market-scoped **regime×sector return matrix** endpoint (spell-clustered SEs, spell-count caveat printed)
and a **sector×regime drill-down** in the assessment decomposition (insufficient-N floor per cell —
expect mostly-grey tables at 11 sectors × 5 regimes).

## Sign-off

Quant-signed 2026-06-12 (v0 → CHANGES-REQUIRED → v1 SIGNED-OFF; consult record:
[[2026-06-12-strategy-assessment-design-and-regime-readout-prereg]]). Params freeze at this spec;
operator approved persistence the same day.
