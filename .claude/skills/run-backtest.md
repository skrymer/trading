---
name: run-backtest
description: This skill shows how to run backtests using the apis and how to verify backtest results using monte carlo simulations
---

# Run Trading Backtest

This skill helps Claude run comprehensive backtests for trading strategies using the Udgaard API and provides detailed comparative analysis.

## Overview

When the user asks to run a backtest, Claude should:

1. **Discover available resources** using MCP tools
2. **Run backtests** with appropriate parameters
3. **Analyze diagnostic metrics** (time-based stats, ATR drawdowns, market conditions, exit reasons)
4. **Calculate comprehensive metrics** (returns, drawdowns, year-by-year, risk-adjusted performance)
5. **Compare strategies** side-by-side
6. **Validate with Monte Carlo** simulations
7. **Present actionable insights** with clear recommendations

## Getting Available Resources

Use the MCP tools from the `stock-backtesting` server to discover what's available:

- **`getAvailableStrategies`** - Lists all entry and exit strategy names
- **`getAvailableRankers`** - Lists rankers for position-limited backtests
- **`getAvailableSymbols`** - Lists stocks with data: symbol, sector, assetType, quoteCount, lastQuoteDate
- **`getAvailableConditions`** - Lists conditions for building custom strategies (with parameter metadata)
- **`getStrategyDetails(strategyName, strategyType)`** - Get detailed info about a specific strategy (description, use case, conditions)
- **`getSystemStatus`** - Health check: database connectivity, stock count, cache status, readiness
- **`explainBacktestMetrics(metrics)`** - Explains what backtest metrics mean (definitions, benchmarks, interpretation)

Always use these MCP tools for discovery instead of curl. The actual backtest execution and Monte Carlo simulation still use the REST API (see sections below).

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
- `equityCurveData` - `[{date, profitPercentage}]` sorted by exit date
- `excursionPoints` - `[{mfe, mae, mfeATR, maeATR, mfeReached, profitPercentage, isWinner}]` (max 5000)
- `excursionSummary` - Pre-computed MFE/MAE averages for winners/losers
- `dailyProfitSummary` - `[{date, profitPercentage, tradeCount}]` grouped by entry date
- `marketConditionStats` - Scatter points + uptrend/downtrend win rates
- `sectorStats` - Sector stats without nested trade lists

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

# All stocks backtest (use empty array)
curl -s -X POST http://localhost:8080/udgaard/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "stockSymbols": [],
    "entryStrategy": {"type": "predefined", "name": "PlanAlpha"},
    "exitStrategy": {"type": "predefined", "name": "PlanMoney"},
    "startDate": "2020-01-01",
    "endDate": "2025-12-13",
    "maxPositions": 10,
    "ranker": "Adaptive",
    "refresh": false
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

**Benefits:**
- Prevents emotional re-entry after losses
- Avoids choppy/sideways market conditions
- Improves win rate and edge per trade
- Can double returns while reducing drawdown

#### 2. Entry Delay

**Delay entry by N trading days** to model realistic execution lag (e.g., seeing signal after market close, entering next day):

```json
{
  "entryDelayDays": 1
}
```

**When to use:**
- **Realistic backtesting** - signal fires after Day 0 closes, you enter at Day 1's close
- **Australian timezone** - user sees signal after US market close, enters ~30 min before next close
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

**When to use:**
- **Leveraged ETFs** (TQQQ, SOXL, UPRO, etc.) - cleaner signals from underlying
- **Inverse ETFs** (SQQQ, SPXU) - use underlying for better signal quality
- **Options strategies** - use underlying stock signals

**Built-in mappings** (automatically detected):
- TQQQ/SQQQ -> QQQ
- UPRO/SPXU -> SPY
- SOXL/SOXS -> SOXX
- TNA/TZA -> IWM
- And many more (see ConfigModal.vue for full list)

#### 4. Sector Filtering

**Include only specific sectors:**

```json
{
  "includeSectors": ["XLK", "XLF", "XLV"]
}
```

**Exclude specific sectors:**

```json
{
  "excludeSectors": ["XLE", "XLP", "XLC", "XLB"]
}
```

**Both can be combined** (include narrows first, then exclude removes from the result):

```json
{
  "includeSectors": ["XLK", "XLF", "XLV", "XLI"],
  "excludeSectors": ["XLI"]
}
```

**Available sector symbols:** XLK (Technology), XLF (Financials), XLV (Health Care), XLI (Industrials), XLY (Consumer Discretionary), XLRE (Real Estate), XLU (Utilities), XLP (Consumer Staples), XLC (Communication Services), XLB (Materials), XLE (Energy)

**Use Case:** Test strategy performance on specific sectors to identify which sectors work best, or exclude consistently poor-performing sectors.

#### 5. Position Limiting

```json
{
  "maxPositions": 10,
  "ranker": "Adaptive"
}
```

**Available rankers:**
- Adaptive (recommended)
- Composite
- Volatility
- DistanceFrom10Ema
- SectorStrength
- Random

#### 6. Position Sizing (ATR-Based)

Position sizing calculates **how many shares to buy** for each trade based on the stock's volatility (ATR) and a risk budget. When enabled, the equity curve tracks actual portfolio dollar value instead of compounding percentage returns.

**Formula:** `shares = floor(portfolioValue × (riskPct / 100) / (nAtr × ATR))`

```json
{
  "positionSizing": {
    "startingCapital": 100000,
    "riskPercentage": 1.5,
    "nAtr": 2.0
  }
}
```

**Parameters:**
- `startingCapital` - Starting portfolio value in dollars (default: 100,000)
- `riskPercentage` - Percentage of portfolio risked per trade (default: 1.5%)
- `nAtr` - ATR multiplier for expected adverse move (default: 2.0)
- `leverageRatio` - Max total open notional as multiple of portfolio value (optional, null = no cap). Examples: `1.0` = cash account (notional ≤ portfolio), `5.0` = deep ITM options (notional ≤ 5× portfolio). When the cap is reached, new positions get fewer shares or 0.

