# Run Trading Backtest

This skill runs backtests for trading strategies using the Udgaard API.

## Instructions

When the user asks to run a backtest:

1. **Ensure the backend is running** on port 8080
2. **Identify the parameters**:
   - Stock symbols (e.g., QQQ, SPY, AAPL)
   - Entry strategy
   - Exit strategy
   - Date range
   - Optional: maxPositions, ranker
3. **Run the backtest** using the API endpoint
4. **Analyze and present** the results in a clear format

## Available Strategies

### Entry Strategies
- **PlanEtf**: ETF strategy (uptrend + buy signal + heatmap < 70 + value zone + below order block)
- **PlanAlpha**: Alpha strategy with market regime filters
- **PlanBeta**: Beta variant with different conditions
- **SimpleBuySignal**: Simple buy signal based entry

### Exit Strategies
- **PlanEtf**: ETF exit (sell signal OR 10/20 EMA cross OR order block OR 3.0 ATR profit target)
- **PlanMoney**: Money management based exits
- **PlanAlpha**: Alpha exit strategy
- **SellSignal**: Exit on sell signal
- **HalfAtr**: Half ATR based stop loss
- **Heatmap**: Heatmap based exits
- Other available: BelowPriorDaysLow, LessGreedy, MarketAndSectorBreadthReverses, etc.

### Stock Rankers (for position-limited backtests)
- **Heatmap**: Ranks by heatmap score
- **RelativeStrength**: Ranks by relative strength
- **Volatility**: Ranks by volatility
- **DistanceFrom10Ema**: Ranks by distance from 10 EMA
- **Composite**: Combines multiple factors
- **SectorStrength**: Ranks by sector strength
- **Adaptive**: Adapts to market conditions
- **Random**: Random selection (baseline)

## API Endpoints

### GET Endpoint (Simple)

```
GET http://localhost:8080/api/backtest
```

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `stockSymbols` | List<String> | No | all stocks | Comma-separated stock symbols |
| `entryStrategy` | String | No | PlanAlpha | Entry strategy name |
| `exitStrategy` | String | No | PlanMoney | Exit strategy name |
| `startDate` | String | No | 2020-01-01 | Start date (YYYY-MM-DD) |
| `endDate` | String | No | today | End date (YYYY-MM-DD) |
| `maxPositions` | Integer | No | null | Max concurrent positions (enables position limiting) |
| `ranker` | String | No | Heatmap | Stock ranking method (when maxPositions is set) |
| `refresh` | Boolean | No | false | Force refresh stock data from API |

### POST Endpoint (Advanced)

```
POST http://localhost:8080/api/backtest
Content-Type: application/json
```

**Request Body:**

```json
{
  "stockSymbols": ["AAPL", "MSFT"],
  "entryStrategy": {
    "type": "predefined",
    "name": "PlanAlpha"
  },
  "exitStrategy": {
    "type": "predefined",
    "name": "PlanMoney"
  },
  "startDate": "2020-01-01",
  "endDate": "2024-12-31",
  "maxPositions": 10,
  "ranker": "Heatmap",
  "refresh": false
}
```

**Note:** The POST endpoint supports both predefined strategies (shown above) and custom strategies built with conditions. Use `"type": "predefined"` for existing strategies or `"type": "custom"` for dynamic strategy building.

**When to use POST vs GET:**

| Feature | GET | POST |
|---------|-----|------|
| **Predefined Strategies** | ✅ Simple query params | ✅ JSON body with type: "predefined" |
| **Custom Strategies** | ❌ Not supported | ✅ Build with conditions |
| **URL Length** | ⚠️ Limited (many stocks = long URL) | ✅ No limit |
| **Ease of Use** | ✅ Quick for simple tests | ⚠️ More verbose |
| **Structure** | Query string | JSON body (cleaner) |
| **Best For** | Quick tests, single/few stocks | Complex strategies, many stocks, custom conditions |

