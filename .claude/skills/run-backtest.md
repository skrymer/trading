# Run Trading Backtest

This skill helps Claude run comprehensive backtests for trading strategies using the Udgaard API and provides detailed comparative analysis.

## Overview

When the user asks to run a backtest, Claude should:

1. **Discover available resources** using MCP tools
2. **Run backtests** with appropriate parameters
3. **Calculate comprehensive metrics** (returns, drawdowns, year-by-year)
4. **Compare strategies** side-by-side
5. **Present actionable insights** with clear recommendations

## Getting Available Resources

Use MCP tools to discover what's available:

```bash
# Get available strategies (entry and exit)
curl -s http://localhost:8080/api/strategies | python3 -m json.tool

# Get available rankers (for position-limited backtests)
curl -s http://localhost:8080/api/rankers | python3 -m json.tool

# Get available stock symbols
curl -s http://localhost:8080/api/stocks | python3 -m json.tool

# Get available conditions (for custom strategies)
curl -s http://localhost:8080/api/conditions | python3 -m json.tool
```

## Running Backtests

### Basic Backtest (POST Method - Recommended)

```bash
curl -s -X POST http://localhost:8080/api/backtest \
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
```

### Advanced Features

#### 1. Cooldown Period (NEW)

**Global cooldown** blocks ALL entries for X trading days after ANY exit:

```json
{
  "cooldownDays": 10  // Wait 10 trading days after exit before allowing new entries
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

#### 2. Underlying Asset Mapping (NEW)

**Use underlying assets for signals while trading leveraged ETFs:**

```json
{
  "stockSymbols": ["TQQQ"],
  "useUnderlyingAssets": true,
  "customUnderlyingMap": {
    "TQQQ": "QQQ"  // Use QQQ signals to trade TQQQ
  }
}
```

**When to use:**
- **Leveraged ETFs** (TQQQ, SOXL, UPRO, etc.) - cleaner signals from underlying
- **Inverse ETFs** (SQQQ, SPXU) - use underlying for better signal quality
- **Options strategies** - use underlying stock signals

**Built-in mappings** (automatically detected):
- TQQQ/SQQQ → QQQ
- UPRO/SPXU → SPY
- SOXL/SOXS → SOXX
- TNA/TZA → IWM
- And many more (see ConfigModal.vue for full list)

#### 3. Position Limiting

```json
{
  "maxPositions": 10,  // Max concurrent positions
  "ranker": "Adaptive" // How to rank stocks when multiple trigger
}
```

**Available rankers:**
- Adaptive (recommended)
- Heatmap
- Composite
- RelativeStrength
- DistanceFrom10Ema
- And more...

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

```bash
# Strategy A
curl -s -X POST http://localhost:8080/api/backtest \
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
curl -s -X POST http://localhost:8080/api/backtest \
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

Use Python to calculate:

```python
import json
from datetime import datetime
from collections import defaultdict

def calculate_drawdown(trades, starting_capital=100000):
    """Calculate max drawdown and equity curve"""
    balance = starting_capital
    peak = balance
    max_dd_pct = 0

    for trade in trades:
        profit_pct = trade['profitPercentage']
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

def calculate_yearly_performance(trades, starting_capital=100000):
    """Calculate year-by-year returns"""
    yearly_stats = defaultdict(lambda: {
        'starting_balance': 0,
        'ending_balance': 0,
        'wins': 0,
        'losses': 0,
        'total_trades': 0
    })

    balance = starting_capital

    for trade in trades:
        year = trade['entryQuote']['date'][:4]

        if yearly_stats[year]['starting_balance'] == 0:
            yearly_stats[year]['starting_balance'] = balance

        profit_pct = trade['profitPercentage']
        balance += balance * (profit_pct / 100)

        yearly_stats[year]['total_trades'] += 1
        yearly_stats[year]['ending_balance'] = balance

        if profit_pct > 0:
            yearly_stats[year]['wins'] += 1
        else:
            yearly_stats[year]['losses'] += 1

    return yearly_stats

def calculate_cagr(final_balance, starting_capital, years):
    """Calculate Compound Annual Growth Rate"""
    return (((final_balance / starting_capital) ** (1 / years)) - 1) * 100
```

### 3. Compare and Present Results

Create comparison tables showing:

**Key Metrics:**
- Total trades (fewer can be better)
- Win rate (%)
- Average win/loss (%)
- Edge per trade (%)

**Returns:**
- Final balance
- Total return (%)
- CAGR (%)

**Risk Metrics:**
- Max drawdown (%)
- Return/Drawdown ratio (higher is better)
- Peak balance

**Year-by-Year:**
- Trades per year
- Win rate per year
- Return per year
- Which strategy won each year

**Exit Reasons:**
- Breakdown of exit triggers
- Identify most profitable exits

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

## Complete Comparison Example