**When to use:**
- **Realistic portfolio simulation** - see actual dollar P&L, not just percentages
- **Multi-stock backtests** - concurrent positions are sized independently from portfolio value at each entry
- **Risk management analysis** - max drawdown in dollars and percentage
- **Strategy comparison** - compare strategies on absolute dollar returns

**Response fields** (when `positionSizing` is included in request):
- `positionSizing.startingCapital` / `finalCapital` - Portfolio start and end values
- `positionSizing.totalReturnPct` - Total return as percentage
- `positionSizing.maxDrawdownPct` / `maxDrawdownDollars` - Peak-to-trough drawdown
- `positionSizing.peakCapital` - Highest portfolio value reached
- `positionSizing.trades[]` - Sized trades with `shares`, `dollarProfit`, `portfolioReturnPct`
- `positionSizing.equityCurve[]` - `{date, portfolioValue}` points for charting

**Example: Full backtest with position sizing**

```bash
curl -s -X POST http://localhost:8080/udgaard/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "stockSymbols": [],
    "entryStrategy": {"type": "predefined", "name": "Mjolnir"},
    "exitStrategy": {"type": "predefined", "name": "Mjolnir"},
    "startDate": "2020-01-01",
    "endDate": "2025-12-31",
    "maxPositions": 15,
    "ranker": "Adaptive",
    "positionSizing": {
      "startingCapital": 100000,
      "riskPercentage": 1.5,
      "nAtr": 2.0
    }
  }' > /tmp/backtest_sized.json
```

**Analyzing position sizing results:**

```python
import json

with open('/tmp/backtest_sized.json', 'r') as f:
    result = json.load(f)

ps = result['positionSizing']
print(f"Starting Capital: ${ps['startingCapital']:,.0f}")
print(f"Final Capital:    ${ps['finalCapital']:,.0f}")
print(f"Total Return:     {ps['totalReturnPct']:.2f}%")
print(f"Max Drawdown:     {ps['maxDrawdownPct']:.2f}% (${ps['maxDrawdownDollars']:,.0f})")
print(f"Peak Capital:     ${ps['peakCapital']:,.0f}")
```

**Monte Carlo with position sizing:**

When position sizing is passed to Monte Carlo, each shuffled/resampled scenario re-applies sizing sequentially (position sizing is path-dependent — trade order changes portfolio value, which changes subsequent position sizes).

```bash
curl -s -X POST http://localhost:8080/udgaard/api/monte-carlo/simulate \
  -H "Content-Type: application/json" \
  -d "{
    \"backtestId\": \"$BACKTEST_ID\",
    \"technique\": \"TRADE_SHUFFLING\",
    \"iterations\": 10000,
    \"positionSizing\": {
      \"startingCapital\": 100000,
      \"riskPercentage\": 1.5,
      \"nAtr\": 2.0
    }
  }" > /tmp/monte_carlo_sized.json
```

**Note:** Without `positionSizing`, behavior is unchanged — equity curve compounds per-trade percentage returns as before.

## Strategy Optimization Goals

**CRITICAL:** Strategies should be optimized for BOTH high edge AND low drawdown, not just total returns.

### Why Both Matter

1. **High Edge (Per Trade Profitability)**
   - Measures average profit per trade
   - Ensures each trade has positive expectancy
   - Target: 2%+ edge per trade
   - Higher edge = more consistent profits

2. **Low Drawdown (Risk Management)**
   - Measures maximum peak-to-trough decline
   - Protects capital during losing streaks
   - Target: < 20% maximum drawdown
   - Lower drawdown = smoother equity curve, less stress

3. **Return/Drawdown Ratio (THE KEY METRIC)**
   - Combines both objectives into single metric
   - Formula: Total Return % / Max Drawdown %
   - Target: > 5.0 (excellent risk-adjusted performance)
   - Example: 100% return with 15% drawdown = 6.67 ratio (excellent)
   - Example: 200% return with 50% drawdown = 4.0 ratio (poor)

### The Tradeoff

- A strategy with 300% returns but 60% drawdown is WORSE than:
  - A strategy with 150% returns but 20% drawdown
  - First: Return/DD = 5.0
  - Second: Return/DD = 7.5 (BETTER)

**Always prioritize risk-adjusted performance over raw returns.**

## Strategy Comparison Workflow

When comparing strategies, follow this pattern:

### 1. Run Both Backtests

**Example: Comparing Predefined Strategies**

```bash
# Strategy A
curl -s -X POST http://localhost:8080/udgaard/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "stockSymbols": ["TQQQ"],
    "entryStrategy": {"type": "predefined", "name": "StrategyA"},
    "exitStrategy": {"type": "predefined", "name": "StrategyA"},
    "startDate": "2020-01-01",
    "endDate": "2025-11-13",
    "cooldownDays": 0,
    "useUnderlyingAssets": true,
    "customUnderlyingMap": {"TQQQ": "QQQ"}
  }' > /tmp/strategy_a.json

# Strategy B
curl -s -X POST http://localhost:8080/udgaard/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "stockSymbols": ["TQQQ"],
    "entryStrategy": {"type": "predefined", "name": "StrategyB"},
    "exitStrategy": {"type": "predefined", "name": "StrategyB"},
    "startDate": "2020-01-01",
    "endDate": "2025-11-13",
    "cooldownDays": 10,
    "useUnderlyingAssets": true,
    "customUnderlyingMap": {"TQQQ": "QQQ"}
  }' > /tmp/strategy_b.json
```

### 2. Calculate Comprehensive Metrics

Use Python to calculate equity curve metrics from the pre-computed `equityCurveData`:

```python
import json
from datetime import datetime

def calculate_equity_metrics(result, starting_capital=100000):
    """Calculate drawdown, returns, and CAGR from equityCurveData"""
    equity_data = result['equityCurveData']
    if not equity_data:
        return {'final_balance': starting_capital, 'max_drawdown_pct': 0, 'peak': starting_capital}

    balance = starting_capital
    peak = balance
    max_dd_pct = 0

    for point in equity_data:
        profit_pct = point['profitPercentage']
        balance += balance * (profit_pct / 100)

        if balance > peak:
            peak = balance

        current_dd_pct = ((peak - balance) / peak) * 100
        if current_dd_pct > max_dd_pct:
            max_dd_pct = current_dd_pct

    return {
        'final_balance': balance,
        'max_drawdown_pct': max_dd_pct,
        'peak': peak
    }

def calculate_cagr(final_balance, starting_capital, years):
    """Calculate Compound Annual Growth Rate"""
    return (((final_balance / starting_capital) ** (1 / years)) - 1) * 100
```

### 3. Compare and Present Results

**Use pre-computed fields directly from the response:**

```python
import json

# Load both results
with open('/tmp/strategy_a.json', 'r') as f:
    strategy_a = json.load(f)

with open('/tmp/strategy_b.json', 'r') as f:
    strategy_b = json.load(f)

# Calculate equity metrics from equityCurveData
a_metrics = calculate_equity_metrics(strategy_a)
b_metrics = calculate_equity_metrics(strategy_b)

# Calculate CAGR
years = 5.87  # 2020-01-01 to 2025-11-13
a_cagr = calculate_cagr(a_metrics['final_balance'], 100000, years)
b_cagr = calculate_cagr(b_metrics['final_balance'], 100000, years)

# Print comparison using pre-computed scalar metrics
print("=" * 90)
print("STRATEGY COMPARISON")
print("=" * 90)

print(f"{'Metric':<35} {'Strategy A':<22} {'Strategy B':<22} {'Difference'}")
print("-" * 90)
print(f"{'Total Trades':<35} {strategy_a['totalTrades']:<22} {strategy_b['totalTrades']:<22} {strategy_b['totalTrades'] - strategy_a['totalTrades']:+d}")
print(f"{'Win Rate':<35} {strategy_a['winRate']*100:>21.2f}% {strategy_b['winRate']*100:>21.2f}% {(strategy_b['winRate'] - strategy_a['winRate'])*100:+.2f}%")
print(f"{'Edge (per trade)':<35} {strategy_a['edge']:>21.2f}% {strategy_b['edge']:>21.2f}% {strategy_b['edge'] - strategy_a['edge']:+.2f}%")

print()
print("RETURNS")
print("-" * 90)
a_return = ((a_metrics['final_balance'] - 100000) / 100000) * 100
b_return = ((b_metrics['final_balance'] - 100000) / 100000) * 100
print(f"{'Final Balance':<35} ${a_metrics['final_balance']:>20,.2f} ${b_metrics['final_balance']:>20,.2f} ${b_metrics['final_balance'] - a_metrics['final_balance']:+,.2f}")
print(f"{'Total Return':<35} {a_return:>21.2f}% {b_return:>21.2f}% {b_return - a_return:+.2f}%")
print(f"{'CAGR':<35} {a_cagr:>21.2f}% {b_cagr:>21.2f}% {b_cagr - a_cagr:+.2f}%")

print()
print("RISK METRICS")
print("-" * 90)
print(f"{'Max Drawdown (%)':<35} {a_metrics['max_drawdown_pct']:>21.2f}% {b_metrics['max_drawdown_pct']:>21.2f}% {b_metrics['max_drawdown_pct'] - a_metrics['max_drawdown_pct']:+.2f}%")

a_ratio = a_return / a_metrics['max_drawdown_pct'] if a_metrics['max_drawdown_pct'] > 0 else 0
b_ratio = b_return / b_metrics['max_drawdown_pct'] if b_metrics['max_drawdown_pct'] > 0 else 0
print(f"{'Return/Drawdown Ratio':<35} {a_ratio:>21.2f} {b_ratio:>21.2f} {b_ratio - a_ratio:+.2f}")

# Year-by-year comparison using timeBasedStats
print()
print("YEAR-BY-YEAR COMPARISON")
print("-" * 90)
a_yearly = strategy_a.get('timeBasedStats', {}).get('byYear', {})
b_yearly = strategy_b.get('timeBasedStats', {}).get('byYear', {})
all_years = sorted(set(list(a_yearly.keys()) + list(b_yearly.keys())))
for year in all_years:
    a_stats = a_yearly.get(year, {})
    b_stats = b_yearly.get(year, {})
    a_avg = a_stats.get('avgProfit', 0) * a_stats.get('trades', 0)
    b_avg = b_stats.get('avgProfit', 0) * b_stats.get('trades', 0)
    a_wr = a_stats.get('winRate', 0) * 100
    b_wr = b_stats.get('winRate', 0) * 100
    print(f"{year}  A: {a_stats.get('trades', 0):>3} trades, {a_wr:>5.1f}% WR  |  B: {b_stats.get('trades', 0):>3} trades, {b_wr:>5.1f}% WR")
```

### 4. Provide Recommendations

Based on analysis, recommend:
- **Which strategy performed better (using Return/Drawdown ratio as primary metric)**
- **Why it performed better:**
  - Higher edge per trade (consistent profitability)
  - Lower drawdown (better risk management)
  - Better Return/Drawdown ratio (optimal balance)
  - Fewer trades but higher quality
  - More consistent year-over-year performance
- **Optimal parameters** (cooldown, position limits, etc.)
- **Which market conditions** favor each strategy
- **Risk-adjusted performance** - ALWAYS prioritize this over raw returns

**REMEMBER:** A strategy that makes 150% with 15% drawdown (10.0 ratio) is SUPERIOR to one that makes 300% with 50% drawdown (6.0 ratio). Emphasize risk-adjusted metrics in all recommendations.

## Backtest Diagnostic Metrics

Backtest reports include comprehensive diagnostic metrics to help analyze strategy performance and identify areas for improvement. These metrics are completely strategy-agnostic and work with any entry/exit strategy combination.