**Quick Decision:**
- Use **GET** for simple backtests with predefined strategies (easier, less verbose)
- Use **POST** when:
  - Building custom strategies with complex conditions
  - Testing many stocks (cleaner than long URL)
  - Need better structure for large requests
  - Integrating with applications that prefer JSON bodies

## Commands

### Basic Backtest (GET)

```bash
# Single stock with specific strategy
curl -s "http://localhost:8080/api/backtest?stockSymbols=QQQ&entryStrategy=PlanEtf&exitStrategy=PlanEtf&startDate=2020-01-01"

# Multiple stocks
curl -s "http://localhost:8080/api/backtest?stockSymbols=QQQ,SPY,TQQQ&entryStrategy=PlanEtf&exitStrategy=PlanEtf"

# With date range
curl -s "http://localhost:8080/api/backtest?stockSymbols=AAPL&entryStrategy=PlanAlpha&exitStrategy=PlanMoney&startDate=2023-01-01&endDate=2024-12-31"
```

### Basic Backtest (POST)

```bash
# Single stock with specific strategy
curl -s -X POST http://localhost:8080/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "stockSymbols": ["QQQ"],
    "entryStrategy": {"type": "predefined", "name": "PlanEtf"},
    "exitStrategy": {"type": "predefined", "name": "PlanEtf"},
    "startDate": "2020-01-01",
    "endDate": "2025-11-13",
    "refresh": false
  }'

# Multiple stocks
curl -s -X POST http://localhost:8080/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "stockSymbols": ["QQQ", "SPY", "TQQQ"],
    "entryStrategy": {"type": "predefined", "name": "PlanEtf"},
    "exitStrategy": {"type": "predefined", "name": "PlanEtf"},
    "startDate": "2020-01-01",
    "refresh": false
  }'

# All stocks (omit stockSymbols for all stocks)
curl -s -X POST http://localhost:8080/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "entryStrategy": {"type": "predefined", "name": "PlanAlpha"},
    "exitStrategy": {"type": "predefined", "name": "PlanMoney"},
    "startDate": "2020-01-01",
    "maxPositions": 10,
    "ranker": "DistanceFrom10Ema",
    "refresh": false
  }'

# With specific date range and ranker
curl -s -X POST http://localhost:8080/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "stockSymbols": ["SPY"],
    "entryStrategy": {"type": "predefined", "name": "PlanEtf"},
    "exitStrategy": {"type": "predefined", "name": "PlanEtf"},
    "startDate": "2020-01-01",
    "endDate": "2025-11-13",
    "ranker": "Heatmap",
    "refresh": false
  }'
```

### Position-Limited Backtest

```bash
# GET: Limit to 10 positions using heatmap ranker
curl -s "http://localhost:8080/api/backtest?stockSymbols=QQQ,SPY,AAPL,MSFT,GOOGL&entryStrategy=PlanEtf&exitStrategy=PlanEtf&maxPositions=10&ranker=Heatmap"

# POST: Limit to 10 positions using distance from 10 EMA ranker
curl -s -X POST http://localhost:8080/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "entryStrategy": {"type": "predefined", "name": "PlanAlpha"},
    "exitStrategy": {"type": "predefined", "name": "PlanMoney"},
    "startDate": "2020-01-01",
    "maxPositions": 10,
    "ranker": "DistanceFrom10Ema",
    "refresh": false
  }'

# POST: All stocks with position limit (realistic portfolio simulation)
curl -s -X POST http://localhost:8080/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "entryStrategy": {"type": "predefined", "name": "PlanAlpha"},
    "exitStrategy": {"type": "predefined", "name": "PlanMoney"},
    "startDate": "2020-01-01",
    "endDate": "2025-11-13",
    "maxPositions": 15,
    "ranker": "Composite",
    "refresh": false
  }'
```

### Custom Strategy Backtest (POST Only)

The POST endpoint supports building custom strategies using conditions. This is more advanced than predefined strategies.

