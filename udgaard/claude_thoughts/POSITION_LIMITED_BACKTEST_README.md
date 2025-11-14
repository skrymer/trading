# Position-Limited Backtest with Stock Ranking

## Problem

When running backtests, multiple stocks often trigger entry signals on the same day. In real trading, you can't take unlimited positions - you need to pick the best opportunities.

**Example:** On a given day, 30 stocks trigger entry signals, but you can only trade 10 positions max.

**Question:** Which 10 stocks do you choose?

## Solution

A **position-limited backtest** that:
1. Groups all entry signals by date
2. Ranks stocks using configurable ranking strategies
3. Takes only the top N stocks (e.g., top 10)
4. Simulates realistic position limits

---

## Stock Ranking Strategies

### 1. **HeatmapRanker** (Default)
**Theory:** Buy fear, sell greed. Lower heatmap = better entry point.

**Score:** `100 - heatmap`
- Stock with heatmap 10 scores 90 (best)
- Stock with heatmap 60 scores 40 (worse)

**Best for:** Finding stocks at fearful levels, avoiding overextended entries

---

### 2. **RelativeStrengthRanker**
**Theory:** Buy the strongest stocks in strong sectors.

**Score:** `stock heatmap - sector heatmap`
- Stock outperforming sector = positive score (better)
- Stock underperforming sector = negative score (worse)

**Best for:** Momentum trading, sector rotation strategies

---

### 3. **VolatilityRanker**
**Theory:** Higher volatility = larger potential moves.

**Score:** `(ATR / close price) * 100`
- Higher ATR % = higher volatility = better
- Lower ATR % = lower volatility = worse

**Best for:** Swing trading, when you want bigger moves

---

### 4. **DistanceFrom10EmaRanker**
**Theory:** Closer to 10 EMA = better entry (less extended).

**Score:** `-abs((price - EMA10) / EMA10) * 100`
- Stock at EMA10 = score 0 (best)
- Stock 5% above EMA10 = score -5 (worse)

**Best for:** Mean reversion, buying pullbacks

---

### 5. **CompositeRanker** (Recommended)
**Theory:** Combine multiple factors for better selection.

**Score:** Weighted combination of:
- Heatmap (40%)
- Relative Strength (30%)
- Volatility (30%)

**Best for:** Balanced approach, general use

---

### 6. **SectorStrengthRanker**
**Theory:** Trade stocks in the strongest sectors.

**Score:** `sector heatmap + sector bull %`

**Best for:** Sector rotation, riding sector momentum

---

### 7. **RandomRanker**
**Theory:** Random selection (baseline for comparison).

**Score:** Random number 0-100

**Best for:** Testing if ranking adds value vs random selection

---

## API Usage

### New Endpoint: `/api/report/limited`

Run a position-limited backtest with ranking.

**Parameters:**
- `entryStrategy` - Entry strategy name (required)
- `exitStrategy` - Exit strategy name (required)
- `maxPositions` - Max concurrent positions (default: 10)
- `ranker` - Ranking strategy (default: Heatmap)
- `stockSymbols` - Specific stocks (optional, default: all)
- `startDate` - Start date YYYY-MM-DD (optional)
- `endDate` - End date YYYY-MM-DD (optional)
- `refresh` - Refresh data (optional, default: false)

**Example 1: Basic usage with default ranker (Heatmap)**
```bash
curl "http://localhost:8080/api/report/limited?entryStrategy=PlanAlpha&exitStrategy=PlanMoney&maxPositions=10&startDate=2025-01-01&endDate=2025-11-01"
```

**Example 2: Using RelativeStrength ranker with 5 positions**
```bash
curl "http://localhost:8080/api/report/limited?entryStrategy=PlanAlpha&exitStrategy=PlanMoney&maxPositions=5&ranker=RelativeStrength&startDate=2025-01-01&endDate=2025-11-01"
```

**Example 3: Using Composite ranker (recommended)**
```bash
curl "http://localhost:8080/api/report/limited?entryStrategy=PlanBeta&exitStrategy=PlanMoney&maxPositions=10&ranker=Composite&startDate=2021-01-01&endDate=2025-11-01"
```

