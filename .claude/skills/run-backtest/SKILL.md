---
name: run-backtest
description: Run backtests using the Udgaard API, analyze results, run Monte Carlo simulations, and walk-forward validation. Use when the user asks to backtest a strategy or analyze trading performance.
argument-hint: "[strategy-name] [options]"
---

# Run Trading Backtest

This skill helps Claude run comprehensive backtests for trading strategies using the Udgaard API and provides detailed comparative analysis.

## Overview

When the user asks to run a backtest, Claude should:

1. **Discover available resources** using REST discovery endpoints
2. **Run backtests** with appropriate parameters
3. **Analyze diagnostic metrics** (time-based stats, ATR drawdowns, market conditions, exit reasons)
4. **Calculate comprehensive metrics** (returns, drawdowns, year-by-year, risk-adjusted performance)
5. **Compare strategies** side-by-side
6. **Validate with Monte Carlo** simulations
7. **Run walk-forward validation** for out-of-sample testing
8. **Present actionable insights** with clear recommendations

## Getting Available Resources

Use the REST discovery endpoints to find what's available:

```bash
# List available entry and exit strategies
curl -s http://localhost:8080/udgaard/api/backtest/strategies

# List available rankers for position-limited backtests
curl -s http://localhost:8080/udgaard/api/backtest/rankers

# List available conditions for custom strategies (with parameter metadata)
curl -s http://localhost:8080/udgaard/api/backtest/conditions

# List stock symbols with data (symbol, sector, assetType, quoteCount, lastQuoteDate)
curl -s http://localhost:8080/udgaard/api/stocks/symbols
```

## Backtest Response Format

**IMPORTANT:** The backtest API returns a lean `BacktestResponseDto` with pre-computed analytics. It does **NOT** include raw trade data. Key fields:

**Scalar metrics** (directly in response):
- `backtestId` - UUID for on-demand trade fetching and Monte Carlo
- `totalTrades`, `numberOfWinningTrades`, `numberOfLosingTrades`
- `winRate`, `lossRate`, `edge`, `profitFactor`
- `averageWinPercent`, `averageLossPercent`
- `stockProfits` - Array of [symbol, totalProfit] pairs
- `underlyingAssetTradeCount` - Trades using underlying asset signals

**Pre-computed analytics** (directly in response):
- `timeBasedStats` - Year/quarter/month performance breakdown
- `exitReasonAnalysis` - Exit reason stats with win rates
- `sectorPerformance` - Per-sector performance breakdown
- `stockPerformance` - Per-stock performance breakdown
- `atrDrawdownStats` - ATR drawdown percentiles and distribution
- `marketConditionAverages` - Avg market breadth, SPY uptrend %
- `edgeConsistencyScore` - Edge consistency across years (0-100)
- `positionSizing` - Position sizing results (only present when `positionSizing` config is in request)

**Pre-computed chart data** (directly in response):
- `equityCurveData` - `[{date, profitPercentage}]` sorted by exit date (unlimited backtests)
- `excursionPoints` - `[{mfe, mae, mfeATR, maeATR, mfeReached, profitPercentage, isWinner}]` (max 5000)
- `excursionSummary` - Pre-computed MFE/MAE averages for winners/losers
- `dailyProfitSummary` - `[{date, profitPercentage, tradeCount}]` grouped by entry date
- `marketConditionStats` - Scatter points + uptrend/downtrend win rates
- `sectorStats` - Sector stats without nested trade lists

**Position sizing equity curve** (when `positionSizing` is in request):
- `positionSizing.equityCurve` - `[{date, portfolioValue}]` daily mark-to-market points
- This is a **daily M2M equity curve**: portfolio value = cash + unrealized P/L of all open positions, computed every trading day
- Much higher resolution than exit-based curves (~2,500 points for 10 years vs ~900 exit-based)
- Drawdown is tracked from these daily M2M values, capturing intra-trade drawdowns

**On-demand trade data** (separate endpoint):
- `GET /api/backtest/{backtestId}/trades?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`
- Returns full `Trade` objects for a specific date range
- Use for drill-down analysis when individual trade details are needed

## Running Backtests

### Basic Backtest

**Using Predefined Strategies:**

