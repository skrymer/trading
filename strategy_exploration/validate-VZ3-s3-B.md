# Validation Report — VZ3-s3-B

**Verdict: REJECTED**  ·  Generated 2026-05-28T12:19:12

## Per-block summary

| Block | Range | Verdict | First failure | CAGR | DD | Sharpe | Calmar | Trades |
|---|---|---|---|---:|---:|---:|---:|---:|
| A | Block A (2000-2014) | PASS | — | 46.64% | 10.89% | 2.77 | 4.28 | 2579 |
| B | Block B (2014-2020 incl COVID) | FAIL | G6_regime_mand | 42.25% | 7.20% | 2.35 | 5.87 | 925 |
| C | — | NOT RUN | — | — | — | — | — | — |
## Per-block gate detail

### Block A — Block A (2000-2014)

| Gate | Status | Value | Threshold |
|---|---|---|---|
| G1_cagr | PASS | 46.639299934521276 | >= 30.00% (max of 10, SPY+2, 30) — 30 dominates |
| G2_dd_aggregated | PASS | 10.891577856927503 | <= 25% |
| G3_dd_per_window | PASS | 10.891577856927505 | <= 20% in worst OOS window |
| G4_positive_pct | PASS | 10/11 = 90.9% | >= 75% positive (N >= 4 rule) |
| G5_cov_edge | PASS | 0.5933586188469464 | stdev/mean <= 1.5 |
| G6_regime_mand | PASS | 2008 GFC OOS edge = 0.5515020692874559 | 2008 GFC OOS > 0 |
| G7_regime_chop | PASS | 2004=0.8932499507627152; 2011=-0.0018323525433359134 | >= 1 of {2004, 2011, 2015-H1} positive |
| G8_min_trades | PASS | 158 | >= 30 per OOS window |
| G9_sharpe_calmar | PASS | sharpe=2.7690786937781477 calmar=4.282143555982269 | Sharpe >= 0.8 AND Calmar >= 0.5 |
| G12_block_trades | PASS | 2579 | >= 100 trades in block aggregate |

### Block B — Block B (2014-2020 incl COVID)

| Gate | Status | Value | Threshold |
|---|---|---|---|
| G1_cagr | PASS | 42.247223527799264 | >= 30.00% (max of 10, SPY+2, 30) — 30 dominates |
| G2_dd_aggregated | PASS | 7.2000483503287835 | <= 25% |
| G3_dd_per_window | PASS | 7.098711890500789 | <= 20% in worst OOS window |
| G4_positive_pct | PASS | 3/4 = 75.0% | >= 75% positive (N >= 4 rule) |
| G5_cov_edge | PASS | 1.2879258550452146 | stdev/mean <= 1.5 |
| G6_regime_mand | FAIL | 2020 COVID OOS edge = -0.04592709396660477 | 2020 COVID OOS > 0 |
| G7_regime_chop | PASS | 2018-Q4=0.21502638962214315 | >= 1 of {2015-H2, 2018-Q4} positive |
| G8_min_trades | PASS | 184 | >= 30 per OOS window |
| G9_sharpe_calmar | PASS | sharpe=2.354290469840537 calmar=5.8676305313797075 | Sharpe >= 0.8 AND Calmar >= 0.5 |
| G12_block_trades | PASS | 925 | >= 100 trades in block aggregate |

## Verdict explanation

Failed Block B. Candidate config is burned for this firewall run.
Indicated remediation axis: **regime_survival_redesign** (informational; firewall does NOT pre-approve specific changes).
Modifying this config and re-running is data-mining, NOT validation. Re-design the variant and re-enter via `/strategy-screen`.