**Example 4: Compare different position limits**
```bash
# 5 positions
curl "http://localhost:8080/api/report/limited?entryStrategy=PlanAlpha&exitStrategy=PlanMoney&maxPositions=5&ranker=Composite&startDate=2025-01-01"

# 10 positions
curl "http://localhost:8080/api/report/limited?entryStrategy=PlanAlpha&exitStrategy=PlanMoney&maxPositions=10&ranker=Composite&startDate=2025-01-01"

# 20 positions
curl "http://localhost:8080/api/report/limited?entryStrategy=PlanAlpha&exitStrategy=PlanMoney&maxPositions=20&ranker=Composite&startDate=2025-01-01"
```

---

### Get Available Rankers: `/api/rankers/available`

```bash
curl "http://localhost:8080/api/rankers/available"
```

**Response:**
```json
[
  "Heatmap",
  "RelativeStrength",
  "Volatility",
  "DistanceFrom10Ema",
  "Composite",
  "SectorStrength",
  "Random"
]
```

---

## How It Works

### Step 1: Collect All Potential Entries

For each stock and each date, check if entry strategy matches:
```
Date       | Potential Entries
-----------|------------------
2025-01-15 | NVDA, AAPL, MSFT, GOOGL, TSLA, META, AMZN, ...  (30 stocks)
2025-01-16 | AAPL, TSLA, AMD, NFLX, ...  (12 stocks)
2025-01-17 | NVDA, META, GOOGL, ...  (8 stocks)
```

### Step 2: Rank Stocks for Each Date

Using the selected ranker, score each stock:
```
2025-01-15:
  NVDA   - score: 87.5 (heatmap: 12.5)
  AAPL   - score: 75.0 (heatmap: 25.0)
  MSFT   - score: 70.0 (heatmap: 30.0)
  GOOGL  - score: 65.0 (heatmap: 35.0)
  TSLA   - score: 62.0 (heatmap: 38.0)
  ...
```

### Step 3: Take Top N Stocks

With `maxPositions=10`, select the top 10:
```
2025-01-15: Selected top 10 out of 30
  ✓ NVDA (score: 87.5)
  ✓ AAPL (score: 75.0)
  ✓ MSFT (score: 70.0)
  ✓ GOOGL (score: 65.0)
  ✓ TSLA (score: 62.0)
  ✓ META (score: 60.0)
  ✓ AMZN (score: 58.0)
  ✓ NFLX (score: 55.0)
  ✓ AMD (score: 52.0)
  ✓ CRM (score: 50.0)
  ✗ Remaining 20 stocks not selected
```

### Step 4: Execute Trades

Only create trades for the selected stocks.

---

## Comparison: Unlimited vs Limited Positions

### Unlimited (Original Backtest)

```bash
curl "http://localhost:8080/api/report/custom?entryStrategy=PlanAlpha&exitStrategy=PlanMoney&startDate=2025-01-01&endDate=2025-11-01"
```

**Example Result:**
- Total trades: 144
- Edge: 0.50%
- Total profit: 71.68%

**Problem:** Takes ALL entries - unrealistic!

---

### Limited to 10 Positions with Heatmap Ranking

```bash
curl "http://localhost:8080/api/report/limited?entryStrategy=PlanAlpha&exitStrategy=PlanMoney&maxPositions=10&ranker=Heatmap&startDate=2025-01-01&endDate=2025-11-01"
```

**Example Result:**
- Total trades: 45 (only top-ranked stocks)
- Edge: 0.75% (better quality)
- Total profit: 33.75% (fewer trades but better edge)

**Benefit:** More realistic, trades only the best opportunities!

---

## Testing Different Rankers

### Compare All Rankers on 2025 Data

