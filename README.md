# Trading Platform - Backtesting System

A comprehensive stock trading backtesting platform with Kotlin/Spring Boot backend (Udgaard) and Nuxt.js frontend (Asgaard).

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
- **Comprehensive performance metrics** (win rate, edge, profit factor, drawdown)
- **Monte Carlo simulation** for statistical validation
- **Sector and stock performance analysis**
- **Time-based performance breakdown** (yearly, quarterly, monthly)
- **Exit reason analysis**
- **Edge consistency scoring**

### Key Capabilities

- Historical stock data analysis with technical indicators (EMA, ATR, Donchian channels)
- Dynamic strategy system with DSL-based strategy creation
- Market and sector breadth analysis
- Portfolio management with live trade tracking (stocks and options)
- MCP (Model Context Protocol) server for Claude AI integration

---

## Quick Start

### Running a Backtest

**Option 1: Via API (POST)**

```bash
curl -X POST http://localhost:8080/udgaard/api/backtest \
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

**Option 2: Via UI**

1. Start the backend: `cd udgaard && ./gradlew startH2Server` (Terminal 1), then `./gradlew bootRun` (Terminal 2)
2. Start the frontend: `cd asgaard && npm run dev`
3. Open http://localhost:3000
4. Navigate to Backtesting page and configure options
5. View results with charts and metrics

---

## Architecture

### System Components

```
┌─────────────────────────────────────────────────────────────┐
│                    Frontend (Asgaard)                        │
│  - Nuxt 4.1.2 + TypeScript + Vue 3                          │
│  - NuxtUI 4.0.1 + ApexCharts + Unovis                      │
│  - Config modal, results display, Monte Carlo UI            │
└────────────────────────┬────────────────────────────────────┘
                         ↓ HTTP POST
