# Trading Platform - Backtesting System

A comprehensive stock trading backtesting platform with Kotlin/Spring Boot backend (Udgaard), Nuxt.js frontend (Asgaard), and Electron desktop wrapper.

---

## Table of Contents

1. [Overview](#overview)
2. [Quick Start](#quick-start)
3. [Architecture](#architecture)
4. [Backtesting Features](#backtesting-features)
5. [How Backtesting Works](#how-backtesting-works)
6. [API Reference](#api-reference)
7. [Strategy System](#strategy-system)
8. [Development](#development)
9. [Troubleshooting](#troubleshooting)

---

## Overview

The backtesting system allows you to test trading strategies against historical stock data to evaluate their performance. It supports:

- **Multiple entry/exit strategies** (predefined and custom)
- **Position limits** with intelligent stock ranking
- **Underlying asset support** (e.g., trade TQQQ using QQQ signals)
- **Global cooldown periods** to prevent overtrading
- **Comprehensive performance metrics** (win rate, edge, profit/loss)
- **Monte Carlo simulation** for statistical validation
- **Sector analysis** and trade visualization

### Key Capabilities

- Historical stock data analysis with technical indicators (EMA, ATR, Donchian channels)
- Dynamic strategy system with DSL-based strategy creation
- Market and sector breadth analysis
- Real-time backtesting with comprehensive performance metrics
- Desktop application packaging via Electron

---

## Quick Start

### Running a Simple Backtest

**Option 1: Via API (GET)**

```bash
curl "http://localhost:8080/api/backtest?stockSymbols=AAPL,GOOGL&entryStrategy=PlanAlpha&exitStrategy=PlanMoney"
```

**Option 2: Via API (POST)**

```bash
curl -X POST http://localhost:8080/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "stockSymbols": ["AAPL", "GOOGL"],
    "entryStrategy": {"type": "predefined", "name": "PlanAlpha"},
    "exitStrategy": {"type": "predefined", "name": "PlanMoney"},
    "startDate": "2020-01-01",
    "endDate": "2025-11-22",
    "maxPositions": 3,
    "cooldownDays": 10
  }'
```

**Option 3: Via UI**

1. Start the backend: `cd udgaard && ./gradlew bootRun`
2. Start the frontend: `cd asgaard && npm run dev`
3. Open http://localhost:3000
4. Click "New Backtest" and configure options
5. View results with charts and metrics

---

## Architecture

### System Components

```
┌─────────────────────────────────────────────────────────────┐
│                    Frontend (Asgaard)                        │
│  - Nuxt 4.1.2 + TypeScript + Vue 3                          │
│  - NuxtUI components + ApexCharts                           │
│  - Config modal, results display, Monte Carlo UI            │
└────────────────────────┬────────────────────────────────────┘
                         ↓ HTTP POST
┌─────────────────────────────────────────────────────────────┐
│                   Backend (Udgaard)                          │
│  - Kotlin 1.9.25 + Spring Boot 3.5.0                        │
│  - MongoDB for data storage                                 │
│  - BacktestController → StockService → Strategy System      │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│                    Data Layer                                │
│  - MongoDB: Stock quotes, market breadth                    │
│  - Ovtlyr: Primary data provider                            │
│  - Alpha Vantage: Volume enrichment                         │
└─────────────────────────────────────────────────────────────┘
```

### Technology Stack

**Backend:**
- Kotlin 1.9.25, Spring Boot 3.5.0, MongoDB
- Coroutines (parallel processing), Caffeine (caching)
- Spring AI MCP Server (Claude integration)

**Frontend:**
- Nuxt 4.1.2, TypeScript 5.9.3, Vue 3 Composition API
- NuxtUI 4.0.1, ApexCharts 5.3.5, Unovis 1.6.1

**Desktop:**
- Electron 28.0.0, electron-builder 24.9.1

---

## Backtesting Features

### 1. Position Limits with Ranking

Simulate real-world capital constraints:
- Set maximum positions per day (e.g., max 3 stocks)
- Rank candidates using multiple algorithms
- Track missed opportunities

**Rankers Available:**
- `Heatmap`: Momentum-based ranking
- `RelativeStrength`: Relative strength vs market
- `Volatility`: ATR-based volatility ranking
- `DistanceFrom10Ema`: Value zone proximity
- `Composite`: Multi-factor combination
- `SectorStrength`: Sector strength ranking
- `Adaptive`: Market condition-based

### 2. Underlying Asset Support

Trade leveraged ETFs with cleaner signals:

**Example:** Trade TQQQ (3x leveraged QQQ) using QQQ signals
- Entry/exit signals from QQQ (less noise, cleaner trends)
- Actual P&L from TQQQ prices
- Best of both worlds

**Configuration:**
```json
{
  "stockSymbols": ["TQQQ"],
  "useUnderlyingAssets": true,
  "customUnderlyingMap": {
    "TQQQ": "QQQ"
  }
}
```

**Auto-detection** also available:
- TQQQ → QQQ
- SOXL → SMH
- UPRO → SPY

### 3. Global Cooldown

Prevent overtrading by enforcing waiting periods:
- Measured in **trading days** (not calendar days)
- Applies after ANY exit (global, not per-stock)
- Forces patience between trades

**Example:**
```
Exit trade on Jan 5th
Cooldown: 10 trading days
Next entry: ~Jan 19 (2 weeks later)
```

### 4. Comprehensive Metrics

**BacktestReport includes:**
- Win/loss statistics (count, rate, amounts)
- Edge (expected % gain per trade)
- Complete trade list with entry/exit details
- Exit reason analysis
- Sector performance breakdown
- Missed opportunity tracking

**Edge Calculation:**
```
Edge = (AvgWinPercent × WinRate) - ((1 - WinRate) × AvgLossPercent)
```

### 5. Monte Carlo Simulation

Validate strategy edge statistically:
- **Trade Shuffling**: Same trades, different order
- **Bootstrap Resampling**: Random sampling with replacement

**Use Case:**
1. Run backtest → 47 trades generated
2. Run Monte Carlo → 10,000 scenarios
3. Analyze: Is profit consistent or just lucky?
4. Result: 100% probability of profit = validated edge

---

## How Backtesting Works

### Date-by-Date Chronological Processing

The backtest simulates trading decisions **chronologically** to prevent look-ahead bias:

```kotlin
// Step 1: Build date range
val allTradingDates = stocks.flatMap { it.quotes }
    .filter { it.date in startDate..endDate }
    .map { it.date }
    .distinct()
    .sorted()

// Step 2: Process each date
allTradingDates.forEach { currentDate ->
    // 2a. Find all stocks meeting entry criteria
    val potentialEntries = stocks.filter { stock ->
        entryStrategy.test(stock, stock.getQuote(currentDate))
    }

    // 2b. Rank candidates
    val rankedEntries = potentialEntries
        .map { stock -> stock to ranker.score(stock, currentDate) }
        .sortedByDescending { it.second }

    // 2c. Apply position limit
    val selectedStocks = rankedEntries.take(maxPositions)

    // 2d. Create trades
    selectedStocks.forEach { stock ->
        val exitReport = stock.testExitStrategy(entryQuote, exitStrategy)
        if (exitReport.matched) {
            trades.add(Trade(...))
        }
    }
}
```

### Key Algorithm Details

1. **No Look-Ahead Bias**: Entry decisions made with data available up to current date only
2. **Realistic Fills**: Assumes fills at close prices
3. **Chronological Order**: Dates processed in sequence
4. **Dual Stock Support**: Trade one symbol, evaluate strategy on another
5. **Cooldown Enforcement**: Tracks last exit date, blocks entries during cooldown

---

## API Reference

### GET /api/backtest

Simple query parameter interface.

**Parameters:**
- `stockSymbols` (optional): Comma-separated symbols
- `entryStrategy` (optional): Entry strategy name (default: "PlanAlpha")
- `exitStrategy` (optional): Exit strategy name (default: "PlanMoney")
- `startDate` (optional): ISO date (default: "2020-01-01")
- `endDate` (optional): ISO date (default: today)
- `maxPositions` (optional): Max positions per day
- `ranker` (optional): Ranking method (default: "Heatmap")
- `cooldownDays` (optional): Global cooldown in trading days (default: 0)

**Example:**
```bash
GET /api/backtest?stockSymbols=AAPL,GOOGL&maxPositions=2&cooldownDays=5
```

### POST /api/backtest

Advanced interface with custom strategy support.

**Request:**
```json
{
  "stockSymbols": ["TQQQ"],
  "entryStrategy": {
    "type": "predefined",
    "name": "PlanEtf"
  },
  "exitStrategy": {
    "type": "custom",
    "conditions": [
      {"type": "stopLoss", "parameters": {"atrMultiplier": 0.5}},
      {"type": "profitTarget", "parameters": {"atrMultiplier": 3.0}}
    ]
  },
  "startDate": "2021-01-01",
  "endDate": "2025-11-22",
  "maxPositions": 2,
  "useUnderlyingAssets": true,
  "cooldownDays": 10
}
```

**Response:**
```json
{
  "winningTrades": [...],
  "losingTrades": [...],
  "missedTrades": [...],
  "winRate": 0.68,
  "averageWinPercent": 12.5,
  "averageLossPercent": 4.2,
  "edge": 7.16,
  "numberOfWinningTrades": 34,
  "numberOfLosingTrades": 16,
  "trades": [
    {
      "stockSymbol": "TQQQ",
      "underlyingSymbol": "QQQ",
      "entryQuote": {...},
      "exitReason": "Price is 2.9 ATR above 20 EMA",
      "profit": 12.50,
      "profitPercentage": 15.2,
      "startDate": "2021-03-15",
      "tradingDays": 23
    }
  ]
}
```

### GET /api/backtest/strategies

Get available strategies.

**Response:**
```json
{
  "entryStrategies": ["PlanAlpha", "PlanEtf", "PlanBeta", "OvtlyrPlanEtf", "VegardPlanEtf"],
  "exitStrategies": ["PlanMoney", "PlanAlpha", "PlanEtf", "OvtlyrPlanEtf", "VegardPlanEtf"]
}
```

### GET /api/backtest/rankers

Get available rankers.

**Response:**
```json
["Heatmap", "RelativeStrength", "Volatility", "DistanceFrom10Ema", "Composite", "SectorStrength", "Random", "Adaptive"]
```

### GET /api/backtest/conditions

Get available conditions for custom strategies.

**Response:**
```json
{
  "entryConditions": [
    {
      "type": "uptrend",
      "displayName": "Stock in Uptrend",
      "description": "10 EMA > 20 EMA and price > 50 EMA",
      "category": "Stock",
      "parameters": []
    },
    {
      "type": "buySignal",
      "displayName": "Buy Signal",
      "description": "Stock has a buy signal",
      "category": "Stock",
      "parameters": [
        {
          "name": "currentOnly",
          "type": "boolean",
          "defaultValue": false
        }
      ]
    }
  ],
  "exitConditions": [...]
}
```

---

## Strategy System

### Strategy Registration

Strategies are auto-discovered using annotations:

```kotlin
@RegisteredStrategy(name = "PlanAlpha", type = StrategyType.ENTRY)
class PlanAlphaEntryStrategy: EntryStrategy {
  private val compositeStrategy = entryStrategy {
    // MARKET (SPY)
    spyBuySignal()
    spyUptrend()
    spyHeatmap(70)

    // SECTOR
    sectorUptrend()
    sectorHeatmapRising()

    // STOCK
    buySignal(currentOnly = true)
    uptrend()
    priceAbove(10)
    stockHeatmapRising()
  }

  override fun test(stock: Stock, quote: StockQuote): Boolean {
    return compositeStrategy.test(stock, quote)
  }
}
```

### Strategy DSL

Build strategies declaratively:

```kotlin
// Entry Strategy
val myEntry = entryStrategy {
    buySignal()
    uptrend()
    priceAbove(20)
    heatmap(70)
}

// Exit Strategy
val myExit = exitStrategy {
    stopLoss(0.5)        // 0.5 ATR
    profitTarget(3.0)    // 3.0 ATR
    priceBelowEma(10)
}
```

### Available Strategies

**Entry Strategies:**
- `PlanAlpha`: Comprehensive multi-factor entry
- `PlanEtf`: ETF-focused entry strategy
- `PlanBeta`: Beta variant entry strategy
- `OvtlyrPlanEtf`: Ovtlyr's ETF strategy
- `VegardPlanEtf`: Vegard's ETF strategy with tighter value zone
- `SimpleBuySignal`: Basic buy signal entry

**Exit Strategies:**
- `PlanMoney`: Money management exit rules
- `PlanAlpha`: Plan Alpha exit rules
- `PlanEtf`: ETF-focused exit strategy
- `OvtlyrPlanEtf`: Ovtlyr's ETF exit with sell signals
- `VegardPlanEtf`: Vegard's ETF exit with trailing stop

---

## Development

### Backend Setup

**Prerequisites:**
- Docker (for MongoDB)
- Java 21+
- Create `udgaard/src/main/resources/secure.properties`:
  ```properties
  ovtlyr.cookies.token=XXX
  ovtlyr.cookies.userid=XXX
  ```

**Start MongoDB:**
```bash
cd udgaard
docker compose up -d
```

**Run Backend:**
```bash
./gradlew bootRun
# or
./gradlew build
java -jar build/libs/udgaard-0.0.1-SNAPSHOT.jar
```

**Backend runs on:** http://localhost:8080

### Frontend Setup

**Install dependencies:**
```bash
cd asgaard
npm install
```

**Run dev server:**
```bash
npm run dev
```

**Frontend runs on:** http://localhost:3000

### Desktop App (Electron)

**Development Mode:**
```bash
# Terminal 1: Start Nuxt dev server
cd asgaard
npm run dev

# Terminal 2: Start Electron
cd ..
npm run dev
```

**Production Build:**
```bash
npm run build:all
npm start
```

**Create Distributable:**
```bash
npm run dist          # Current platform
npm run dist:linux    # Linux AppImage + .deb
npm run dist:win      # Windows installer
npm run dist:mac      # macOS DMG
```

### Running Tests

**Backend:**
```bash
cd udgaard
./gradlew test
```

**Frontend:**
```bash
cd asgaard
npm run typecheck
npm run lint
```

---

## Troubleshooting

### Issue: "Backtest timeout"

**Symptoms:** Request times out after 2 minutes

**Solutions:**
1. Reduce stock count
2. Use cached data (`refresh: false`)
3. Reduce date range
4. Frontend timeout already set to 10 minutes

**Check:**
```typescript
// asgaard/app/pages/backtesting.vue
const report = await $fetch('/udgaard/api/backtest', {
  timeout: 600000 // 10 minutes
})
```

### Issue: "Missing underlying asset"

**Error:** `Missing underlying asset data for: QQQ`

**Solution:** Load underlying asset first
```bash
curl -X POST http://localhost:8080/api/stock/load \
  -H "Content-Type: application/json" \
  -d '{"symbols": ["QQQ"]}'
```

### Issue: "No trades generated"

**Possible Causes:**
1. **Strategy too strict**: No stocks meet criteria
   - Solution: Relax conditions or use different strategy
2. **Date range mismatch**: No data in range
   - Solution: Check stock data availability
3. **All in cooldown**: Global cooldown too long
   - Solution: Reduce cooldown days

**Debug:**
```bash
# Check available strategies
curl http://localhost:8080/api/backtest/strategies

# Test with known-good strategy
curl "http://localhost:8080/api/backtest?stockSymbols=AAPL&entryStrategy=SimpleBuySignal&exitStrategy=PlanMoney"
```

### Issue: "MongoDB connection failed"

**Solution:**
```bash
cd udgaard
docker compose up -d
docker ps  # Verify MongoDB is running
```

---

## Performance Optimizations

### Caching

Stock data is cached using Caffeine:
- **TTL:** 30 minutes
- **Max entries:** 1,000 per cache
- **Performance gain:** 43.7% faster on warm cache

**Cache warming:**
```bash
# Pre-load stocks into cache
curl -X POST http://localhost:8080/api/stock/load \
  -d '{"symbols": ["AAPL", "GOOGL", "MSFT", "NVDA"]}'
```

### Parallel Stock Fetching

Backend uses Kotlin coroutines for parallel fetching:
- Up to 10 concurrent fetches
- Graceful failure handling
- Significantly faster for multiple stocks

### Frontend Optimizations

- Long timeout for large backtests (10 min)
- Loading states with progress indicators
- Keep-alive connections for API calls
- Efficient chart rendering with ApexCharts

---

## Common Use Cases

### 1. Simple Backtest

Test default strategy on a few stocks:
```bash
GET /api/backtest?stockSymbols=AAPL,GOOGL,MSFT
```

### 2. Position-Limited Backtest

Simulate limited capital (max 3 positions):
```bash
GET /api/backtest?stockSymbols=AAPL,GOOGL,MSFT,NVDA,TSLA&maxPositions=3&ranker=Heatmap
```

### 3. Leveraged ETF with Underlying Signals

Trade TQQQ using QQQ signals:
```json
{
  "stockSymbols": ["TQQQ"],
  "entryStrategy": {"type": "predefined", "name": "VegardPlanEtf"},
  "exitStrategy": {"type": "predefined", "name": "VegardPlanEtf"},
  "useUnderlyingAssets": true,
  "cooldownDays": 10
}
```

### 4. Custom Strategy

Build from individual conditions:
```json
{
  "stockSymbols": ["AAPL"],
  "entryStrategy": {
    "type": "custom",
    "conditions": [
      {"type": "uptrend"},
      {"type": "priceAbove", "parameters": {"ema": 20}},
      {"type": "heatmap", "parameters": {"threshold": 50}}
    ]
  },
  "exitStrategy": {
    "type": "custom",
    "conditions": [
      {"type": "stopLoss", "parameters": {"atrMultiplier": 0.5}},
      {"type": "profitTarget", "parameters": {"atrMultiplier": 3.0}}
    ]
  }
}
```

### 5. Cooldown Analysis

Compare results with/without cooldown:
```bash
# Without cooldown
GET /api/backtest?stockSymbols=TQQQ&cooldownDays=0

# With 10-day cooldown
GET /api/backtest?stockSymbols=TQQQ&cooldownDays=10
```

---

## Project Structure

```
trading/
├── .claude/                    # Claude context and skills
│   ├── context.md
│   ├── commands/
│   └── skills/
│
├── electron/                   # Desktop app
│   ├── main.js
│   ├── preload.js
│   └── README.md
│
├── udgaard/                    # Backend (Kotlin/Spring Boot)
│   ├── src/main/kotlin/com/skrymer/udgaard/
│   │   ├── controller/         # REST controllers
│   │   │   ├── BacktestController.kt
│   │   │   ├── MonteCarloController.kt
│   │   │   ├── StockController.kt
│   │   │   └── dto/
│   │   ├── model/              # Domain models
│   │   │   ├── BacktestReport.kt
│   │   │   ├── Trade.kt
│   │   │   ├── Stock.kt
│   │   │   └── strategy/       # Trading strategies
│   │   ├── service/            # Business logic
│   │   │   ├── StockService.kt
│   │   │   ├── MonteCarloService.kt
│   │   │   └── StrategyRegistry.kt
│   │   └── integration/        # External APIs
│   ├── src/test/kotlin/
│   └── build.gradle
│
├── asgaard/              # Frontend (Nuxt.js)
│   ├── app/
│   │   ├── components/
│   │   │   ├── backtesting/
│   │   │   ├── charts/
│   │   │   └── strategy/
│   │   ├── pages/
│   │   │   ├── backtesting.vue
│   │   │   ├── portfolio.vue
│   │   │   └── market-breadth.vue
│   │   ├── types/
│   │   └── utils/
│   ├── nuxt.config.ts
│   └── package.json
│
└── README.md                   # This file
```

---

## Documentation

- **DYNAMIC_STRATEGY_SYSTEM.md**: How the strategy system works
- **MCP_SERVER_README.md**: MCP server setup and usage
- **MONTE_CARLO_BUG_FIX_REPORT.md**: Critical compounding bug fix
- **TQQQ_BACKTEST_COMPARISON_REPORT.md**: Strategy comparison analysis
- **DATA_COMPARISON_REPORT.md**: Ovtlyr vs Yahoo Finance validation
- **udgaard/claude_thoughts/**: Implementation notes and summaries

---

## Resources

- [Nuxt Documentation](https://nuxt.com/docs)
- [NuxtUI Documentation](https://ui.nuxt.com)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Model Context Protocol](https://modelcontextprotocol.io/)

---

## License

[Add your license information here]

---

## Contributors

Built with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