### Available Diagnostic Metrics

**1. Time-Based Performance (`timeBasedStats`)**

Breakdown of strategy performance by time periods:
- **By Year**: Annual performance with trades, win rate, avg profit, holding days, exit reasons
- **By Quarter**: Quarterly breakdown (e.g., "2025-Q1")
- **By Month**: Monthly breakdown (e.g., "2025-01")

**Use Case:** Identify which years/quarters/months underperformed and correlate with market conditions.

```python
# Example: Analyze year-by-year performance
time_stats = result['timeBasedStats']
for year, stats in time_stats['byYear'].items():
    print(f"{year}: {stats['trades']} trades, {stats['winRate']*100:.1f}% win rate, {stats['avgProfit']:.2f}% avg profit")
```

**2. ATR Drawdown Statistics (`atrDrawdownStats`)**

Shows how much adverse movement (in ATR units) winning trades endure before becoming profitable:
- **Percentiles**: 25th, 50th, 75th, 90th, 95th, 99th
- **Mean/Median**: Average and median drawdown
- **Min/Max**: Best and worst case
- **Distribution**: Buckets with cumulative percentages

**Use Case:** Understand pain tolerance required. If 75% of winning trades require enduring >2 ATR drawdown, you know most winners will test your discipline.

```python
# Example: Analyze ATR drawdown distribution
atr_stats = result['atrDrawdownStats']
print(f"Median drawdown: {atr_stats['medianDrawdown']:.2f} ATR")
print(f"75th percentile: {atr_stats['percentile75']:.2f} ATR")
print(f"95th percentile: {atr_stats['percentile95']:.2f} ATR")

# Distribution shows cumulative percentages
for range_name, bucket in atr_stats['distribution'].items():
    print(f"{bucket['range']}: {bucket['count']} trades ({bucket['cumulativePercentage']:.1f}% cumulative)")
```

**3. Market Condition Stats (`marketConditionStats`)**

Pre-computed market condition analysis:
- `scatterPoints` - Array of `{breadth, profitPercentage, isWinner, spyInUptrend}` for each trade
- `uptrendWinRate` / `downtrendWinRate` - Win rates during SPY uptrend vs downtrend
- `uptrendCount` / `downtrendCount` - Trade counts per regime

**Use Case:** Identify if poor performance correlates with specific market conditions.

```python
# Example: Analyze market regime performance
mc_stats = result.get('marketConditionStats')
if mc_stats:
    print(f"SPY Uptrend: {mc_stats['uptrendWinRate']:.1f}% win rate ({mc_stats['uptrendCount']} trades)")
    print(f"SPY Downtrend: {mc_stats['downtrendWinRate']:.1f}% win rate ({mc_stats['downtrendCount']} trades)")

    # Analyze breadth correlation from scatter points
    points = mc_stats['scatterPoints']
    low_breadth = [p for p in points if p['breadth'] < 50]
    high_breadth = [p for p in points if p['breadth'] >= 50]
    if low_breadth:
        low_wr = sum(1 for p in low_breadth if p['isWinner']) / len(low_breadth) * 100
        print(f"Low breadth (<50%): {low_wr:.1f}% win rate ({len(low_breadth)} trades)")
    if high_breadth:
        high_wr = sum(1 for p in high_breadth if p['isWinner']) / len(high_breadth) * 100
        print(f"High breadth (>=50%): {high_wr:.1f}% win rate ({len(high_breadth)} trades)")
```

**4. Excursion Summary (`excursionSummary` + `excursionPoints`)**

Pre-computed MFE/MAE statistics:
- `excursionSummary` - Averages for all trades, winners, and losers:
  - `totalTrades`, `avgMFE`, `avgMAE`, `avgMFEATR`, `avgMAEATR`
  - `profitReachRate` - % of trades that reached positive territory
  - `avgMFEEfficiency` - How much of MFE was captured (winners only)
  - `winnerAvgMFE`, `winnerAvgMAE`, `winnerAvgFinalProfit`
  - `loserAvgMFE`, `loserAvgMAE`, `loserAvgFinalLoss`, `loserMissedWinRate`
- `excursionPoints` - Per-trade `{mfe, mae, mfeATR, maeATR, mfeReached, profitPercentage, isWinner}` (max 5000)

**Use Case:** Identify if exits are too early or stops are too tight.

```python
# Example: Analyze exit efficiency
exc = result.get('excursionSummary')
if exc:
    print(f"MFE Efficiency: {exc['avgMFEEfficiency']:.1f}% of max profit captured")
    print(f"Winners avg MFE: {exc['winnerAvgMFE']:.2f}%, avg final: {exc['winnerAvgFinalProfit']:.2f}%")
    if exc['loserMissedWinRate'] > 50:
        print(f"WARNING: {exc['loserMissedWinRate']:.1f}% of losers reached profit before losing - exits may be too slow")
```

**5. Exit Reason Analysis (`exitReasonAnalysis`)**

Statistics per exit reason:
- **By Reason**: Count, avg profit, avg holding days, win rate per exit reason
- **By Year and Reason**: Historical breakdown

**Use Case:** Identify which exit conditions are most profitable and which are problematic.

```python
# Example: Compare exit reason profitability
exit_analysis = result['exitReasonAnalysis']
for reason, stats in exit_analysis['byReason'].items():
    print(f"{reason}: {stats['count']} exits, {stats['avgProfit']:.2f}% avg, {stats['winRate']*100:.1f}% win rate")
```

**6. Sector Performance (`sectorPerformance`)**

Performance breakdown by sector:
- Trades, win rate, avg profit, avg holding days per sector

**Use Case:** Identify which sectors work best with the strategy.

```python
# Example: Find best performing sectors
sector_perf = result['sectorPerformance']
sorted_sectors = sorted(sector_perf, key=lambda x: x['avgProfit'], reverse=True)
for sector in sorted_sectors[:5]:
    print(f"{sector['sector']}: {sector['avgProfit']:.2f}% avg profit, {sector['winRate']*100:.1f}% win rate")
```

