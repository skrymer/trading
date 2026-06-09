---
type: source
title: RS-momentum class EARNED-DEAD — fixed-engine re-run of #130 + #137 vs seeded Random (2026-06-09)
summary: Fixed-engine re-run: single-factor (MRM) + multi-factor (#137) residual momentum both below the whole 10-seed Random cloud on edge AND CAGR (K=0/10) — anti-selective; class earned-dead.
status: stable
tags: [strategy-screen, g-random, beta-delivery, idiosyncratic-momentum, reject, verdict, rs-momentum]
sources: ["knowledge/wiki/entities/mrm.request.json", "knowledge/wiki/entities/multifactor-residual-momentum.request.json", "knowledge/wiki/entities/mrm.rerun-sweep.tsv", "docs/adr/0018-trailing-ranker-warmup-history-is-loaded-but-never-traded.md", "https://github.com/skrymer/trading/issues/130", "https://github.com/skrymer/trading/issues/137"]
related: ["[[mrm]]", "[[multifactor-residual-momentum]]", "[[beta-delivery]]", "[[the-funnel]]", "[[purpose]]", "[[2026-06-09-trailing-ranker-warmup-starvation]]", "[[long-premise-in-narrow-leadership]]"]
updated: 2026-06-09
---

# RS-momentum class earned-dead — fixed-engine re-run (2026-06-09)

The honest test the warmup-starvation finding ([[2026-06-09-trailing-ranker-warmup-starvation]], ADR 0018)
unblocked: re-run BOTH residual-momentum recipes on the fixed engine, against a **seeded Random
distribution**, with the ranker actually scoring in OOS.

## Setup (quant pre-registered)

- **Universe:** seeded uniform random sample, **N=1,500** from the ~4,934-symbol STOCK table
  (`universeSeed=7`, ~40% delisted preserved — no survivorship tilt), frozen, identical across all arms.
  Persisted: `entities/mrm.universe.txt`. (Reduced from the full universe for runtime/memory — the
  MRM-vs-Random contrast is universe-internal, so the shrink is unbiased; full-universe MRM control
  showed the same direction.)
- **Arms:** MRM (single-factor) seed 42 · MultiFactor (market+sector, #137) seed 42 · Random seeds 1–10.
  The Random baseline is ranker-agnostic, so the same 10 draws serve both candidates.
- **Scaffold:** the [[george]] neutral-entry ranker-screen (tradability-only entry, 126d/52wk exit,
  percentEquity 3.33%, maxPositions 30), 2005–2015, 36/12/12, seed 42. Only `ranker` varies.
- **Decision rule:** revived iff candidate > Random **p95** on per-trade edge AND blended CAGR;
  earned-dead if ≤ Random **median** on either. Primary metric per-trade edge (not win-rate/WFE).

## Result — both recipes EARNED-DEAD (K=0/10)

| Arm | OOS edge | Blended CAGR | Sharpe | SPY-base | vs Random |
|---|---|---|---|---|---|
| **MRM (single-factor)** | 0.588 | 5.52% | 0.25 | FAIL | **below all 10** on edge & CAGR |
| **MultiFactor (market+sector, #137)** | **0.249** | **2.02%** | 0.12 | FAIL | **below all 10** on edge & CAGR |
| Random (10 seeds) | min 0.80 / med 1.19 / max 1.43 | min 6.25 / med 9.91 / max 12.40 | ~0.5 | 4 PASS | — |

Both candidates sit **below the entire Random cloud** on both binding legs — not just losing, *anti-selective*
(they pick systematically worse names than a coin flip). Stripping the sector factor made it **worse**
(edge 0.59→0.25, CAGR 5.5%→2.0%), the opposite of the "sector momentum is the contaminant" hypothesis.

## What it concludes

- **The factor-neutral idiosyncratic-RS class is EARNED-DEAD.** Per the quant asymmetry
  ([[multifactor-residual-momentum]]): a market+sector FAIL kills the class — stripping size/value (the
  un-done Fama-French step) can only remove more signal, and the residual is already negatively selective.
  **No Fama-French sourcing needed.** The sector look-ahead leak only biases toward a weaker apparent
  signal (can erode a win, never manufacture this loss), so the FAIL is robust.
- **Refutes** the [[long-premise-in-narrow-leadership]] sub-hypothesis that narrow leadership *feeds*
  stock-specific (idiosyncratic) momentum — here, the more factors stripped, the more anti-selective.
- **Confirms** the void #130 *conclusion* (anti-selective beta-delivery) was right — but #130 measured it
  by accident (RNG-vs-RNG); this is the first *trustworthy* measurement. The engine fix didn't flip the
  answer, it made it defensible.
- **Validates ADR 0018** end-to-end: every run clean, ranker scoring in OOS, no warmup tripwire, no crash.

## Pages updated

[[mrm]] (disputed → earned-dead), [[multifactor-residual-momentum]] (new, earned-dead), [[purpose]] (#4
RS-momentum class closed), [[beta-delivery]] (both instances re-confirmed), [[long-premise-in-narrow-leadership]],
[[overview]], index, log.