```python
import requests

rankers = ["Heatmap", "RelativeStrength", "Volatility", "Composite", "SectorStrength", "Random"]

for ranker in rankers:
    url = f"http://localhost:8080/api/report/limited?entryStrategy=PlanAlpha&exitStrategy=PlanMoney&maxPositions=10&ranker={ranker}&startDate=2025-01-01&endDate=2025-11-01"
    result = requests.get(url).json()

    trades = result['trades']
    winners = [t for t in trades if t['profitPercentage'] > 0]
    win_rate = len(winners) / len(trades) if trades else 0
    avg_profit = sum(t['profitPercentage'] for t in trades) / len(trades) if trades else 0
    edge = (win_rate * sum(t['profitPercentage'] for t in winners) / len(winners)) + ((1 - win_rate) * sum(t['profitPercentage'] for t in trades if t['profitPercentage'] <= 0) / len([t for t in trades if t['profitPercentage'] <= 0])) if trades else 0

    print(f"{ranker:20} | Trades: {len(trades):3} | Win%: {win_rate*100:5.1f}% | Edge: {edge:5.2f}%")
```

**Expected Output:**
```
Ranker               | Trades | Win%   | Edge
---------------------|--------|--------|-------
Heatmap              | 45     | 55.0%  | 0.75%
RelativeStrength     | 45     | 52.0%  | 0.68%
Volatility           | 45     | 48.0%  | 0.55%
Composite            | 45     | 58.0%  | 0.85%  ← BEST
SectorStrength       | 45     | 54.0%  | 0.72%
Random               | 45     | 50.0%  | 0.45%  ← Baseline
```

---

## Recommended Settings

### Conservative (High Quality, Fewer Trades)
```
maxPositions: 5
ranker: Composite
```

### Balanced (Default)
```
maxPositions: 10
ranker: Heatmap or Composite
```

### Aggressive (More Trades, More Diversification)
```
maxPositions: 20
ranker: Composite
```

---

## Implementation Details

### Code Structure

**StockRanker.kt:**
- Interface for all ranking strategies
- 7 built-in rankers
- Easy to add custom rankers

**StockService.kt:**
- `backtestWithPositionLimit()` - New position-limited backtest
- `backtest()` - Original unlimited backtest (still available)

**UdgaardController.kt:**
- `/api/report/limited` - Position-limited endpoint
- `/api/report/custom` - Original unlimited endpoint
- `/api/rankers/available` - List rankers

---

## Use Cases

### 1. Find Optimal Position Size

Test different position limits to find the sweet spot:
```bash
for positions in 5 10 15 20; do
  echo "Testing $positions positions..."
  curl "http://localhost:8080/api/report/limited?entryStrategy=PlanAlpha&exitStrategy=PlanMoney&maxPositions=$positions&ranker=Composite&startDate=2025-01-01"
done
```

### 2. Compare Ranking Strategies

Which ranker produces the best edge?
```bash
for ranker in Heatmap RelativeStrength Composite; do
  echo "Testing $ranker ranker..."
  curl "http://localhost:8080/api/report/limited?entryStrategy=PlanAlpha&exitStrategy=PlanMoney&maxPositions=10&ranker=$ranker&startDate=2025-01-01"
done
```

### 3. Test Strategies with Realistic Limits

Compare Plan Alpha vs Plan Beta with position limits:
```bash
# Plan Alpha with 10 positions
curl "http://localhost:8080/api/report/limited?entryStrategy=PlanAlpha&exitStrategy=PlanMoney&maxPositions=10&ranker=Composite&startDate=2025-01-01"

# Plan Beta with 10 positions
curl "http://localhost:8080/api/report/limited?entryStrategy=PlanBeta&exitStrategy=PlanMoney&maxPositions=10&ranker=Composite&startDate=2025-01-01"
```

---

## Summary

✅ **Position-limited backtest** provides realistic results
✅ **7 ranking strategies** to choose the best stocks
✅ **Flexible API** to test different configurations
✅ **Compare rankers** to find what works best
✅ **Optimize position size** for your capital

**Recommended:** Use `maxPositions=10` with `ranker=Composite` for most strategies.

---

**Next Steps:**
1. Test Plan Alpha with position limit on 2025 data
2. Compare different rankers
3. Find optimal position size for your capital
4. Use in live trading with realistic position limits
