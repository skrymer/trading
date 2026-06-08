---
type: entity
title: PEAD — Post-Earnings Announcement Drift
summary: Active direction; first surprise proxy (OHLCV price gap) REJECTED at /condition-screen (regime sign-flip = beta-delivery). Class survives; proxy in redesign — market-neutral gap residual next.
status: active
tags: [candidate, event-driven, earnings, drift]
sources: ["knowledge/wiki/sources/2026-06-05-funnel-deepresearch-findings.md", "knowledge/wiki/sources/2026-06-09-pead-earnings-gap-screen-reject.md", "strategy_exploration/dossier/condition-earningsgap.jsonl"]
related: ["[[participate-and-lose]]", "[[beta-delivery]]", "[[thinning-not-selecting]]", "[[lottery-vs-signature]]", "[[long-premise-in-narrow-leadership]]", "[[aliased-regime-sensitivity]]", "[[btc-tyr]]", "[[purpose]]"]
updated: 2026-06-09
---

# PEAD — Post-Earnings Announcement Drift

The **new active search direction** (quant-recommended 2026-06-08, after [[btc-tyr]] died and emptied the
funnel). The first member of an entirely **unexplored premise class: event-conditioned, per-name** entries
— the only corner off the regime-beta axis that killed all four deprecated families. Flagged as
"durable and factor-robust" by the funnel's own deep-research (G2, [[2026-06-05-funnel-deepresearch-findings]]).

> **Status (2026-06-09):** the first surprise proxy — the OHLCV **price gap** — was **REJECTED** at
> design-time `/condition-screen` ([[2026-06-09-pead-earnings-gap-screen-reject]]). PEAD's pre-registered
> most-likely death ([[beta-delivery]] via the gap selecting high-momentum names) **materialised**: the
> gap reads market-direction microstructure, not firm-specific surprise. **The class survives** — only the
> OHLCV-gap shortcut died. Next proxy: a **market-neutral gap residual** (see *Redesign* below). This was a
> design-time reject — no `config_hash` burned, the firewall brake is not engaged.

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

## Redesign — next surprise proxy (quant consult 2026-06-09)

The reject condemns the **price-gap surprise proxy**, not the mechanism. The fix must isolate
firm-specific surprise content **orthogonal to that day's market move**. Ranked successors (each a new,
screened-from-scratch condition — never an iteration of the dead config):

1. **Market-neutral gap residual** (run first; PIT-clean, OHLCV-only): `residualAtr =
   (open[g]−close[g−1])/atr[g−1] − (spyOpen[g]−spyClose[g−1])/spyAtr[g−1]`. Subtracting the same-day SPY
   gap removes the common-factor component the regime tertiles isolate — exactly what flipped the sign.
   **Feasible today:** `BacktestContext.getSpyQuote(date)` is reachable from an inline script
   (`ConditionScreenService.buildContext` loads the SPY quote map). Sweep θ center 0.75 ATR ±0.5
   ({0.25,0.75,1.25}); screen relVol-gate both ways; close-near-high excluded (re-imports momentum).
   Pre-registered bar unchanged (20d ≥ +1.5%, ≥2×SE) **plus** flat-tape ≥ +1.0% and no regime sign-flip.
2. **EPS-surprise-gated residual** (reserved fallback): EPS confirms *sign only* (`surprisePercentage > 0`),
   the PIT-clean residual stays the trigger — never threshold on EPS magnitude (S4 restatement risk). EPS
   fields are 100% populated 2000-2020, but PIT-suspect, which is why this is second.

**Deferred — sector-neutral residual / sector-regime gating.** Subtracting the *sector* gap, or gating on
"sector in an uptrend" (operator idea), needs sector **price** at evaluation time — `BacktestContext` today
exposes only sector *breadth*, not a sector quote map. Sector-ETF OHLCV is available to 1998-12-22
(firewall-safe), so it's worth adding as a reusable capability — tracked in **issue #143**. ^[inferred]
Build trigger: the market-neutral residual survives but shows residual sector tilt, **or** we pursue the
sector-regime-gated composite as a candidate. Per [[lottery-vs-signature]] / [[participate-and-lose]]
discipline, any regime/sector gate must be designed into a **new** candidate from the start, never bolted
onto a post-OOS result.

## Most-likely death — MATERIALISED (2026-06-09)

[[beta-delivery]] via the back door (the gap selects high-momentum names) — **confirmed** at the first
screen: the price gap delivered SPY-direction beta (edge only in down-tape, flat/up negative), not
firm-specific drift. The redesign's whole job is to strip that beta out *before* entry.

## Related

[[btc-tyr]] (the death that opened this) · [[gjallarhorn]] · [[participate-and-lose]] · [[beta-delivery]] ·
[[thinning-not-selecting]] · [[lottery-vs-signature]] · [[long-premise-in-narrow-leadership]] · [[purpose]]
