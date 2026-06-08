---
type: entity
title: PEAD — Post-Earnings Announcement Drift
summary: NEW active direction (2026-06-08) — event-conditioned long: enter on a confirmed positive earnings-day gap, hold the underreaction drift. The regime-orthogonal 5th premise class. Feasibility GREEN.
status: active
tags: [candidate, event-driven, earnings, drift]
sources: ["knowledge/wiki/sources/2026-06-05-funnel-deepresearch-findings.md"]
related: ["[[participate-and-lose]]", "[[beta-delivery]]", "[[thinning-not-selecting]]", "[[lottery-vs-signature]]", "[[long-premise-in-narrow-leadership]]", "[[btc-tyr]]", "[[purpose]]"]
updated: 2026-06-08
---

# PEAD — Post-Earnings Announcement Drift

The **new active search direction** (quant-recommended 2026-06-08, after [[btc-tyr]] died and emptied the
funnel). The first member of an entirely **unexplored premise class: event-conditioned, per-name** entries
— the only corner off the regime-beta axis that killed all four deprecated families. Flagged as
"durable and factor-robust" by the funnel's own deep-research (G2, [[2026-06-05-funnel-deepresearch-findings]]).
Still **SCOPING** — feasibility confirmed, concrete condition spec + screen still to come.

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

## Most-likely death

[[beta-delivery]] via the back door (the gap selects high-momentum names). Detect early at condition-screen
by confirming the post-entry drift exists independent of the gap-day move, and by pairing G-RANDOM on the
event-eligible basket once selection is added.

## Related

[[btc-tyr]] (the death that opened this) · [[gjallarhorn]] · [[participate-and-lose]] · [[beta-delivery]] ·
[[thinning-not-selecting]] · [[lottery-vs-signature]] · [[long-premise-in-narrow-leadership]] · [[purpose]]