**7. Edge Consistency Score (`edgeConsistencyScore`)**

Measures how consistent a strategy's edge is across yearly periods (0-100 score):
- **score**: Composite score (0-100)
- **profitablePeriodsScore**: % of years with positive edge (weight: 40%)
- **stabilityScore**: % of years with edge >= 1.5% (minimum tradeable threshold). Measures what fraction of years produce an edge large enough to overcome transaction costs and slippage (weight: 40%)
- **downsideScore**: How bad the worst year is -- 100 if worst year is positive, scales down linearly to 0 at -10% edge (weight: 20%)
- **yearsAnalyzed**: Number of years with trades included in the calculation
- **yearlyEdges**: Map of year -> edge value for each year
- **interpretation**: "Excellent" (80+), "Good" (60-79), "Moderate" (40-59), "Poor" (20-39), "Very Poor" (<20)

Returns null when fewer than 2 years have trades.

**Use Case:** Quickly assess whether a strategy's edge is reliable or driven by one or two outlier years.

```python
# Example: Check edge consistency
ecs = result.get('edgeConsistencyScore')
if ecs:
    print(f"Edge Consistency: {ecs['score']:.0f}/100 ({ecs['interpretation']})")
    for year, edge in sorted(ecs['yearlyEdges'].items()):
        print(f"  {year}: {edge:.2f}%")
```

**8. Market Condition Averages (`marketConditionAverages`)**

Average market conditions across all trades:
- `avgMarketBreadth`: Average bull percentage
- `spyUptrendPercent`: % of trades entered during SPY uptrend

**Use Case:** Understand typical market environment for the strategy.

### Using Diagnostic Metrics for Strategy Improvement

**Common Analysis Patterns:**

**Performance Degradation Analysis**
```python
# Why did strategy underperform in a specific year?
time_stats = result['timeBasedStats']
stats_2025 = time_stats['byYear'].get('2025', {})
print(f"2025: {stats_2025.get('trades', 0)} trades, {stats_2025.get('winRate', 0)*100:.1f}% win rate")

# Compare with overall averages
overall_avg_breadth = result.get('marketConditionAverages', {}).get('avgMarketBreadth', 0)
print(f"Overall avg market breadth: {overall_avg_breadth:.1f}%")
```

**Stop Loss Optimization**
```python
# What % of winners required enduring >2 ATR drawdown?
atr_stats = result['atrDrawdownStats']

# Find cumulative % that stayed under 2.0 ATR
below_2atr_cumulative = 0
for range_name, bucket in sorted(atr_stats['distribution'].items()):
    range_max = float(bucket['range'].split('-')[1])
    if range_max <= 2.0:
        below_2atr_cumulative = bucket['cumulativePercentage']

pct_above_2atr = 100 - below_2atr_cumulative
print(f"{pct_above_2atr:.1f}% of winners required enduring >2 ATR drawdown")
# If this is high (>50%), a 2 ATR stop loss would kill most winners
```

**Exit Strategy Analysis**
```python
# Which exit reasons are problematic?
exit_analysis = result['exitReasonAnalysis']
for reason, stats in exit_analysis['byReason'].items():
    if stats['winRate'] < 0.3:  # Less than 30% win rate
        print(f"WARNING: {reason}: Only {stats['winRate']*100:.1f}% win rate with {stats['avgProfit']:.2f}% avg")
```

**Market Regime Filtering**
```python
# Should we filter out low breadth entries? Use pre-computed marketConditionStats
mc_stats = result.get('marketConditionStats')
if mc_stats:
    points = mc_stats['scatterPoints']
    low_breadth = [p for p in points if p['breadth'] < 50]
    high_breadth = [p for p in points if p['breadth'] >= 50]

    if low_breadth:
        low_wr = sum(1 for p in low_breadth if p['isWinner']) / len(low_breadth) * 100
        print(f"Low breadth (<50%): {low_wr:.1f}% win rate")
    if high_breadth:
        high_wr = sum(1 for p in high_breadth if p['isWinner']) / len(high_breadth) * 100
        print(f"High breadth (>=50%): {high_wr:.1f}% win rate")
```

### Fetching Individual Trades On-Demand

When you need individual trade details (e.g., for drill-down analysis), use the on-demand endpoint:

```bash
# Fetch trades for a specific date range
curl -s "http://localhost:8080/udgaard/api/backtest/${BACKTEST_ID}/trades?startDate=2024-01-15&endDate=2024-01-19"

# Fetch trades for a single date
curl -s "http://localhost:8080/udgaard/api/backtest/${BACKTEST_ID}/trades?startDate=2024-01-15"
```

The `backtestId` comes from the backtest response. Trade data is cached server-side for 1 hour.

## Analysis Guidelines

### What to Look For

**PRIMARY OBJECTIVE:** Optimize for BOTH high edge AND low drawdown. Never optimize for returns alone.

1. **Risk-Adjusted Returns (MOST IMPORTANT)**
   - **Don't just compare total returns** - this is misleading
   - **Return/Drawdown ratio is THE key metric** (target: > 5.0)
   - Lower drawdown with similar returns = BETTER strategy
   - High returns with high drawdown = POOR strategy (psychological torture, capital at risk)
   - Example comparison:
     - Strategy A: 200% return, 40% drawdown -> Ratio: 5.0
     - Strategy B: 120% return, 15% drawdown -> Ratio: 8.0 <- **WINNER**

2. **Trade Quality (Edge Per Trade)**
   - **Target: 2%+ edge per trade minimum**
   - Fewer trades with higher edge is usually better
   - High win rate (65-80%) indicates good strategy
   - Compare average win vs average loss (win should be 1.5x+ loss)
   - Edge < 1% will be destroyed by transaction costs and slippage

