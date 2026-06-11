---
type: source
title: quality-profitability-tilt firewall run (2026-06-12)
summary: Refined-firewall run for quality-profitability-tilt (2026-06-12); REJECTED at Block A (G6 2008 + structural G1/G15). First candidate to run a real Block A. Distilled into the entity.
status: stable
tags: [run, firewall, rejected]
sources: ["strategy_exploration/dossier/quality-profitability-tilt.jsonl", "docs/adr/0021-block-a-gfc-mandate-is-a-crash-survival-test-not-a-strict-positive-gate.md"]
related: ["[[quality-profitability-tilt]]", "[[participate-and-lose]]", "[[beta-delivery]]"]
updated: 2026-06-12
---

> **Distilled (2026-06-12).** Cross-layer read folded into [[quality-profitability-tilt]] + [[participate-and-lose]]
> + [[beta-delivery]]. Firewall-analyst verdict: REJECTED, root cause **G6 (2008 GFC edge −0.99%)**; the
> 2008 window drives G6/G3/most-of-G2, but **G1 (ex-2008 CAGR ~17.8%) and G15 (Calmar ~0.9) survive its
> excision** — structurally below floor. **NOT beta-delivery (G16 PASS, Calmar 0.512 vs SPY 0.164, 3.1×)**,
> NOT lottery (9/11 positive). The crisis-undefended [[participate-and-lose]] sub-type. First candidate to
> ever run a real Block A firewall → motivated ADR 0021 (the GFC-mandate recalibration).

# Validation Report — quality-profitability-tilt

**Verdict: REJECTED**  ·  Generated 2026-06-12T07:38:20

_Framework: refined-2026-05-28 (Block A + Block B + 25y binding; Block C informational)_

## Per-layer summary

| Layer | Binding | Range | Verdict | First failure | CAGR | DD | Sharpe | Calmar | Trades |
|---|---|---|---|---|---:|---:|---:|---:|---:|
| A | yes | Block A (2000-2014) | FAIL | G1_cagr | 18.57% | 36.25% | 0.83 | 0.51 | 2357 |
| B | yes | — | NOT RUN | — | — | — | — | — | — |
| 25y | yes | — | NOT RUN | — | — | — | — | — | — |
| C | **info** | — | NOT RUN | — | — | — | — | — | — |
## Per-layer gate detail

### Layer A — Block A (2000-2014)

| Gate | Status | Value | Threshold |
|---|---|---|---|
| G1_cagr | FAIL | 18.570035759173663 | >= 25.00% (max of 10, SPY+2, 25) — 25 dominates |
| G2_dd_aggregated | FAIL | 36.25180842744804 | <= 25% |
| G3_dd_per_window | FAIL | 30.37641909698678 | <= 20% in worst OOS window |
| G4_positive_pct | PASS | 9/11 = 81.8% | >= 75% positive (N >= 4 rule) |
| G5_cov_edge | PASS | 1.2860675204729815 | stdev/mean <= 1.5 |
| G6_regime_mand | FAIL | 2008 GFC OOS edge = -0.9910493046256865 | 2008 GFC OOS > 0 |
| G7_regime_chop | PASS | 2004=0.23149734911500586; 2011=0.8534407940157616 | >= 1 of {2004, 2011, 2015-H1} positive |
| G8_min_trades | PASS | 115 | >= 30 per OOS window |
| G9_sharpe | PASS | 0.8327372714083815 | Sharpe >= 0.5 |
| G15_calmar | FAIL | 0.5122512935137704 | Calmar >= 1.5 (absolute floor; binding A/B/25y, informational C) |
| G12_block_trades | PASS | 2357 | >= 100 trades in block aggregate |
| G16_spy_baseline | PASS | PASS (strategy Calmar=0.5122512935137704 vs SPY Calmar=0.16395324988929216) | strategy stitched Calmar >= SPY (binding A/B/25y; informational C) |

## Verdict explanation

Failed binding layer A. Candidate config is burned for this firewall run.
Indicated remediation axis: **regime_survival_redesign** (informational; firewall does NOT pre-approve specific changes).
Modifying this config and re-running is data-mining, NOT validation. Re-design the variant and re-enter via `/strategy-screen`.

## Reproducing

The exact, validated request JSON for this run is persisted beside the entity at
`knowledge/wiki/entities/quality-profitability-tilt.request.json` (ADR 0017). When /wiki-ingest distils this draft, carry the pointer
into the entity frontmatter (`request: "quality-profitability-tilt.request.json"`) and keep this
Reproducing section (george.md is the template). Re-run:

```bash
API_KEY=… .claude/scripts/udgaard-post.sh /api/backtest/walk-forward \
  @knowledge/wiki/entities/quality-profitability-tilt.request.json /tmp/quality-profitability-tilt.json
```

The pipeline overrides only `startDate`/`endDate` per firewall layer; every other field
(entry stack, exit, ranker, sizer, `maxPositions`, seed) is the persisted skeleton verbatim.