```bash
# Custom entry strategy with multiple conditions
curl -s -X POST http://localhost:8080/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "stockSymbols": ["QQQ", "SPY"],
    "entryStrategy": {
      "type": "custom",
      "operator": "AND",
      "description": "Custom uptrend + buy signal strategy",
      "conditions": [
        {"type": "uptrend"},
        {"type": "buySignal"},
        {"type": "priceAboveEma", "parameters": {"period": 20}},
        {"type": "marketHeatmapAbove", "parameters": {"threshold": 50}}
      ]
    },
    "exitStrategy": {
      "type": "predefined",
      "name": "PlanMoney"
    },
    "startDate": "2020-01-01"
  }'

# Custom exit strategy with stop loss and profit target
curl -s -X POST http://localhost:8080/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "stockSymbols": ["AAPL"],
    "entryStrategy": {
      "type": "predefined",
      "name": "PlanAlpha"
    },
    "exitStrategy": {
      "type": "custom",
      "operator": "OR",
      "description": "Stop loss or profit target",
      "conditions": [
        {"type": "stopLoss", "parameters": {"atrMultiple": 0.5}},
        {"type": "profitTarget", "parameters": {"atrMultiple": 3.0}},
        {"type": "sellSignal"}
      ]
    },
    "startDate": "2020-01-01",
    "endDate": "2025-11-13"
  }'

# Both custom entry and exit strategies
curl -s -X POST http://localhost:8080/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "stockSymbols": ["SPY"],
    "entryStrategy": {
      "type": "custom",
      "operator": "AND",
      "description": "Conservative entry",
      "conditions": [
        {"type": "uptrend"},
        {"type": "buySignal"},
        {"type": "marketInUptrend"},
        {"type": "sectorInUptrend"}
      ]
    },
    "exitStrategy": {
      "type": "custom",
      "operator": "OR",
      "description": "Quick exit",
      "conditions": [
        {"type": "priceBelowEma", "parameters": {"period": 10}},
        {"type": "stopLoss", "parameters": {"atrMultiple": 1.0}}
      ]
    },
    "startDate": "2020-01-01",
    "endDate": "2025-11-13"
  }'
```

**Getting Available Conditions:**

To see all available conditions for building custom strategies:

```bash
# Get available conditions and their parameters
curl -s http://localhost:8080/api/conditions | python3 -m json.tool
```

This will show entry and exit conditions with:
- `type`: Condition identifier
- `displayName`: Human-readable name
- `description`: What the condition checks
- `parameters`: Required/optional parameters with types and defaults
- `category`: Condition category (Stock, Market, SPY, Sector, etc.)

### Save and Analyze Results

```bash
# Save to file (GET)
curl -s "http://localhost:8080/api/backtest?stockSymbols=QQQ&entryStrategy=PlanEtf&exitStrategy=PlanEtf&startDate=2020-01-01" > backtest_results.json

# Save to file (POST)
curl -s -X POST http://localhost:8080/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "stockSymbols": ["QQQ"],
    "entryStrategy": {"type": "predefined", "name": "PlanEtf"},
    "exitStrategy": {"type": "predefined", "name": "PlanEtf"},
    "startDate": "2020-01-01"
  }' > backtest_results.json

# Pretty print with Python (POST)
curl -s -X POST http://localhost:8080/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "stockSymbols": ["SPY"],
    "entryStrategy": {"type": "predefined", "name": "PlanEtf"},
    "exitStrategy": {"type": "predefined", "name": "PlanEtf"},
    "startDate": "2020-01-01",
    "endDate": "2025-11-13"
  }' | python3 -m json.tool

# Extract summary metrics (POST)
curl -s -X POST http://localhost:8080/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "stockSymbols": ["SPY"],
    "entryStrategy": {"type": "predefined", "name": "PlanEtf"},
    "exitStrategy": {"type": "predefined", "name": "PlanEtf"},
    "startDate": "2020-01-01",
    "endDate": "2025-11-13"
  }' | python3 -c "import sys, json; data=json.load(sys.stdin); print(f\"Trades: {data['totalTrades']}, Win Rate: {data['winRate']*100:.2f}%, Edge: {data['edge']:.2f}%\")"
```