┌─────────────────────────────────────────────────────────────┐
│                   Backend (Udgaard)                          │
│  - Kotlin 2.3.0 + Spring Boot 3.5.0                         │
│  - H2 Database + jOOQ + Flyway                              │
│  - BacktestController → BacktestService → Strategy System   │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│                    Data Layer                                │
│  - H2 Database: Stock quotes, symbols, market breadth       │
│  - AlphaVantage: Primary data source (adjusted OHLCV, ATR)  │
│  - Technical indicators calculated locally (EMA, Donchian)  │
└─────────────────────────────────────────────────────────────┘
```

### Technology Stack

**Backend:**
- Kotlin 2.3.0, Spring Boot 3.5.0
- H2 2.2.224 Database (file-based, no Docker required)
- jOOQ 3.19.23 (type-safe SQL), Flyway (migrations)
- Caffeine (caching), Spring AI MCP Server (Claude integration)
- detekt 2.0.0-alpha.2 (static analysis), ktlint (code style)

**Frontend:**
- Nuxt 4.1.2, TypeScript 5.9.3, Vue 3 Composition API
- NuxtUI 4.0.1, ApexCharts 5.3.5, Unovis 1.6.1

---

## Backtesting Features

### 1. Position Limits with Ranking

Simulate real-world capital constraints:
- Set maximum positions per day (e.g., max 3 stocks)
- Rank candidates using multiple algorithms
- Track missed opportunities

**Rankers Available:**
- `Adaptive`: Market condition-based ranking (default)
- `Composite`: Multi-factor combination
- `Volatility`: ATR-based volatility ranking
- `DistanceFrom10Ema`: Value zone proximity
- `SectorStrength`: Sector strength ranking
- `Random`: Random selection

### 2. Underlying Asset Support

Trade leveraged ETFs with cleaner signals:

**Example:** Trade TQQQ (3x leveraged QQQ) using QQQ signals
- Entry/exit signals from QQQ (less noise, cleaner trends)
- Actual P&L from TQQQ prices

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

**Auto-detection** also available (TQQQ → QQQ, SOXL → SMH, UPRO → SPY).

### 3. Global Cooldown

Prevent overtrading by enforcing waiting periods:
- Measured in **trading days** (not calendar days)
- Applies after ANY exit (global, not per-stock)

### 4. Comprehensive Metrics

**BacktestReport includes:**
- Win/loss statistics (count, rate, amounts, percentages)
- Edge (expected % gain per trade)
- Profit factor (gross profit / gross loss)
- Maximum drawdown
- Edge consistency score (how stable the edge is across years)
- Time-based stats (yearly, quarterly, monthly breakdown)
- Exit reason analysis with per-reason and per-year stats
- Sector performance breakdown
- Stock-level performance breakdown
- ATR drawdown statistics for winning trades
- Market condition averages at trade entry
- Complete trade list with entry/exit details
- Missed opportunity tracking

**Edge Calculation:**
```
Edge = (AvgWinPercent × WinRate) - ((1 - WinRate) × AvgLossPercent)
```

### 5. Monte Carlo Simulation

Validate strategy edge statistically:
- **Trade Shuffling**: Same trades, different order
- **Bootstrap Resampling**: Random sampling with replacement

---

## How Backtesting Works

### Date-by-Date Chronological Processing

The backtest simulates trading decisions **chronologically** to prevent look-ahead bias:

1. Build a sorted list of all trading dates in the range
2. For each date:
   - Check exits for open positions
   - Find all stocks meeting entry criteria
   - Rank candidates using selected ranker
   - Apply position limit (take top N)
   - Track missed trades (qualified but couldn't enter)
   - Apply cooldown after exits
3. Compile results into BacktestReport

### Key Algorithm Details

1. **No Look-Ahead Bias**: Entry decisions made with data available up to current date only
2. **Realistic Fills**: Assumes fills at close prices
3. **Chronological Order**: Dates processed in sequence
4. **Dual Stock Support**: Trade one symbol, evaluate strategy on another
5. **Cooldown Enforcement**: Tracks last exit date, blocks entries during cooldown

---

## API Reference

### POST /api/backtest

Run a backtest with full configuration.

**Request:**
```json
{
  "stockSymbols": ["AAPL", "GOOGL"],
  "assetTypes": ["STOCK"],
  "entryStrategy": {
    "type": "predefined",
    "name": "PlanAlpha"
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
  "maxPositions": 3,
  "ranker": "Adaptive",
  "useUnderlyingAssets": true,
  "customUnderlyingMap": {"TQQQ": "QQQ"},
  "cooldownDays": 10
}
```

**Parameters:**
- `stockSymbols` (optional): List of symbols to test
- `assetTypes` (optional): Filter by asset type (STOCK, ETF, LEVERAGED_ETF, INDEX, BOND_ETF, COMMODITY_ETF)
- `entryStrategy`: Predefined name or custom conditions
- `exitStrategy`: Predefined name or custom conditions
- `startDate` / `endDate` (optional): Date range
- `maxPositions` (optional): Max concurrent positions
- `ranker` (optional): Ranking method (default: "Adaptive")
- `useUnderlyingAssets` (optional): Use underlying for signals (default: true)
- `cooldownDays` (optional): Global cooldown in trading days (default: 0)

**Response:**
```json
{
  "winningTrades": [...],
  "losingTrades": [...],
  "missedTrades": [...],
  "winRate": 0.68,
  "edge": 7.16,
  "profitFactor": 2.1,
  "averageWinPercent": 12.5,
  "averageLossPercent": 4.2,
  "totalTrades": 50,
  "timeBasedStats": {...},
  "exitReasonAnalysis": {...},
  "sectorPerformance": [...],
  "stockPerformance": [...],
  "edgeConsistencyScore": {...}
}
```

### GET /api/backtest/strategies

Get available predefined strategies.

### GET /api/backtest/rankers

Get available rankers.

**Response:**
```json
["Adaptive", "Volatility", "DistanceFrom10Ema", "Composite", "SectorStrength", "Random"]
```

### GET /api/backtest/conditions

Get available conditions for custom strategies, including parameter metadata.

### Other Endpoints

**Stocks:** `GET /api/stocks`, `GET /api/stocks/{symbol}`, `POST /api/stocks/refresh`

**Market Breadth:** `GET /api/breadth`, `GET /api/breadth/{symbol}`, `POST /api/breadth/refresh`

**Portfolio:** `GET/POST /api/portfolio`, `GET/DELETE /api/portfolio/{id}`, trade management endpoints

**Monte Carlo:** `POST /api/monte-carlo/run`

**Data Management:** `POST /api/data/import`, `POST /api/cache/evict`, `POST /api/cache/evict-all`

---

## Strategy System

### Strategy Registration

Strategies are auto-discovered using the `@RegisteredStrategy` annotation:

```kotlin
@RegisteredStrategy(name = "PlanAlpha", type = StrategyType.ENTRY)
class PlanAlphaEntryStrategy : DetailedEntryStrategy {
  private val compositeStrategy = entryStrategy {
    marketInUptrend()
    sectorInUptrend()
    uptrend()
    priceAboveEma(20)
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
    marketInUptrend()
    sectorInUptrend()
    uptrend()
    priceAboveEma(20)
    marketBreadthAbove(50.0)
}

// Exit Strategy
val myExit = exitStrategy {
    stopLoss(0.5)        // 0.5 ATR
    profitTarget(3.0)    // 3.0 ATR
    priceBelowEma(10)
    emaCross()
}
```

### Available Strategies

**Entry Strategies:**
- `PlanAlpha`: Multi-factor entry with market, sector, and stock conditions
- `PlanQ`: Focused entry strategy variant
- `PlanMV`: Minimum volatility entry strategy
- `OvtlyrPlanEtf`: ETF-focused entry strategy
- `ProjectX`: Experimental entry strategy

**Exit Strategies:**
- `PlanMoney`: Money management exit rules (ATR-based stops and targets)
- `PlanAlpha`: Plan Alpha exit rules
- `PlanQ`: Focused exit strategy variant
- `OvtlyrPlanEtf`: ETF-focused exit strategy
- `ProjectX`: Experimental exit strategy

### Available Conditions

**Entry Conditions (22):**
- **Market**: MarketUptrend, MarketBreadthAbove
- **Sector**: SectorUptrend, SectorBreadthGreaterThanSpy
- **Stock Trend**: Uptrend, EmaAlignment, EmaBullishCross, PriceAboveEma
- **Value Zone**: ValueZone, ConsecutiveHigherHighsInValueZone, BelowOrderBlock, AboveBearishOrderBlock
- **Order Block**: NotInOrderBlock, OrderBlockRejection
- **Risk**: ADXRange, ATRExpanding, MinimumPrice, VolumeAboveAverage, PriceAbovePreviousLow
- **Earnings**: DaysSinceEarnings, NoEarningsWithinDays

**Exit Conditions (11):**
- StopLoss, ProfitTarget, ATRTrailingStopLoss
- PriceBelowEma, PriceBelowEmaForDays, PriceBelowEmaMinusAtr
- EmaCross, BelowPreviousDayLow
- MarketAndSectorDowntrend, BearishOrderBlock, BeforeEarnings

---

## Development

### Backend Setup

**Prerequisites:**
- Java 21+
- Gradle (included via wrapper)

**First-Time Setup:**
```bash
cd udgaard

# Create secure.properties for API credentials
touch src/main/resources/secure.properties
# Add your AlphaVantage API key (optional, can also be set via Settings UI)

# Terminal 1: Start H2 database server
./gradlew startH2Server

# Terminal 2: Initialize database and build
./gradlew initDatabase  # Creates schema via Flyway migrations
./gradlew build         # Generates jOOQ code, runs tests, builds JAR

# Start the application
./gradlew bootRun
```

**Regular Development:**
```bash
# Terminal 1: Start H2 server (if not already running)
./gradlew startH2Server

# Terminal 2: Run application
./gradlew bootRun
```

**Backend runs on:** http://localhost:8080/udgaard

### Frontend Setup

```bash
cd asgaard
npm install
npm run dev
```

**Frontend runs on:** http://localhost:3000

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

### Code Quality

**Kotlin code style (ktlint):**
```bash
cd udgaard
./gradlew ktlintCheck       # Check
./gradlew ktlintFormat      # Auto-fix
```

**Static analysis (detekt):**
```bash
cd udgaard
./gradlew detekt            # Run analysis
./gradlew detektBaseline    # Regenerate baseline
```

---

## Troubleshooting

### Issue: "Backtest timeout"

**Symptoms:** Request times out

**Solutions:**
1. Reduce stock count or date range
2. The backend timeout is 30 minutes (`spring.mvc.async.request-timeout=1800000`)
3. The frontend timeout is 10 minutes

### Issue: "Missing underlying asset"

**Error:** `Missing underlying asset data for: QQQ`

**Solution:** Load the underlying asset data first via the Data Manager page or API.

### Issue: "No trades generated"

**Possible Causes:**
1. **Strategy too strict**: No stocks meet all entry conditions
2. **Date range mismatch**: No data in the specified range
3. **All in cooldown**: Global cooldown too long

### Issue: Database connection errors

**Solution:**
```bash
# Ensure H2 server is running
cd udgaard
./gradlew startH2Server

# If locked, stop and restart
./gradlew stopH2Server
./gradlew startH2Server
```

### Issue: Build fails

**Solution:**
```bash
cd udgaard

# Ensure H2 server is running (required for jOOQ code generation)
./gradlew startH2Server

# Clean rebuild
./gradlew clean build
```

---

## Performance

### Caching

Stock data is cached using Caffeine:
- **TTL:** 30 minutes
- **Max entries:** 1,000 per cache

### Scaling Notes

- 500 stocks: ~2GB heap, ~29 min runtime
- ~4MB per stock in memory
- HTTP timeout: 30 minutes (main bottleneck for large batches)

---

## Project Structure

```
trading/
├── udgaard/                    # Backend (Kotlin/Spring Boot)
│   ├── src/main/kotlin/com/skrymer/udgaard/
│   │   ├── backtesting/        # Backtesting engine
│   │   │   ├── controller/     # REST controllers
│   │   │   ├── dto/            # Request/response DTOs
│   │   │   ├── model/          # BacktestReport, Trade, metrics
│   │   │   ├── service/        # BacktestService, DynamicStrategyBuilder
│   │   │   └── strategy/       # Entry/exit strategies and conditions
│   │   ├── data/               # Data layer
│   │   │   ├── controller/     # Stock, Breadth, DataManagement controllers
│   │   │   ├── integration/    # AlphaVantage client
│   │   │   ├── model/          # Stock, StockQuote, MarketBreadth
│   │   │   ├── repository/     # jOOQ repositories
│   │   │   └── service/        # StockService, BreadthService, etc.
│   │   └── mcp/                # MCP server for Claude AI
│   ├── src/main/resources/
│   │   ├── db/migration/       # Flyway SQL migrations
│   │   └── application.properties
│   ├── build.gradle
│   ├── detekt.yml              # Static analysis config
│   └── detekt-baseline.xml     # Baseline for existing violations
│
├── asgaard/                    # Frontend (Nuxt.js)
│   ├── app/
│   │   ├── components/         # Vue components
│   │   │   ├── backtesting/    # Backtest config, results, charts
│   │   │   ├── portfolio/      # Portfolio management
│   │   │   ├── charts/         # Chart components
│   │   │   ├── data-management/ # Data refresh and stats
│   │   │   └── strategy/       # Strategy builder
│   │   ├── pages/              # File-based routing
│   │   │   ├── index.vue       # Dashboard
│   │   │   ├── backtesting.vue # Backtesting UI
│   │   │   ├── portfolio.vue   # Portfolio management
│   │   │   ├── stock-data.vue  # Stock data viewer
│   │   │   ├── data-manager.vue # Data management
│   │   │   ├── settings.vue    # Application settings
│   │   │   └── app-metrics.vue # App metrics
│   │   └── types/              # TypeScript definitions
│   ├── nuxt.config.ts
│   └── package.json
│
├── CLAUDE.md                   # Claude AI context
└── README.md                   # This file
```

---

## Documentation

- [Backend README](udgaard/README.MD)
- [Frontend README](asgaard/README.md)
- [Dynamic Strategy System](claude_thoughts/DYNAMIC_STRATEGY_SYSTEM.md)
- [MCP Server Setup](claude_thoughts/MCP_SERVER_README.md)
- [AlphaVantage Architecture](claude_thoughts/ALPHAVANTAGE_REFACTORING_SUMMARY.md)

---

## Resources

- [Nuxt Documentation](https://nuxt.com/docs)
- [NuxtUI Documentation](https://ui.nuxt.com)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Model Context Protocol](https://modelcontextprotocol.io/)

---

## Contributors

Built with [Claude Code](https://claude.com/claude-code)

Co-Authored-By: Claude <noreply@anthropic.com>
