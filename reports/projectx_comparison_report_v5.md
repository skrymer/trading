# ProjectX Backtest: Backend vs CSV Comparison Report (v5)

**Generated:** 2026-02-13 10:36
**Period:** 2015-01-01 to 2026-02-13
**Symbols:** 20 (AAPL, ATI, BILI, CAT, CMG, COKE, COP, DINO, EPRT, MSCI, NU, PM, PNC, RCL, SPOT, TGT, TPG, VLO, VSCO, WT)
**Strategy:** ProjectXEntryStrategy / ProjectXExitStrategy
**CSV File:** Plan X - Historic Trades _new_.csv
**Excluded:** ET, MTG, CM, IBKR (per user request); Open (still-active) trades
**Settings:** No position limits, no cooldown, no underlying assets

## Executive Summary

| Metric | Count |
|--------|-------|
| CSV trades (closed, excl. ET/MTG) | 967 |
| Backend trades | 1091 |
| **Entry date matches** | **965** |
| Full matches (entry + exit) | 952 (98.7%) |
| Exit mismatches | 13 (1.3% of matches) |
| CSV only (no backend entry) | 2 |
| Backend only (no CSV entry) | 126 |

## Per-Symbol Summary

| Symbol | CSV | Backend | Entry Match | Full Match | Exit Mismatch | CSV Only | BT Only |
|--------|-----|---------|-------------|------------|---------------|----------|---------|
| AAPL | 78 | 82 | 78 | 76 | 2 | 0 | 4 |
| ATI | 52 | 55 | 52 | 51 | 1 | 0 | 3 |
| BILI | 28 | 36 | 28 | 28 | 0 | 0 | 8 |
| CAT | 69 | 73 | 69 | 68 | 1 | 0 | 4 |
| CMG | 50 | 57 | 50 | 50 | 0 | 0 | 7 |
| COKE | 58 | 67 | 58 | 57 | 1 | 0 | 9 |
| COP | 48 | 51 | 48 | 48 | 0 | 0 | 3 |
| DINO | 40 | 48 | 40 | 40 | 0 | 0 | 8 |
| EPRT | 41 | 46 | 41 | 40 | 1 | 0 | 5 |
| MSCI | 65 | 81 | 65 | 64 | 1 | 0 | 16 |
| NU | 22 | 24 | 22 | 22 | 0 | 0 | 2 |
| PM | 67 | 76 | 66 | 64 | 2 | 1 | 10 |
| PNC | 59 | 62 | 58 | 58 | 0 | 1 | 4 |
| RCL | 59 | 68 | 59 | 59 | 0 | 0 | 9 |
| SPOT | 41 | 49 | 41 | 40 | 1 | 0 | 8 |
| TGT | 52 | 59 | 52 | 52 | 0 | 0 | 7 |
| TPG | 24 | 25 | 24 | 24 | 0 | 0 | 1 |
| VLO | 55 | 62 | 55 | 53 | 2 | 0 | 7 |
| VSCO | 16 | 18 | 16 | 16 | 0 | 0 | 2 |
| WT | 43 | 52 | 43 | 42 | 1 | 0 | 9 |

## Year-by-Year Alignment Summary

| Year | Full Match | Exit Mismatch | CSV Only | Backend Only | Match Rate |
|------|-----------|---------------|----------|-------------|------------|
| 2015 | 13 | 0 | 0 | 84 | 100% |
| 2016 | 42 | 0 | 0 | 6 | 100% |
| 2017 | 89 | 1 | 0 | 0 | 99% |
| 2018 | 68 | 1 | 0 | 18 | 99% |
| 2019 | 95 | 1 | 0 | 5 | 99% |
| 2020 | 74 | 1 | 0 | 0 | 99% |
| 2021 | 120 | 1 | 1 | 3 | 99% |
| 2022 | 71 | 0 | 0 | 5 | 100% |
| 2023 | 119 | 2 | 0 | 0 | 98% |
| 2024 | 140 | 5 | 1 | 2 | 97% |
| 2025 | 108 | 1 | 0 | 1 | 99% |
| 2026 | 13 | 0 | 0 | 2 | 100% |

## Exit Mismatch Pattern Analysis