## Analyzing Results

The backtest response contains:

```json
{
  "totalTrades": 69,
  "winRate": 0.6812,
  "numberOfWinningTrades": 47,
  "numberOfLosingTrades": 22,
  "averageWinPercent": 2.75,
  "averageLossPercent": 1.57,
  "edge": 1.37,
  "trades": [...],
  "exitReasonCount": {...},
  "winningTrades": [...],
  "losingTrades": [...]
}
```

### Python Analysis Script Template

```python
import json
from datetime import datetime

# Load results
with open('backtest_results.json', 'r') as f:
    data = json.load(f)

# Extract key metrics
total_trades = data['totalTrades']
win_rate = data['winRate'] * 100
avg_win = data['averageWinPercent']
avg_loss = data['averageLossPercent']
edge = data['edge']

# Simulate compounding returns
starting_capital = 100000
balance = starting_capital

for trade in data['trades']:
    profit_pct = trade['profitPercentage']
    profit_dollars = balance * (profit_pct / 100)
    balance += profit_dollars

total_return = ((balance - starting_capital) / starting_capital) * 100

# Calculate CAGR
trades = data['trades']
first_date = datetime.strptime(trades[0]['entryQuote']['date'], '%Y-%m-%d')
last_date = datetime.strptime(trades[-1]['entryQuote']['date'], '%Y-%m-%d')
years = (last_date - first_date).days / 365.25
cagr = (((balance / starting_capital) ** (1 / years)) - 1) * 100

# Print summary
print(f"Win Rate: {win_rate:.2f}%")
print(f"Total Return: {total_return:.2f}%")
print(f"CAGR: {cagr:.2f}%")
print(f"Final Balance: ${balance:,.2f}")
```

## Expected Output Structure

### Key Metrics to Report:
1. **Overall Performance**
   - Total trades
   - Win rate
   - Average win/loss percentages
   - Edge per trade

2. **Financial Results**
   - Starting capital (assume $100,000)
   - Final balance
   - Total return %
   - CAGR (Compound Annual Growth Rate)

3. **Year-by-Year Breakdown**
   - Trades per year
   - Win rate per year
   - Return % per year

4. **Exit Reason Analysis**
   - Which exit reasons are most profitable
   - Which exit reasons are least profitable
   - Count of trades per exit reason

## Examples

### Example 1: QQQ with PlanEtf Strategy

**GET:**
```bash
curl -s "http://localhost:8080/api/backtest?stockSymbols=QQQ&entryStrategy=PlanEtf&exitStrategy=PlanEtf&startDate=2020-01-01&endDate=2025-11-10"
```

**POST:**
```bash
curl -s -X POST http://localhost:8080/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "stockSymbols": ["QQQ"],
    "entryStrategy": {"type": "predefined", "name": "PlanEtf"},
    "exitStrategy": {"type": "predefined", "name": "PlanEtf"},
    "startDate": "2020-01-01",
    "endDate": "2025-11-10"
  }'
```

**Expected Results:**
- 69 trades over 5.5 years
- 68% win rate
- +148% total return
- 17.9% CAGR

### Example 2: SPY with PlanEtf Strategy (5+ years)

**POST:**
```bash
curl -s -X POST http://localhost:8080/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "stockSymbols": ["SPY"],
    "entryStrategy": {"type": "predefined", "name": "PlanEtf"},
    "exitStrategy": {"type": "predefined", "name": "PlanEtf"},
    "startDate": "2020-01-01",
    "endDate": "2025-11-13"
  }'
```

**Expected Results:**
- 41 trades over 5+ years
- 65.85% win rate
- 1.61% edge per trade

### Example 3: Multiple Tech Stocks with Position Limit

