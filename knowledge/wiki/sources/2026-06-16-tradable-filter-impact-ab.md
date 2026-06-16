---
type: source
title: Tradable-filter impact A/B — the filter strips most of minervini's unfiltered edge (2026-06-16)
summary: First quantified A/B of the #173/#179 tradable filter — minervini base, filter on vs off, full 25y PRD. The filter cut ~22% of trades but ~73% of per-trade edge (3.94→1.06) and ~79% of CAGR (14.9→3.2): a majority of the apparent edge lived in illiquid/sub-$5 single-trade-lottery names. Quantifies why pre-filter verdicts overstate tradable edge.
status: stable
tags: [run, universe, liquidity-filter, universe-epoch, ab-test, data-readiness]
sources: ["docs/adr/0026-tradable-universe-is-liquidity-gated-and-universe-changes-open-an-epoch.md", "strategy_exploration/assessments/minervini-vcp-breakout/minervini-vcp-breakout.request.json"]
related: ["[[universe-epoch]]", "[[minervini-vcp-breakout]]", "[[component-firewall]]", "[[lottery-vs-signature]]"]
updated: 2026-06-16
---

# Tradable-filter impact A/B (2026-06-16)

**What was run.** A single A/B against PRD (udgaard 1.0.96, the cap-floor deploy): the
[[minervini-vcp-breakout]] base config, full 25y (2000–2025), continuous `/backtest`, **identical in every
field except `applyLiquidityFilter`** (true vs false; `false` reproduces the pre-#173 unfiltered universe).
A demonstration of the [[universe-epoch]] filter "in action", not a strategy verdict (minervini is already
REJECTED). Run sequentially (no concurrent backtests). Plain `/backtest`, not the firewall — no trial counted.

## Headline — the filter strips most of the apparent edge

| metric | filtered (tradable) | unfiltered (pre-#173) | delta |
|---|---|---|---|
| total trades | 759 | 975 | −22% |
| **per-trade edge** | **+1.06%** | +3.94% | **−73%** |
| **CAGR** | **3.2%** | 14.9% | **−79%** |
| Sharpe | 0.17 | 0.70 | −76% |
| Calmar | 0.05 | 0.37 | −86% |
| win rate | 31.0% | 33.5% | — |

Removing ~22% of trades erased ~73% of the per-trade edge. **A large majority of minervini's 25-year
apparent edge lived in names the tradable filter excludes.**

## Mechanism — the removed names are the illiquid / sub-$5 single-trade-lottery tail

The top profit contributors that the filter removed (their stats in the *unfiltered* run):

| name | total profit % | trades | edge % | why excluded |
|---|---|---|---|---|
| `TGISQ` | +248 | 1 | +248 | ~1,850 sh/day (~$25K/day $-vol); `Q` = bankruptcy. Un-executable. |
| `ALVU` | +214 | 1 | +214 | ~2,231 sh/day. |
| `MGIC` | +164 | 1 | +164 | median close **$3.09** (< $5 floor). |
| `GHM` | +207 | 3 | +69 | ~$400K/day $-vol (< $1M floor). |

The pattern is unmistakable: **single-trade +100–250% pops in names you could never have filled at the
modelled price** — the exact "fiction" the 10 bps cost model breaks on (CONTEXT.md "Trading universe"). The
filter strips them; what remains (edge +1.06, CAGR 3.2%) is the realistically-tradable reality.

**Honesty caveat.** The two runs are **not a clean subset** — the filtered run's ranker re-selects from the
changed cohort (637 distinct names, 219 of them new vs the unfiltered set), so the per-name "removed names
carried 57% of unfiltered profit" figure conflates filter-exclusion with re-selection. The **aggregate
metric deltas above are the clean filter effect** (one config, one flag) and are unambiguous; the per-name
table illustrates the *mechanism*, not a clean decomposition.

## What it taught (durable)

- **The tradable filter is not cosmetic — it materially changes tradable edge.** First quantified
  measurement of the [[universe-epoch]] impact: for minervini, the difference between a 14.9%-CAGR fiction
  and a 3.2%-CAGR reality.
- **Pre-filter (pre-#173) backtests systematically overstate tradable edge** — a chunk of their P&L is in
  un-fillable names. This is the empirical force behind ADR 0026's supersede-and-re-validate rule: a verdict
  earned on the unfiltered universe is not a tradable verdict. ^[inferred — the generalization beyond
  minervini is synthesis; the minervini deltas are measured]
- **Minervini-specific:** reconfirms REJECTED and quantifies that its apparent edge was illiquid-tail-carried
  — the *inverse* of a filter-rescuable corpse. Like DV1/MR3's `minPrice≥5` re-fires, the gate makes it
  **more** dead — corroborating the change is a correctness fix, not a revival lever.
- **The illiquid names are kept in the DB on purpose** (the *measurement* universe — breadth / gap / regime
  / survivorship / point-in-time tradability / cross-sectional percentiles). The same names that must stay
  for measurement are correctly excluded from trading by the filter: two populations, one DB (ADR 0026).

## Process footnote (the readiness bug this surfaced)

The A/B was initially blocked by a **silent 0-trades** result: the 2026-06-15 PRD re-ingest pulled raw
quotes before Midgaard's RS/quality cross-sectional passes had run, so udgaard ingested **null
`relativeStrengthPercentile`** universe-wide → minervini's `RS ≥ 70` AND-gate failed closed → 0 trades, with
nothing surfacing it. Not a filter/code/request bug — a data-readiness ordering gap. Fixed by re-ingesting
udgaard (Midgaard already held the percentiles); hardened as issue **#124** (a fail-fast prerequisite gate
+ derived-data readiness reporting). See #124.