```python
import json

# Load both results
with open('/tmp/strategy_a.json', 'r') as f:
    strategy_a = json.load(f)

with open('/tmp/strategy_b.json', 'r') as f:
    strategy_b = json.load(f)

# Calculate metrics
a_metrics = calculate_drawdown(strategy_a['trades'])
b_metrics = calculate_drawdown(strategy_b['trades'])

a_yearly = calculate_yearly_performance(strategy_a['trades'])
b_yearly = calculate_yearly_performance(strategy_b['trades'])

# Calculate CAGR
years = 5.87  # 2020-01-01 to 2025-11-13
a_cagr = calculate_cagr(a_metrics['final_balance'], 100000, years)
b_cagr = calculate_cagr(b_metrics['final_balance'], 100000, years)

# Print comparison
print("=" * 90)
print("STRATEGY COMPARISON")
print("=" * 90)

# Performance comparison
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

# Year-by-year comparison
print()
print("YEAR-BY-YEAR COMPARISON")
print("-" * 90)
all_years = sorted(set(list(a_yearly.keys()) + list(b_yearly.keys())))
for year in all_years:
    a_year_return = ((a_yearly[year]['ending_balance'] - a_yearly[year]['starting_balance']) / a_yearly[year]['starting_balance'] * 100) if year in a_yearly else 0
    b_year_return = ((b_yearly[year]['ending_balance'] - b_yearly[year]['starting_balance']) / b_yearly[year]['starting_balance'] * 100) if year in b_yearly else 0

    winner = "Strategy B" if b_year_return > a_year_return else "Strategy A"
    winner_symbol = "🏆" if b_year_return > a_year_return else "  "

    print(f"{year:<8} {a_year_return:>14.2f}% {b_year_return:>14.2f}% {winner_symbol} {winner}")
```

## Analysis Guidelines

### What to Look For

**PRIMARY OBJECTIVE:** Optimize for BOTH high edge AND low drawdown. Never optimize for returns alone.

1. **Risk-Adjusted Returns (MOST IMPORTANT)**
   - **Don't just compare total returns** - this is misleading
   - **Return/Drawdown ratio is THE key metric** (target: > 5.0)
   - Lower drawdown with similar returns = BETTER strategy
   - High returns with high drawdown = POOR strategy (psychological torture, capital at risk)
   - Example comparison:
     - Strategy A: 200% return, 40% drawdown → Ratio: 5.0
     - Strategy B: 120% return, 15% drawdown → Ratio: 8.0 ← **WINNER**

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

**Step 1: Run Backtest** (see above sections)

**Step 2: Run Monte Carlo on Backtest Results**

```bash
# Extract trades from backtest and run Monte Carlo simulation
python3 << 'EOF'
import json

# Read backtest results
with open('/tmp/backtest_results.json', 'r') as f:
    backtest = json.load(f)

# Create Monte Carlo request
monte_carlo_request = {
    "trades": backtest['trades'],  # Only send trades array
    "technique": "TRADE_SHUFFLING",  # or "BOOTSTRAP_RESAMPLING"
    "iterations": 10000,             # 10,000 scenarios
    "includeAllEquityCurves": False  # Save bandwidth - only get percentile curves
}

# Save request
with open('/tmp/monte_carlo_request.json', 'w') as f:
    json.dump(monte_carlo_request, f)

print(f"Monte Carlo request created with {len(backtest['trades'])} trades")
EOF

# Run simulation
curl -X POST http://localhost:8080/udgaard/api/monte-carlo/simulate \
  -H "Content-Type: application/json" \
  -d @/tmp/monte_carlo_request.json \
  -o /tmp/monte_carlo_result.json \
  --max-time 300
```

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

**3. Price Path Randomization** (Future)
- Randomizes price movements within trades
- Tests strategy robustness to market noise

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
    print("✓ VALIDATED EDGE: 95%+ scenarios profitable - edge is real!")
elif stats['probabilityOfProfit'] >= 70:
    print("⚠ MODERATE EDGE: 70-95% scenarios profitable - some risk")
else:
    print("✗ WEAK EDGE: <70% scenarios profitable - likely curve-fitted")
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

print(f"⚠ SEQUENCE RISK: Drawdown varies by {dd_spread:.2f}% depending on trade order")
print(f"  - Same profitable trades can produce very different emotional experiences")
print(f"  - Worst case: {worst_dd:.2f}% drawdown")
print(f"  - Best case: {best_dd:.2f}% drawdown")
print(f"  - You must be prepared to handle worst-case path psychologically")
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
print(f"Return:            {result['originalReturnPercentage']:>8.2f}%   {stats['meanReturnPercentage']:>8.2f}%    {'✓' if stats['returnPercentiles']['p25'] <= result['originalReturnPercentage'] <= stats['returnPercentiles']['p75'] else '✗'}")
print(f"Edge:              {result['originalEdge']:>8.2f}%   {stats['meanEdge']:>8.2f}%    {'✓' if stats['edgePercentiles']['p25'] <= result['originalEdge'] <= stats['edgePercentiles']['p75'] else '✗'}")
print(f"Win Rate:          {result['originalWinRate']*100:>8.1f}%   {stats['meanWinRate']*100:>8.1f}%    {'✓' if stats['winRatePercentiles']['p25'] <= result['originalWinRate'] <= stats['winRatePercentiles']['p75'] else '✗'}")
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
1. ✓ **Edge is REAL** - 100% of scenarios profitable
2. ✓ **You WILL make money** - if you can stick with it
3. ⚠ **Journey varies wildly** - same destination, different pain levels
4. ⚠ **Psychological test** - can you handle 54% drawdown?

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
- Example: Kelly = (p × b - q) / b where p=winRate, b=avgWin/avgLoss

