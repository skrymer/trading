# VCP Production Trading Plan

*Generated 2026-04-17 based on validated backtest results under the capital-aware backtest engine (commit 15c9fe2), which simulates real-world capital constraints by skipping unfundable signals.*

## Strategy Summary

Volatility Contraction Pattern (VCP) — trend-following breakout strategy that captures stocks in strong uptrends consolidating with decreasing volatility, then breaking out above institutional supply zones with volume confirmation.

**Entry:** marketUptrend + sectorUptrend + uptrend (5>10>20 EMA, price > 50 EMA) + volatilityContracted(10d, 3.5 ATR) + aboveBearishOrderBlock(1d, age=0) + priceNearDonchianHigh(3%) + volumeAboveAverage(1.2x, 20d) + minimumPrice($10)

**Exit:** emaCross(10, 20) + stopLoss(2.5 ATR) + stagnation(3%, 15 days)

**Ranker:** SectorEdge (deterministic, IS-derived sector rankings)

---

## Validated Performance (2016-2025, $10K start, **1.25% risk**, leverage 1.0, 15 max positions)

**Primary sizing recommendation: 1.25% risk per trade.** Selected after a 4-cell risk sweep (0.75% / 1.0% / 1.25% / 1.5%) and 4-seed validation at 1.25%. All numbers below are the 4-seed mean.

| Metric | Value (4-seed mean) |
|---|---|
| Final Capital | ~$690K (~69x) |
| CAGR | 51.4% |
| Max Drawdown | 18.9% (range 18.1-20.7% across seeds) |
| Max DD Duration | 241 days (worst seed: 294d) |
| Total Trades | ~612 |
| Win Rate | 52.9% |
| Edge | +6.4% |
| Profit Factor | ~4.2 |
| Sharpe / Sortino / Calmar | 2.29 / ~2.75 / **2.73** |
| SPY Correlation / Beta / Alpha | 0.55 / 0.68 / ~37.9% |
| Edge Consistency Score | 96.0 (Excellent) |

**Walk-forward — primary reference (1.5% baseline, 4 year-length windows):** WFE 0.667, aggregate OOS edge +3.64% on 447 trades. Reproduced 2026-04-21 within rounding (WFE 0.679, +3.74% on 449 trades) as a regression guard.

**Walk-forward — confirmatory at the production config (2026-04-21):** 27 disjoint 3-month OOS quarters, 36-month IS, at 1.25% ATR-risk with position-sizing and capital-aware engine. Aggregate WFE 0.830, OOS edge +5.03%, 481 trades. **Do not treat as a replacement for the 0.667 headline** — the +1.08% OOS-edge improvement is not statistically distinguishable from the baseline (Welch t=0.59 on per-window OOS edges); the higher aggregate WFE is partly mechanical (trade-weighted OOS numerator vs simple-mean IS denominator). Cite as confirmation that edge persists under real trading constraints, not as a stronger edge claim. Median per-window WFE 0.48; 7/27 negative quarters.

**Reproduced 2026-04-18** on DEV with the Appendix config across seeds 1 / 7 / 42 / 100 — every headline metric above matched to within rounding (CAGR 51.42%, MaxDD 18.92%, Calmar 2.730, edge +6.38%, WR 52.98%, 612.5 mean trades). Per-seed Calmar 2.38 / 2.63 / 3.20 / 2.71 — seed 42 confirmed as the upside outlier noted in Section 2.

### Why 1.25% beat 1.5%

| | 1.5% (prior baseline) | 1.25% (4-seed mean) |
|---|---|---|
| CAGR | 50.75% | 51.4% |
| Max DD | 21.33% | **18.9%** |
| Calmar | 2.38 | **2.73** (+15%) |
| Alpha | 32.2% | **~37.9%** |
| Effective concurrent positions | 6-7 | ~8 |

