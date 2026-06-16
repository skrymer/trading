---
type: source
title: THRUST-specialist abandoned — quant adjudication (2026-06-16)
summary: A no-backtest quant adjudication killed the #83 THRUST-gated-breakout under the operator's improve-or-abandon rule — two mechanistic kills: published-THRUST is built from R1's earned-dead gap-sign + the failed breadth level, and the dd-leg precedence discards the recovery alpha.
status: stable
tags: [run, regime-specialist, breakout, thrust, abandoned, design-time-kill]
sources: ["knowledge/wiki/queries/thrust-specialist-design-priming.md", "knowledge/wiki/entities/minervini-vcp-breakout.md", "knowledge/wiki/entities/r1-leadership-gap-breakout.md", "docs/adr/0024-regime-read-out-v2-accepted-with-limitations.md"]
related: ["[[minervini-vcp-breakout]]", "[[r1-leadership-gap-breakout]]", "[[participate-and-lose]]", "[[thinning-not-selecting]]", "[[regime-read-out]]", "[[crisis-timer-cadence-ceiling]]", "[[thrust-specialist-design-priming]]"]
updated: 2026-06-16
---

# Source summary — THRUST-specialist abandoned (2026-06-16)

**What was run.** *Nothing* — a design-time **quant-analyst adjudication** (no backtest, by instruction),
scoping the #83 "THRUST-specialist" candidate: the shelved [[minervini-vcp-breakout]] rebuilt as a
THRUST-regime-gated breakout. The operator's kill-criterion, verbatim: *"if we are not improving on the
dead THRUST config then abandon."* Primed by [[thrust-specialist-design-priming]] (which surfaced the
two tensions the quant then made decisive).

## Headline — VERDICT: ABANDON (not close)

Two **independent, mechanistic** kills, both anchored in stable quant-signed pages; neither needs a
backtest to see.