**6. Document Results**
- Save Monte Carlo results alongside backtest
- Include probability of profit
- Document drawdown range (best/worst case)
- Use for risk disclosure and position sizing

### Complete Workflow: Backtest + Monte Carlo

```bash
# 1. Run backtest
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

# 2. Extract trades and run Monte Carlo
python3 << 'EOF'
import json

with open('/tmp/tqqq_backtest.json', 'r') as f:
    backtest = json.load(f)

monte_carlo_request = {
    "trades": backtest['trades'],
    "technique": "TRADE_SHUFFLING",
    "iterations": 10000,
    "includeAllEquityCurves": False
}

with open('/tmp/monte_carlo_request.json', 'w') as f:
    json.dump(monte_carlo_request, f)
EOF

curl -X POST http://localhost:8080/udgaard/api/monte-carlo/simulate \
  -H "Content-Type: application/json" \
  -d @/tmp/monte_carlo_request.json \
  -o /tmp/monte_carlo_result.json \
  --max-time 300

# 3. Generate comprehensive report
python3 analysis_with_monte_carlo.py > TQQQ_VALIDATED_STRATEGY_REPORT.md
```

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

### Example: Interpreting TQQQ Results

```
Strategy: TQQQ using PlanEtf (QQQ signals)
Period: 2021-01-01 to 2025-11-19
Trades: 26

MONTE CARLO RESULTS (10,000 iterations, Trade Shuffling):
Probability of Profit:     100.00%  ✓ VALIDATED EDGE
Mean Return:               193.43%  ✓ STRONG RETURNS
Mean Edge:                   7.44%  ✓ EXCELLENT EDGE

Mean Drawdown:              34.46%  ⚠ MODERATE RISK
95th Percentile Drawdown:   54.30%  ⚠ HIGH WORST CASE
Drawdown Range:          21-54.30%  ⚠ HIGH SEQUENCE RISK

INTERPRETATION:
✓ Edge is REAL and validated (100% probability of profit)
✓ You WILL make money if you stick with it
⚠ Worst case requires enduring 54% drawdown
⚠ Psychological discipline is CRITICAL
⚠ Position sizing must account for 54% worst case

RECOMMENDATION:
- Use 50% of capital maximum (if 25% account DD is your tolerance)
- Understand you're committing to worst-case 54% drawdown path
- Edge is validated, but risk management is paramount
- Consider half-Kelly: 11.25% per trade based on statistics
```

### Saving Monte Carlo Reports

Always save comprehensive reports:

```bash
# Generate full report with both backtest and Monte Carlo
python3 << 'EOF'
import json
# ... (load both backtest and monte carlo results)
# ... (calculate all metrics)
# ... (save to markdown file)
EOF

# Report should include:
# - Backtest summary
# - Monte Carlo validation section
# - Probability of profit analysis
# - Drawdown distribution and path risk
# - Edge consistency verification
# - Position sizing recommendations
# - Risk warnings and green flags
```

See `TQQQ_MONTECARLO_REPORT.md` for example format.

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

## Best Practices

1. **Optimize for BOTH edge AND drawdown** - never optimize for returns alone
2. **Use Return/Drawdown ratio as primary metric** (target > 5.0)
3. **Always use POST endpoint** for backtests (cleaner, more flexible)
4. **Save results to files** before analysis
5. **Calculate comprehensive metrics** (edge, drawdown, CAGR, win rate)
6. **Compare risk-adjusted performance** (Return/Drawdown ratio is king)
7. **Analyze year-by-year** to ensure consistency
8. **Check exit reasons** to understand strategy behavior
9. **Consider cooldown periods** especially for leveraged ETFs
10. **Use underlying assets** for cleaner signals on leveraged instruments
11. **Present clear recommendations** based on risk-adjusted metrics
12. **Generate written reports** for future reference
13. **Reject strategies with drawdown > 25%** regardless of returns
14. **Reject strategies with edge < 2%** regardless of win rate

## MCP Integration

Use MCP tools to:
- Get available strategies dynamically
- Fetch strategy details and descriptions
- Discover stock symbols
- Understand available conditions for custom strategies

Never hardcode strategy lists - always fetch from API.
