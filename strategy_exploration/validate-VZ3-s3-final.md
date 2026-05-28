# Validation Report — VZ3-s3

**Verdict: REJECTED**  ·  Generated 2026-05-28T12:28:54

## Per-block summary

| Block | Range | Verdict | First failure | CAGR | DD | Sharpe | Calmar | Trades |
|---|---|---|---|---:|---:|---:|---:|---:|
| A | Block A (2000-2014) | PASS | — | 36.02% | 11.92% | 2.54 | 3.02 | 2761 |
| B | Block B (2014-2020 incl COVID) | PASS | — | 36.33% | 8.61% | 2.32 | 4.22 | 912 |
| C | Block C (2021-2025) | FAIL | G4b_block_cagr | 4.26% | 9.29% | 0.62 | 0.46 | 245 |
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
| G1_cagr | PASS | 36.329069253365546 | >= 30.00% (max of 10, SPY+2, 30) — 30 dominates |
| G2_dd_aggregated | PASS | 8.6114674698561 | <= 25% |
| G3_dd_per_window | PASS | 5.9896476884657055 | <= 20% in worst OOS window |
| G4_positive_pct | PASS | 4/4 = 100.0% | >= 75% positive (N >= 4 rule) |
| G5_cov_edge | PASS | 0.6949283021249998 | stdev/mean <= 1.5 |
| G6_regime_mand | PASS | 2020 COVID OOS edge = 0.3094377629315739 | 2020 COVID OOS > 0 |
| G7_regime_chop | PASS | 2018-Q4=0.3447751179514069 | >= 1 of {2015-H2, 2018-Q4} positive |
| G8_min_trades | PASS | 192 | >= 30 per OOS window |
| G9_sharpe_calmar | PASS | sharpe=2.3188671628984556 calmar=4.218685070870112 | Sharpe >= 0.8 AND Calmar >= 0.5 |
| G12_block_trades | PASS | 912 | >= 100 trades in block aggregate |

### Block C — Block C (2021-2025)

| Gate | Status | Value | Threshold |
|---|---|---|---|
| G2_dd_aggregated | PASS | 9.287946156336009 | <= 25% |
| G3_dd_per_window | PASS | 9.28794615633601 | <= 20% in worst OOS window |
| G4a_no_blowup | PASS | worst window CAGR = 4.263977724366819 | >= -5% (N < 4 fallback) |
| G4b_block_cagr | FAIL | 4.263977724366819 | >= 30.00% (block-aggregate; N < 4 fallback) |
| G5_cov_edge | FAIL | None | stdev/mean <= 1.5 |
| G6_regime_mand | FAIL | 2022 inflation bear OOS edge = None | 2022 inflation bear OOS > 0 |
| G7_regime_chop | PASS | skipped for this block | block has no defined chop regime |
| G8_min_trades | PASS | 245 | >= 30 per OOS window |
| G9_sharpe_calmar | FAIL | sharpe=0.6153752545973661 calmar=0.4590872570313123 | Sharpe >= 0.8 AND Calmar >= 0.5 |
| G12_block_trades | PASS | 245 | >= 100 trades in block aggregate |

## Verdict explanation

Failed Block C. Candidate config is burned for this firewall run.
Indicated remediation axis: **regime_survival_redesign** (informational; firewall does NOT pre-approve specific changes).
Modifying this config and re-running is data-mining, NOT validation. Re-design the variant and re-enter via `/strategy-screen`.