### Exit Date Difference (Backend - CSV)

- **Mean:** -12.5 days
- **Median:** -9 days
- **Range:** -38 to 1 days

| Difference | Count |
|-----------|-------|
| Backend exits >5d earlier | 7 |
| Backend exits 2-5d earlier | 3 |
| Backend exits 1d earlier | 2 |
| Backend exits 1d later | 1 |

### Exit Signal Cross-Tab (CSV Signal vs Backend Reason)

| CSV Signal | OBTouch | VZ Low Fail | Total |
|---|---|---|---|
| OBTouch | 4 | 0 | 4 |
| VZ Low Fail | 8 | 1 | 9 |

### Exit Signal Cross-Tab (All Matched Trades)

| CSV Signal | EMA10/20 | OBTouch | VZ Low Fail | Total |
|---|---|---|---|---|
| EMA10/20 | 7 | 0 | 0 | 7 |
| OBTouch | 0 | 166 | 0 | 166 |
| VZ Low Fail | 0 | 8 | 784 | 792 |

## P&L Comparison (Matched Trades)

### P&L % Difference (Backend - CSV)

- **Mean difference:** -0.01%
- **Median difference:** -0.00%
- **Std dev:** 0.62%
- **Range:** -11.00% to 9.26%
- **Within +/-1%:** 953 (98.8%)
- **Within +/-0.5%:** 953 (98.8%)

### Entry Price Difference (Backend - CSV)

- **Mean:** $-0.05
- **Within $0.50:** 964 (99.9%)
- **Within $1.00:** 965 (100.0%)

## Aggregate Performance Comparison

| Metric | CSV | Backend | Delta |
|--------|-----|---------|-------|
| Total Trades | 967 | 1091 | +124 |
| Winning Trades | 488 | 541 | +53 |
| Losing Trades | 479 | 550 | +71 |
| Win Rate | 50.5% | 49.6% | -0.9% |
| Avg Win % | 6.23% | 6.19% | -0.04% |
| Avg Loss % | 2.55% | 2.59% | +0.04% |
| Edge | 1.88% | 1.76% | -0.11% |
| Profit Factor | 2.49 | 2.59 | +0.11 |
| Worst Symbol Drawdown | 29.77% | 30.00% | +0.23% |
| Avg Symbol Drawdown | 15.13% | 17.19% | +2.06% |
| Avg Symbol Return | 135.98% | 152.88% | +16.90% |
| Sum of Symbol Returns | 2719.55% | 3057.54% | +337.99% |

## Per-Symbol Performance Comparison

| Symbol | CSV Trades | CSV Return % | Backend Trades | Backend Return % | Delta Return |
|--------|-----------|-------------|----------------|-----------------|-------------|
| AAPL | 78 | 139.88% | 82 | 141.52% | +1.64% |
| ATI | 52 | 105.96% | 55 | 131.69% | +25.73% |
| BILI | 28 | 262.79% | 36 | 255.55% | -7.24% |
| CAT | 69 | 178.16% | 73 | 164.50% | -13.67% |
| CMG | 50 | 201.22% | 57 | 189.43% | -11.79% |
| COKE | 58 | 250.09% | 67 | 449.96% | +199.88% |
| COP | 48 | 65.79% | 51 | 52.50% | -13.30% |
| DINO | 40 | 156.55% | 48 | 238.04% | +81.49% |
| EPRT | 41 | 34.28% | 46 | 64.92% | +30.65% |
| MSCI | 65 | 245.64% | 81 | 273.36% | +27.71% |
| NU | 22 | 71.62% | 24 | 66.26% | -5.37% |
| PM | 67 | 88.27% | 76 | 87.08% | -1.19% |
| PNC | 59 | 81.50% | 62 | 84.76% | +3.25% |
| RCL | 59 | 140.29% | 68 | 146.69% | +6.40% |
| SPOT | 41 | 232.28% | 49 | 202.63% | -29.64% |
| TGT | 52 | 59.62% | 59 | 39.45% | -20.17% |
| TPG | 24 | 53.11% | 25 | 62.35% | +9.24% |
| VLO | 55 | 222.82% | 62 | 290.98% | +68.16% |
| VSCO | 16 | 49.58% | 18 | 35.42% | -14.17% |
| WT | 43 | 80.08% | 52 | 80.44% | +0.36% |

