---
type: entity
title: PEAD — Post-Earnings Announcement Drift
summary: Long-only-drift form ABANDONED (quant RECONSIDER_CLASS 2026-06-09) — all three surprise proxies REJECTED, beta enters via the holding-window return not the entry; #143/sector-residual predicted-dead for PEAD; next class = structurally beta-hedged. Mechanism untouched.
status: stable
tags: [candidate, event-driven, earnings, drift]
sources: ["knowledge/wiki/sources/2026-06-05-funnel-deepresearch-findings.md", "knowledge/wiki/sources/2026-06-09-pead-earnings-gap-screen-reject.md", "knowledge/wiki/sources/2026-06-09-pead-market-neutral-residual-screen-reject.md", "knowledge/wiki/sources/2026-06-09-pead-eps-gated-residual-screen-reject.md", "knowledge/wiki/sources/2026-06-09-pead-premise-class-adjudication.md", "strategy_exploration/dossier/condition-earningsgap.jsonl", "strategy_exploration/dossier/condition-earningsgapresidual.jsonl", "strategy_exploration/dossier/condition-earningsepsgatedresidual.jsonl"]
related: ["[[participate-and-lose]]", "[[beta-delivery]]", "[[thinning-not-selecting]]", "[[lottery-vs-signature]]", "[[long-premise-in-narrow-leadership]]", "[[aliased-regime-sensitivity]]", "[[btc-tyr]]", "[[purpose]]", "[[2026-06-09-pead-eps-gated-residual-screen-reject]]", "[[2026-06-09-pead-premise-class-adjudication]]"]
updated: 2026-06-09
---

# PEAD — Post-Earnings Announcement Drift

The **new active search direction** (quant-recommended 2026-06-08, after [[btc-tyr]] died and emptied the
funnel). The first member of an entirely **unexplored premise class: event-conditioned, per-name** entries
— the only corner off the regime-beta axis that killed all four deprecated families. Flagged as
"durable and factor-robust" by the funnel's own deep-research (G2, [[2026-06-05-funnel-deepresearch-findings]]).

