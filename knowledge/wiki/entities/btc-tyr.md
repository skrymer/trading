---
type: entity
title: BTC + Tyr
summary: Active search — breadth-thrust CONTINUATION (BTC) + institutional-breakout-on-breadth-recovery (Tyr); fresh premise via the breadth-thrust/recovery regime gate. Partly blocked on RSP ingest #99.
status: active
tags: [candidate, timing, breadth, breakout]
sources: ["strategy_exploration/BTC_TYR_STRATEGY_DEVELOPMENT.md", "strategy_exploration/dossier/tyr.jsonl"]
related: ["[[gjallarhorn]]", "[[the-funnel]]", "[[crisis-timer-cadence-ceiling]]", "[[participate-and-lose]]", "[[thinning-not-selecting]]", "[[lottery-vs-signature]]", "[[long-premise-in-narrow-leadership]]", "[[purpose]]"]
updated: 2026-06-06
---

# BTC + Tyr

The **current active search** — the next candidate after [[gjallarhorn]] was shelved. A **fresh
premise class**, quant-recommended (2026-06-04) as the strongest pick precisely because it avoids all
four deprecated long families (long-pullback MR, breakout-in-uptrend, leveraged-ETF timing, RS-momentum
rotation). Still **SCOPING** — design basis only, not yet specced or screened.

## Premise

Two named components combined into one candidate:

- **BTC — Breadth-Thrust Continuation.** Deploy long when a market **breadth *thrust*** signals the
  start of a *fresh, broad* recovery, then **ride** the expansion (deploy-and-hold) — not buying
  individual fresh-high breakouts. Anchor: Zweig Breadth Thrust (the *continuation* half) + the
  leadership-concentration regime.
- **Tyr — Institutional Breakout.** An order-block breakout **event** trigger, gated by an **early
  market-breadth-recovery** regime condition (the dossier differentiator was
  `orderBlockBreakout(2,5,0) AND marketBreadthRecovering()`, ADX 25-50, bullish candle).

**Where the freshness actually lives:** the breakout-event **trigger** is *not* fresh — it's a cousin
of the deprecated breakout-in-uptrend family. The genuinely fresh part is the **breadth-recovery /
breadth-thrust regime GATE** — a *transition-timer*, structurally akin to [[gjallarhorn]]. So this
candidate is worth screening **for the gate, not the trigger**. ^[inferred]

**Why it's the strongest next pick (quant, 2026-06-04):**
- *Adjacent to proven alpha* — [[gjallarhorn]] established that breadth-*state* signals carry real,
  null-beating timing information (+22σ). BTC+Tyr is the *continuation* sibling: deploy-and-ride, not
  buy-the-exact-bottom.
- *Fixes Gjallarhorn's two standalone killers* — it can be active **far more than ~10% of the calendar**
  (rides the post-thrust expansion), clearing the cash-drag / sub-CAGR-floor wall; and thrust-continuation
  tapes (2003, 2009-H2, 2020-H2) are where the largest broad-based gains live → a real shot at the floor.
- *Distinctness PASS* — gates on a **market breadth thrust + participation**, not individual-stock
  fresh-high breakouts → structurally sidesteps the breakout-in-uptrend narrow-leadership death tape
  ([[participate-and-lose]]).

## The breadth-event family — screen together, don't double-count