Smaller positions (~12.5% of equity vs 15%) let the portfolio absorb more of the sector's signal flow — effective concurrent position count rose from 6-7 to ~8, which diversifies idiosyncratic risk and produces a shallower drawdown profile at nearly identical CAGR. 3 of 4 seeds independently beat the 1.5% Calmar; the worst seed matches it. The floor is the same; the typical case is better.

> **Note on prior numbers.** Earlier drafts of this plan cited CAGR 59.0%, MDD 16.8%, WFE 1.13, final capital $1.35M at 1.5% risk. Those came from a pre-capital-aware engine that allowed unfundable signals to count as trades. Under the current engine, 90%+ of signal candidates at $10K are skipped because the portfolio is already fully deployed. The numbers above are what a real trader at $10K can actually achieve.

---

## 1. Starting Capital and Position Sizing

**Recommended starting capital: $30K AUD (~$20K USD)**

- At $20K USD, average realized position size is ~$2,500. Commission drag is ~0.05% per side — negligible.
- At $10K AUD (~$7K USD): still viable, ~8 effective concurrent positions. Accept ~0.5-1.0% annual commission drag. This is the minimum for live deployment.

**Position sizing: 1.25% risk per trade (ATR-based)**

```
Shares = floor(Account Equity × 0.0125 / (2.5 × ATR))
```

Example at $20K account, stock at $50 with ATR of $2:
- Stop distance = 2.5 × $2 = $5 (10% below entry)
- Risk amount = $20,000 × 0.0125 = $250
- Shares = $250 / $5 = 50 shares
- Position value = 50 × $50 = $2,500 (12.5% of account)

**Effective concurrent positions is ~8 regardless of starting capital.** At 1.25% risk with 2.0 ATR stop and leverage cap 1.0, each position occupies ~12.5% of equity. Eight positions saturate 100% leverage. See Section 2.

Do not reduce risk below 1.25% except during Phase 1 ramp-up (see Section 7). The strategy's exit rules (only ~6% of trades hit the stop) mean realized losses are far less severe than the 1.25% risk implies.

## 2. Maximum Positions

**Set `maxPositions: 15` in the config. Expect ~8 actual concurrent positions live.**

The leverage cap (1.0×) binds before the position cap on virtually every signal-dense day. The capital-aware backtest engine confirms this: at $10K with 1.25% risk, 90%+ of candidate signals are skipped because the portfolio is already fully deployed.

**Why set 15, not 8?** The effective count is derived from leverage math, not a hardcoded limit. Setting `maxPositions=8` would cause the slot limit to bind instead of leverage — dropping signals by arrival order rather than by rank within each bar's cohort. Keep 15 so leverage is the binding constraint and the ranker has room to work when a position exits and releases capital.

### Risk sweep results (all at $10K, seed 42)

| riskPct | Notional per position | Effective positions | CAGR | MaxDD | Calmar |
|---|---|---|---|---|---|
| 0.75% | ~7.5% | ~13 | 45.8% | 18.3% | 2.50 |
| 1.0% | ~10% | ~10 | 44.1% | 20.7% | 2.13 |
| **1.25% (selected)** | **~12.5%** | **~8** | **51.4%\*** | **18.9%\*** | **2.73\*** |
| 1.5% (prior) | ~15% | 6-7 | 50.75% | 21.33% | 2.38 |

*1.25% values are 4-seed mean. Seeds 1/7/42/100 produced Calmar 2.38/2.63/3.20/2.71. Seed 42 is an upside outlier.*

**1.25% dominates:** highest mean Calmar (2.73), lowest MaxDD, Sharpe/Sortino best-in-sweep. The floor (worst-seed 2.38) matches the 1.5% baseline; the typical case beats it.

## 3. Capital Sensitivity

At constant sizing, trade composition is nearly identical across capital tiers. Raising capital scales per-trade dollars, not trade count.

### At 1.25% risk (recommended sizing)

| Starting | CAGR | MaxDD | Sharpe | Calmar | Alpha | Final | Sample |
|---|---|---|---|---|---|---|---|
| $10K | 51.4% | 18.9% | 2.29 | 2.73 | 37.9% | ~$690K | 4-seed mean |
| $20K | 50.7% | 19.4% | 2.28 | 2.62 | 33.3% | $1.28M | 4-seed mean |
| $50K | 50.5% | 18.4% | 2.25 | 2.75 | 34.6% | $3.18M | 4-seed mean |