## CSV-Only Trades (No Backend Entry)

**2 trades** found in CSV but not triggered by backend entry strategy.

| Symbol | Entry Date | Exit Date | CSV Signal | P&L % |
|--------|-----------|-----------|-----------|-------|
| PM | 2021-06-25 | 2021-06-29 | VZ Low Fail | -1.12% |
| PNC | 2024-05-06 | 2024-05-10 | OBTouch | 0.73% |

### CSV-Only Summary by Symbol

| Symbol | Count | Avg P&L % |
|--------|-------|----------|
| PM | 1 | -1.12% |
| PNC | 1 | 0.73% |

## Backend-Only Trades (No CSV Entry)

**126 trades** found in backend but not in CSV.

| Symbol | Entry Date | Exit Date | Exit Reason | P&L % |
|--------|-----------|-----------|------------|-------|
| AAPL | 2015-01-22 | 2015-01-27 | VZ Low Fail | -2.90% |
| AAPL | 2015-02-04 | 2015-02-25 | VZ Low Fail | 8.14% |
| AAPL | 2015-03-18 | 2015-03-20 | VZ Low Fail | -2.00% |
| AAPL | 2018-05-08 | 2018-06-11 | VZ Low Fail | 3.18% |
| ATI | 2015-02-02 | 2015-03-05 | VZ Low Fail | 11.50% |
| ATI | 2015-05-05 | 2015-05-19 | VZ Low Fail | 1.16% |
| ATI | 2024-02-23 | 2024-03-11 | VZ Low Fail | 5.76% |
| BILI | 2018-05-10 | 2018-05-23 | VZ Low Fail | 13.09% |
| BILI | 2018-05-29 | 2018-06-21 | VZ Low Fail | 9.38% |
| BILI | 2018-08-29 | 2018-09-04 | VZ Low Fail | -9.16% |
| BILI | 2018-09-13 | 2018-09-21 | VZ Low Fail | -6.89% |
| BILI | 2018-09-28 | 2018-10-02 | VZ Low Fail | -1.26% |
| BILI | 2018-11-01 | 2018-11-09 | VZ Low Fail | -5.57% |
| BILI | 2018-12-31 | 2019-01-03 | VZ Low Fail | -11.24% |
| BILI | 2019-01-09 | 2019-02-01 | OBTouch | 13.21% |
| CAT | 2015-02-17 | 2015-02-19 | VZ Low Fail | -2.44% |
| CAT | 2015-04-29 | 2015-05-07 | VZ Low Fail | -1.22% |
| CAT | 2015-06-09 | 2015-06-15 | VZ Low Fail | 0.46% |
| CAT | 2015-06-19 | 2015-06-23 | OBTouch | 1.06% |
| CMG | 2015-01-26 | 2015-02-02 | VZ Low Fail | -1.50% |
| CMG | 2015-02-13 | 2015-03-04 | VZ Low Fail | -1.59% |
| CMG | 2015-03-13 | 2015-03-25 | VZ Low Fail | -1.27% |
| CMG | 2015-04-20 | 2015-04-22 | VZ Low Fail | -6.65% |
| CMG | 2015-07-13 | 2015-07-22 | OBTouch | 10.31% |
| CMG | 2015-08-05 | 2015-08-07 | VZ Low Fail | -1.14% |
| CMG | 2015-10-02 | 2015-10-06 | VZ Low Fail | -1.55% |
| COKE | 2015-01-12 | 2015-01-28 | VZ Low Fail | 12.25% |
| COKE | 2015-02-24 | 2015-03-05 | VZ Low Fail | -1.91% |
| COKE | 2015-03-16 | 2015-03-19 | VZ Low Fail | -1.05% |
| COKE | 2015-04-06 | 2015-04-17 | VZ Low Fail | -3.56% |
| COKE | 2015-05-18 | 2015-05-20 | OBTouch | 2.08% |
| COKE | 2015-06-04 | 2015-06-08 | VZ Low Fail | -1.04% |
| COKE | 2015-06-18 | 2015-07-07 | VZ Low Fail | 14.88% |
| COKE | 2015-07-10 | 2015-07-16 | VZ Low Fail | -6.08% |
| COKE | 2015-09-14 | 2015-10-26 | VZ Low Fail | 26.92% |
| COP | 2015-01-21 | 2015-01-28 | VZ Low Fail | -3.04% |
| COP | 2015-10-07 | 2015-10-19 | VZ Low Fail | -1.56% |
| COP | 2024-07-18 | 2024-07-22 | VZ Low Fail | -3.68% |
| DINO | 2015-01-26 | 2015-02-19 | VZ Low Fail | 19.99% |
| DINO | 2015-03-25 | 2015-03-27 | VZ Low Fail | -4.38% |
| DINO | 2015-04-22 | 2015-04-30 | VZ Low Fail | 0.23% |
| DINO | 2015-06-18 | 2015-06-30 | OBTouch | 1.59% |
| DINO | 2015-07-06 | 2015-07-27 | VZ Low Fail | 1.17% |
| DINO | 2015-09-24 | 2015-09-28 | VZ Low Fail | -2.24% |
| DINO | 2015-10-02 | 2015-10-09 | VZ Low Fail | -0.45% |
| DINO | 2022-05-04 | 2022-05-26 | OBTouch | 14.62% |
| EPRT | 2018-08-09 | 2018-08-31 | VZ Low Fail | 2.37% |
| EPRT | 2018-10-01 | 2018-10-03 | EMA10/20 | -1.69% |
| EPRT | 2019-01-07 | 2019-02-01 | VZ Low Fail | 12.02% |
| EPRT | 2019-02-19 | 2019-03-01 | VZ Low Fail | -0.84% |
| EPRT | 2019-03-15 | 2019-04-03 | VZ Low Fail | 6.93% |
| MSCI | 2015-02-04 | 2015-02-26 | VZ Low Fail | 3.33% |
| MSCI | 2015-03-18 | 2015-04-09 | VZ Low Fail | 3.49% |
| MSCI | 2015-04-24 | 2015-04-30 | VZ Low Fail | -2.47% |
| MSCI | 2015-06-18 | 2015-06-29 | VZ Low Fail | -0.27% |
| MSCI | 2015-07-13 | 2015-07-27 | VZ Low Fail | 2.48% |
| MSCI | 2015-07-31 | 2015-08-04 | VZ Low Fail | -1.76% |
| MSCI | 2015-10-26 | 2015-11-09 | VZ Low Fail | 5.93% |
| MSCI | 2015-11-18 | 2015-12-09 | VZ Low Fail | 0.03% |
| MSCI | 2015-12-28 | 2016-01-04 | VZ Low Fail | -2.45% |
| MSCI | 2016-02-17 | 2016-02-24 | VZ Low Fail | 1.98% |
| MSCI | 2016-03-18 | 2016-03-24 | VZ Low Fail | -0.74% |
| MSCI | 2016-03-30 | 2016-04-07 | VZ Low Fail | 0.78% |
| MSCI | 2016-04-14 | 2016-05-03 | VZ Low Fail | 0.73% |
| MSCI | 2016-05-10 | 2016-05-13 | VZ Low Fail | -2.64% |
| MSCI | 2016-05-23 | 2016-06-03 | VZ Low Fail | 1.05% |
| MSCI | 2019-11-13 | 2019-11-27 | VZ Low Fail | 3.88% |
| NU | 2022-02-08 | 2022-02-22 | VZ Low Fail | -1.12% |
| NU | 2022-09-09 | 2022-09-16 | VZ Low Fail | -2.04% |
| PM | 2015-01-14 | 2015-01-16 | VZ Low Fail | -0.86% |
| PM | 2015-01-22 | 2015-01-23 | VZ Low Fail | -2.75% |
| PM | 2015-05-08 | 2015-05-21 | VZ Low Fail | -0.06% |
| PM | 2015-06-18 | 2015-06-29 | VZ Low Fail | -2.41% |
| PM | 2015-07-14 | 2015-07-24 | VZ Low Fail | 2.38% |
| PM | 2015-08-04 | 2015-08-07 | VZ Low Fail | -0.86% |
| PM | 2015-10-05 | 2015-10-08 | OBTouch | 2.99% |
| PM | 2021-06-07 | 2021-06-08 | VZ Low Fail | -2.19% |
| PM | 2021-06-14 | 2021-06-29 | VZ Low Fail | -1.37% |
| PM | 2026-02-06 | 2026-02-09 | VZ Low Fail | -0.54% |
| PNC | 2015-01-21 | 2015-01-28 | VZ Low Fail | -1.33% |
| PNC | 2015-02-04 | 2015-03-19 | VZ Low Fail | 8.11% |
| PNC | 2015-07-14 | 2015-07-27 | VZ Low Fail | 0.01% |
| PNC | 2015-08-18 | 2015-08-20 | VZ Low Fail | -3.96% |
| RCL | 2015-01-21 | 2015-01-28 | VZ Low Fail | -1.69% |
| RCL | 2015-02-12 | 2015-02-13 | VZ Low Fail | -1.16% |
| RCL | 2015-02-25 | 2015-03-09 | VZ Low Fail | 1.97% |
| RCL | 2015-03-20 | 2015-03-25 | VZ Low Fail | -5.85% |
| RCL | 2015-05-18 | 2015-06-10 | VZ Low Fail | 1.20% |
| RCL | 2015-08-07 | 2015-08-12 | VZ Low Fail | -1.62% |
| RCL | 2015-09-10 | 2015-09-22 | VZ Low Fail | 0.49% |
| RCL | 2015-10-05 | 2015-10-14 | VZ Low Fail | -6.74% |
| RCL | 2026-01-26 | 2026-01-29 | OBTouch | 18.02% |
| SPOT | 2018-04-30 | 2018-05-04 | VZ Low Fail | -4.58% |
| SPOT | 2018-05-25 | 2018-05-29 | EMA10/20 | -1.26% |
| SPOT | 2018-06-01 | 2018-06-19 | VZ Low Fail | 6.69% |
| SPOT | 2018-06-22 | 2018-06-25 | VZ Low Fail | -5.84% |
| SPOT | 2018-07-11 | 2018-07-27 | VZ Low Fail | 2.15% |
| SPOT | 2018-08-13 | 2018-08-20 | VZ Low Fail | -2.69% |
| SPOT | 2018-09-12 | 2018-09-17 | VZ Low Fail | -4.73% |
| SPOT | 2018-09-26 | 2018-10-03 | VZ Low Fail | -2.80% |
| TGT | 2015-03-18 | 2015-03-26 | VZ Low Fail | -0.76% |
| TGT | 2015-04-06 | 2015-04-15 | VZ Low Fail | -1.66% |
| TGT | 2015-06-03 | 2015-06-05 | VZ Low Fail | -1.42% |
| TGT | 2015-06-22 | 2015-06-29 | VZ Low Fail | -2.54% |
| TGT | 2015-07-14 | 2015-07-21 | VZ Low Fail | -2.16% |
| TGT | 2015-08-19 | 2015-08-21 | VZ Low Fail | -3.05% |
| TGT | 2015-10-02 | 2015-10-06 | VZ Low Fail | -1.80% |
| TPG | 2022-07-21 | 2022-08-04 | OBTouch | 6.06% |
| VLO | 2015-01-22 | 2015-03-02 | VZ Low Fail | 23.13% |
| VLO | 2015-03-17 | 2015-03-27 | VZ Low Fail | 3.29% |
| VLO | 2015-05-22 | 2015-05-26 | VZ Low Fail | -2.39% |
| VLO | 2015-06-18 | 2015-06-30 | OBTouch | 3.87% |
| VLO | 2015-07-06 | 2015-07-23 | VZ Low Fail | 0.58% |
| VLO | 2015-09-10 | 2015-09-16 | VZ Low Fail | -4.21% |
| VLO | 2015-10-02 | 2015-10-13 | VZ Low Fail | -1.49% |
| VSCO | 2021-12-31 | 2022-01-03 | OBTouch | 1.08% |
| VSCO | 2022-03-29 | 2022-04-01 | VZ Low Fail | -10.45% |
| WT | 2015-01-16 | 2015-02-12 | VZ Low Fail | 14.92% |
| WT | 2015-03-06 | 2015-03-18 | VZ Low Fail | 1.51% |
| WT | 2015-04-10 | 2015-04-14 | VZ Low Fail | -3.40% |
| WT | 2015-05-20 | 2015-06-02 | VZ Low Fail | 0.39% |
| WT | 2015-06-24 | 2015-06-29 | VZ Low Fail | -5.03% |
| WT | 2015-08-03 | 2015-08-06 | VZ Low Fail | -5.41% |
| WT | 2015-11-03 | 2015-11-13 | VZ Low Fail | -0.47% |
| WT | 2015-11-20 | 2015-11-27 | VZ Low Fail | 2.17% |
| WT | 2025-07-10 | 2025-07-29 | VZ Low Fail | 5.77% |

