---
type: synthesis
title: Overview — state of the search
summary: State of the search — zero tradable strategies; active direction PEAD (event-conditioned earnings drift, regime-orthogonal 5th class) had its first surprise proxy (price gap) REJECTED at screen (beta-delivery) — class alive, proxy in redesign; one shelved overlay (Gjallarhorn).
status: active
tags: [overview]
updated: 2026-06-08
---

# Overview — state of the search

The evolving global summary: where strategy research stands right now. Read [[index]] for the catalog and
[`../purpose.md`](../purpose.md) for the goal; this page is the *current position*.

## One-paragraph state (2026-06-08)

**Zero tradable strategies, and the funnel is now empty.** The active search — **BTC+Tyr** — **died at
design-time screen on 2026-06-08** ([[2026-06-08-btc-breadth-thrust-screen-reject]]): its genuinely-fresh
component, the breadth-thrust GATE, failed in isolation (SPY-regime sign-flip at all 3 horizons, no
10/20d edge, the "thrust" degenerating into a level gate — [[thrust-degenerates-to-level]] — carried by
the 2009–14 tape). Three long premise classes remain earned-dead (long-pullback MR, breakout-in-uptrend,
leveraged-ETF timing), each reducing to [[participate-and-lose]] under a too-coarse regime selector that
no flavor rescues ([[thinning-not-selecting]]). A fourth — **cross-sectional RS-momentum rotation** — is
**downgraded to untested**: the [[george]] flavour is dead, the single-factor [[mrm]] variant's screen
reject is **VOID** (warmup-starvation artifact, ADR 0018 — [[2026-06-09-trailing-ranker-warmup-starvation]];
re-opened, pending re-run), and a **multi-factor-neutral** residual (#137) is built and un-ruled-out. The
regime-conditional *portfolio* ambition is abandoned (long-only ⇒ defense = cash ⇒ no viable second long
component). [[gjallarhorn]] is the one research-confirmed asset — a +22σ timing-alpha crisis-bottom timer —
but its composite A/B with the breakout was a **NO-GO** (2026-06-04), leaving it shelved and homeless.
**The active direction is [[pead]]** — Post-Earnings Announcement Drift, the first member of an
unexplored **event-conditioned, per-name** premise class (the regime-orthogonal "5th class" the
deep-research flagged as durable). Data feasibility is **GREEN** (EODHD earnings to 1993; PRD table 245k
rows / 3,712 symbols, dense 2000-2019). Its first surprise proxy — the OHLCV **price gap** — was
**REJECTED** at design-time `/condition-screen` (2026-06-09): a 20d SPY-regime sign-flip (down +1.73% /
flat −0.38% / up −0.56%) proved the gap delivers market-direction beta, not firm-specific surprise —
[[beta-delivery]] materialised ([[2026-06-09-pead-earnings-gap-screen-reject]]). **The class survives;**
the surprise proxy is in redesign — next is a **market-neutral gap residual** (subtract the same-day SPY
gap; `getSpyQuote` makes it feasible today), EPS-surprise-gated residual reserved. A sector-neutral /
sector-regime variant is deferred behind a reusable engine change (sector quote map, issue #143).

## Live threads

| Thread | State |
|---|---|
| [[pead]] candidate | **Active direction** — first proxy (OHLCV price gap) REJECTED at `/condition-screen` (2026-06-09, regime sign-flip = [[beta-delivery]]); class alive; next = market-neutral gap residual (feasible now), sector variant behind issue #143 |
| [[btc-tyr]] candidate | ⛔ **DEAD** (2026-06-08) — breadth-thrust gate failed solo `/condition-screen`; NOT a firewall death; class re-scopable only via a structurally different transition predicate |
| #137 residual-momentum | FILED thread (lower priority than PEAD) — a multi-factor-neutral residual-momentum ranker vs a seeded Random baseline; run to close the RS-momentum class ([[mrm]], [[purpose]] #4) |
| Gjallarhorn composite | ❌ composite A/B **NO-GO** (2026-06-04) — structural (fixed crisis sleeve = cash drag + correlated crisis DDs); shelved matched-pair with the breakout, no iteration |
| Regime-transition layer | #83 — the missing piece that would host the shelved breakout + Gjallarhorn |
| Firewall recalibration | ✅ shipped — G16 (#102), DSR flag (#105), G1/G9/G15 gate recalibration (#106, 2026-06-06). Gates re-confirmed against the cost+idle-cash engine — KEEP, no recalibration ([[2026-06-06-gate-basis-and-cagr-floor-feasibility]]) |
| Transaction-cost model | ✅ shipped (#101) — all metrics now net-by-default 10 bps |
| Idle-cash crediting | ✅ shipped (#103, ADR 0016) — idle cash earns the historical 3-mo T-bill rate, default ON, Sharpe-neutral |

## What would change this page

The PEAD **market-neutral gap residual** `/condition-screen` result (proceed, or — if the regime sign-flip
persists — the price-proxy class dies and EPS-surprise is the last test before abandoning the surprise-proxy
axis); a #137 run; a passing composite A/B; or a regime-transition layer that lets a shelved component
re-enter. Until then the headline is: *zero tradable; the PEAD class is alive but its first surprise proxy
died at screen (beta-delivery); one shelved overlay.*