> **Scope — this is a candidate death, NOT a regime-classifier verdict.** Abandoned = the **candidate**:
> gating the breakout to the *current published-THRUST label*. **Determining the regime cycles/states is a
> separate, generic, strategy-blind concern** — a property of the universe at large, developed independently
> of any strategy and explicitly *never fitted to a strategy's P&L* (CONTEXT *Market regime*; the live
> [[2026-06-14-regime-v3-dispersion-overturned|#168 regime-v3]] effort). Nothing here touches the goal of
> detecting a THRUST regime / the four states, nor the regime-specialist program. The breakout merely
> *consumed* the existing strategy-blind THRUST label and found it unfit for its own purpose. The two kills
> sit on opposite sides of that line: **Kill 2 is a *generic detector* property** of the current published
> label (post-crash THRUST suppression — already documented on [[regime-read-out]] / ADR 0024; it is an
> *input to* the strategy-blind v3 effort, not a breakout finding), while **Kill 1 is *label-agnostic***
> (a market-level gate only thins a cross-sectional loss — it would kill the breakout-via-market-gate even
> with a *perfect* THRUST detector, but says nothing about regime detection's value for the assessment
> read-out or for premises whose loss is genuinely regime-level rather than cross-sectional).

### Kill 1 — a published-THRUST gate is *built from R1's dead signal* (R1-equivalence, worse than generic)

A published-THRUST label is **one number per day, identical for all names** (ADR 0024: THRUST = (breadth
HIGH ≥50 OR slope RISING ≥+3/5 bars) AND gap NEG). It is the scalar market-regime gate the durable law in
[[thinning-not-selecting]] says has **zero cross-sectional resolution** — it admits/denies the whole
same-day cohort identically, so it cannot solve the breakout's per-name follow-through failure. Worse, it
is *mechanically assembled from two signals already dead-by-mechanism for this exact breakout*:

- THRUST's **`gap NEG` leg = the sign of the `SPY − equal-weight` 20-bar return gap** — the *same signal*
  [[r1-leadership-gap-breakout]] ruled **EARNED-DEAD at any parameter setting** (deploy-fraction corr≈0 to
  edge; in-market Calmar 0.32 ≈ ungated 0.42). A THRUST gate is that earned-dead gap sign, thresholded.
- THRUST's **`breadth HIGH ≥50` leg = the `breadthEma10Above50` scalar** the breakout's own Track-2 fix
  already tried — and it failed *worse* ([[thinning-not-selecting]]: halved trades, destroyed Block B's
  0-neg-window proof; the [[thrust-degenerates-to-level]] level-read).

Combining two dead scalar aggregates does not buy cross-sectional resolution. A design that *would* bite
on the cross-section (a per-name real-time participation read) is, by definition, a **new entry
condition**, not a THRUST-regime gate — exactly the [[participate-and-lose]] prescription, and a
from-scratch `/condition-screen` candidate, not a re-validation of the +7.45 THRUST bucket.

### Kill 2 (decisive) — the gate keeps the weaker half and discards the stronger

The frozen read-out **suppresses THRUST ~12 months post-crash** (the −20%-from-252-bar-high / washout
CRISIS leg takes precedence — 2009-Q2/Q3 at 0% THRUST, 2020-Q2/Q3 at 27%). But the breakout's documented
best tape *is* the post-washout recovery (CONTEXT *Broad-rally thrust* = "bases firing off a washout";
Block B's real **2020 +56.5% recovery alpha**). So, reading the published-label decomposition
(THRUST **+7.45**/trade, CRISIS **−3.05**/trade) together with the precedence:

- The explosive recovery alpha lands in the **−3.05 CRISIS bucket** (netted against the crash bleed —
  which is why that bucket is negative despite embedding the premise's biggest winners).
- The **+7.45 THRUST bucket** is the *residual*: quieter mid-cycle thrusts (2003-style broadening,
  >12 months past any crash) — real but lower-magnitude, **and absent in exactly the year after every crash.**

Gating to published-THRUST therefore **keeps the mid-cycle half and throws away the recovery half** — the
opposite of "improving on the dead config" — and it is guaranteed by the **non-tunable** dd-leg precedence
(ADR 0024: no v3 by iteration; the precedence is an accepted trade-off, not a defect to tune out).

### Cadence corroboration (Q4)

~946–1043 trades / 25y ≈ 38–42/yr ungated; THRUST ≈ 10–20% of days → ~4–8 THRUST-gated trades/yr
aggregate — *above* the [[crisis-timer-cadence-ceiling]] <~1/yr ceiling, so not disqualified on raw
cadence. **But** THRUST is empty in precisely the ~12-month post-crash windows the premise was built for,
so any 36/12/12 OOS window landing in a recovery holds **zero THRUST trades** → per-window WFE/stability
uncomputable → **un-validatable standalone**; viable only as a higher-cadence-composite leg — which
rescues validatability, not the value proposition (Kills 1+2 still apply to the leg).

### The within-THRUST null (Q3, moot — and a flagged false-green-light)

If it had survived: selection-within-regime null (the breakout selects *which* names) → random *names* on
THRUST days at matched per-day firing rate, 20 deliberately-seeded repo-persisted paths (ADR 0017), primary
metric per-trade edge > null p95. **Trap:** the breakout's loss is *not* a selection failure (it picks
decent names; they fail at the fresh high), so the +7.45 bucket would likely **pass** this null — a
*misleading GO* that confirms only "picks decent names on broad-thrust days," not the binding constraints.

## What it taught (durable)

- **A gateable published regime LABEL is a calendar gate; it cannot host a specialist whose loss is
  cross-sectional.** The read-out's only gateable labels are CRISIS + THRUST, both market aggregates —
  using one to "specialize" a [[participate-and-lose]] premise is R1's death again, by construction. The
  regime-specialist-stable thesis, *as expressed through the read-out's labels*, is closed for
  cross-sectional-loss premises; a real specialist needs a **from-scratch per-name cross-sectional axis**,
  not a label gate. ^[inferred — the generalization across both gateable labels is Claude's synthesis from
  the two sourced kills; each kill is quant-signed]
- **"Improving on the dead config" was unmeetable here** — it would require keeping the recovery alpha the
  THRUST gate discards (impossible under the frozen precedence) *and* biting cross-sectionally (impossible
  for any market-aggregate label).
- The legitimate heir: a **per-name cross-sectional participation entry premise** embedded in a
  higher-cadence composite — a *new lineage*, `/condition-screen` from scratch, **not** a rescue of this one.

## Optional confirmations (named, not run — would not flip the mechanistic verdict)

1. `GET /api/regime/decomposition/{backtestId}` for the clean continuous run (`config_hash 81a1d38ee0a6` /
   `239232fb`): confirm (a) the THRUST bucket's per-window trade distribution (Q4) and (b) the 2020-Q2/Q3
   recovery entries bucket as **CRISIS, not THRUST** (Q2). Re-buckets stored trades; not a re-run.
2. `GET /api/regime/readout?after=2000-01-01&before=2025-12-31`: per-year published-THRUST day frequency,
   to confirm the post-crash THRUST-zero clustering (Q4). Read-only.

Skipped — the kills are mechanistic, not data-contingent, and PRD is on the prior universe epoch.

## Pages updated

[[minervini-vcp-breakout]] (THRUST-specialist hypothesis REFUTED — path closed), [[participate-and-lose]]
(THRUST-label instance), [[thinning-not-selecting]] (published-label = calendar gate instance), index, log.
