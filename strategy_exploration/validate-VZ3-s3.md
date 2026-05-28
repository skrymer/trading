# Validation Report — VZ3-s3

**Verdict: REJECTED**  ·  Generated 2026-05-28T08:09:45

## Per-block summary

| Block | Range | Verdict | First failure | CAGR | DD | Sharpe | Calmar | Trades |
|---|---|---|---|---:|---:|---:|---:|---:|
| A | Block A (2000-2014) | PASS | — | 36.02% | 11.92% | 2.54 | 3.02 | 2761 |
| B | Block B (2014-2020 incl COVID) | FAIL | G1_cagr | 28.39% | 4.74% | 2.92 | 5.99 | 615 |
| C | — | NOT RUN | — | — | — | — | — | — |

## G11 — cross-block edge decay

- **Could not evaluate G11**: None

## Per-block gate detail

### Block A — Block A (2000-2014)

| Gate | Status | Value | Threshold |
|---|---|---|---|
| G1_cagr | PASS | 36.018536050906725 | >= 30.00% (max of 10, SPY+2=10.0, 30%) |
| G2_dd_aggregated | PASS | 11.920842479787614 | <= 25% |
| G3_dd_per_window | PASS | 11.92084247978762 | <= 20% in worst OOS window |
| G4_positive_pct | PASS | 10/11 = 90.9% | >= 75% positive (N >= 4 rule) |
| G5_cov_edge | PASS | 0.7100188624131297 | stdev/mean <= 1.5 |
| G6_regime_mand | PASS | 2008 GFC OOS edge = 0.3936218262641087 | 2008 GFC OOS > 0 |
| G7_regime_chop | PASS | 2004=0.4724572943145302; 2011=-0.07007369113311368 | >= 1 of {2004, 2011, 2015-H1} positive |
| G8_min_trades | PASS | 174 | >= 30 per OOS window |
| G9_sharpe_calmar | PASS | sharpe=2.539170931731726 calmar=3.0214757146550637 | Sharpe >= 0.8 AND Calmar >= 0.5 |
| G12_block_trades | PASS | 2761 | >= 100 trades in block aggregate |

### Block B — Block B (2014-2020 incl COVID)

| Gate | Status | Value | Threshold |
|---|---|---|---|
| G1_cagr | FAIL | 28.392821050074456 | >= 30.00% (max of 10, SPY+2=10.0, 30%) |
| G2_dd_aggregated | PASS | 4.740435250786694 | <= 25% |
| G3_dd_per_window | PASS | 4.740435250786682 | <= 20% in worst OOS window |
| G4a_no_blowup | PASS | worst window CAGR = 8.843769339405206 | >= -5% (N < 4 fallback) |
| G4b_block_cagr | FAIL | 28.392821050074456 | >= 30.00% (block-aggregate; N < 4 fallback) |
| G5_cov_edge | PASS | 0.6980884374486142 | stdev/mean <= 1.5 |
| G6_regime_mand | FAIL | 2020 COVID OOS edge = None | 2020 COVID OOS > 0 |
| G7_regime_chop | PASS | 2018-Q4=0.3447751179514069 | >= 1 of {2015-H2, 2018-Q4} positive |
| G8_min_trades | PASS | 192 | >= 30 per OOS window |
| G9_sharpe_calmar | PASS | sharpe=2.9207252656157032 calmar=5.989496649144728 | Sharpe >= 0.8 AND Calmar >= 0.5 |
| G12_block_trades | PASS | 615 | >= 100 trades in block aggregate |

## Verdict explanation

Failed Block B. Candidate config is burned for this firewall run.
Modifying this config and re-running is data-mining, NOT validation. Re-design the variant and re-enter via `/strategy-screen`.
