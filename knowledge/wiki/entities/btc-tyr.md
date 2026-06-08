---
type: entity
title: BTC + Tyr
summary: DEAD (2026-06-08) — the fresh component (breadth-thrust GATE) failed solo /condition-screen (regime sign-flip, no 10/20d edge, thrust-degenerates-to-level). NOT a firewall death; class re-scopable.
status: superseded
tags: [candidate, timing, breadth, breakout, dead]
sources: ["strategy_exploration/dossier/tyr.jsonl", "strategy_exploration/dossier/condition-breadththrust.jsonl"]
request: "btc-tyr.breadth-thrust-screen.request.json"
related: ["[[gjallarhorn]]", "[[the-funnel]]", "[[crisis-timer-cadence-ceiling]]", "[[participate-and-lose]]", "[[thinning-not-selecting]]", "[[lottery-vs-signature]]", "[[thrust-degenerates-to-level]]", "[[2026-06-08-btc-breadth-thrust-screen-reject]]", "[[long-premise-in-narrow-leadership]]", "[[purpose]]"]
updated: 2026-06-08
---

# BTC + Tyr

> ⛔ **DEAD (2026-06-08).** The genuinely-fresh component — the breadth-thrust **GATE** — failed its solo
> `/condition-screen` ([[2026-06-08-btc-breadth-thrust-screen-reject]]): SPY-regime sign-flip at all three
> horizons, no detectable 10/20d edge, and the "thrust" degenerated into a level gate
> ([[thrust-degenerates-to-level]]) carried by the 2009–14 tape. The whole bet rested on this gate (the
> order-block trigger is a deprecated-breakout-family cousin already weak in isolation), so the candidate
> is dead. **NOT a firewall death** — no `config_hash` burned, G13 brake not engaged; the breadth-event
> premise *class* is re-scopable, but only via a **structurally different transition predicate** (see
> "What a successor needs" below), never a tune of this gate.