### At 1.5% risk (prior baseline, seed 42 only)

| Starting | CAGR | MaxDD | Sharpe | Calmar | Alpha | Final |
|---|---|---|---|---|---|---|
| $10K | 50.75% | 21.3% | 2.20 | 2.38 | 32.2% | $636K |
| $20K | 52.53% | 20.3% | 2.26 | 2.58 | 33.4% | $1.43M |
| $50K | 49.18% | 19.5% | 2.18 | 2.52 | 31.4% | $2.86M |

**Key findings:**
- **1.25% dominates at $10K and $50K** — Calmar +0.35 and +0.23 over 1.5% respectively. The advantage is stable across capital tiers.
- **1.25% leads 1.5% at $20K on 4-seed mean** — Calmar 2.62 vs 2.58. Advantage narrower than at $10K/$50K but consistent in direction.
- **CAGR is stable in a 50-52% band** across 5× capital range at 1.25%.
- **MaxDD is consistently 18-20% at 1.25%** (vs 19-21% at 1.5%).
- **Alpha vs SPY is 34-39% across all tiers** — independent edge, not scaled beta.
- **Trade counts near-identical across capital tiers** — you don't trade more with more capital, you trade bigger.

**Implication:** Don't wait to grow the account before starting. $10K and $50K trade the same strategy in different dollar amounts. Pick 1.25% regardless of tier; the advantage is real at both small and large capital.

## 4. Leverage

**No leverage beyond 1.0×. Zero margin.**

- 51.4% CAGR at 1.0× leverage (1.25% risk, 4-seed mean). Even at a 50% haircut, 25-30% CAGR is exceptional.
- Leverage amplifies drawdowns: 18.9% at 2× becomes 37.8%.
- The only acceptable margin use is temporary overnight settlement.

## 5. Drawdown Response Plan

**Circuit breakers only. No continuous drawdown scaling.**

DD scaling was tested and rejected — it costs significant terminal wealth for marginal DD reduction. The strategy's exit rules and sector rotation provide natural DD protection.

### Level 1: Monitoring (0-10% drawdown)
- Normal operations. No changes.
- Drawdowns below 10% are frequent and recover in 2-4 weeks.

### Level 2: Heightened Awareness (10-15% drawdown)
- Review all open positions. Verify exit conditions are executing correctly.
- No position sizing changes.
- Log emotional state daily.

### Level 3: Pause New Entries (15-20% drawdown)
- Stop opening new positions until drawdown recovers to 12%.
- Continue managing existing positions normally — do not close early.
- 4-seed mean worst drawdown at 1.25% is 18.9% / 241 days. Worst single seed was 20.7% / 294 days — expect to sit through 8-10 months in the worst regimes.

### Level 4: Full Stop (>23% drawdown)
- Close all positions at market. Stop trading for 30 days minimum.
- Exceeding the 20.7% worst-seed by 2pp+ suggests execution error or regime change.
- Conduct full review before resuming.

**Critical rule:** Never reduce position size during a drawdown. Either trade full size or stop entirely.

## 6. Expected Trade Frequency and Time Commitment

**Trade frequency:** ~61 trades/year = ~1.2 trades/week (at 1.25% risk). Signals cluster around market recoveries and sector rotations. Expect 0 signals for 2-3 weeks, then 4-5 in one week. 90%+ of candidate signals get skipped because the portfolio is already fully deployed — this is by design.

**Average holding periods** (from 2016-2025 data):
- EMA cross exits (~73% of trades): 55 days
- Stagnation exits (~21%): 22 days
- Stop loss exits (~6%): 10 days

**Daily commitment: 15-30 minutes after market close**

1. Run scanner for new entry signals (5 min)
2. Check exit conditions on open positions (5 min)
3. Place/adjust orders for next day (5 min)
4. Log trades (5 min)

