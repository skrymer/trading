# Backtesting Skills — Known Limitations Status

Cross-skill rollup of the 13 limitations originally documented in the per-skill `REFERENCE.md` files (backtest / walk-forward / monte-carlo). Tracks which have been closed, which are still open, and which are documented-as-designed (not a fix to ship).

Last updated: 2026-05-03 — after the Backtest persistence PR landed on `feature/backtest-result-persistence` (closes #3 + #11).

## Status legend

- ✅ **Closed** — backend now produces the value; analyst reads pre-computed result
- 🟡 **Open** — still listed in the relevant `REFERENCE.md`'s Known limitations section
- 📝 **Documented as designed** — not a defect; the behaviour is correct, the limitation is just an honest caveat

## Status table

| #  | Limitation                                                                                              | Skill(s)              | Status | Closed by / next step |
|----|---------------------------------------------------------------------------------------------------------|-----------------------|--------|------------------------|
| 1  | Risk-adjusted metrics (Sharpe / Sortino / CAGR / Calmar / SPY-corr / DD-duration) computed in analyst   | backtest, walk-forward| ✅ Closed | PR #1 (`73a4b71`) — `RiskMetricsService` + `BacktestReport.riskMetrics`/`benchmarkComparison`/`cagr`/`drawdownEpisodes` |
| 2  | SPY correlation needs SPY in symbol set or separate fetch                                               | backtest              | ✅ Closed | PR #1 — controller fetches `SPY` via `stockRepository.findBySymbol("SPY", ...)` transparently when sized |
| 3  | `BacktestResultStore` keeps only most-recent                                                            | monte-carlo           | ✅ Closed | Backtest persistence PR — `backtest_reports` JSONB table + `BacktestReportJooqRepository`; multiple `backtestId`s coexist; manual cleanup via `BacktestReportController` (list + delete + batch-delete) and the `/backtest-reports` page |
| 4  | `P(maxDD > X%)` computed in analyst, not backend                                                        | monte-carlo           | ✅ Closed | PR #4 (`3b5f979`) — `drawdownThresholds` request field + `MonteCarloStatistics.drawdownThresholdProbabilities` (also includes CVaR / `expectedDrawdownGivenExceeded`) |
| 5  | Per-window regime tagging is analyst-side via SPY/breadth queries                                       | walk-forward          | ✅ Closed | PR #5 (`024800b`) — `WalkForwardWindow.{inSampleBreadthUptrendPercent, inSampleBreadthAvg, outOfSampleBreadthUptrendPercent, outOfSampleBreadthAvg}` |
| 6  | Daily bars only — no intraday slippage                                                                  | all three             | 🟡 Open   | Needs intraday data ingestion + execution model — large structural |
| 7  | Survivorship bias (V18 mitigates partially)                                                             | all three             | 🟡 Open   | Improve delisted-stock retention + handling — medium-large |
| 8  | Perfect fills at close (`entryDelayDays:1` partially mitigates)                                         | backtest              | 🟡 Open   | Slippage/commission model — medium |
| 9  | Bootstrap assumes IID trades                                                                            | monte-carlo           | 🟡 Open   | Block bootstrap (resample regime-blocks not single trades) — medium |
| 10 | Trade shuffling destroys temporal correlation                                                           | monte-carlo           | 📝 Documented | Fundamental property of the technique — preserved as caveat in `monte-carlo/REFERENCE.md` |
| 11 | Cache expiry on `backtestId` — 1h                                                                       | monte-carlo           | ✅ Closed (subsumed by #3) | Closed by the Backtest persistence PR — JSONB store has no TTL; retention is manual via the `/backtest-reports` page |
| 12 | `derivedSectorRanking` informational only                                                               | walk-forward          | 📝 Documented | Working as designed; per-window IS-derived ranking does NOT re-rank OOS trades |
| 13 | Walk-forward tests parameter durability, not optimization procedure                                     | walk-forward          | 📝 Documented | Different harness; out of scope for the WF skill |

## What landed in the closing PRs

All three closing PRs cherry-picked onto `feature/backtest-skill-roadmap` (now linear: `e5c9fec → 46ed1d8 → 024800b → 3b5f979 → 73a4b71`).

### PR #5 — `024800b` — Walk-forward per-window regime tagging
- 4 new fields on `WalkForwardWindow` (IS + OOS breadth uptrend % + breadth avg)
- Reuses `MarketBreadthDaily.isInUptrend()` (canonical project definition); zero extra DB I/O via `sharedContext.marketBreadthMap`
- Removed "Per-window regime tagging is best-effort" bullet from `walk-forward/REFERENCE.md`

### PR #4 — `3b5f979` — Monte Carlo drawdown threshold probabilities + CVaR
- Optional `drawdownThresholds: List<Double>?` request field with `init {}` validation
- New `DrawdownThresholdProbability(drawdownPercent, probability, expectedDrawdownGivenExceeded)` data class
- CVaR added beyond the original ask — answers "given the threshold is breached, how bad on average?"
- Removed "Drawdown-threshold probabilities computed in analyst" bullet from `monte-carlo/REFERENCE.md`

### PR #1 — `73a4b71` — Risk-adjusted metrics + API restructure + math fixes
- New `RiskMetricsService` owns: Sharpe, Sortino, CAGR, Calmar, SQN, tailRatio, benchmark comparison, drawdown episodes
- `BacktestReport` API restructured: existing `calmarRatio`/`sqn`/`tailRatio` moved into `riskMetrics` sub-object; new `benchmarkComparison` sub-object; `cagr` + `drawdownEpisodes` flat at top-level; `profitFactor` stays flat (it's profitability, not risk-adjusted)
- Quant-review-driven math fixes: Calmar = `CAGR / |maxDD|` (was `totalReturn / maxDD`, inflated by ~N years); CAGR uses calendar-day annualization (365.25 base); drawdown episodes use Magdon-Ismail state machine (opens at DD < −0.5%, closes ONLY at new ATH); tailRatio gates at trades.size < 20; benchmark correlation requires ≥ 60-day overlap; beta = `cov/var` (NOT zero-intercept regression); active return labelled honestly (NOT Jensen's alpha — no RF subtraction); new optional `riskFreeRatePct` request field for non-raw Sharpe + Sortino MAR
- USD-only assumption documented; survivorship-bias note added
- Frontend (asgaard) updated in same PR per no-backwards-compat-shim rule

## Live validation done in this session

Numbers below are the 4-seed (1/7/42/100) sweep at `1.25%` ATR-risk, $10K start, 2016-2025, against the new backend on dev 8080.

| Metric | 4-seed mean (range) | Validates |
|---|---|---|
| Calmar | 2.35 (1.99–2.80) | New `cagr / abs(maxDD)` formula — verified to 3 decimals across all 4 seeds |
| CAGR | 54.5% (49.5–60.9%) | Calendar-day annualization |
| Sharpe (raw, RF=0) | 2.17 (2.03–2.34) | New backend value |
| Sortino | 3.78 (3.58–4.11) | New backend value |
| SQN | 6.62 (5.94–7.17) | Ported from `BacktestReport` helper |
| Tail Ratio | 3.46 (2.92–3.76) | Ported with n ≥ 20 gate |
| Benchmark correlation (vs SPY) | 0.47 (0.46–0.49) | New `cov/var` beta + auto SPY fetch |
| Benchmark beta | 0.65 (0.63–0.69) | |
| Active return vs benchmark | 36.4% (33.0–39.7%) | NEW field; matches plan's prior "Alpha 37.9%" framing — labelled correctly as active return, NOT Jensen's α |

Walk-forward re-verification at 1.25% (PR #5's regime fields populate per window):

| Cadence | Single-seed WFE / OOS edge | Reproduces documented? |
|---|---|---|
| 23-window 36mo/12mo, 2000-2026 | 1.29 / +3.86% (1,262 trades) | Yes — within 1.5% of documented 1.31 / +3.49% |
| 4-window 5y/1y, 2016-2025 | 0.405 / +2.39% (311 trades) | New — flagged as statistically thin (4 windows × 1 seed) |
| 27-window 36mo/3mo, 2016-2025 (4-seed) | Mean **0.90 / +4.40%** (SE ±0.04 / ±0.18pp); 489.5 trades; 76.9% positive | Promoted to plan's primary reference; documented seed-42 (0.83 / +5.03%) was at the high end of the 4-seed distribution |

## Still open (priority order for follow-up work)

1. **#9 (Block bootstrap for MC)** — medium difficulty; tightens edge confidence on regime-correlated strategies
2. **#8 (Slippage/commission model)** — medium difficulty; affects all reported edge numbers (currently overstate by an estimated 0.5–1pp per the plan's own framing)
3. **#7 (Survivorship bias improvements)** — medium-large; V18 + the 2026-04-28 EODHD delisted import already mitigated this, but more universe coverage tightens the bias further
4. **#6 (Intraday slippage)** — large structural; needs intraday data pipeline + execution model. Lowest priority because the 0.5–1pp estimated drag is already baked into the plan's haircut calculations.
