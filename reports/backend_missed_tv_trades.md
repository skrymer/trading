# ProjectX TV vs Backend Comparison Report

**Generated:** 2026-02-10
**Period:** 2017-01-01 to 2026-02-09
**Symbols:** 18 (AAPL, BILI, CAT, CMG, COKE, COP, DINO, GTX, MSCI, NU, PM, PNC, RCL, SPOT, TGT, TPG, VLO, WT)
**Strategy:** ProjectXEntryStrategy / ProjectXExitStrategy
**TV CSV:** `Plan X - new-trades.csv`

## Overall Summary

| Metric | Count |
|--------|-------|
| TV trades (closed, >=2017, 18 symbols) | 830 |
| Backend trades | 860 |
| Entry matches | 826 |
| **Full matches (entry + exit)** | **816** |
| Exit mismatches | 10 |
| TV-only (missed entries) | 4 |
| Backend-only | 34 |
| **Match rate** | **98.8%** |

---

## 1. TV-Only Trades (4) — Missed Entries

Trades present in TV but not in the backend.

| # | Symbol | Entry | Exit | TV Signal | P&L | Blocking Reason |
|---|--------|-------|------|-----------|-----|-----------------|
| 1 | PM | 2021-06-25 | 2021-06-29 | VZ Low Fail | -1.1% | In prior backend trade (2021-06-14 → 2021-06-29) |
| 2 | PM | 2023-03-31 | 2023-04-21 | VZ Low Fail | +0.5% | In prior backend trade (2023-03-30 → 2023-04-19) |
| 3 | PNC | 2024-05-06 | 2024-05-10 | OBTouch | +0.7% | BelowOrderBlockCondition: price 2.0% below OB (requires >= 2.0%) |
| 4 | VLO | 2017-09-11 | 2017-10-25 | VZ Low Fail | +11.3% | AboveBearishOrderBlockCondition: cooldown 2 bars (need 3) |

### Entry Condition Diagnostics

**PM 2021-06-25** — All entry conditions pass, but backend was already in a trade (2021-06-14 → 2021-06-29). The backend entered earlier on 06-14; TV entered later on 06-25.

**PM 2023-03-31** — All entry conditions pass, but backend entered one day earlier (2023-03-30) and is in a trade (2023-03-30 → 2023-04-19). 1-day entry offset — backend and TV both entered but on adjacent days.

**PNC 2024-05-06** — `BelowOrderBlockCondition` fails: price is exactly 2.0% below the bullish OB at 150.05, but the condition requires strictly >= 2.0%. Edge case on the boundary.

**VLO 2017-09-11** — `AboveBearishOrderBlockCondition` fails: only 2 bars since last inside/near bearish OB, need 3. Cooldown is 1 bar short.

---

## 2. Exit Mismatches (10) — Same Entry, Different Exit

All 9 cases where the backend exits earlier are due to the bearish OB overlap check triggering before TV's OBTouch/VZ Low Fail exit. The 1 case where backend exits later (GTX) is a different exit condition (EMA-ATR vs VZ Low Fail).

| # | Symbol | Entry | TV Exit | TV Signal | BT Exit | BT Reason | Diff |
|---|--------|-------|---------|-----------|---------|-----------|------|
| 1 | AAPL | 2018-05-02 | 2018-06-11 | VZ Low Fail | 2018-05-04 | Bearish OB | -38d |
| 2 | AAPL | 2023-04-19 | 2023-05-18 | OBTouch | 2023-05-05 | Bearish OB | -13d |
| 3 | CAT | 2024-07-12 | 2024-07-18 | OBTouch | 2024-07-15 | Bearish OB | -3d |
| 4 | COKE | 2024-04-23 | 2024-05-16 | VZ Low Fail | 2024-05-07 | Bearish OB | -9d |
| 5 | GTX | 2021-01-11 | 2021-02-18 | VZ Low Fail | 2021-02-22 | EMA-ATR | +4d |
| 6 | MSCI | 2019-10-30 | 2019-11-27 | VZ Low Fail | 2019-11-01 | Bearish OB | -26d |
| 7 | PM | 2021-02-05 | 2021-02-10 | OBTouch | 2021-02-09 | Bearish OB | -1d |
| 8 | SPOT | 2020-04-16 | 2020-05-13 | VZ Low Fail | 2020-04-29 | Bearish OB | -14d |
| 9 | VLO | 2024-01-26 | 2024-01-31 | OBTouch | 2024-01-30 | Bearish OB | -1d |
| 10 | WT | 2025-06-11 | 2025-07-29 | VZ Low Fail | 2025-07-02 | Bearish OB | -27d |

---

## 3. Backend-Only Trades (34)

Trades present in the backend but not in TV.

### By Category

| Category | Count | Explanation |
|----------|-------|-------------|
| Pre-TV data range | 25 | Symbol not yet tracked in TV CSV at that time |
| Exit mismatch cascade | 3 | Backend exited earlier (OB), re-entered before TV exited |
| 1-day entry offset | 2 | Backend entered 1 day before TV (PM, COP) — near-miss on key matching |
| TV in different trade | 2 | TV entered on adjacent day (VLO 09-11 vs BT 09-12, RCL same-day) |
| TV didn't enter | 2 | PM 2021-06-07/06-14: backend entered, TV has no trade there |

### Pre-TV Data Range (25)

These symbols weren't tracked in the TV CSV until later dates. Backend has data going back further.

| Symbol | TV starts | Backend-only trades | Count |
|--------|-----------|---------------------|-------|
| BILI | 2019-04-23 | 2018-05-10 to 2019-01-09 | 8 |
| SPOT | 2019-07-11 | 2018-04-30 to 2018-09-26 | 8 |
| GTX | 2020-07-23 | 2019-01-15 to 2020-12-24 | 6 |
| NU | 2023-02-28 | 2022-02-08 to 2022-09-09 | 2 |
| TPG | 2023-01-09 | 2022-07-21 | 1 |

### Exit Mismatch Cascade (3)

Backend exits earlier via bearish OB, re-enters while TV is still in the original trade.

| Symbol | Original trade | BT early exit | Cascade re-entry | Cascade P&L |
|--------|---------------|---------------|------------------|-------------|
| AAPL | 2018-05-02 | 2018-05-04 (-38d) | 2018-05-08 → 2018-06-11 | +3.2% |
| MSCI | 2019-10-30 | 2019-11-01 (-26d) | 2019-11-13 → 2019-11-27 | +3.9% |
| WT | 2025-06-11 | 2025-07-02 (-27d) | 2025-07-10 → 2025-07-29 | +5.8% |

### Remaining Backend-Only (6)

| # | Symbol | Entry | Exit | Reason | P&L | Explanation |
|---|--------|-------|------|--------|-----|-------------|
| 1 | COP | 2024-07-18 | 2024-07-22 | EMA-ATR | -3.7% | TV has no trade in Jul 2024 for COP |
| 2 | PM | 2021-06-07 | 2021-06-08 | EMA-ATR | -2.2% | TV has no trade until 2021-06-25 |
| 3 | PM | 2021-06-14 | 2021-06-29 | EMA-ATR | -1.4% | TV has no trade until 2021-06-25 |
| 4 | PM | 2023-03-30 | 2023-04-19 | Bearish OB | +5.1% | 1-day entry offset vs TV 2023-03-31 |
| 5 | RCL | 2026-01-26 | 2026-01-29 | Bearish OB | +18.0% | TV also enters 01-26 but with different exit (02-06) |
| 6 | VLO | 2017-09-12 | 2017-10-26 | EMA-ATR | +8.7% | 1-day entry offset vs TV 2017-09-11 |
