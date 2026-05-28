# Validation Report — MR3-s1

**Verdict: REJECTED**  ·  Generated 2026-05-28T07:34:32

## Per-block summary

| Block | Range | Verdict | First failure | CAGR | DD | Sharpe | Calmar | Trades |
|---|---|---|---|---:|---:|---:|---:|---:|
| A | Block A (2000-2014) | FAIL | G3_dd_per_window | 43.83% | 20.47% | 2.58 | 2.14 | 4830 |
| B | — | NOT RUN | — | — | — | — | — | — |
| C | — | NOT RUN | — | — | — | — | — | — |

## G11 — cross-block edge decay

- **Could not evaluate G11**: None

## Per-block gate detail

### Block A — Block A (2000-2014)

| Gate | Status | Value | Threshold |
|---|---|---|---|
| G1_cagr | PASS | 43.82959430941531 | >= 30.00% (max of 10, SPY+2=10.0, 30%) |
| G2_dd_aggregated | PASS | 20.468889968964383 | <= 25% |
| G3_dd_per_window | FAIL | 20.468889968964397 | <= 20% in worst OOS window |
| G4_positive_pct | FAIL | 8/11 = 72.7% | >= 75% positive (N >= 4 rule) |
| G5_cov_edge | FAIL | 1.7730764466846594 | stdev/mean <= 1.5 |
| G6_regime_mand | PASS | 2008 GFC OOS edge = 1.1856292422526082 | 2008 GFC OOS > 0 |
| G7_regime_chop | PASS | 2004=0.633988501233226; 2011=-0.7380783169242364 | >= 1 of {2004, 2011, 2015-H1} positive |
| G8_min_trades | PASS | 364 | >= 30 per OOS window |
| G9_sharpe_calmar | PASS | sharpe=2.581170797974278 calmar=2.1412785146566917 | Sharpe >= 0.8 AND Calmar >= 0.5 |
| G12_block_trades | PASS | 4830 | >= 100 trades in block aggregate |

## Verdict explanation

Failed Block A. Candidate config is burned for this firewall run.
Modifying this config and re-running is data-mining, NOT validation. Re-design the variant and re-enter via `/strategy-screen`.
