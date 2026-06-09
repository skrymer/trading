---
type: source
title: R1 leadership-gap breakout — ABANDON (#83 regime-rescue refuted)
summary: Breakout + #83 leadership-gap gate ABANDONED on its pre-registered diagnostic — deploy signal orthogonal to edge (corr≈0); a market-level gate can't rescue participate-and-lose.
status: active
tags: [run, firewall, diagnostic, abandoned, regime-gate, participate-and-lose]
sources: ["strategy_exploration/dossier/", "knowledge/wiki/entities/r1-leadership-gap-breakout.request.json"]
related: ["[[r1-leadership-gap-breakout]]", "[[minervini-vcp-breakout]]", "[[participate-and-lose]]", "[[thinning-not-selecting]]", "[[regime-conditional-portfolio]]", "[[aliased-regime-sensitivity]]"]
updated: 2026-06-09
---

# R1 leadership-gap breakout — ABANDON (2026-06-09)

**Candidate.** R1 = the shelved [[minervini-vcp-breakout]] (real broad-tape edge, dies
[[participate-and-lose]] in narrow tape) AND-ed with a breadth-confirmed **leadership-gap regime
ON-gate** (#83). The gate: `GAP = SPY_20d_return − equal-weight_universe_mean_20d_return`; GAP<0
(equal-weight leading) ⇒ broad thrust ⇒ deploy; GAP>0 ⇒ thin tape ⇒ cash. EMA10 smoothing, Schmitt
±0.5% dead-band, sustained-breadth-washout crisis veto. Frozen pre-registered params. Run unlevered,
single 25y `/backtest` [2000–2025], 300-sym sanity universe. backtestId `ac49bee8`.

**The thesis under test (pre-registered §A2):** the gate would transform the ungated breakout's
**Calmar 0.42 / DD 42%** into a tradable **in-market Calmar ≥3.0 / DD ≤12%** by sitting in cash through
narrow-leadership regimes. A ~7× Calmar improvement — a high, honestly-framed bar.

## Verdict: ABANDON

| Gate | Criterion | Result | Verdict |
|---|---|---|---|
| Gate-0 regime integrity | median ON & OFF spell ≥15d; sane flips | ON 34d, OFF 30d; 131 flips (~5/yr) | **PASS** |
| Gate-1 named windows | 2015/21/23 predominantly cash; abandon if deployed-and-bleeding | 2015: 6 tr +0.19% · **2021: 3 tr, 0% win, −6.98%** · 2023: 0 tr | **FAIL** (2021 deployed-and-bleeding) |
| Gate-2 MASTER | in-market Calmar ≥3.0 (CAGR ≥40%, DD ≤12%) | **Calmar 0.32** · CAGR 7.61% · DD 23.81% | **FAIL** (~9× short) |

First-fail in order is Gate-1; Gate-2 independently fails the master. Pre-registered ⇒ **ABANDON, no
tuning** (tuning the gate after seeing breakout-year outcomes is the [[aliased-regime-sensitivity]] trap).
138 trades / 25y (5.5/yr), win 32.6%, PF 11.95 (tail-driven), blended CAGR 3.93%.

## The crux — the deploy signal is orthogonal to the edge

The single statistic that adjudicates the candidate: the gate deploys **the same fraction of the time in
the years the strategy bleeds as in the years it earns.**

| | mean gate ON-fraction |
|---|---|
| 9 negative-edge years | 0.593 |
| 12 positive-edge years | 0.599 |
| `corr(ON-fraction, annual edge)` | **+0.05** |

The decision variable carries ~zero mutual information with the outcome it gates. It was **ON 48% of
2021, 48% of 2023, 67% of 2024** — i.e. it did *not* identify the narrowest-leadership (Mag-7) years as
cash. The in-market reconstruction (deployed = days holding ≥1 position, 38.3% of calendar): in-market
Calmar **0.32** — marginally *worse* than the ungated 0.42, because an edge-orthogonal gate that discards
~62% of days truncates the good runs as often as the bad (anti-selective by accident).

## Why it failed (quant post-mortem — three mechanisms, descending force)

1. **`SPY_20d − EW_20d` is a momentum-dispersion oscillator, not a concentration-regime state variable.**
   A *difference of two 20-day trailing returns* mean-reverts on a weeks horizon; a narrow-leadership
   regime is a *multi-quarter* structural state. **Sampling a multi-quarter phenomenon with a multi-week
   instrument** — textbook horizon aliasing. The diagnostics confirm it: 131 flips, median ON-spell 34d —
   the state variable turns over monthly, far faster than the regime it claims to track.
2. **Equal-weight-vs-cap-weight is the wrong concentration proxy** even at the right horizon — its sign is
   dominated by the size factor's recent payoff, which is *not* monotone in concentration (a broad-but-
   large-led thrust reads "narrow"; a thin tape where small-caps merely fell less reads "broad"). A
   *direct* concentration measure (% at new highs, A-D line, % above own 200-EMA, index-weight Herfindahl)
   is pointed at the right axis; the return-gap is not.
3. **Deeper — the breakout premise loses in narrow tape for reasons no market-level overlay can gate.**
   Even a perfect narrow/broad oracle wouldn't help: the breakout bleeds because **individual breakouts
   fail individually** (false breaks at fresh highs in thin tape), even on days a perfect classifier says
   "broad." The lossy unit is the **trade** (cross-sectional selection); the gate acts on the **calendar**
   (deploy/cash days) — one level of aggregation *above* where the alpha decays. A market-state gate can
   only remove days; it cannot fix the entries on the days it keeps. This is why [[participate-and-lose]]
   is **structurally immune** to regime overlays. See [[thinning-not-selecting]].

## Disposition — earned-dead vs still-open (quant)

- **EARNED-DEAD (do not re-attempt, any parameter):** `SPY − equal-weight` trailing-**return-gap** as a
  leadership/concentration regime signal. Dead by *mechanism* (horizon aliasing, §1/§2), not by one bad
  backtest — re-running with a different lookback/EMA/dead-band tunes the dead-band of an instrument
  measuring the wrong frequency. The frozen-untuned params make this a clean class-kill, not an overfit.
- **STILL-OPEN:** a *structurally different, direct, low-frequency* concentration/breadth signal — but only
  as a portfolio overlay on a premise whose edge lives at the **market-timing** level (a breadth-thrust
  long, an index-momentum strategy), **never bolted onto a breakout** (§3 stands regardless of gate
  quality). R1 killed one signal construction, not the question "can narrow leadership be detected?"

## Durable upgrade — the memory is now mechanistically proven

R1 **strengthens** the standing memory *regime-filtering a participate-and-lose premise doesn't work; a
structurally different ENTRY premise is required* — and promotes it from empirical-pattern to
**mechanistically-explained**. Earlier cases ([[vz3]], [[mr3]], the breakout's own three gate tracks)
showed regime filters fail *empirically* (IS-fitting the single OOS window). R1 adds the *why*: a clean,
stable, correctly-implemented, **pre-registered non-overfit** gate still fails, because the gate operates
on the calendar while the loss operates on the cross-section. The fix is a different entry premise (a
cross-sectional selection that doesn't false-break in thin tape), **not** a better market-level gate.

## Notes

- The gate **code is clean** — Gate-0 well-behaved (median spells 34/30d, correct default-OFF warm-up
  seeding, washout veto mirrors the breadth condition). The failure is the **signal axis**, not the
  implementation; the engine (regime precompute + observability + the loud all-cash WARN) is reusable.
- Implementation is committed (gate condition + regime service + diagnostics); the breakout base + the
  gate are durable repo assets. What died is the **premise pairing**, not the engineering.
- Diagnostic recipe captured for re-use: **cross-tab the gate's per-year deploy fraction against per-year
  edge *before* judging the headline** — an orthogonal deploy signal (`corr≈0`) is the tell, and no
  threshold tune fixes an aliased horizon.