The 1-day entry delay means you scan after close and place orders before the next open. No intraday monitoring required.

**Weekly: 1 hour on weekends** — review performance, check sector rotation, verify sizing.

## 7. Ramp-Up Plan

### Phase 0: Paper Trading (4-6 weeks)
- Use scanner to generate signals daily, track on paper.
- Goal: verify YOUR execution matches what the scanner recommends over 15-20 paper trades.

### Phase 1: Small Capital (Months 1-3)
- Deploy $5K-$10K USD
- 1.0% risk per trade (reduced for psychological calibration)
- Target: 15-25 trades executed correctly
- Success criteria: executing mechanically without overrides

### Phase 2: Partial Deployment (Months 4-6)
- Increase to $15K-$20K USD
- Move to 1.25% risk per trade
- Target: 20-30 more trades
- Success criteria: no discretionary overrides, drawdowns handled without emotional response

### Phase 3: Full Deployment (Month 7+)
- Full intended capital
- 1.25% risk per trade

**If you override the system >10% of the time in Phase 1-2, go back to paper trading.**

## 8. Key Metrics to Monitor

### Weekly

| Metric | Expected Range | Concern Threshold |
|--------|---------------|-------------------|
| Win rate (rolling 20 trades) | 48-58% | Below 40% for 20+ trades |
| Edge (rolling 20 trades) | +4% to +8% | Below +2% for 30+ trades |
| Open positions | Typically 5-8 | Persistent at 0 or 8 for weeks |
| Exit rule compliance | 100% mechanical | Any discretionary exit |

### Monthly

| Metric | Expected Range | Concern Threshold |
|--------|---------------|-------------------|
| Profit factor | >2.0 | Below 1.5 for a quarter |
| Trade frequency | 4-6 trades/month | <2 or >12 |
| Stop loss rate | 5-8% of trades | >15% |
| Stagnation exit rate | 18-25% of trades | >35% |

### Quarterly

| Metric | Baseline | Concern Threshold |
|--------|----------|-------------------|
| Edge vs. WF OOS | +3.64% | Below +1.5% for 2 quarters |
| Rolling 12-mo OOS edge | +3.64% | Below +1% (soft kill-switch) |
| Max drawdown | Compare to 18.9% mean / 20.7% worst-seed | Exceeding 23% |
| SPY correlation | ~0.55 | Rising above 0.75 |
| Trade count | ~14/quarter | Persistent deviation >50% |

## 9. Kill Criteria

### Hard Stops (stop immediately)

1. **Drawdown exceeds 23%.** Beyond the 20.7% worst-seed plus buffer. Something is structurally broken.
2. **Override the system on >3 trades in a month.** You are no longer trading the backtested strategy.
3. **Regulatory or broker change** materially affects execution.

### Soft Stops (stop and review for 30 days)

1. **Rolling 12-month OOS edge < +1%.** Sustained alpha decay. WF baseline is +3.64%; +1% is near breakeven after costs.
2. **Rolling 50-trade edge falls below +1.5%.** Same class of signal, shorter window.
3. **Rolling 50-trade win rate falls below 42%.** Near breakeven given the W/L ratio.
4. **Three consecutive months with negative P&L.** Rare enough to warrant investigation.
5. **You dread running the scanner.** Psychological aversion is a valid signal.

**"Stop and review" means:** Close all positions, wait 30 days, re-run backtest on recent data. If edge persists in data but not in your trading, it's an execution issue — go back to Phase 1. If edge has decayed in the data, the strategy needs revalidation.

## 10. Realistic Return Expectations

**The capital-aware backtest already reflects real-world capital constraints. Apply a 20-30% haircut for remaining frictions** (slippage, commissions, FX drag, missed trades).

### $10K AUD (~$7K USD)

| Scenario | Annual Return | Year 1 | Year 3 |
|----------|-------------|--------|--------|
| Conservative (20% haircut on CAGR 50%) | 40% | $14,000 | $27,400 |
| Base (30% haircut) | 35% | $13,500 | $24,600 |
| Pessimistic (50% haircut) | 25% | $12,500 | $19,500 |

