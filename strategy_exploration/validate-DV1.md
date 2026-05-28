# Validation Report — DV1

**Verdict: REJECTED**  ·  Generated 2026-05-28T07:06:18

## Per-block summary

| Block | Range | Verdict | First failure | CAGR | DD | Sharpe | Calmar | Trades |
|---|---|---|---|---:|---:|---:|---:|---:|
| A | Block A (2000-2014) | FAIL | G1_cagr | 23.31% | 24.35% | 1.74 | 0.96 | 1607 |
| B | — | NOT RUN | — | — | — | — | — | — |
| C | — | NOT RUN | — | — | — | — | — | — |

## G11 — cross-block edge decay

- **Could not evaluate G11**: None

## Per-block gate detail

### Block A — Block A (2000-2014)

| Gate | Status | Value | Threshold |
|---|---|---|---|
| G1_cagr | FAIL | 23.307835140301126 | >= 30.00% (max of 10, SPY+2=10.0, 30%) |
| G2_dd_aggregated | PASS | 24.34642968779164 | <= 25% |
| G3_dd_per_window | FAIL | 24.346429687791634 | <= 20% in worst OOS window |
| G4_positive_pct | PASS | 9/11 = 81.8% | >= 75% positive (N >= 4 rule) |
| G5_cov_edge | PASS | 1.2884716848848838 | stdev/mean <= 1.5 |
| G6_regime_mand | FAIL | 2008 GFC OOS edge = -1.3475946049160437 | 2008 GFC OOS > 0 |
| G7_regime_chop | PASS | 2004=0.42952840805172676; 2011=0.44546584884165696 | >= 1 of {2004, 2011, 2015-H1} positive |
| G8_min_trades | PASS | 78 | >= 30 per OOS window |
| G9_sharpe_calmar | PASS | sharpe=1.7382593964859174 calmar=0.9573409916439899 | Sharpe >= 0.8 AND Calmar >= 0.5 |
| G12_block_trades | PASS | 1607 | >= 100 trades in block aggregate |

## Verdict explanation

Failed Block A. Candidate config is burned for this firewall run.
Modifying this config and re-running is data-mining, NOT validation. Re-design the variant and re-enter via `/strategy-screen`.
