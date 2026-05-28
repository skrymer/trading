# V4 strategy-selection sweep — Block A results

Per [/tmp/goal-spec-v4.md](/tmp/goal-spec-v4.md). Each candidate is a 12-window walk-forward against `startDate 2000-01-01 / endDate 2015-01-01`, IS 36mo / OOS 12mo / step 12mo, sizer `atrRisk(1.25%, 2.0)`, leverage 1.0, fired against PRD (`-Xmx15360m`, post `MAX_CONCURRENT_WINDOWS=1` fix) via `/tmp/v3-fire.sh`. Each run restarts udgaard for a clean heap.

Evaluator `/tmp/v4-eval.py` applies the 9 v4 absolute gates documented in the spec.

## Verdict summary

| Candidate | Verdict | Pass/Fail/Defer | First failure | CAGR | maxDD | Sharpe | Calmar | Notes |
|---|---|---|---|---|---|---|---|---|
| **VCP-corrected** (reference) | **FAIL** | 3/6/0 | G2_dd_aggregated | 40.62% | 39.16% | 0.54 | 1.04 | Lumpy: W1 2003 +117% edge + W6 2008 -3.48% + worst DD 26% |
| **MR3-s1** | **FAIL** | 8/1/0 | G5_cov_edge | 40.59% | 19.65% | 2.46 | 2.07 | Clean except W5 2007 outlier (+72% edge, 422 trades) inflates CoV to 3.10 |

## Per-candidate detail

### VCP-corrected (reference baseline)

- 12 windows, 713 trades, aggregate OOS edge 9.28%
- **Gates passed**: G1 (CAGR 40.62% > 10%), G7 (2011 EU-debt +1.18%), G8 (min trades 37)
- **Gates failed**: G2 (DD 39.16% > 25%), G3 (worst DD 26.35% W6 GFC > 20%), G4 (66.7% positive < 75%, 8/12), G5 (CoV 3.44 > 1.5, W1 2003 +117% dominates), G6 (2008 GFC -3.48%), G9 (Sharpe 0.54 < 0.8; Calmar 1.04 passes)
- **Interpretation**: confirmed-fragile baseline. The "headline" CAGR is purely 2003's +1029% window; 2008 collapsed.

### MR3-s1 (two-down-day reversal in uptrend, seed 1)

- 12 windows, 5328 trades, aggregate OOS edge 6.43% (driven by W5 2007 +72.24%)
- **Gates passed**: G1 (40.59%), G2 (DD 19.65%), G3 (worst 19.65% W9 2011), G4 (83.3% positive, 10/12), G6 (2008 +4.12%, 431 trades — strong structural win), G7 (2011 +0.42%), G8 (min trades 378), G9 (Sharpe 2.46, Calmar 2.07)
- **Gate failed**: G5 (CoV 3.10 > 1.5) — single-window dominance, driven by W5 OOS 2007 edge +72.24% vs other 11 windows mean +0.51%
- **W5 2007 worth inspection**: 422 trades × 72% edge per trade gives a 31.6% CAGR for that window. Two interpretations: (a) script caught a handful of huge bounces during 2007 commodity/financial volatility, or (b) data artifact (small-cap with stale prices). Re-examine at end of sweep.
- **Structural interpretation**: 11 of 12 windows behave consistently; the 2008 GFC win and low DD make this genuinely better than VCP regardless of the outlier. G5 is technically firing as designed but may be too aggressive for this profile.

## Sweep run log

Started 2026-05-26T13:07:00+10:00. 16 candidates queued.