### $30K AUD (~$20K USD)

| Scenario | Annual Return | Year 1 | Year 3 |
|----------|-------------|--------|--------|
| Conservative | 40% | $42,000 | $82,300 |
| Base | 35% | $40,500 | $73,800 |
| Pessimistic | 25% | $37,500 | $58,600 |

### $50K AUD (~$33K USD)

| Scenario | Annual Return | Year 1 | Year 3 |
|----------|-------------|--------|--------|
| Conservative | 40% | $70,000 | $137,200 |
| Base | 35% | $67,500 | $123,000 |
| Pessimistic | 25% | $62,500 | $97,700 |

**2022-like worst case:** the worst OOS year in the walk-forward (2022) produced +0.90% edge on 119 OOS trades. At $30K AUD expect flat-to-slightly-positive P&L for a full year. Be content protecting capital while waiting for the next cycle.

## 11. Psychological Preparation

### Expected Losing Streaks (52.9% WR)

- **3 losses in a row:** Every 2-3 weeks. Completely normal.
- **5 losses in a row:** 2-3 times per year. At 1.25% risk, costs ~6.25% of equity.
- **7 losses in a row:** Once every 2-3 years. This is where discipline is tested most severely.

### What the Worst Drawdown Feels Like (18.9% mean / 20.7% worst-seed, 241-294 days)

- **Weeks 1-4:** Account drops 5-10%. "Normal pullback." You're calm.
- **Weeks 5-10:** Account drops 12-16%. You check P&L more often. You wonder if something is broken.
- **Weeks 11-20:** Account drops 17-21%. You seriously consider stopping. "This time is different." **This is the critical moment where most traders abandon working strategies.**
- **Weeks 21-26:** Drawdown stabilizes. New signals appear. You're gun-shy.
- **Weeks 27-42:** Recovery. Each small win rebuilds confidence slowly.

The 2022-2023 backtest went through this sequence and ended positive OOS edge (+0.90%). Longest observed duration across 4 seeds was 294 days (~10 months).

### The Five Hardest Moments

1. **First real loss.** Your first $300 stop loss stings disproportionately. Log it, confirm the exit was correct, move on.
2. **Stopped-out stock rallies without you.** This is the cost of the ~94% of trades where the stop doesn't trigger as expected.
3. **A flat month of stagnation exits.** Stagnation exits (~+0.33% avg) protect you from dead money. They free capital for the next EMA cross winner (~+9% avg).
4. **Someone makes easy money in meme stocks.** Ask their 3-year track record. Systematic edge compounds; lottery tickets don't.
5. **The scanner signals a stock you "feel bad about."** Take the trade. Your feelings are not backtested.

### Daily Mindset Rules

- You are executing a process, not making predictions
- Each individual trade is meaningless — only the distribution matters
- The strategy's edge is +6.4% per trade on average — but any single trade can lose 10%
- You already know the worst case (up to 20.7% DD, up to 294 days). You accepted this when you chose the strategy.
- If you wouldn't take a trade the scanner recommends, you shouldn't be trading the strategy

---

## Methodology Notes

**Capital-aware backtest engine (commit 15c9fe2, merged 2026-04-17).** Before each trade selection, the engine checks whether the portfolio has budget to fund the candidate at its computed share size under the leverage cap. Unfundable candidates are skipped and moved to a "missed" list. This eliminates the previous engine's implicit assumption of unlimited leverage on high-signal days.

**Instrumentation added 2026-04-17.** Each Trade now carries an `EntryDecisionContext` snapshot (cash, open notional, cohort rank, positions open, shares reserved) at decision time. Missed trades are exposed via `GET /api/backtest/{id}/missed-trades` with the same context. Enables post-hoc selection-bias analysis.