> **Status (2026-06-09):** the **surprise-proxy axis is EXHAUSTED** — all **three** surprise proxies are
> now **REJECTED** at design-time `/condition-screen`, each dying the same way (20d flat-tape sign-flip
> negative). (1) The OHLCV **raw price gap** — beta-delivery, regime sign-flip
> ([[2026-06-09-pead-earnings-gap-screen-reject]]). (2) The **market-neutral gap residual** — sign-flip
> **PERSISTED after neutralisation** in both arms ([[2026-06-09-pead-market-neutral-residual-screen-reject]]).
> (3) The **EPS-surprise-gated residual** — a price-INDEPENDENT fundamental sign gate **also** left the
> flat-tape negative (−0.31%), headline 20d lift 0.03× SE ([[2026-06-09-pead-eps-gated-residual-screen-reject]]).
> Durable finding: the beta the earnings-event long entry delivers is **irreducible to ALL of** the gap
> itself, the same-night SPY-index gap, *and* the EPS surprise sign — i.e. to **every surprise proxy
> expressible on current data**. **The PEAD mechanism (slow diffusion of firm-specific news) is formally
> untouched**, but no current-data entry proxy isolates it. Per the pre-registered fork, next is a
> **class-level decision** (see *Redesign* below): reconsider the premise class, or the deferred
> sector-neutral residual (engine change #143). All three rejects were design-time — no `config_hash`
> burned, the firewall brake is not engaged.

## Premise

On a stock's **earnings announcement date** (an exogenous, dated, per-name event), the market
**underreacts** to the surprise; price **drifts** in the surprise's direction for weeks. Entry mechanic:

> Measure the surprise via the **post-report price gap** — the gap from the pre-report close to the
> post-report open (sign and size), optionally confirmed by same-day relative volume and a close-near-high.
> On a strong, confirmed **positive** gap, **enter long the next session** and hold a bounded drift window
> (~20–40 trading days) or until an ATR/structure exit.

**Why the price-gap surprise proxy** (not analyst-estimate EPS): the gap is the market's own real-time read
of the surprise, derived from OHLCV we have clean to 2000 — it sidesteps the much harder "consensus-EPS
history back to 2000" data problem. The raw dependency is just **earnings dates + OHLCV**.

## Why it's structurally distinct (off the axis that killed everything)

Every prior long death sits on one axis — *how the premise relates to the market regime*:
- **Continuous-state, always-eligible** premises ([[vz3]]/[[mr3]]/[[idunn]] pullback-MR,
  [[minervini-vcp-breakout]] breakout, [[george]]/[[mrm]] RS-momentum) are eligible on every name every
  day; their alpha is conditional on broad participation they can't see at entry → [[participate-and-lose]],
  unrescued by any scalar/per-name gate ([[thinning-not-selecting]]).
- **Market-timing** premises ([[gjallarhorn]], [[btc-tyr]]) escape that but can't populate OOL folds
  ([[crisis-timer-cadence-ceiling]]) or degenerate to a level read ([[thrust-degenerates-to-level]]).

PEAD breaks the axis: alpha is anchored to a **per-name, exogenous, dated event**, not a market state or a
universe-wide ranking. It is eligible only in the ~few days around *its own* report — the cross-sectional
resolution every scalar/breadth gate provably lacked.

## Why it could survive narrow-leadership tape (the mechanism)

The edge source is **slow information diffusion about a single firm** (anchoring/underreaction) — an
investor-behaviour effect documented across decades, *not* a factor exposure. In narrow tape the leaders
still report and still drift; PEAD self-selects into whichever names had positive surprises (in a narrow
tape, that *is* the concentrated leadership) and is **never eligible on un-surprised laggards** —
eligible-only-when-the-name-edge-is-present, the opposite of [[participate-and-lose]]. Tie to
[[long-premise-in-narrow-leadership]].

## Feasibility — GREEN (verified 2026-06-08)

- **Provider depth:** EODHD `/fundamentals` Earnings::History for AAPL reaches **1993-12-31** (131
  quarters); payload carries `reportDate`, `beforeAfterMarket` (BMO/AMC — needed for gap-entry timing), and
  surprise fields. The ~2000 data-span kill-switch is cleared.
- **PRD data populated:** the udgaard `earnings` table holds **245,843 rows / 3,712 symbols**, dense across
  the firewall window (2000 ≈ 4,900 events / 1,295 symbols → 2019 ≈ 10,900 / 2,841). No ingestion run
  needed. (Dev DBs are empty — populate dev before any local run.)
- **Cadence:** thousands of events/yr — abundant fold population, the inverse of the Gjallarhorn ceiling.
- **Data-quality caveat:** a small set of **`1899` sentinel `report_date` values** (placeholder for missing
  dates) must be filtered at screen/condition time.

## What it must clear

- **First funnel step — `/condition-screen` the EarningsGapCondition in isolation** (gap + confirmation
  sweep, horizons 5/20/40d, `endDate` capped at Block C per ADR 0007).
- **Kill if:** SPY-regime forward-return **sign-flips** across down/flat/up at 20d (would prove regime-beta,
  not event-alpha — the [[btc-tyr]] death); OR lift concentrates in the loosest gap-threshold cell and
  collapses as a real surprise is demanded ([[thrust-degenerates-to-level]]); OR firing concentrates in one
  tape ([[lottery-vs-signature]]).
- **The decisive early test:** does the drift survive **after next-session entry** (not just the gap day you
  already missed)? If the edge is all the gap day, it's a momentum/beta artifact → reject at screen.
- **[[beta-delivery]] guard:** the moment any ranking/selection is added, pair a **G-RANDOM** baseline drawn
  from the *event-eligible* population — beat it on per-trade edge AND CAGR or the "edge" is entry-universe
  beta.
- **CAGR floor** 25% as the eventual standalone tradability bar ([[purpose]]).

## Funnel history

| Date | Event | Result |
|---|---|---|
| 2026-06-08 | `quant-analyst` next-premise consult (post-[[btc-tyr]] death) | **PEAD = top pick** (event-conditioned, regime-orthogonal 5th class) |
| 2026-06-08 | Data feasibility check (EODHD depth probe + PRD earnings query) | **GREEN** — AAPL→1993; PRD 245k rows / 3,712 symbols, dense 2000-2019 |
| 2026-06-09 | `EarningsGapCondition` (OHLCV price-gap proxy, gapAtr 1.0±0.5) — first `/condition-screen` (300-sym sanity) | **REJECT** — 20d SPY-regime sign-flip (down +1.73% / flat −0.38% / up −0.56%), 20d meanLift +0.114% = 0.44× SE (bar +1.5%), non-monotone gap-size island. Proxy dead, class alive ([[2026-06-09-pead-earnings-gap-screen-reject]]) |
| 2026-06-09 | Market-neutral gap residual (residualAtr, θ 0.75±0.5), arms A (no vol gate) + B (relVol≥1.5) — `/condition-screen` (300-sym sanity), quant-signed-off | **REJECT — KILL TRIGGER** — 20d SPY-regime sign-flip **PERSISTED after neutralisation** in BOTH arms (A down +0.90/flat −0.52/up −0.57; B down +1.07/flat −0.20/up −0.89), flat-tape negative. 20d headline negative+sub-SE (A −0.13%/−0.46×; B −0.08%/−0.30×). θ-1.25 cell negative. Vol gate = thinning-not-selecting. **Price-based proxy class condemned → escalate to EPS-gated residual** ([[2026-06-09-pead-market-neutral-residual-screen-reject]]) |
| 2026-06-09 | EPS-surprise-gated residual (residualAtr ≥ θ AND `surprise>0` via `beatEstimates()`, θ 0.75±0.5) — run A `/condition-screen` (300-sym sanity), data-quality-gate PASS, quant-signed-off | **REJECT — KILL TRIGGER (axis exhausted)** — a price-INDEPENDENT EPS-sign gate **still** sign-flipped: 20d down +0.99% (n664) / flat **−0.31%** (n903) / up −0.51% (n873), flat-tape negative; near-equal tertile firing (genuine cross-sectional flip). 20d headline meanLift **−0.0069% = 0.03× SE** (abs 20d return = universe baseline = pure beta). θ-island all-negative {−0.056%, −0.0069%, −0.070%}. Horizon non-monotone. EPS gate thinned ~21% (3,093→2,443) = thinning-not-selecting. **Surprise-proxy axis EXHAUSTED** → class-level decision ([[2026-06-09-pead-eps-gated-residual-screen-reject]]) |

## Redesign — next surprise proxy (quant consult 2026-06-09)

The rejects condemn the **price-based surprise proxy class**, not the mechanism. The fix must isolate
firm-specific surprise content **orthogonal to that day's market move**. Ranked successors (each a new,
screened-from-scratch condition — never an iteration of a dead config):

