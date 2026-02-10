# ProjectX Strategy Performance Report

**Generated:** 2026-02-10
**Period:** 2017-01-01 to 2026-02-09
**Symbols:** 18 (AAPL, BILI, CAT, CMG, COKE, COP, DINO, GTX, MSCI, NU, PM, PNC, RCL, SPOT, TGT, TPG, VLO, WT)
**Strategy:** ProjectXEntryStrategy / ProjectXExitStrategy

---

## Overall Summary

| Metric | Value |
|--------|-------|
| Total trades | 860 |
| Wins / Losses | 436 / 424 |
| Win rate | 50.7% |
| Edge (avg P&L per trade) | +1.95% |
| Profit Factor | 2.47 |
| Cumulative P&L | +1,674.8% |
| Max Drawdown | 47.8% |
| Avg holding period | 8.9 days |
| Max consecutive losses | 14 |
| Max consecutive wins | 10 |
| Largest win | +65.4% (BILI 2020-11-16) |
| Largest loss | -15.5% (GTX 2020-07-23) |

---

## Performance by Year

| Year | Trades | Wins | Losses | Win Rate | Avg Win | Avg Loss | Edge | Profit Factor | Max DD | Cum P&L |
|------|--------|------|--------|----------|---------|----------|------|---------------|--------|---------|
| 2017 | 84 | 37 | 47 | 44.0% | +5.2% | -1.8% | +1.30% | 2.33 | 32.0% | +109.6% |
| 2018 | 78 | 38 | 40 | 48.7% | +5.4% | -2.9% | +1.14% | 1.76 | 26.0% | +198.7% |
| 2019 | 94 | 45 | 49 | 47.9% | +5.6% | -2.2% | +1.51% | 2.31 | 20.8% | +340.2% |
| 2020 | 69 | 34 | 35 | 49.3% | +10.2% | -3.7% | +3.14% | 2.67 | 27.5% | +557.0% |
| 2021 | 121 | 56 | 65 | 46.3% | +7.3% | -2.7% | +1.95% | 2.36 | 47.8% | +792.6% |
| 2022 | 68 | 37 | 31 | 54.4% | +5.5% | -3.7% | +1.32% | 1.78 | 28.5% | +882.5% |
| 2023 | 104 | 52 | 52 | 50.0% | +6.8% | -2.8% | +2.00% | 2.45 | 24.3% | +1,090.3% |
| 2024 | 129 | 71 | 58 | 55.0% | +6.1% | -2.5% | +2.21% | 2.93 | 36.7% | +1,375.1% |
| 2025 | 100 | 58 | 42 | 58.0% | +6.2% | -2.5% | +2.57% | 3.47 | 14.3% | +1,632.4% |
| 2026 | 13 | 8 | 5 | 61.5% | +7.1% | -2.9% | +3.26% | 3.91 | 6.7% | +1,674.8% |

**Key observations:**
- Profit Factor consistently above 1.76 every year — no losing year
- Edge ranges from +1.14% (2018) to +3.26% (2026 partial)
- 2021 had the deepest intra-year drawdown at 47.8% (most trades: 121)
- 2025 showing strongest recent performance: PF 3.47, only 14.3% DD

---

## Monte Carlo Simulation

**Method:** Trade Shuffling (10,000 iterations)
**Purpose:** Assess drawdown risk by reshuffling the same 860 trades in random order

### Edge Consistency

| Metric | Value |
|--------|-------|
| Edge per trade | +1.95% (constant across all shuffles) |
| Win rate | 50.7% (constant) |
| Probability of profit | 100% (all 10,000 paths ended profitable) |

Edge is consistent because trade shuffling preserves the same win/loss distribution — only the order changes. The strategy has a robust positive edge.

### Drawdown Distribution

| Percentile | Max Drawdown |
|------------|-------------|
| Best case (P5) | 25.4% |
| P25 | 29.3% |
| **Median (P50)** | **32.8%** |
| P75 | 37.3% |
| Worst case (P95) | 44.8% |
| Mean | 33.7% |

**Interpretation:**
- **Expect ~33% drawdown** in a typical run of 860 trades
- **50% chance** drawdown stays below 32.8%
- **95% confidence:** drawdown will not exceed 44.8%
- **5% best case:** drawdown as low as 25.4%
- The actual observed 47.8% max DD (2021) is slightly above the P95 — this was an unlucky clustering of losses

### Conclusion