### Backend-Only Summary by Symbol

| Symbol | Count | Avg P&L % |
|--------|-------|----------|
| AAPL | 4 | 1.61% |
| ATI | 3 | 6.14% |
| BILI | 8 | 0.20% |
| CAT | 4 | -0.53% |
| CMG | 7 | -0.48% |
| COKE | 9 | 4.72% |
| COP | 3 | -2.76% |
| DINO | 8 | 3.82% |
| EPRT | 5 | 3.76% |
| MSCI | 16 | 0.83% |
| NU | 2 | -1.58% |
| PM | 10 | -0.57% |
| PNC | 4 | 0.71% |
| RCL | 9 | 0.51% |
| SPOT | 8 | -1.63% |
| TGT | 7 | -1.91% |
| TPG | 1 | 6.06% |
| VLO | 7 | 3.25% |
| VSCO | 2 | -4.68% |
| WT | 9 | 1.16% |

## Exit Mismatch Details (Largest Date Differences)

| Symbol | Entry Date | CSV Exit | BT Exit | Days Diff | CSV Signal | BT Reason | CSV P&L | BT P&L |
|--------|-----------|----------|---------|-----------|-----------|-----------|---------|--------|
| AAPL | 2018-05-02 | 2018-06-11 | 2018-05-04 | -38 | VZ Low Fail | OBTouch | 8.72% | 4.11% |
| WT | 2025-06-11 | 2025-07-29 | 2025-07-02 | -27 | VZ Low Fail | OBTouch | 29.82% | 18.82% |
| MSCI | 2019-10-30 | 2019-11-27 | 2019-11-01 | -26 | VZ Low Fail | OBTouch | 12.98% | 7.32% |
| ATI | 2024-02-09 | 2024-03-11 | 2024-02-15 | -25 | VZ Low Fail | OBTouch | 16.97% | 10.32% |
| SPOT | 2020-04-16 | 2020-05-13 | 2020-04-29 | -14 | VZ Low Fail | OBTouch | 6.80% | 11.68% |
| AAPL | 2023-04-19 | 2023-05-18 | 2023-05-05 | -13 | OBTouch | OBTouch | 4.57% | 3.54% |
| COKE | 2024-04-23 | 2024-05-16 | 2024-05-07 | -9 | VZ Low Fail | OBTouch | 12.27% | 21.53% |
| EPRT | 2024-11-11 | 2024-12-02 | 2024-11-27 | -5 | VZ Low Fail | OBTouch | 0.35% | 3.10% |
| CAT | 2024-07-12 | 2024-07-18 | 2024-07-15 | -3 | OBTouch | OBTouch | 6.09% | 3.03% |
| PM | 2023-03-31 | 2023-04-21 | 2023-04-19 | -2 | VZ Low Fail | OBTouch | 0.54% | 5.06% |
| PM | 2021-02-05 | 2021-02-10 | 2021-02-09 | -1 | OBTouch | OBTouch | 1.96% | 1.60% |
| VLO | 2017-09-11 | 2017-10-25 | 2017-10-26 | +1 | VZ Low Fail | VZ Low Fail | 11.28% | 8.66% |
| VLO | 2024-01-26 | 2024-01-31 | 2024-01-30 | -1 | OBTouch | OBTouch | 1.49% | 2.89% |