3. **Consistency**
   - Positive returns in most years
   - Similar performance across different market conditions
   - No single year dominating all returns
   - Edge Consistency Score > 60 (Good) indicates reliable strategy

4. **Exit Reason Analysis**
   - Which exit conditions trigger most often?
   - Are profitable exits different from losing ones?
   - Too many stop losses = entries might be poor

5. **Cooldown Impact**
   - Compare same strategy with/without cooldown
   - Look for improved win rate and edge
   - Expect fewer trades but better quality

### Red Flags

**Risk Warnings (Drawdown):**
- Max drawdown > 25% (too risky - psychologically unsustainable)
- Max drawdown > 30% (unacceptable - abandon strategy)
- Multiple drawdowns > 20% (choppy, unreliable)

**Edge Warnings:**
- Win rate < 55% (poor edge)
- Edge < 1% per trade (transaction costs will kill it)
- Average win < 1.3x average loss (poor risk/reward)

**Other Warnings:**
- Returns concentrated in one or two years (luck, not skill)
- Too many trades (overtrading, death by 1000 cuts)
- **Return/Drawdown ratio < 3.0 (poor risk-adjusted performance)**
- **Edge Consistency Score < 40 (Moderate/Poor -- unreliable strategy)**

### Green Flags

**Excellent Risk-Adjusted Performance:**
- **Return/Drawdown ratio > 5.0** (target metric)
- **Return/Drawdown ratio > 8.0** (exceptional)
- Max drawdown < 20% (sustainable, tradeable)
- Max drawdown < 15% (excellent risk management)

**Strong Edge:**
- Edge 2%+ per trade (consistent profitability)
- Edge 3%+ per trade (exceptional)
- Win rate 65-80% (solid win rate)
- Average win 1.5x+ average loss (good risk/reward)

**Consistency:**
- Positive returns in 75%+ of years
- Consistent performance across market cycles
- Similar win rate across different years
- Edge Consistency Score > 80 (Excellent)
- Edge Consistency Score > 60 (Good)

## Saving Results and Reports

Always save backtest results and analysis:

```bash
# Save raw results
curl -s -X POST ... > /tmp/backtest_results.json

# Generate comprehensive report
python3 analysis.py > BACKTEST_REPORT_$(date +%Y%m%d).md
```

Report should include:
- Executive summary with recommendation
- Performance metrics table
- Risk metrics comparison
- Year-by-year breakdown
- ATR drawdown analysis (percentiles and distribution)
- Exit reason breakdown with win rates
- Market condition analysis
- Sector performance comparison
- Key insights and why one strategy won
- Strategy differences explanation
- Technical details and configuration

See `TQQQ_BACKTEST_COMPARISON_REPORT.md` for example format.

## Monte Carlo Simulation

After running a backtest, you should ALWAYS validate the strategy edge using Monte Carlo simulation. This confirms whether your results represent genuine edge or just a lucky trade sequence.

### Why Monte Carlo Matters

**Problem:** A backtest shows 193% return, but is this:
- Real edge that will persist?
- Or just lucky trade ordering?

**Solution:** Monte Carlo simulation runs thousands of scenarios to test if edge holds regardless of trade sequence.

**Critical Insight:** With trade shuffling, total return stays the same across all scenarios (same trades, different order), BUT drawdown varies significantly (21-54%). This reveals:
- **Edge is real** if 100% of scenarios are profitable
- **Path risk is real** - same profitable trades can have very different psychological journeys
- **Drawdown is sequence-dependent** - you need discipline to survive worst-case paths

### Running Monte Carlo Simulation

Monte Carlo uses the `backtestId` from the backtest response -- no need to send trade data.

**Step 1: Run Backtest**

```bash
curl -s -X POST http://localhost:8080/udgaard/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "stockSymbols": ["TQQQ"],
    "entryStrategy": {"type": "predefined", "name": "PlanEtf"},
    "exitStrategy": {"type": "predefined", "name": "PlanEtf"},
    "startDate": "2021-01-01",
    "endDate": "2025-11-19",
    "useUnderlyingAssets": true,
    "customUnderlyingMap": {"TQQQ": "QQQ"}
  }' > /tmp/tqqq_backtest.json
```

**Step 2: Run Monte Carlo using backtestId**

```bash
# Extract backtestId and run Monte Carlo
python3 << 'EOF'
import json

with open('/tmp/tqqq_backtest.json', 'r') as f:
    backtest = json.load(f)

backtest_id = backtest['backtestId']
print(f"Backtest ID: {backtest_id}")

# Save backtestId for curl
with open('/tmp/backtest_id.txt', 'w') as f:
    f.write(backtest_id)
EOF

BACKTEST_ID=$(cat /tmp/backtest_id.txt)

curl -s -X POST http://localhost:8080/udgaard/api/monte-carlo/simulate \
  -H "Content-Type: application/json" \
  -d "{
    \"backtestId\": \"$BACKTEST_ID\",
    \"technique\": \"TRADE_SHUFFLING\",
    \"iterations\": 10000,
    \"includeAllEquityCurves\": false
  }" > /tmp/monte_carlo_result.json
```

**NOTE:** The Monte Carlo endpoint retrieves trades from the server-side cache using `backtestId`. This avoids sending hundreds of MB of trade data over HTTP. The cache expires after 1 hour, so run Monte Carlo shortly after the backtest.

### Monte Carlo Techniques

**1. Trade Shuffling (Recommended)**
- Randomly reorders existing trades
- Tests if edge holds regardless of sequence
- Total return stays constant (same trades)
- Drawdown varies (sequence matters!)
- **Best for:** Validating edge consistency

**2. Bootstrap Resampling**
- Samples trades with replacement
- Creates scenarios with different trade combinations
- Both return AND drawdown vary
- **Best for:** Understanding distribution of outcomes

### Analyzing Monte Carlo Results