BTC (breadth-thrust **continuation**) and [[gjallarhorn]] (breadth-thrust **exhaustion-reversal**) are
the **two halves of one breadth-event family**. They must be **screened together and the regime must
not be double-counted** — both read the same market-breadth transition, just at different points of it
(BTC rides the expansion that begins where Gjallarhorn's bottom-timer fires).

## Status

**ACTIVE — the live search.** SCOPING stage: design basis collected, formal spec still to be routed to
`quant-analyst` before any build or screen. **Partly blocked on data:** the RSP/SPY leadership signal
depends on RSP ingestion (#99) — the internal breadth-thrust is usable now, so the block is partial,
not total.

## Funnel history

| Date | Event | Result |
|---|---|---|
| 2026-05-31 | Tyr solo `/condition-screen` ([[the-funnel]]) | **NO PROCEED → redesign** — *not* a firewall death |
| 2026-06-04 | Re-scoped: screen **BTC + Tyr together** | active SCOPING |

**Tyr's earlier solo `/condition-screen` (2026-05-31) earned NO PROCEED**, on the differentiator
`marketBreadthRecovering() AND orderBlockBreakout(2,5,0)`. Crucially this was **NOT a firewall death**:
no `config_hash` was burned DEAD and the cross-candidate G13 brake was **not** engaged — so the premise
is free to be re-scoped and re-screened from scratch. It was also **not anti-predictive** (distinct from
[[baldr]]): the naive negative `meanLift` was a lumpiness artifact, and the **clustered** mean was
*positive* (5d +0.13%, 10d +0.48%, 20d +1.30%). The reasons for NO PROCEED were:
- **SPY-regime sign-flip** (binding flag) — down/flat-positive → up-NEGATIVE at every horizon, opposite
  to the long-breakout thesis.
- **5d edge in the noise band** — clustered t ≈ 1.0, roughly half-eaten by the +0.11% fill gap.
- **Extreme date-clustering** — firing only **0.347%** across ~364 dates / 21y (~96 signals/date);
  effective N tiny, the 10d/20d positives carried by right-tail dates (skew 6-138). The ARS sweep was
  *clean* (no fragility); the `ageInDays` sweep was degenerate (pinned at 0).

The redesign paths flagged in the dossier (screen `orderBlockBreakout` ALONE; or define a *native*
down/flat-regime breakout predicate rather than IS-fitting a post-hoc SPY gate) feed the current
BTC+Tyr re-scoping. (The order-block leg was separately isolated and screened — see
[[order-block-breakout-condition]].)

## What it must clear

- **Cadence / lottery-window gate FIRST (Step-0).** Confirm BTC+Tyr deploys **≥1-2 windows/yr** (not
  Gjallarhorn's ~0.5/yr) at the Step-0 firing-rate check, **before** building anything bespoke — else it
  inherits the [[crisis-timer-cadence-ceiling]] and is un-validatable standalone (can't populate
  walk-forward OOS folds). Front-load this so a rare breadth-thrust gate doesn't repeat Gjallarhorn's
  standalone-unvalidatability.
- **Screen-together, don't-double-count** the breadth regime with [[gjallarhorn]] (see family note above).
- **G-RANDOM** — if BTC+Tyr selects *what to buy* after the thrust gate fires, it must beat a
  byte-identical `Random`-ranker baseline on **blended CAGR AND per-trade edge** (not win-rate/WFE) —
  else the "edge" is entry-universe beta ([[beta-delivery]]).
- **Screen-stage participation** — ≥3 positive participating windows, no single window ≥60% of compounded
  return ([[lottery-vs-signature]]).
- **IS-fitting guardrail** — the design hypotheses (ADX, thrust-not-level, RSP/SPY) are derived from the
  breakout's own data and must be validated **out-of-sample** on BTC+Tyr's trades, never tuned within
  sample.
- **CAGR floor** — the final standalone tradability bar (25% per memory `feedback_min_cagr_tradable`).

## Open design questions

- **ADX gate — selects or merely thins?** ADX is the **strongest unused breakout discriminator**: in the
  breakout anatomy win rate climbs **31% → 48%** as ADX goes <20 → >40 (mean +8% P/L at ADX 30-40), and
  the breakout never used it. Add an ADX trend-strength condition (e.g. `adxRange(minADX≈25-30,
  maxADX=100)`); the decisive screen-time question is whether it **selects** (lifts win%/payoff, retains
  right-tail winners — [[thinning-not-selecting]]) or merely thins. Sweep the threshold for
  [[aliased-regime-sensitivity]] at condition-screen / Step-0; do not hard-code the value that happens
  to pass.
- **RSP/SPY leadership-concentration signal vs internal breadth-thrust.** The RSP/SPY ratio
  (equal-weight vs cap-weight S&P) is the cleanest *direct* broad-vs-narrow detector (rising = broad,
  falling = narrow/mega-cap concentration). At screen time, compare RSP/SPY-divergence against the
  internal breadth-thrust as BTC+Tyr's regime signal — whichever discriminates good-vs-bad years better
  in the anatomy wins. **Blocked on #99.** **Data-span caveat:** RSP launched **2003** → covers the
  2005-2015 screen window + GFC/COVID/2022 + Blocks B/C, but **truncates Block A to 2003-2014 (misses the
  2000-2002 dot-com bear)** — a partial gap to disclose, not a full disqualifier.
- **Breadth as THRUST/TRANSITION, not LEVEL.** Entry-day breadth *level* does not predict win rate (flat
  ~33-35% from breadth <30 to >60) — it only scales payoff magnitude. A `marketBreadth > 50%` level gate
  only **thins**, never **selects** ([[thinning-not-selecting]]) — empirically why Track-2/2b were
  rejected. BTC+Tyr's core signal must be the **thrust/transition**, which a daily breadth reading can't
  see but a multi-day thrust can.
- **Avoid the dilutive loose tails** — the breakout's `%52wHigh(25)`, `%52wLow(30)`, Donchian-"near"
  tails were dilutive; if BTC+Tyr reuses any trend-template conditions, tighten them as a *fresh*
  hypothesis (validate OOS), don't copy the breakout's loose defaults.

## Related

[[gjallarhorn]] (sibling breadth-event family) · [[the-funnel]] · [[crisis-timer-cadence-ceiling]] ·
[[participate-and-lose]] · [[thinning-not-selecting]] · [[lottery-vs-signature]] ·
[[order-block-breakout-condition]] · [[long-premise-in-narrow-leadership]] · [[purpose]]