```bash
# Single stock backtest
curl -s -X POST http://localhost:8080/udgaard/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "stockSymbols": ["QQQ"],
    "entryStrategy": {"type": "predefined", "name": "VegardPlanEtf"},
    "exitStrategy": {"type": "predefined", "name": "VegardPlanEtf"},
    "startDate": "2020-01-01",
    "endDate": "2025-11-13",
    "cooldownDays": 10,
    "refresh": false
  }' > /tmp/backtest_results.json

# All stocks backtest using assetTypes (preferred over empty stockSymbols)
curl -s -X POST http://localhost:8080/udgaard/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "assetTypes": ["STOCK"],
    "useUnderlyingAssets": false,
    "entryStrategy": {"type": "predefined", "name": "Vcp"},
    "exitStrategy": {"type": "predefined", "name": "MjolnirExitStrategy"},
    "startDate": "2016-01-01",
    "endDate": "2025-12-31"
  }' > /tmp/backtest_all_stocks.json
```

**Using Custom Strategies:**

```bash
curl -s -X POST http://localhost:8080/udgaard/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "stockSymbols": ["QQQ"],
    "entryStrategy": {
      "type": "custom",
      "conditions": [
        {"type": "uptrend"},
        {"type": "marketUptrend"},
        {"type": "priceAboveEma", "parameters": {"period": 20}},
        {"type": "valueZone", "parameters": {"atrMultiplier": 2.0}}
      ]
    },
    "exitStrategy": {"type": "predefined", "name": "OvtlyrPlanEtf"},
    "startDate": "2020-01-01",
    "endDate": "2025-11-13",
    "cooldownDays": 10,
    "refresh": false
  }' > /tmp/backtest_results.json
```

**IMPORTANT:** Custom strategy conditions require parameters to be nested in a `"parameters"` object:
- **Correct**: `{"type": "priceAboveEma", "parameters": {"period": 20}}`
- **Incorrect**: `{"type": "priceAboveEma", "period": 20}` (parameters will be ignored!)

### Universe Selection

**By asset type** (preferred for broad backtests):

```json
{
  "assetTypes": ["STOCK"],
  "useUnderlyingAssets": false
}
```

Available asset types: `STOCK`, `ETF`, `LEVERAGED_ETF`. Can combine: `["STOCK", "ETF"]`.

**By symbols** (for specific stock testing):

```json
{
  "stockSymbols": ["AAPL", "MSFT", "GOOGL"]
}
```

**By sector** (include/exclude):

```json
{
  "includeSectors": ["XLK", "XLF", "XLV"],
  "excludeSectors": ["XLE", "XLP"]
}
```

Available sector symbols: XLK (Technology), XLF (Financials), XLV (Health Care), XLI (Industrials), XLY (Consumer Discretionary), XLRE (Real Estate), XLU (Utilities), XLP (Consumer Staples), XLC (Communication Services), XLB (Materials), XLE (Energy)

### Advanced Features

#### 1. Cooldown Period

**Global cooldown** blocks ALL entries for X trading days after ANY exit:

```json
{
  "cooldownDays": 10
}
```

**When to use:**
- **Essential for leveraged ETFs** (TQQQ, SOXL, etc.) - prevents whipsaw trades
- **Recommended for VegardPlanEtf strategy** - shown to improve returns significantly
- **Reduces overtrading** - fewer, higher-quality trades
- Set to 0 to disable (default)

#### 2. Entry Delay

**Delay entry by N trading days** to model realistic execution lag:

```json
{
  "entryDelayDays": 1
}
```