```python
import json

# Read results
with open('/tmp/monte_carlo_result.json', 'r') as f:
    result = json.load(f)

stats = result['statistics']

print("=" * 80)
print("MONTE CARLO VALIDATION")
print("=" * 80)
print()

# 1. EDGE VALIDATION
print("EDGE VALIDATION")
print("-" * 80)
print(f"Probability of Profit:    {stats['probabilityOfProfit']:.2f}%")
print(f"Mean Return:              {stats['meanReturnPercentage']:.2f}%")
print(f"Mean Edge per Trade:      {stats['meanEdge']:.2f}%")
print()

# Interpretation
if stats['probabilityOfProfit'] >= 95:
    print("VALIDATED EDGE: 95%+ scenarios profitable - edge is real!")
elif stats['probabilityOfProfit'] >= 70:
    print("MODERATE EDGE: 70-95% scenarios profitable - some risk")
else:
    print("WEAK EDGE: <70% scenarios profitable - likely curve-fitted")
print()

# 2. RETURN DISTRIBUTION
print("RETURN DISTRIBUTION")
print("-" * 80)
print(f"  5th Percentile (Worst):  {stats['returnPercentiles']['p5']:.2f}%")
print(f" 25th Percentile:          {stats['returnPercentiles']['p25']:.2f}%")
print(f" 50th Percentile (Median): {stats['returnPercentiles']['p50']:.2f}%")
print(f" 75th Percentile:          {stats['returnPercentiles']['p75']:.2f}%")
print(f" 95th Percentile (Best):   {stats['returnPercentiles']['p95']:.2f}%")
print()

# 3. DRAWDOWN RISK (CRITICAL!)
print("DRAWDOWN RISK ANALYSIS")
print("-" * 80)
print(f"Mean Max Drawdown:        {stats['meanMaxDrawdown']:.2f}%")
print(f"Median Max Drawdown:      {stats['medianMaxDrawdown']:.2f}%")
print(f"Best Case (5th):          {stats['drawdownPercentiles']['p5']:.2f}%")
print(f"Worst Case (95th):        {stats['drawdownPercentiles']['p95']:.2f}%")
print(f"95% CI:                   {stats['drawdownConfidenceInterval95']['lower']:.2f}% to {stats['drawdownConfidenceInterval95']['upper']:.2f}%")
print()

# Critical insight
worst_dd = stats['drawdownPercentiles']['p95']
best_dd = stats['drawdownPercentiles']['p5']
dd_spread = worst_dd - best_dd

print(f"SEQUENCE RISK: Drawdown varies by {dd_spread:.2f}% depending on trade order")
print(f"  - Worst case: {worst_dd:.2f}% drawdown")
print(f"  - Best case: {best_dd:.2f}% drawdown")
print()

# 4. EDGE STABILITY
print("EDGE CONSISTENCY")
print("-" * 80)
print(f"Mean Edge:                {stats['meanEdge']:.2f}%")
print(f"Edge Range:               {stats['edgePercentiles']['p5']:.2f}% to {stats['edgePercentiles']['p95']:.2f}%")
print(f"Mean Win Rate:            {stats['meanWinRate'] * 100:.1f}%")
print()

# 5. COMPARISON WITH ORIGINAL
print("ORIGINAL vs SIMULATION")
print("-" * 80)
print(f"                    Original    Mean (MC)    Within Range?")
in_range_ret = 'Y' if stats['returnPercentiles']['p25'] <= result['originalReturnPercentage'] <= stats['returnPercentiles']['p75'] else 'N'
in_range_edge = 'Y' if stats['edgePercentiles']['p25'] <= result['originalEdge'] <= stats['edgePercentiles']['p75'] else 'N'
in_range_wr = 'Y' if stats['winRatePercentiles']['p25'] <= result['originalWinRate'] <= stats['winRatePercentiles']['p75'] else 'N'
print(f"Return:            {result['originalReturnPercentage']:>8.2f}%   {stats['meanReturnPercentage']:>8.2f}%    {in_range_ret}")
print(f"Edge:              {result['originalEdge']:>8.2f}%   {stats['meanEdge']:>8.2f}%    {in_range_edge}")
print(f"Win Rate:          {result['originalWinRate']*100:>8.1f}%   {stats['meanWinRate']*100:>8.1f}%    {in_range_wr}")
print()
print("=" * 80)
```

### Key Monte Carlo Metrics

**1. Probability of Profit** (Most Important)
- Target: 95%+ (validated edge)
- 70-95%: Moderate edge
- <70%: Weak/luck-based

**2. Return Percentiles**
- Shows distribution of possible outcomes
- 5th percentile = worst realistic case
- 95th percentile = best realistic case
- Use for risk assessment

**3. Drawdown Distribution** (Critical for Risk Management!)
- **Same winning trades can have very different drawdown paths**
- Worst case (95th percentile) tells you max pain to expect
- If worst-case drawdown > 30%, reconsider strategy
- Example: 100% probability of profit BUT 54% worst-case drawdown
  - Strategy WILL be profitable eventually
  - BUT are you prepared for 54% decline psychologically?

**4. Edge Consistency**
- Should be stable across scenarios
- Large variation = strategy is fragile
- Target: edge variance < 2%

**5. Confidence Intervals**
- 95% CI tells you realistic range
- Narrow CI = predictable outcomes
- Wide CI = high uncertainty

### Critical Insights from Monte Carlo

**The Path-Dependent Risk Revelation:**

When you run Trade Shuffling Monte Carlo, you discover:

```
Total Return:    193.43% in ALL 10,000 scenarios (constant)
Max Drawdown:    21.25% to 54.30% across scenarios (varies!)
```

**What this means:**
1. **Edge is REAL** - 100% of scenarios profitable
2. **You WILL make money** - if you can stick with it
3. **Journey varies wildly** - same destination, different pain levels
4. **Psychological test** - can you handle 54% drawdown?

**The Discipline Requirement:**
- You know the strategy works (100% probability)
- BUT you don't know which path you'll get (21% or 54% drawdown?)
- You must commit to riding out WORST CASE to reach the destination
- This is why drawdown matters as much as returns!