| Candidate | Verdict | P/F/D | First failure | CAGR | maxDD | Sharpe | Calmar | Notes |
|---|---|---|---|---|---|---|---|---|
| **MR3-s2** | FAIL | 8/1/0 | G5_cov_edge | 40.59% | 19.65% | 2.46 | 2.07 | 5328 trades |
| **MR3-s3** | FAIL | 0/7/2 | G1_cagr | - | - | - | - | null trades |
| **MO1-s1** | FAIL | 5/4/0 | G2_dd_aggregated | 116.17% | 36.22% | 0.31 | 3.21 | 1890 trades |
| **MO1-s2** | FAIL | 6/3/0 | G2_dd_aggregated | 37.12% | 33.16% | 1.69 | 1.12 | 1899 trades |
| **MO1-s3** | FAIL | 5/4/0 | G2_dd_aggregated | 57.58% | 39.17% | 0.67 | 1.47 | 1924 trades |
| **MO3-s1** | FAIL | 0/7/2 | G1_cagr | - | - | - | - | null trades |
| **MO3-s2** | FAIL | 6/3/0 | G3_dd_per_window | 31.1% | 20.94% | 2 | 1.49 | 2326 trades |
| **MO3-s3** | FAIL | 0/7/2 | G1_cagr | - | - | - | - | null trades |

## Resumed sweep (post heap bump to 20 GiB, midgaard stopped)

Restarted 2026-05-26T20:25:10+10:00. 11 candidates queued (OOM re-runs first).

| Candidate | Verdict | P/F/D | First failure | CAGR | maxDD | Sharpe | Calmar | Notes |
|---|---|---|---|---|---|---|---|---|
| **MR3-s3** | FAIL | 8/1/0 | G5_cov_edge | 40.59% | 19.65% | 2.46 | 2.07 | 5328 trades |
| **MO3-s1** | FAIL | 5/4/0 | G3_dd_per_window | 109.34% | 22.3% | 0.33 | 4.9 | 2328 trades |
| **MO3-s3** | FAIL | 6/3/0 | G5_cov_edge | 95.15% | 22.37% | 0.33 | 4.25 | 2273 trades |
| **BR1-s1** | FAIL | 4/5/0 | G4_positive_pct | 78.55% | 11.83% | 0.51 | 6.64 | 996 trades |
| **BR1-s2** | FAIL | 5/4/0 | G4_positive_pct | 16.31% | 13.51% | 2.34 | 1.21 | 962 trades |
| **BR1-s3** | FAIL | 6/3/0 | G5_cov_edge | 25.63% | 14.98% | 2.06 | 1.71 | 964 trades |
| **BR2** | FAIL | 5/4/0 | G2_dd_aggregated | 61.13% | 25.37% | 0.64 | 2.41 | 2742 trades |
| **VZ3-s1** | FAIL | 6/3/0 | G5_cov_edge | 221.66% | 9.01% | 0.39 | 24.6 | 3181 trades |
| **VZ3-s2** | FAIL | 7/2/0 | G5_cov_edge | 174.77% | 9.36% | 0.32 | 18.68 | 3172 trades |
| **VZ3-s3** | FAIL | 6/3/0 | G5_cov_edge | 129.67% | 10.85% | 0.3 | 11.95 | 3184 trades |
| **DV1** | FAIL | 8/1/0 | G5_cov_edge | 34.86% | 12.6% | 2.36 | 2.77 | 1960 trades |

Resumed sweep complete 2026-05-27T02:25:20+10:00

## Re-fire with minimumPrice >= 5 (data-leak fix per #45)

Restarted 2026-05-27T06:42:41+10:00. Tests the hypothesis that AAK-class penny-stock bad prints inflate edge.

| Candidate | Verdict | P/F/D | First failure | CAGR | maxDD | Sharpe | Calmar | Notes |
|---|---|---|---|---|---|---|---|---|
| **MR3-s1-minprice** | FAIL | 4/5/0 | G2_dd_aggregated | 20.56% | 32.88% | 1.42 | 0.63 | 4180 trades |
| **DV1-minprice** | FAIL | 7/2/0 | G3_dd_per_window | 17.27% | 23.06% | 1.24 | 0.75 | 1304 trades |

minimumPrice re-fire complete 2026-05-27T07:24:35+10:00