**When to use:**
- **Realistic backtesting** - signal fires after Day 0 closes, you enter at Day 1's close
- Set to 0 to disable (default, enter at signal day's close)

**How it works:**
- Entry signal is evaluated on Day 0 using Day 0's quotes
- Actual entry uses Day 0+N's close price
- If Day 0+N has no data (e.g., signal on last trading day), the entry is skipped
- Exit strategy starts evaluating from the delayed entry date

#### 3. Underlying Asset Mapping

**Use underlying assets for signals while trading leveraged ETFs:**

```json
{
  "stockSymbols": ["TQQQ"],
  "useUnderlyingAssets": true,
  "customUnderlyingMap": {
    "TQQQ": "QQQ"
  }
}
```

**Built-in mappings** (automatically detected):
- TQQQ/SQQQ -> QQQ
- UPRO/SPXU -> SPY
- SOXL/SOXS -> SOXX
- TNA/TZA -> IWM
- And many more (see ConfigModal.vue for full list)

#### 4. Position Limiting

```json
{
  "maxPositions": 15,
  "ranker": "SectorEdge",
  "rankerConfig": {
    "sectorRanking": ["XLC", "XLI", "XLK", "XLY", "XLV", "XLF", "XLE", "XLU", "XLP", "XLB", "XLRE"]
  }
}
```

**Available rankers:**
- **SectorEdge** - Ranks by sector priority (deterministic, reproducible). Requires `rankerConfig.sectorRanking` array. Some strategies have a preferred SectorEdge ranking built-in (e.g., Vcp)
- **Adaptive** - Adapts to recent market conditions
- **Composite** - Multi-factor ranking
- **Volatility** - Ranks by volatility characteristics
- **DistanceFrom10Ema** - Ranks by distance from 10 EMA
- **SectorStrength** - Ranks by sector momentum
- **Random** - Random selection (non-deterministic, results vary between runs)

**Note:** When `ranker` is omitted, the strategy's preferred ranker is used if it has one. The Vcp strategy defaults to its preferred SectorEdge ranker with a built-in sector ordering.

#### 5. Position Sizing (Pluggable Sizers)

Position sizing calculates **how many shares to buy** for each trade. The equity curve tracks daily mark-to-market portfolio value. Four sizers are available: `atrRisk` (default, risk-based), `percentEquity` (notional-based), `kelly` (Kelly criterion), `volTarget` (equal-vol contribution).

```json
{
  "positionSizing": {
    "startingCapital": 10000,
    "sizer": {
      "type": "atrRisk",
      "riskPercentage": 1.25,
      "nAtr": 2.0
    },
    "leverageRatio": 1.0
  }
}
```

**Top-level parameters:**
- `startingCapital` - Starting portfolio value in dollars (default: 100,000)
- `sizer` - Polymorphic sizer config (see below)
- `leverageRatio` - Max total open notional as multiple of portfolio value (optional, null = no cap). `1.0` = cash account (notional <= portfolio). When cap is reached, candidates get 0 shares and are recorded as capital-skipped.

**Available sizers:**

1. **`atrRisk`** — classical ATR-based risk sizing. `shares = floor(equity × risk% / (nAtr × ATR))`
   ```json
   {"type": "atrRisk", "riskPercentage": 1.25, "nAtr": 2.0}
   ```

2. **`percentEquity`** — equal-notional baseline, ignores ATR. `shares = floor(equity × percent% / price)`
   ```json
   {"type": "percentEquity", "percent": 12.5}
   ```

3. **`kelly`** — Kelly-criterion sizing. `shares = floor(equity × f × fractionMultiplier / price)` where `f = W - (1-W)/R`. Default `fractionMultiplier=0.25` (quarter-Kelly recommended — W/R estimates have wide CIs).
   ```json
   {"type": "kelly", "winRate": 0.52, "winLossRatio": 1.5, "fractionMultiplier": 0.25}
   ```

4. **`volTarget`** — equal-vol-contribution. `shares = floor(equity × targetVolPct% / (kAtr × ATR))`
   ```json
   {"type": "volTarget", "targetVolPct": 0.5, "kAtr": 1.0}
   ```

**IMPORTANT:** Always include `leverageRatio: 1.0` for stock backtests. Without it, `atrRisk` or `volTarget` with small ATR can produce extreme leverage on low-ATR stocks, leading to unrealistic results.

#### 6. Drawdown-Responsive Position Sizing (Optional)

Reduces risk per trade when the portfolio is in drawdown, scaling back up as equity recovers to new highs. This is a position sizing overlay — it does not change entry signals, only dollar exposure.

```json
{
  "positionSizing": {
    "startingCapital": 10000,
    "sizer": {"type": "atrRisk", "riskPercentage": 1.25, "nAtr": 2.0},
    "leverageRatio": 1.0,
    "drawdownScaling": {
      "thresholds": [
        {"drawdownPercent": 5.0, "riskMultiplier": 0.67},
        {"drawdownPercent": 10.0, "riskMultiplier": 0.33}
      ]
    }
  }
}
```

**How it works:**
- At each entry, current drawdown % is computed from peak capital vs cash
- Thresholds are evaluated deepest-first; the first match applies its `riskMultiplier` via the sizer's `scale()` method
- Each sizer decides which of its parameters scales (`atrRisk` scales `riskPercentage`; `percentEquity` scales `percent`; `kelly` scales `fractionMultiplier`; `volTarget` scales `targetVolPct`)
- Example at 7% drawdown with `atrRisk` at 1.25%: effective risk = 1.25% × 0.67 = 0.84%
- Omit `drawdownScaling` entirely to disable (default behavior)

**Impact (VCP 2016-2025):** Max DD 21.2% → 13.6% (-36%), CAGR 46.4% → 39.8% (-6.6pp), Calmar 2.18 → 2.90 (+33%). All risk-adjusted ratios improve (Sharpe, Sortino, Calmar) at the cost of lower absolute returns from compounding drag during recovery phases.

**Response fields** (when `positionSizing` is included in request):
- `positionSizing.startingCapital` / `finalCapital` - Portfolio start and end values
- `positionSizing.totalReturnPct` - Total return as percentage
- `positionSizing.maxDrawdownPct` / `maxDrawdownDollars` - Peak-to-trough drawdown (daily M2M)
- `positionSizing.peakCapital` - Highest portfolio value reached
- `positionSizing.trades[]` - Sized trades with `shares`, `dollarProfit`, `portfolioReturnPct`
- `positionSizing.equityCurve[]` - Daily M2M `{date, portfolioValue}` points

**How daily M2M equity curve works:**
- Portfolio value = `cash + sum(unrealized P/L of open positions)` computed each trading day
- Cash tracks starting capital + realized P/L (not reduced by purchases)
- Open position unrealized P/L = `shares * (currentPrice - entryPrice)`
- Peak and drawdown tracked from these daily values, capturing intra-trade drawdowns
- ~2,500 points for a 10-year backtest (one per trading day with open positions)
- **Gaps exist** when no positions are open -- equity curve has no data points during fully uninvested periods. Cash value is flat during these gaps.

**Example: Full VCP backtest with position sizing**

```bash
curl -s -X POST http://localhost:8080/udgaard/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "assetTypes": ["STOCK"],
    "useUnderlyingAssets": false,
    "entryStrategy": {"type": "predefined", "name": "Vcp"},
    "exitStrategy": {"type": "predefined", "name": "MjolnirExitStrategy"},
    "startDate": "2016-01-01",
    "endDate": "2025-12-31",
    "maxPositions": 15,
    "entryDelayDays": 1,
    "positionSizing": {
      "startingCapital": 10000,
      "sizer": {"type": "atrRisk", "riskPercentage": 1.25, "nAtr": 2.0},
      "leverageRatio": 1.0
    }
  }' > /tmp/backtest_sized.json
```

**Note:** Full 10-year VCP backtest with position sizing requires ~12GB heap (`-Xmx12288m` in `build.gradle` bootRun task) and takes 10-15 minutes.

## Two Backtest Modes

### 1. Unlimited (Statistical Analysis)

Best for measuring raw strategy edge, sector analysis, parameter sweeps, and ablation studies. No position limits or sizing -- every signal is taken.

```bash
curl -s -X POST http://localhost:8080/udgaard/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "assetTypes": ["STOCK"],
    "useUnderlyingAssets": false,
    "entryStrategy": {"type": "predefined", "name": "Vcp"},
    "exitStrategy": {"type": "predefined", "name": "MjolnirExitStrategy"},
    "startDate": "2016-01-01",
    "endDate": "2025-12-31"
  }'
```

**Use for:** Edge measurement, sector performance, condition ablation, parameter sweeps.

### 2. Position-Sized (Realistic Simulation)

Simulates real trading with capital constraints, position sizing, entry delay, and position cap. Uses the strategy's preferred ranker for deterministic results.

```bash
curl -s -X POST http://localhost:8080/udgaard/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "assetTypes": ["STOCK"],
    "useUnderlyingAssets": false,
    "entryStrategy": {"type": "predefined", "name": "Vcp"},
    "exitStrategy": {"type": "predefined", "name": "MjolnirExitStrategy"},
    "startDate": "2016-01-01",
    "endDate": "2025-12-31",
    "maxPositions": 15,
    "entryDelayDays": 1,
    "positionSizing": {
      "startingCapital": 10000,
      "sizer": {"type": "atrRisk", "riskPercentage": 1.25, "nAtr": 2.0},
      "leverageRatio": 1.0
    }
  }'
```

**Use for:** CAGR, real dollar P/L, max drawdown, equity curves, Monte Carlo validation.

## Strategy Optimization Goals

### Key Metrics (Priority Order)

1. **Edge Consistency Score (EC)** - Measures reliability across years (0-100)
   - EC >= 80: Excellent -- strategy works consistently
   - EC 60-79: Good -- reliable with some weak years
   - EC 40-59: Moderate -- inconsistent
   - EC 20-39: Poor -- unreliable
   - EC < 20: Very Poor -- likely no real edge

2. **Edge (per-trade average profit)** - Raw strategy quality
   - Target: >= 1.5% for tradeable edge (accounts for transaction costs/slippage)
   - Edge >= 3%: Strong
   - Edge >= 5%: Excellent

3. **Risk-Adjusted Returns** - Computed from position-sized equity curve (NOT in API response)
   - **Calmar Ratio** = CAGR / Max Drawdown (target: > 1.0, excellent: > 1.5)
   - **Sharpe Ratio** = annualized excess return / annualized volatility (target: > 1.0, excellent: > 2.0). Uses risk-free rate = 0 (excess return = total return). For precise Sharpe, subtract current T-bill rate.
   - **Sortino Ratio** = annualized return / downside deviation (target: > 1.5, excellent: > 3.0)
   - **Profit Factor** = gross wins / gross losses (target: > 1.5, excellent: > 2.0). Available in API response as `profitFactor`.

4. **Max Drawdown** - For position-sized backtests
   - < 20%: Good (psychologically sustainable)
   - 20-30%: Acceptable if CAGR is proportionally high
   - > 30%: Concerning -- review position sizing
   - Plan for 1.5-2x observed max DD in live trading (tail events can exceed historical worst)

5. **Win Rate + Win/Loss Ratio** - Context-dependent
   - Asymmetric strategies (e.g., VCP): 45-50% WR is fine if W/L ratio is 3:1+
   - Mean-reversion strategies: 65-80% WR expected with lower W/L ratio
   - The combination of WR and W/L ratio determines edge, not either alone

### Important: Win Rate Depends on Strategy Type

Do NOT apply a blanket "win rate > 55%" requirement. Trend-following and breakout strategies (VCP, Mjolnir) are designed to have ~45-50% win rate with 3:1 win/loss ratio. This produces strong edge (+5%) despite losing more than half the trades. Judge by edge and EC, not win rate alone.

## Backtest Diagnostic Metrics

### Available Diagnostic Metrics

**1. Time-Based Performance (`timeBasedStats`)**

Breakdown of strategy performance by time periods:
- **By Year**: Annual performance with trades, win rate, avg profit, holding days, exit reasons
- **By Quarter**: Quarterly breakdown (e.g., "2025-Q1")
- **By Month**: Monthly breakdown (e.g., "2025-01")

```python
time_stats = result['timeBasedStats']
for year, stats in time_stats['byYear'].items():
    print(f"{year}: {stats['trades']} trades, {stats['winRate']*100:.1f}% win rate, {stats['avgProfit']:.2f}% avg profit")
```

**2. ATR Drawdown Statistics (`atrDrawdownStats`)**

Shows how much adverse movement (in ATR units) winning trades endure before becoming profitable:
- **Percentiles**: 25th, 50th, 75th, 90th, 95th, 99th
- **Distribution**: Buckets with cumulative percentages

```python
atr_stats = result['atrDrawdownStats']
print(f"Median drawdown: {atr_stats['medianDrawdown']:.2f} ATR")
print(f"75th percentile: {atr_stats['percentile75']:.2f} ATR")
print(f"95th percentile: {atr_stats['percentile95']:.2f} ATR")
```

**3. Market Condition Stats (`marketConditionStats`)**

- `scatterPoints` - Array of `{breadth, profitPercentage, isWinner, spyInUptrend}` per trade
- `uptrendWinRate` / `downtrendWinRate` - Win rates during SPY uptrend vs downtrend
- `uptrendCount` / `downtrendCount` - Trade counts per regime

**4. Excursion Summary (`excursionSummary` + `excursionPoints`)**

Pre-computed MFE/MAE statistics:
- `avgMFEEfficiency` - How much of maximum favorable excursion was captured (winners only)
- `winnerAvgMFE`, `winnerAvgMAE`, `winnerAvgFinalProfit`
- `loserAvgMFE`, `loserAvgMAE`, `loserAvgFinalLoss`, `loserMissedWinRate`

**5. Exit Reason Analysis (`exitReasonAnalysis`)**

Statistics per exit reason: count, avg profit, avg holding days, win rate per exit reason.

**6. Sector Performance (`sectorPerformance`)**

Performance breakdown by sector: trades, win rate, avg profit (`avgProfit` field), avg holding days per sector. Sorted by `avgProfit` descending to find best/worst sectors.

**7. Edge Consistency Score (`edgeConsistencyScore`)**

Composite score (0-100) measuring edge reliability:
- **profitablePeriodsScore** (weight 40%): % of years with positive edge
- **stabilityScore** (weight 40%): % of years with edge >= 1.5% (tradeable threshold)
- **downsideScore** (weight 20%): How bad the worst year is (100 if positive, scales to 0 at -10%)
- **yearlyEdges**: Map of year -> edge value

```python
ecs = result.get('edgeConsistencyScore')
if ecs:
    print(f"Edge Consistency: {ecs['score']:.0f}/100 ({ecs['interpretation']})")
    for year, edge in sorted(ecs['yearlyEdges'].items()):
        tradeable = "T" if edge >= 1.5 else ""
        print(f"  {year}: {edge:+.2f}% {tradeable}")
```

**Important:** The top-level field is `edgeConsistencyScore` (an object with `score`, `interpretation`, `yearlyEdges`, etc.). Do not confuse with `edgeConsistency` which appears on individual `sectorStats` entries.

## Sub-Agent Delegation

The following analysis tasks should be delegated to specialized sub-agents (defined in `.claude/agents/`). This keeps the main conversation focused and lets agents run in parallel.

### Available Sub-Agents

| Sub-Agent | When to Use |
|-----------|------------|
| **post-backtest-analyst** | After a position-sized backtest. Computes drawdown duration, SPY correlation, Sharpe/Sortino/Calmar/CAGR. |
| **monte-carlo-analyst** | After any backtest. Runs bootstrap resampling (edge confidence) and trade shuffling (drawdown distribution). Needs `backtestId`. |
| **walk-forward-analyst** | To validate a strategy out-of-sample. Runs walk-forward, computes WFE, per-window analysis. |
| **quant-analyst** *(voltagent plugin)* | After completing strategy analysis. Independent audit for methodological flaws, overfitting, bias. |

### Typical Workflow

1. Run unlimited backtest (edge measurement)
2. Run position-sized backtest (realistic simulation)
3. Delegate to **post-backtest-analyst** with the result file
4. Delegate to **monte-carlo-analyst** with the `backtestId`
5. Delegate to **walk-forward-analyst** with the strategy config
6. Delegate to **quant-analyst** with the strategy document or results
7. Synthesize findings and present to user

Steps 3-5 can run in parallel since they are independent.

## Analysis Guidelines

### What to Look For

1. **Edge Consistency (EC Score)** - Is the edge reliable across years?
2. **Edge magnitude** - Is it above the tradeable threshold (1.5%)?
3. **Yearly edge breakdown** - Any losing years? How many tradeable years (>= 1.5%)?
4. **Sector performance** - All sectors profitable? Any outliers?
5. **Exit reason analysis** - Are stop losses too frequent? EMA exits capturing gains?
6. **Position-sized CAGR and drawdown** - Realistic expectations
7. **Walk-forward WFE** - Does edge persist out-of-sample?
8. **Monte Carlo confidence** - Is edge statistically robust?

### Minimum Trade Counts for Statistical Significance

- **< 30 trades**: Results are noise. Do not draw conclusions.
- **30-100 trades**: Directional only. Edge sign may be meaningful, magnitude is unreliable.
- **100-300 trades**: Useful for coarse analysis. Win rate and edge start stabilizing.
- **300+ trades**: Statistically robust. Confidence intervals narrow enough for decisions.
- **Per-sector/per-year**: Need 30+ trades per slice for meaningful breakdown analysis.

### Red Flags

- Edge < 1.5% (below tradeable threshold after costs)
- EC Score < 60 (inconsistent)
- Multiple losing years
- Single sector providing all the edge
- Stop loss exits > 20% of all exits (entries too loose)
- Walk-forward WFE < 0.3 (likely curve-fit)
- Monte Carlo p5 edge < 0 (edge may not be real)
- Max drawdown > 35% in position-sized mode (review sizing)
- Actual DD exceeds MC p95 by large margin (structural correlation risk)

### Green Flags

- EC Score >= 80 (Excellent consistency)
- Edge >= 3% with tight yearly distribution
- All or nearly all sectors profitable
- 8+/10 years profitable, 7+/10 tradeable (edge >= 1.5%)
- Walk-forward WFE > 0.5
- Monte Carlo p5 edge well above 1.5%
- Calmar ratio > 1.5 (strong risk-adjusted returns)
- Sharpe ratio > 2.0
- SPY correlation < 0.5 (mostly independent alpha)

## Fetching Individual Trades On-Demand

```bash
# Fetch trades for a specific date range
curl -s "http://localhost:8080/udgaard/api/backtest/${BACKTEST_ID}/trades?startDate=2024-01-15&endDate=2024-01-19"

# Fetch trades for a single date
curl -s "http://localhost:8080/udgaard/api/backtest/${BACKTEST_ID}/trades?startDate=2024-01-15"
```

The `backtestId` comes from the backtest response. Trade data is cached server-side for 1 hour.

## Critical Warnings

### Overfitting & Multiple Testing
Every parameter you tweak and re-test increases the risk of curve-fitting to historical noise. If you test 20 parameter combinations, expect ~1 to look good by chance alone (p=0.05). Mitigations:
- **Walk-forward validation** is mandatory before trusting any optimized result
- **Never data-mine conditions** -- have a hypothesis BEFORE adding a condition
- **Report all attempts** -- if you tried 10 variations, present all 10, not just the best
- **Bonferroni correction**: if testing N parameter combos, use significance threshold p < 0.05/N

### Survivorship Bias
The stock universe (V2__Populate_symbols.sql) contains only stocks that survived to the present. Stocks that went bankrupt, were acquired, or delisted during the backtest period are absent. This inflates win rates and average returns because the worst outcomes are excluded from the sample. Until historical delisting data is added, treat all backtest results as optimistic upper bounds.

### Transaction Costs & Slippage
Backtests assume perfect fills at close price with zero commissions and zero slippage. In live trading:
- Commission costs reduce edge (especially for small positions)
- Slippage on entry/exit can be 0.1-0.5% per trade for mid/small-cap stocks
- A strategy with 1.5% edge may have <1% real edge after costs
- Minimum practical edge threshold: ~1.5% to survive costs

## Best Practices

1. **Run unlimited first** for statistical analysis, then position-sized for realistic simulation
2. **Always include `leverageRatio: 1.0`** for stock position-sized backtests
3. **Use `assetTypes: ["STOCK"]`** instead of `stockSymbols: []` for all-stock backtests
4. **Use `entryDelayDays: 1`** for realistic execution modeling
5. **Validate with Monte Carlo** -- bootstrap for edge confidence, trade shuffling for drawdown distribution
6. **Run walk-forward** for out-of-sample validation (WFE > 0.5 = robust)
7. **Analyze drawdown duration** -- depth AND recovery time matter for live trading
8. **Check SPY correlation** to separate alpha from beta
9. **Judge by EC and edge, not win rate** -- asymmetric strategies have low WR by design
10. **Plan for 1.5-2x observed max drawdown** in live trading (tail events exceed historical worst)
11. **Save results to files** before analysis (backtests can take 10-15 minutes)
12. **Don't run unlimited and position-sized in parallel** -- the server handles one heavy backtest at a time