**GET:**
```bash
curl -s "http://localhost:8080/api/backtest?stockSymbols=AAPL,MSFT,GOOGL,AMZN,META&entryStrategy=PlanAlpha&exitStrategy=PlanMoney&maxPositions=3&ranker=Composite&startDate=2022-01-01"
```

**POST:**
```bash
curl -s -X POST http://localhost:8080/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "stockSymbols": ["AAPL", "MSFT", "GOOGL", "AMZN", "META"],
    "entryStrategy": {"type": "predefined", "name": "PlanAlpha"},
    "exitStrategy": {"type": "predefined", "name": "PlanMoney"},
    "startDate": "2022-01-01",
    "maxPositions": 3,
    "ranker": "Composite"
  }'
```

This limits to 3 concurrent positions, ranking by composite score.

### Example 4: Custom Strategy with Tight Stop Loss

**POST Only:**
```bash
curl -s -X POST http://localhost:8080/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "stockSymbols": ["QQQ"],
    "entryStrategy": {"type": "predefined", "name": "PlanAlpha"},
    "exitStrategy": {
      "type": "custom",
      "operator": "OR",
      "description": "Tight stop with profit target",
      "conditions": [
        {"type": "stopLoss", "parameters": {"atrMultiple": 0.5}},
        {"type": "profitTarget", "parameters": {"atrMultiple": 2.5}}
      ]
    },
    "startDate": "2020-01-01",
    "endDate": "2025-11-13"
  }'
```

This tests PlanAlpha entry with a custom exit that uses tight stop loss (0.5 ATR) and moderate profit target (2.5 ATR).

## Common Issues

### Backend Not Running
```bash
# Check if backend is running
curl -s http://localhost:8080/api/strategies

# If not running, start it
cd udgaard && ./gradlew bootRun
```

### Invalid Strategy Name
```bash
# List available strategies
curl -s http://localhost:8080/api/strategies | python3 -m json.tool

# List available rankers
curl -s http://localhost:8080/api/rankers | python3 -m json.tool
```

### No Stock Data
```bash
# Force refresh stock data
curl -s "http://localhost:8080/api/backtest?stockSymbols=QQQ&refresh=true&entryStrategy=PlanEtf&exitStrategy=PlanEtf"
```

### Timeout for Large Backtests
- Reduce the number of stocks
- Reduce the date range
- Save results to file instead of printing to console

## Related Endpoints

```bash
# Get available strategies
curl -s http://localhost:8080/api/strategies

# Get available rankers
curl -s http://localhost:8080/api/rankers

# Get stock symbols
curl -s http://localhost:8080/api/stocks

# Get specific stock data
curl -s http://localhost:8080/api/stocks/QQQ

# Get market breadth
curl -s "http://localhost:8080/api/market-breadth?marketSymbol=FULLSTOCK"
```

## Tips

1. **Always save large backtest results to a file** - responses can be very large
2. **Use Python for analysis** - JSON is easy to parse and analyze
3. **Compare multiple strategies** - run same stocks with different strategies
4. **Check exit reasons** - identify which exits are most profitable
5. **Use position limits for realistic simulation** - unlimited positions is unrealistic
6. **Consider transaction costs** - the backtest doesn't include commissions/slippage
7. **Year-by-year analysis** - understand how strategy performs in different market conditions
8. **Use POST for complex requests** - cleaner structure, supports custom strategies
9. **Use GET for quick tests** - faster to type for simple predefined strategy backtests
10. **Test custom conditions incrementally** - start with simple conditions, add complexity gradually

## Performance Benchmarks

Good strategies typically have:
- Win rate: 60-70%+
- Edge: 1.0%+ per trade
- CAGR: 15%+ on equity ETFs
- Max drawdown: < 20%
- Positive returns in most years

## Next Steps After Backtest

1. Analyze exit reason profitability
2. Compare against buy-and-hold
3. Test on different symbols/sectors
4. Optimize parameters based on findings
5. Consider transaction costs and slippage
6. Validate with out-of-sample data