**Selection-bias finding (from $10K instrumented baseline at 1.5% risk).** 561 taken vs 7,867 capital-skipped. Taken edge +5.68% vs skipped edge +5.04% — 64bp gap inside 1 standard error. The capital-aware selector is largely rank-agnostic in practice: it takes "whatever fits after ranks 1-2 consume the budget," not "the top of every cohort." Under 1.25% risk the effective concurrent position count moves from 6-7 to ~8, modestly reducing this compression.

**Risk sweep and seed validation (2026-04-17).** Tested 4 risk points (0.75% / 1.0% / 1.25% / 1.5%) at seed 42, then validated 1.25% across 4 seeds (1, 7, 42, 100) at both $10K and $50K. Seed 42 at 1.25% produced upside outliers at both capital tiers (CAGR 58% at $10K, 55% at $50K) but the 4-seed mean Calmar is 2.73 at $10K and 2.75 at $50K — a stable +0.23 to +0.35 edge vs the 1.5% baseline. The advantage is real and capital-invariant. Worst seeds at 1.25% match or exceed the 1.5% baseline floor.

**Pluggable sizer comparison (2026-04-17).** After shipping the pluggable sizer abstraction (AtrRisk / PercentEquity / Kelly / VolatilityTarget), ran 9 variants including multi-seed validation of the two closest challengers. `AtrRisk(1.25%, 2.0)` remains Pareto-dominant on 4-seed mean: Calmar 2.73 vs VolTarget(1.2%, 2.0) 2.52 and Kelly-half 2.22. ATR-anchored sizing auto-shrinks positions when realized volatility expands — exactly when drawdowns accelerate. See `VCP_STRATEGY_DEVELOPMENT.md` section "Pluggable Sizer Comparison" for the full sweep.

**Quarterly walk-forward at the production config (2026-04-21, commits `c273780` + `d0b07aa`).** Added month-based IS/OOS/step with `positionSizing` + `randomSeed` threading to `/api/backtest/walk-forward`, then ran a 36-month IS / 3-month OOS / 3-month step sweep (27 disjoint OOS windows, 2019-01 through 2025-10) at the production trading config: $10K, AtrRisk(1.25%, 2.0), leverage 1.0, seed 42. Aggregate WFE 0.830, OOS edge +5.03%, 481 trades. The year-based 5y/1y baseline was reproduced in the same session at WFE 0.679 (~0.667 headline) as a regression check. The two measurements are consistent within noise — Welch t=0.59 on per-window OOS edges — but the quarterly run surfaces a per-window distribution the 4-window sample could not: OOS-edge SD 7.00% (vs baseline 2.48%), median per-window WFE 0.48 (half of quarters under-realize 50% of IS edge), and 7/27 negative OOS quarters (four of them outside known bear regimes: 2019-Q2, 2019-Q3, 2023-Q3, plus Covid 2020-Q1).

**IS-edge decay — open monitoring item (2026-04-21).** Across the quarterly WF, mean IS edge by training-window vintage: 2019-2021 windows **7.01%**, 2022-2023 windows **6.56%**, 2024-2025 windows **4.17%**. OOS kept pace with IS in 2024-2025 only because IS also fell. Unclear whether this reflects crowding, regime change, or Mag-7 breadth compression; worth tracking a rolling 3-year IS-analog once live to see whether the decay continues.

## Follow-Ups

- **Live paper-trade validation** — 15-20 paper trades tracking scanner recommendations to ensure real execution matches backtest assumptions.
- **Track IS-edge decay live** — see Methodology Notes. Compute a rolling 3-year IS-analog edge once live and compare to the 2019-2021 (7.0%) / 2022-2023 (6.6%) / 2024-2025 (4.2%) vintages. Further decay is a soft kill-switch input.