Was the **active search** after [[gjallarhorn]] was shelved — a **fresh premise class**,
quant-recommended (2026-06-04) as the strongest pick because it avoided all four prior long families
(long-pullback MR, breakout-in-uptrend, leveraged-ETF timing, RS-momentum rotation — the last downgraded
to untested, [[purpose]] #4). It got as far as a gate-isolation screen and died there.

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

**DEAD — design-time kill (2026-06-08).** The breadth-thrust gate, screened in isolation, failed (details
below in Funnel history + [[2026-06-08-btc-breadth-thrust-screen-reject]]). Reached only the
gate-isolation `/condition-screen`; never assembled, never strategy-screened, never validated. The RSP/SPY
leg and ADX gate (open questions below) were never exercised — moot given the gate's failure.

_Historical scoping context (pre-death):_ formal spec was routed to `quant-analyst` 2026-06-08, which
returned PROCEED-as-gate-isolation-screen. **RSP ingested — data block lifted (#99 CLOSED 2026-06-04).**
The RSP/SPY leadership leg is now buildable, but carries a **permanent residual caveat** (not removable by
ingestion): RSP launched **2003-04-30**, so the RSP/SPY ratio is undefined before 2003 — it covers the
screen window, GFC, COVID, 2022 and all of firewall Blocks B/C, but **truncates Block A to 2003-2014,
missing the 2000-2002 dot-com bear** (the 2008 GFC G6 mandate is unaffected). So the RSP/SPY leg is
**fully usable on the screen + Blocks B/C, partially usable on Block A** — disclose the dot-com gap on any
RSP-based candidate. The *internal* breadth-thrust signal has no such gap (full Block-A span) — prefer it
as the primary regime signal, with RSP/SPY as the corroborating broad-vs-narrow detector
([[2026-06-07-funnel-correctness-consult]]).

## Funnel history

| Date | Event | Result |
|---|---|---|
| 2026-05-31 | Tyr solo `/condition-screen` ([[the-funnel]]) | **NO PROCEED → redesign** — *not* a firewall death |
| 2026-06-04 | Re-scoped: screen **BTC + Tyr together** | active SCOPING |
| 2026-06-08 | `quant-analyst` spec consult | PROCEED as gate-isolation screen; BUILD-NEW inline-script breadth-thrust gate; internal-thrust primary; ADX & RSP/SPY deferred |
| 2026-06-08 | **BTC breadth-thrust gate solo `/condition-screen`** | **REJECT (design-time kill) → candidate DEAD** — [[2026-06-08-btc-breadth-thrust-screen-reject]] |

**The death (2026-06-08).** The fresh component — the breadth-thrust **GATE** (dip-then-surge: breadth
dipped ≤30 within the prior 10 trading days, then ≥55) — was screened in isolation on the 300-sym sanity
universe (2000–2021, sweeps `window` 10±2 / `low` 30±5 / `high` 55±5). **Step-0 cadence PASSED** (199
distinct firing dates/21yr, 15/21 years — not a [[crisis-timer-cadence-ceiling]]). Three independent
binding failures killed it:
- **SPY-regime sign-flip at all 3 horizons** — 5d down **−1.62%** / flat +0.10% / up +0.29%; 10d flat
  −0.11%; 20d up −0.23%. The same failure that killed the solo-Tyr screen, here worse and incoherent. The
  gate fires 6.98% in up-tape vs 0.17% in down-tape → it measures *"market already recovered,"* not the
  transition *into* recovery. A regime gate can't rescue a regime-sign-flipped premise.
- **No detectable edge** — 5d meanLift +0.249% (t≈1.23, sub-threshold), hit-rate lift negative at 10d
  (−1.47pp) and 20d (−0.86pp); horizon shape *decays* (wrong for deploy-and-ride); ~32% of the lone 5d
  positive eaten by the fill gap.
- **The "thrust" is a level gate** ([[thrust-degenerates-to-level]]) — all lift in the loosest near-level
  cell `high=50`, collapsing at `high=60`; firing un-holdable across the grid (+46/+47/−52% per-step). And
  62% of firings sit in 2009–2014 — a [[lottery-vs-signature]] one-tape artifact.

## What a successor needs (do NOT resurrect this gate)

The breadth-event premise *class* is re-scopable (no `config_hash` burned), but **not by tuning
`window`/`low`/`high`** (no robust corner) and **not by bolting a SPY-regime gate on top** (= IS-fitting
the very sign-flip that condemns it). A successor must be a **structurally different transition predicate**
— a true multi-day thrust/slope measure that is *regime-sign-consistent* (holds positive sign when it
fires), screened from scratch as a fresh candidate **and** screened-together-with-[[gjallarhorn]] to avoid
double-counting the same breadth read.

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
  in the anatomy wins. **RSP ingested (#99 done) — buildable now.** **Data-span caveat (permanent):** RSP
  launched **2003** → covers the 2005-2015 screen window + GFC/COVID/2022 + Blocks B/C, but **truncates
  Block A to 2003-2014 (misses the 2000-2002 dot-com bear)** — a partial gap to disclose, not a full
  disqualifier; the internal breadth-thrust (full Block-A span) is the preferred primary signal.
- **Breadth as THRUST/TRANSITION, not LEVEL.** Entry-day breadth *level* does not predict win rate (flat
  ~33-35% from breadth <30 to >60) — it only scales payoff magnitude. A `marketBreadth > 50%` level gate
  only **thins**, never **selects** ([[thinning-not-selecting]]) — empirically why Track-2/2b were
  rejected. BTC+Tyr's core signal must be the **thrust/transition**, which a daily breadth reading can't
  see but a multi-day thrust can.
- **Avoid the dilutive loose tails** — the breakout's `%52wHigh(25)`, `%52wLow(30)`, Donchian-"near"
  tails were dilutive; if BTC+Tyr reuses any trend-template conditions, tighten them as a *fresh*
  hypothesis (validate OOS), don't copy the breakout's loose defaults.

## Reproducing

The one recoverable config so far is **Tyr's solo `/condition-screen`** (2026-05-31), persisted beside
this entity at **`btc-tyr.request.json`** (ADR 0017). It is a **conditions-screen** request
(`POST /api/conditions/screen`) — `marketBreadthRecovering() AND orderBlockBreakout(2, 5, 0)` on the
default full `STOCK` universe over the standard screen window (2000-01-01 → 2021-01-01 leakage cap,
`entryDelayDays` 1, horizons 5/10/20):

```bash
API_KEY=… .claude/scripts/udgaard-post.sh /api/conditions/screen \
  @knowledge/wiki/entities/btc-tyr.request.json /tmp/condition-screen-tyr.json
```

**Scope caveat:** this captures the *solo-Tyr* differentiator that earned NO PROCEED, **not** the
combined **BTC + Tyr** candidate — that one is still SCOPING (no spec, no screen), so no combined request
JSON exists yet. When BTC + Tyr is screened, persist *its* request as the new canonical
`btc-tyr.request.json` (one skeleton per candidate, ADR 0017) and note the solo-Tyr config here. The
order-block leg's own standalone screen is persisted separately on [[order-block-breakout-condition]].

## Related

[[gjallarhorn]] (sibling breadth-event family) · [[the-funnel]] · [[crisis-timer-cadence-ceiling]] ·
[[participate-and-lose]] · [[thinning-not-selecting]] · [[lottery-vs-signature]] ·
[[order-block-breakout-condition]] · [[long-premise-in-narrow-leadership]] · [[purpose]]