1. ~~**Market-neutral gap residual** (PIT-clean, OHLCV-only): `residualAtr =
   (open[g]−close[g−1])/atr[g−1] − (spyOpen[g]−spyClose[g−1])/spyAtr[g−1]`.~~ **RUN + REJECTED 2026-06-09**
   ([[2026-06-09-pead-market-neutral-residual-screen-reject]]). Subtracting the same-night SPY gap did
   **not** remove the regime-tertile sign-flip — flat-tape stayed negative in both the no-vol-gate and
   relVol≥1.5 arms. Durable: the gap's beta is **irreducible to the same-day SPY-index-gap factor**, so the
   OHLCV market-neutral-residual repair path is dead, not merely one parametrisation of it.
2. ~~**EPS-surprise-gated residual** (the last price-independent surprise test): EPS confirms *sign only*
   (`residualAtr ≥ θ AND surprise > 0` via `beatEstimates()`), the PIT-clean residual stays the
   trigger — never threshold on EPS magnitude (S4 restatement risk).~~ **RUN + REJECTED 2026-06-09**
   ([[2026-06-09-pead-eps-gated-residual-screen-reject]]). The price-independent fundamental sign gate
   **also** left the 20d flat-tape negative (−0.31%), with a zero-lift headline (20d meanLift 0.03× SE).
   Data-quality gate passed first (gated on `surprise`, 100% non-null, not the gappier
   `surprisePercentage`; ~21% thin). **This was the pre-registered KILL: PEAD's surprise-proxy axis is
   now EXHAUSTED.** Three independent surprise measures all delivered the same SPY-direction beta — the
   beta is irreducible to the gap, the same-night SPY gap, AND the EPS surprise sign. Do **not** invent a
   4th price proxy.