*Resolved:*
- ~~Sizer comparison~~ — 9-variant sweep completed 2026-04-17. AtrRisk(1.25%, 2.0) retained. See `VCP_STRATEGY_DEVELOPMENT.md` section "Pluggable Sizer Comparison".
- ~~Walk-forward at 1.25% + quarterly stepping~~ — combined run completed 2026-04-21 via the new month-based stepping. 27 disjoint OOS quarters at the production config (1.25% ATR-risk, $10K, seed 42). Confirms the 5y/1y baseline within noise (Welch t=0.59). See Methodology Notes.
- ~~$20K multi-seed validation at 1.25%~~ — 4-seed run completed 2026-04-21 (seeds 1/7/42/100). 4-seed mean: CAGR 50.7%, MaxDD 19.4%, Sharpe 2.28, Calmar 2.62, Alpha 33.3%, final $1.28M. Seed 42 only modestly above mean on CAGR/Sharpe but **inflated the prior Alpha figure by ~6pp** (39.2% → 33.3%). Capital-invariance of the "1.25% dominates" claim holds: $20K mean sits squarely between $10K and $50K 4-seed means on every risk-adjusted metric.

*Dropped:*
- ~~Monte Carlo re-run under capital-aware engine at 1.25%~~ — removed 2026-04-21. The prior MC value (pre-merge p5 edge +4.93%) isn't load-bearing anywhere in the plan (not in Kill Criteria, monitoring thresholds, or return expectations). What MC was meant to show — edge stability and tail-risk framing — is now covered more directly by the 27-window quarterly WF (empirical OOS distribution over the historical period) plus the 4-seed runs at $10K/$20K/$50K (tie-breaking variance). Seed-based MC would only tighten those bounds on a variance axis that doesn't capture the dominant risk (regime change). If kill-switch thresholds ever need probability framing, revisit with a proper bootstrap-of-signals implementation — not the current trade-shuffling code, which is path-dependently biased.

---

## Appendix: Reproducing the Baseline Backtest

The config that produced the "Validated Performance" numbers (single-seed run; the 4-seed mean requires repeating with `randomSeed` 1 / 7 / 42 / 100):

```bash
curl -s -X POST http://localhost:8080/udgaard/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "assetTypes": ["STOCK"],
    "useUnderlyingAssets": false,
    "entryStrategy": {"type": "predefined", "name": "Vcp"},
    "exitStrategy": {"type": "predefined", "name": "VcpExitStrategy"},
    "startDate": "2016-01-01",
    "endDate": "2025-12-31",
    "maxPositions": 15,
    "entryDelayDays": 1,
    "randomSeed": 42,
    "positionSizing": {
      "startingCapital": 10000,
      "sizer": {"type": "atrRisk", "riskPercentage": 1.25, "nAtr": 2.0},
      "leverageRatio": 1.0
    }
  }' > /tmp/vcp_baseline.json
```

**Notes:**
- **Ranker omitted** — VCP's preferred `SectorEdge` with its built-in IS-derived sector ranking is used by default.
- **`randomSeed`** controls tie-breaking within the ranker when multiple candidates share the same sector rank. `SectorEdge` itself is deterministic, but cohort-level ties still exist.
- **Runtime:** 10-15 minutes; requires ~12GB JVM heap (`-Xmx12288m` already configured in `bootRun`).
- **Against production:** swap host for `http://localhost:9080/udgaard/api/backtest` and add `-H "X-API-Key: <your-key>"`. Run one heavy backtest at a time.
- **Expected output:** the `backtestId` in the response can be fed to `/api/backtest/{id}/missed-trades` (selection-bias analysis) and the Monte Carlo endpoint.

To vary capital or risk, change `startingCapital` and `sizer.riskPercentage` only — everything else is fixed by the plan.

---

## First 30 Days Checklist

1. Fund IBKR account with starting capital
2. Configure scanner for VCP strategy
3. Run scanner daily after US market close
4. Paper trade for 4-6 weeks, logging every signal and decision
5. Deploy Phase 1 capital ($5K-$10K USD) at 1.0% risk
6. Track all metrics from Section 8 from day one
7. Review after 20 live trades — are you executing mechanically?
8. If yes, proceed to Phase 2. If no, return to paper trading.

**The strategy's edge is real and validated under the capital-aware engine that simulates what a real trader can actually achieve. The risk is not the strategy. The risk is you.**