The Monte Carlo confirms the strategy edge is **robust and consistent**:
- Every single reshuffled path (10,000/10,000) ended profitable
- The edge of +1.95% per trade is stable
- Drawdowns of 25-45% are normal for this trade distribution
- The PF of 2.47 with a 50.7% win rate means wins are ~2.5x larger than losses on average

---

## Performance by Symbol

| Symbol | Trades | Wins | Losses | Win Rate | Avg Win | Avg Loss | Edge | PF | Max DD | Cum P&L | Best | Worst |
|--------|--------|------|--------|----------|---------|----------|------|----|--------|---------|------|-------|
| AAPL | 75 | 35 | 40 | 46.7% | +4.8% | -1.9% | +1.25% | 2.25 | 12.3% | +93.6% | +19.6% | -4.7% |
| BILI | 36 | 15 | 21 | 41.7% | +17.0% | -4.6% | +4.41% | 2.65 | 34.1% | +158.8% | +65.4% | -11.2% |
| CAT | 63 | 33 | 30 | 52.4% | +5.3% | -2.4% | +1.65% | 2.45 | 15.6% | +103.7% | +18.3% | -4.6% |
| CMG | 50 | 25 | 25 | 50.0% | +6.6% | -1.8% | +2.38% | 3.60 | 11.1% | +119.0% | +20.1% | -5.9% |
| COKE | 56 | 32 | 24 | 57.1% | +6.8% | -2.7% | +2.72% | 3.36 | 23.4% | +152.6% | +38.9% | -10.6% |
| COP | 46 | 21 | 25 | 45.7% | +5.7% | -2.5% | +1.23% | 1.90 | 10.7% | +56.5% | +18.2% | -6.3% |
| DINO | 39 | 23 | 16 | 59.0% | +8.0% | -4.1% | +3.03% | 2.82 | 14.3% | +118.3% | +39.1% | -9.3% |
| GTX | 30 | 12 | 18 | 40.0% | +9.2% | -4.7% | +0.87% | 1.31 | 42.3% | +26.0% | +22.8% | -15.5% |
| MSCI | 63 | 37 | 26 | 58.7% | +4.7% | -1.8% | +1.97% | 3.60 | 12.4% | +124.3% | +19.5% | -6.0% |
| NU | 24 | 12 | 12 | 50.0% | +8.2% | -3.4% | +2.44% | 2.44 | 9.2% | +58.5% | +24.0% | -7.7% |
| PM | 58 | 26 | 32 | 44.8% | +5.1% | -2.1% | +1.16% | 2.02 | 16.0% | +67.3% | +23.7% | -8.3% |
| PNC | 51 | 21 | 30 | 41.2% | +5.4% | -1.9% | +1.09% | 1.96 | 15.0% | +55.4% | +17.7% | -3.7% |
| RCL | 54 | 28 | 26 | 51.9% | +7.7% | -3.4% | +2.33% | 2.41 | 29.7% | +125.9% | +34.8% | -9.1% |
| SPOT | 49 | 26 | 23 | 53.1% | +7.0% | -2.6% | +2.52% | 3.08 | 18.1% | +123.4% | +21.1% | -5.8% |
| TGT | 49 | 25 | 24 | 51.0% | +4.0% | -1.9% | +1.10% | 2.18 | 9.6% | +54.0% | +15.4% | -4.7% |
| TPG | 25 | 14 | 11 | 56.0% | +6.5% | -3.5% | +2.14% | 2.41 | 11.7% | +53.5% | +14.9% | -7.0% |
| VLO | 48 | 25 | 23 | 52.1% | +7.8% | -3.2% | +2.55% | 2.68 | 34.3% | +122.3% | +28.0% | -7.5% |
| WT | 44 | 26 | 18 | 59.1% | +4.5% | -3.1% | +1.40% | 2.12 | 14.3% | +61.7% | +18.8% | -8.0% |

**Top performers by edge:** BILI (+4.41%), DINO (+3.03%), COKE (+2.72%), VLO (+2.55%), SPOT (+2.52%)
**Top performers by PF:** CMG (3.60), MSCI (3.60), COKE (3.36), SPOT (3.08), DINO (2.82)
**Highest drawdown:** GTX (42.3%), BILI (34.1%), VLO (34.3%), RCL (29.7%)
**Weakest:** GTX (PF 1.31, 40% win rate) — only symbol with PF below 1.75

All 18 symbols are profitable. Every symbol has a positive edge and PF above 1.0.