**Class-level decision — ADJUDICATED: RECONSIDER_CLASS** (`quant-analyst` 2026-06-09,
[[2026-06-09-pead-premise-class-adjudication]]). The long-only-drift form is **abandoned**. The three
rejects are one failure re-confirmed under strengthening controls: the EPS-sign gate is a
**price-independent** control (strictly stronger than a sector residual), and the flat tertile still went
negative — so even on **genuine fundamental beats**, the entry pays only in down-tape. The beta therefore
enters through the **holding-window forward return** (oversold-down-tape mean-reversion), **not** the
entry selection. Consequence for option (b): a sector-neutral residual is a *weaker, entry-day*
co-movement control aimed at a *holding-window* failure that a stronger control already failed to fix →
**#143/sector-residual is predicted-dead FOR PEAD**; build #143 only if a *different* premise needs sector
quotes, never justify it on PEAD. Next class steer: a **structurally beta-hedged** premise (paired /
spread long-short / dispersion) that nets the SPY-direction component out **in the P&L**, not hoped-away
at entry — route the 6th-class mechanism to a fresh quant consult before scoping.

**Deferred — sector-neutral residual / sector-regime gating.** Subtracting the *sector* gap, or gating on
"sector in an uptrend" (operator idea), needs sector **price** at evaluation time — `BacktestContext` today
exposes only sector *breadth*, not a sector quote map. Sector-ETF OHLCV is available to 1998-12-22
(firewall-safe), so it's worth adding as a reusable capability — tracked in **issue #143**. ^[inferred]
Build trigger: the market-neutral residual survives but shows residual sector tilt, **or** we pursue the
sector-regime-gated composite as a candidate. Per [[lottery-vs-signature]] / [[participate-and-lose]]
discipline, any regime/sector gate must be designed into a **new** candidate from the start, never bolted
onto a post-OOS result.

## Most-likely death — MATERIALISED (2026-06-09)

[[beta-delivery]] via the back door (the entry selects high-momentum names) — **confirmed three times**.
(1) The raw price gap delivered SPY-direction beta (edge only in down-tape, flat/up negative). (2) The
market-neutral residual — built specifically to strip that beta — **still** sign-flipped (flat-tape
negative, both arms), proving the gap's beta is irreducible to the same-day SPY-index-gap factor. (3) The
EPS-surprise-gated residual — a **price-independent** fundamental sign gate — **also** sign-flipped
(flat −0.31%, headline 0.03× SE), proving the beta is irreducible even to the fundamental surprise sign.
The redesign's whole job (strip the beta *before* entry) is **not achievable through any surprise proxy
expressible on current data**; the surprise-proxy axis is **exhausted**.

## Related

[[btc-tyr]] (the death that opened this) · [[gjallarhorn]] · [[participate-and-lose]] · [[beta-delivery]] ·
[[thinning-not-selecting]] · [[lottery-vs-signature]] · [[long-premise-in-narrow-leadership]] · [[purpose]]