### Monte Carlo Best Practices

**1. Always Run After Backtests**
- Validate EVERY strategy with Monte Carlo
- Never trust a single backtest sequence
- Look for 95%+ probability of profit

**2. Focus on Drawdown Distribution**
- Mean/median drawdown = expected case
- 95th percentile = worst realistic case
- Plan for worst case, hope for best case
- If 95th percentile drawdown > 30%, reject strategy

**3. Check Edge Stability**
- Edge should be consistent across scenarios
- High variance = fragile strategy
- Stable edge = robust strategy

**4. Interpret Probability of Profit Correctly**
- 100% = edge is validated (not luck)
- But doesn't tell you about the journey (drawdowns)
- Need BOTH high probability AND acceptable worst-case drawdown

**5. Use for Position Sizing**
- Worst-case drawdown informs position size
- If 95th percentile DD = 54%, use 50% of capital max
- Apply Kelly Criterion with Monte Carlo statistics
- Example: Kelly = (p * b - q) / b where p=winRate, b=avgWin/avgLoss

**6. Document Results**
- Save Monte Carlo results alongside backtest
- Include probability of profit
- Document drawdown range (best/worst case)
- Use for risk disclosure and position sizing

### Red Flags in Monte Carlo Results

**Edge Warnings:**
- Probability of profit < 70% (weak edge, likely luck)
- Edge varies by > 3% across scenarios (unstable)
- Original result outside 25th-75th percentile range (outlier)

**Risk Warnings:**
- 95th percentile drawdown > 30% (psychologically unsustainable)
- 95th percentile drawdown > 50% (unacceptable - reject strategy)
- Drawdown spread > 40% (high sequence risk)

**Statistical Warnings:**
- Wide confidence intervals (unpredictable)
- Original result in top 5% of scenarios (got lucky)
- Mean return << original return (likely mean reversion ahead)

### Green Flags in Monte Carlo Results

**Validated Edge:**
- Probability of profit > 95% (real edge confirmed)
- Probability of profit = 100% (exceptional confidence)
- Edge stable across scenarios (robust)
- Original result within 25th-75th percentile (typical)

**Acceptable Risk:**
- 95th percentile drawdown < 25% (manageable)
- 95th percentile drawdown < 20% (excellent)
- Narrow drawdown distribution (predictable risk)

**Statistical Confidence:**
- Tight confidence intervals (predictable)
- Consistent metrics across techniques
- Large sample size (30+ trades)

## Common Use Cases

### 1. Compare Two Strategies

Run both strategies on same stock/period, calculate all metrics, present side-by-side comparison with clear winner and reasoning.

### 2. Test Cooldown Impact

Run same strategy twice (cooldown=0 vs cooldown=10), show how cooldown improves trade quality and returns.

### 3. Leveraged vs Unleveraged

Compare TQQQ vs QQQ with same strategy, show leverage amplification and importance of risk management.

### 4. Position Limit Optimization

Test same strategy with different maxPositions values (1, 5, 10, 15, unlimited), find optimal.

### 5. Year-by-Year Analysis

Break down performance by calendar year to understand strategy behavior in different market conditions (bull, bear, choppy).

### 6. Diagnose Underperformance

Use diagnostic metrics to identify why a strategy underperforms:
- Check time-based stats to isolate problematic periods
- Analyze market condition stats for regime-dependent performance
- Review ATR drawdown distribution to optimize stop losses
- Examine exit reason analysis to identify ineffective exits
- Compare sector performance to find weak sectors
- Check excursion summary for exit timing issues

## Best Practices

1. **Optimize for BOTH edge AND drawdown** - never optimize for returns alone
2. **Use Return/Drawdown ratio as primary metric** (target > 5.0)
3. **Save results to files** before analysis
4. **Use pre-computed fields** from the response (don't iterate raw trades)
5. **Leverage diagnostic metrics** to understand performance:
   - Use time-based stats to identify underperforming periods
   - Analyze ATR drawdown distribution for stop loss optimization
   - Check market condition stats for regime performance
   - Review exit reason analysis to identify problematic exits
   - Examine excursion summary to optimize exit timing
6. **Compare risk-adjusted performance** (Return/Drawdown ratio is king)
7. **Analyze year-by-year** to ensure consistency
8. **Check exit reasons** to understand strategy behavior
9. **Consider cooldown periods** especially for leveraged ETFs
10. **Use underlying assets** for cleaner signals on leveraged instruments
11. **Validate with Monte Carlo** using backtestId (not trades array)
12. **Present clear recommendations** based on risk-adjusted metrics
13. **Generate written reports** for future reference
14. **Reject strategies with drawdown > 25%** regardless of returns
15. **Reject strategies with edge < 2%** regardless of win rate

## MCP Integration

Use the `stock-backtesting` MCP server tools for all discovery and informational queries:

| MCP Tool | Use For |
|----------|---------|
| `getAvailableStrategies` | List entry/exit strategy names before running a backtest |
| `getAvailableRankers` | List rankers for position-limited backtests |
| `getAvailableSymbols` | Discover stocks with data (symbol, sector, assetType, quoteCount, lastQuoteDate) |
| `getAvailableConditions` | Get condition metadata (types, parameters, defaults) for custom strategies |
| `getStrategyDetails` | Understand what a specific strategy does before using it |
| `getSystemStatus` | Verify system is ready before running a backtest |
| `explainBacktestMetrics` | Help interpret backtest results for the user |

Never hardcode strategy lists - always use MCP tools to fetch them dynamically.

**What still requires REST API (curl):**
- `POST /api/backtest` - Running backtests (returns `BacktestResponseDto` with `backtestId`)
- `POST /api/monte-carlo/simulate` - Running Monte Carlo simulations (accepts `backtestId`)
- `GET /api/backtest/{backtestId}/trades?startDate=X&endDate=Y` - Fetching individual trades on-demand
