# Trading Platform - Claude Context

This file provides comprehensive context for the Trading Platform project.

## Technology-Specific Context Files

**IMPORTANT:** When working on specific parts of the codebase, read the relevant technology-specific guide:

- **Frontend work** (Vue/Nuxt/UI components): Read `asgaard/claude.md`
  - Includes NuxtUI component patterns, Vue composition API examples, form validation, styling conventions

- **Backend work** (Kotlin/Spring Boot/Strategies): Read `udgaard/claude.md`
  - Includes strategy development patterns, backtesting engine details, service patterns, testing guidelines

## Project Overview

This is a stock trading backtesting platform with a Kotlin/Spring Boot backend (Udgaard), a Nuxt.js frontend (Asgaard), and an Electron desktop wrapper. The platform enables users to backtest trading strategies using historical stock data with advanced technical indicators and market sentiment analysis.

**Key Capabilities:**
- Historical stock data analysis with technical indicators (EMA, ATR, Donchian channels)
- Dynamic strategy system with DSL-based strategy creation
- Multiple entry/exit strategy combinations
- Market and sector breadth analysis
- **Portfolio management** with live trade tracking (stocks and options)
- **ETF analysis** with holdings and performance metrics
- **Monte Carlo simulations** for strategy validation
- MCP (Model Context Protocol) server for Claude AI integration
- Real-time backtesting with comprehensive performance metrics
- Desktop application packaging via Electron

---

## Architecture

### Desktop App: Electron Wrapper

**Tech Stack:**
- Electron 28.0.0
- electron-builder 24.9.1
- tree-kill 1.2.2 for process management
- Node.js for main process

**Architecture:**
The desktop app uses a three-process architecture:
1. **Main Process** (`electron/main.js`) - Manages app lifecycle, spawns backend subprocess
2. **Backend Subprocess** - Spring Boot JAR runs as child process on port 8080
3. **Renderer Process** - Displays Nuxt UI in BrowserWindow

**Key Features:**
- **Development Mode** (`--dev` flag):
  - Loads frontend from `http://localhost:3000` (Nuxt dev server)
  - Opens DevTools automatically
  - Uses JAR from `udgaard/build/libs/`

- **Production Mode**:
  - Loads frontend from built assets in `asgaard/.output/`
  - Uses JAR from bundled resources
  - No DevTools

**Process Management:**
- Backend startup with health check polling (30s timeout)
- Graceful shutdown on app quit
- Process cleanup with tree-kill

**Security:**
- `nodeIntegration: false` - Prevents Node.js access in renderer
- `contextIsolation: true` - Isolates Electron APIs
- `enableRemoteModule: false` - Disables deprecated remote module
- Preload script (`preload.js`) - Exposes only necessary APIs via contextBridge

**Files:**
- `electron/main.js` - Main process, backend lifecycle, window management
- `electron/preload.js` - Security bridge for renderer process
- `electron/README.md` - Detailed documentation
- `package.json` (root) - Electron build configuration

**Scripts:**
- `npm run dev` - Development mode with Nuxt dev server
- `npm start` - Production mode with built assets
- `npm run build:all` - Build backend + frontend
- `npm run dist` - Create distributable for current platform
- `npm run dist:win/mac/linux` - Platform-specific builds

**Build Output:**
- Windows: NSIS installer (.exe)
- macOS: DMG disk image
- Linux: AppImage and .deb package
- Output directory: `dist-electron/`

### Backend: Udgaard (Kotlin/Spring Boot)

**Tech Stack:**
- Kotlin 2.1.21
- Spring Boot 3.5.0
- H2 Database (file-based) with JPA/Hibernate for data storage
- Gradle build system
- Spring AI MCP Server for Claude integration
- **AlphaVantage API as PRIMARY data source** (adjusted OHLCV, volume, ATR)
- Ovtlyr API for enrichment (signals, heatmaps, sector sentiment)

**Key Components:**
1. **Strategy System** (`src/main/kotlin/com/skrymer/udgaard/model/strategy/`)
   - Dynamic strategy discovery using `@RegisteredStrategy` annotation
   - DSL-based strategy builder (`StrategyDsl.kt`)
   - Composite strategies for complex logic
   - Entry strategies: PlanAlpha, PlanEtf, PlanBeta, SimpleBuySignal
   - Exit strategies: PlanMoney, PlanAlpha, PlanEtf
   - Market regime filters for market condition-based filtering

2. **Services** (`src/main/kotlin/com/skrymer/udgaard/service/`)
   - `StockService.kt`: Stock data management and backtesting logic
   - `TechnicalIndicatorService.kt`: Calculate EMAs, Donchian channels, trend determination
   - `OvtlyrEnrichmentService.kt`: Enrich AlphaVantage data with Ovtlyr signals and heatmaps
   - `BreadthService.kt`: Market breadth calculations (SPY, QQQ, IWM)
   - `EtfService.kt`: ETF data management and holdings analysis
   - `PortfolioService.kt`: Portfolio and trade management with statistics
   - `StrategyRegistry.kt`: Dynamic strategy registration and discovery
   - `DynamicStrategyBuilder.kt`: Runtime strategy creation from configurations

3. **MCP Server** (`src/main/kotlin/com/skrymer/udgaard/mcp/`)
   - `StockMcpTools.kt`: MCP tools for Claude AI integration
   - Tools: getStockData, getMultipleStocksData, getMarketBreadth, getStockSymbols, runBacktest
   - Enables Claude to perform custom backtesting and strategy analysis

4. **Integration** (`src/main/kotlin/com/skrymer/udgaard/integration/`)
   - **AlphaVantage** (`alphavantage/`): **PRIMARY data source** - Adjusted daily OHLCV, ATR, ETF profiles
   - **Ovtlyr** (`ovtlyr/`): **Enrichment source** - Buy/sell signals, heatmaps, sector sentiment, breadth data

**API Endpoints:**

**Backtesting:**
- `POST /api/backtest` - Run backtest with strategy parameters
- `GET /api/strategies/available` - Get list of available strategies

**Stocks:**
- `GET /api/stocks` - Get all stocks
- `GET /api/stocks/{symbol}` - Get specific stock with quotes
- `POST /api/stocks/refresh` - Refresh stock data from data providers
- `GET /api/stock-symbols` - Get all available stock symbols

**Portfolio Management:**
- `GET /api/portfolio` - Get all portfolios
- `POST /api/portfolio` - Create new portfolio
- `GET /api/portfolio/{id}` - Get specific portfolio
- `DELETE /api/portfolio/{id}` - Delete portfolio
- `GET /api/portfolio/{id}/stats` - Get portfolio statistics
- `GET /api/portfolio/{id}/trades` - Get portfolio trades (filter by status)
- `POST /api/portfolio/{id}/trades` - Open new trade
- `PUT /api/portfolio/{id}/trades/{tradeId}` - Update trade
- `DELETE /api/portfolio/{id}/trades/{tradeId}` - Delete trade
- `PUT /api/portfolio/{id}/trades/{tradeId}/close` - Close trade
- `GET /api/portfolio/{id}/equity-curve` - Get equity curve data

**Market Breadth:**
- `GET /api/breadth` - Get all breadth symbols
- `GET /api/breadth/{symbol}` - Get market breadth for specific symbol (SPY/QQQ/IWM)
- `POST /api/breadth/refresh` - Refresh breadth data

**ETF:**
- `GET /api/etf` - Get all ETFs
- `GET /api/etf/{symbol}` - Get specific ETF with quotes and holdings
- `POST /api/etf/refresh` - Refresh ETF data

**Monte Carlo Simulations:**
- `POST /api/monte-carlo/run` - Run Monte Carlo simulation

**Cache Management:**
- `POST /api/cache/evict` - Evict specific cache
- `POST /api/cache/evict-all` - Evict all caches

**Data Management:**
- `POST /api/data/import` - Import stock data from CSV/file

### Frontend: Asgaard (Nuxt.js)

**Tech Stack:**
- Nuxt 4.1.2
- NuxtUI 4.0.1 (component library)
- TypeScript 5.9.3
- Vue 3 Composition API
- Tailwind CSS (via NuxtUI)
- ApexCharts 5.3.5 for data visualization
- Unovis 1.6.1 for advanced charts
- date-fns 4.1.0 for date handling
- Zod 4.1.11 for validation
- npm package manager

**Key Components:**

1. **Backtesting Components** (`app/components/backtesting/`)
   - `Cards.vue`: Summary cards for backtest metrics
   - `ConfigModal.vue`: Strategy configuration modal
   - `EquityCurve.vue`: Equity curve visualization
   - `SectorAnalysis.vue`: Sector performance analysis
   - `TradeChart.client.vue`: Interactive trade charts (client-side only)
   - `TradeDetailsModal.vue`: Detailed trade information

2. **Portfolio Components** (`app/components/portfolio/`)
   - `CreateModal.vue`: Create new portfolio
   - `OpenTradeModal.vue`: Open new trade with validation
   - `EditTradeModal.vue`: Edit existing trade
   - `CloseTradeModal.vue`: Close open trade
   - `DeleteTradeModal.vue`: Delete trade confirmation
   - `DeleteModal.vue`: Delete portfolio confirmation
   - `EquityCurve.vue`: Portfolio equity curve chart
   - `OpenTradeChart.vue`: Individual trade performance chart

3. **Strategy Components** (`app/components/strategy/`)
   - Strategy builder and configuration components

4. **Pages** (`app/pages/`)
   - `index.vue`: Dashboard/home page
   - `backtesting.vue`: Main backtesting interface
   - `portfolio.vue`: Portfolio management and live trade tracking
   - `market-breadth.vue`: Market breadth analysis and charts
   - `stock-data.vue`: Stock data viewer and management
   - `etf-stats.vue`: ETF statistics and holdings analysis
   - `test-chart.vue`: Chart testing page

**Type Definitions:**
- `app/types/index.d.ts`: Main type definitions for backtesting, trades, stocks
- `app/types/enums.ts`: Enums for strategies and other constants

**Patterns:**
- Auto-imports for Vue, Nuxt, and NuxtUI composables
- File-based routing
- TypeScript with strict typing
- Composition API with `<script setup>`
- ESLint with custom rules (no trailing commas, 1tbs brace style)

---

## Project Structure

```
trading/
├── CLAUDE.md                         # This file - project-wide context
│
├── .claude/                          # Claude configuration
│   ├── commands/                     # Custom slash commands
│   ├── skills/                       # Skills (e.g., run-backtest)
│   └── settings.local.json           # Local settings
│
├── claude_thoughts/                  # Documentation created by Claude
│   ├── Migration docs (H2, database, refactoring)
│   ├── Implementation docs (features, strategies, integrations)
│   ├── Performance reports and analysis
│   └── Bug fix and verification reports
│
├── electron/                         # Desktop app wrapper
│   ├── main.js                       # Electron main process
│   ├── preload.js                    # Security bridge
│   ├── README.md                     # Electron documentation
│   └── .gitignore
│
├── udgaard/                          # Backend (Kotlin/Spring Boot)
│   ├── src/main/kotlin/com/skrymer/udgaard/
│   │   ├── controller/               # REST controllers
│   │   │   ├── BacktestController.kt # Backtesting API
│   │   │   ├── StockController.kt    # Stock data API
│   │   │   ├── PortfolioController.kt # Portfolio management API
│   │   │   ├── BreadthController.kt  # Market breadth API
│   │   │   ├── EtfController.kt      # ETF data API
│   │   │   ├── MonteCarloController.kt # Monte Carlo simulations
│   │   │   ├── CacheController.kt    # Cache management
│   │   │   ├── DataController.kt     # Data import/export
│   │   │   └── dto/                  # Request/response DTOs
│   │   ├── integration/              # External integrations
│   │   │   ├── ovtlyr/              # Ovtlyr stock data provider
│   │   │   └── alphavantage/        # AlphaVantage API client
│   │   ├── mcp/                      # MCP server tools
│   │   │   └── StockMcpTools.kt     # Claude AI integration tools
│   │   ├── model/                    # Domain models
│   │   │   ├── BacktestReport.kt    # Backtest results
│   │   │   ├── Stock.kt             # Stock entity (JPA)
│   │   │   ├── StockQuote.kt        # Price quote with indicators (JPA)
│   │   │   ├── Portfolio.kt         # Portfolio entity (JPA)
│   │   │   ├── PortfolioTrade.kt    # Trade entity (JPA)
│   │   │   ├── Breadth.kt           # Market breadth entity (JPA)
│   │   │   ├── BreadthQuote.kt      # Breadth quote entity (JPA)
│   │   │   ├── EtfEntity.kt         # ETF entity (JPA)
│   │   │   ├── EtfQuote.kt          # ETF quote entity (JPA)
│   │   │   ├── EtfHolding.kt        # ETF holdings entity (JPA)
│   │   │   └── strategy/            # Trading strategies
│   │   │       ├── CompositeEntryStrategy.kt
│   │   │       ├── CompositeExitStrategy.kt
│   │   │       ├── MarketRegimeFilter.kt
│   │   │       ├── PlanAlphaEntryStrategy.kt
│   │   │       ├── PlanBetaEntryStrategy.kt
│   │   │       ├── PlanEtfEntryStrategy.kt
│   │   │       ├── PlanMoneyExitStrategy.kt
│   │   │       ├── RegisteredStrategy.kt  # Annotation for auto-discovery
│   │   │       ├── SimpleBuySignalEntryStrategy.kt
│   │   │       ├── StockRanker.kt
│   │   │       ├── StrategyDsl.kt         # DSL builder
│   │   │       └── condition/             # Strategy conditions
│   │   ├── service/                  # Business logic
│   │   │   ├── DynamicStrategyBuilder.kt
│   │   │   ├── StockService.kt      # Stock data orchestration
│   │   │   ├── TechnicalIndicatorService.kt # EMA, Donchian, trend calculations
│   │   │   ├── OvtlyrEnrichmentService.kt   # Ovtlyr signal enrichment
│   │   │   ├── BreadthService.kt    # Market breadth service
│   │   │   ├── PortfolioService.kt  # Portfolio management
│   │   │   ├── EtfService.kt        # ETF data service
│   │   │   └── StrategyRegistry.kt  # Strategy management
│   │   ├── repository/               # JPA repositories
│   │   ├── config/                   # Configuration
│   │   │   └── CacheConfig.kt       # Caffeine cache config
│   │   └── UdgaardApplication.kt    # Main application
│   ├── src/main/resources/
│   │   ├── application.properties    # Config (H2, API keys, etc.)
│   │   └── secure.properties        # Ovtlyr credentials (not in git)
│   ├── src/test/kotlin/             # Unit tests
│   ├── build.gradle                 # Gradle build config
│   ├── compose.yaml                 # Docker compose (deprecated - used MongoDB)
│   └── *.md                         # Documentation files
│
├── asgaard/                    # Frontend (Nuxt.js)
│   ├── app/
│   │   ├── components/              # Vue components
│   │   │   ├── backtesting/         # Backtesting UI components
│   │   │   ├── portfolio/           # Portfolio management components
│   │   │   ├── charts/              # Chart components
│   │   │   └── strategy/            # Strategy builder components
│   │   ├── layouts/
│   │   │   └── default.vue          # Main layout with sidebar
│   │   ├── pages/                   # File-based routing
│   │   │   ├── index.vue            # Dashboard
│   │   │   ├── backtesting.vue      # Backtesting page
│   │   │   ├── portfolio.vue        # Portfolio manager
│   │   │   ├── market-breadth.vue   # Market breadth analysis
│   │   │   ├── stock-data.vue       # Stock data viewer
│   │   │   ├── etf-stats.vue        # ETF statistics
│   │   │   └── test-chart.vue       # Chart testing
│   │   ├── plugins/                 # Nuxt plugins
│   │   ├── types/                   # TypeScript definitions
│   │   │   ├── index.d.ts           # Main types
│   │   │   └── enums.ts             # Enums
│   │   ├── app.vue                  # Root component
│   │   └── error.vue                # Error page
│   ├── assets/css/                  # Global styles
│   ├── nuxt.config.ts               # Nuxt configuration
│   ├── package.json                 # Dependencies
│   ├── tsconfig.json                # TypeScript config
│   └── claude.md                    # Nuxt-specific context
│
├── .idea/                           # IntelliJ IDEA config
├── README.md                        # Main project README
└── compose.yaml                     # Docker compose (if any)
```

---

## Development Workflow

### Running the Backend

1. **Prerequisites:**
   - Java 21+
   - Create `udgaard/src/main/resources/secure.properties` with API credentials:
     ```properties
     ovtlyr.cookies.token=XXX
     ovtlyr.cookies.userid=XXX
     ```
   - **Note:** AlphaVantage API key is in `application.properties` (get free key at https://www.alphavantage.co/support/#api-key)

2. **Database:**
   - **H2 Database** (file-based, no Docker required!)
   - Automatically created at: `~/.trading-app/database/trading`
   - Access H2 Console: http://localhost:8080/udgaard/h2-console
   - JDBC URL: `jdbc:h2:file:~/.trading-app/database/trading`
   - Username: `sa`, Password: (empty)

3. **Build and Run:**
   ```bash
   cd udgaard
   ./gradlew build
   java -jar build/libs/udgaard-0.0.1-SNAPSHOT.jar
   ```
   Or simply: `./gradlew bootRun`

4. **Health Check:**
   - Backend API: http://localhost:8080/udgaard/actuator/health
   - MCP Server: http://localhost:8080/udgaard/mcp/messages

### Running the Frontend

1. **Install dependencies:**
   ```bash
   cd asgaard
   npm install
   ```

2. **Run dev server:**
   ```bash
   npm run dev
   ```
   Runs on http://localhost:3000

### Running the Desktop App (Electron)

**Prerequisites:**
- Node.js 18+
- Backend JAR built (`cd udgaard && ./gradlew bootJar`)
- Linux: System libraries installed (libnss3, libatk, libgtk-3, etc.)

**Development Mode (Recommended):**
```bash
# Terminal 1: Start Nuxt dev server
cd asgaard
npm run dev

# Terminal 2: Start Electron (loads from http://localhost:3000)
cd ..
npm run dev
```

**Production Mode:**
```bash
# Build everything once
npm run build:all

# Start Electron (loads from built assets)
npm start
```

**Building Distributable:**
```bash
npm run dist          # Current platform
npm run dist:linux    # Linux AppImage + .deb
npm run dist:win      # Windows installer
npm run dist:mac      # macOS DMG
```

**Notes:**
- Dev mode opens DevTools automatically
- Backend runs as subprocess, starts automatically
- Waits up to 30s for backend health check
- Graceful shutdown cleans up all processes

**Linux System Dependencies:**
```bash
sudo apt install libatk1.0-0 libatk-bridge2.0-0 libgdk-pixbuf2.0-0 \
  libgtk-3-0 libgbm-dev libnss3-dev libxss-dev libasound2t64 \
  libxkbcommon0 libxcomposite1 libxdamage1 libxfixes3 libxrandr2 \
  libdrm2 libcups2
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
npm run typecheck  # TypeScript validation
npm run lint       # ESLint
```

### CI/CD Pipeline

The project uses GitHub Actions for automated testing and releases.

**Continuous Integration (`.github/workflows/ci.yml`):**
- **Triggers**: Push to main, pull requests
- **Jobs**:
  - Backend tests (Gradle test + JAR build)
  - Frontend tests (typecheck + lint + build)
  - Integration build (full app verification)
  - Code quality checks (Kotlin compilation)
- **Runtime**: ~5-8 minutes
- **View results**: `https://github.com/YOUR_USERNAME/trading/actions`

**Continuous Deployment (`.github/workflows/release.yml`):**
- **Triggers**: Version tags (e.g., `v1.0.0`)
- **Builds**: Windows, macOS, Linux installers
- **Runtime**: ~10-15 minutes per platform
- **Release**: Automatic GitHub Release with installers

**Quick Release:**
```bash
git tag v1.0.0
git push origin v1.0.0
```

See `release/CI_WORKFLOW.md` and `release/DEPLOYMENT.md` for details.

---

## Key Concepts

### Strategy DSL

The platform uses a Kotlin DSL for building trading strategies declaratively:

```kotlin
val myEntryStrategy = entryStrategy {
    buySignal()
    marketInUptrend()
    sectorInUptrend()
    priceAboveEma(20)
    marketHeatmapAbove(50)
}

val myExitStrategy = exitStrategy {
    stopLoss(0.5)  // 0.5 ATR
    priceBelowEma(10)
    heatmapThreshold()
}
```

See `StrategyDsl.kt` for available conditions.

### Strategy Registration

Add new strategies by simply annotating the class:

```kotlin
@RegisteredStrategy(name = "MyStrategy", type = StrategyType.ENTRY)
class MyEntryStrategy: EntryStrategy {
    override fun test(stock: Stock, quote: StockQuote): Boolean {
        // Strategy logic
    }
    override fun description() = "My custom entry strategy"
}
```

No need to update controllers or frontend - the strategy is automatically discovered!

### Stock Quote Data Structure

Each stock quote includes:
- **Price data**: open, close, high, low, volume (from AlphaVantage adjusted daily)
- **Technical indicators**: EMA (5/10/20/50/200 - calculated), ATR (from AlphaVantage), Donchian bands
- **Trend determination**: Uptrend when (EMA5 > EMA10 > EMA20) AND (Price > EMA50)
- **Sentiment**: Stock/sector/market heatmaps (0-100 scale from Ovtlyr)
- **Signals**: Buy/sell signals from Ovtlyr
- **Market context**: SPY data, market breadth, uptrend status
- **Sector context**: Sector breadth, uptrend counts

### Backtest Report

Backtest results include:
- Win/loss statistics (count, rate, average amounts)
- Edge (expected % gain per trade)
- Complete trade list with entry/exit dates and prices
- Profit/loss breakdown by stock
- Exit reason analysis
- Daily profit summary

### Portfolio Management

The portfolio system tracks live trades with comprehensive statistics:

**Portfolio Features:**
- Multiple portfolios with independent balances and currencies
- Initial balance and current balance tracking
- Real-time P/L calculation
- YTD and annualized returns
- Win rate and proven edge metrics

**Trade Types Supported:**
- **Stocks**: Standard equity positions with entry/exit prices
- **Leveraged ETFs**: ETF positions with leverage tracking
- **Options**:
  - Strike price and expiration date
  - Option type (CALL/PUT)
  - Contracts and multiplier (usually 100)
  - Intrinsic and extrinsic value tracking
  - Underlying symbol for signal-based trading

**Trade Lifecycle:**
1. **Open Trade**: Enter position with price, date, quantity, and strategies
2. **Edit Trade**: Update position details (entry price, quantity, strategies)
3. **Close Trade**: Exit position with exit price and date
4. **Delete Trade**: Remove trade from portfolio (if entered incorrectly)

**Statistics Calculated:**
- Total trades, open trades, closed trades
- Win/loss counts and percentages
- Average win and average loss (as percentages)
- Proven edge: (Win Rate × Avg Win) - (Loss Rate × |Avg Loss|)
- Total profit in currency and percentage
- Largest win and largest loss
- **Projected metrics**: What-if analysis if all open trades closed at current prices

**Equity Curve:**
- Cumulative balance over time
- Visual representation of portfolio growth
- Shows impact of each closed trade

**Options Example:**
```
Symbol: SPY250117C00585000  (Call option on SPY)
Underlying: SPY
Strike Price: $585
Expiration: 2025-01-17
Option Type: CALL
Contracts: 2
Multiplier: 100
Entry Price: $5.50 (per contract)
Position Value: $5.50 × 2 × 100 = $1,100
```

---

## Important Files to Reference

### Documentation
- `claude_thoughts/ALPHAVANTAGE_REFACTORING_SUMMARY.md` - AlphaVantage-primary architecture refactoring
- `claude_thoughts/DYNAMIC_STRATEGY_SYSTEM.md` - How the strategy system works
- `claude_thoughts/MCP_SERVER_README.md` - MCP server setup and usage
- `claude_thoughts/MARKET_REGIME_FILTER_IMPLEMENTATION.md` - Market filter details
- `claude_thoughts/PLAN_BETA_STRATEGY_README.md` - Plan Beta strategy docs
- `udgaard/README.MD` - Backend setup instructions
- `udgaard/claude.md` - Backend development guide (Kotlin/Spring Boot patterns)
- `asgaard/claude.md` - Frontend development guide (Nuxt/Vue patterns)
- `release/CI_WORKFLOW.md` - CI/CD pipeline and automated testing
- `release/DEPLOYMENT.md` - Release and deployment guide
- `release/RELEASE_QUICKSTART.md` - Quick release guide
- `electron/README.md` - Electron desktop app documentation
- Various implementation and performance reports in `claude_thoughts/`

### Configuration
- `udgaard/src/main/resources/application.properties` - Backend config
- `asgaard/nuxt.config.ts` - Frontend config
- `asgaard/package.json` - Frontend dependencies
- `udgaard/build.gradle` - Backend dependencies

### Core Code
- `udgaard/src/main/kotlin/com/skrymer/udgaard/service/StrategyRegistry.kt` - Strategy management
- `udgaard/src/main/kotlin/com/skrymer/udgaard/model/strategy/StrategyDsl.kt` - Strategy DSL
- `udgaard/src/main/kotlin/com/skrymer/udgaard/service/StockService.kt` - Backtesting logic
- `udgaard/src/main/kotlin/com/skrymer/udgaard/mcp/StockMcpTools.kt` - MCP tools
- `asgaard/app/pages/backtesting.vue` - Main backtesting UI

---

## Common Tasks

### Adding a New Entry Strategy
1. Create class implementing `EntryStrategy` (or `DetailedEntryStrategy` for condition diagnostics)
2. Add `@RegisteredStrategy(name = "StrategyName", type = StrategyType.ENTRY)`
3. Implement `test()` and `description()` methods
4. If using `DetailedEntryStrategy`, also implement `testWithDetails()` for UI diagnostics
5. Strategy automatically appears in UI!

### Adding a New Exit Strategy
1. Create class implementing `ExitStrategy`
2. Add `@RegisteredStrategy(name = "StrategyName", type = StrategyType.EXIT)`
3. Implement `match()`, `reason()`, and `description()` methods
4. Strategy automatically appears in UI!

### Running a Backtest via MCP
Claude can use the `runBacktest` tool:
```
Run a backtest on AAPL,MSFT,GOOGL from 2020-01-01 to 2024-01-01
using PlanAlpha entry and PlanMoney exit
```

### Querying Stock Data via MCP
Claude can use `getStockData` or `getMultipleStocksData`:
```
Get stock data for SPY from 2023-01-01 to 2024-01-01
```

---

## Environment & Configuration

### Backend Environment Variables
Configured in `application.properties`:
- `spring.datasource.url`: H2 database file location (`jdbc:h2:file:~/.trading-app/database/trading`)
- `spring.jpa.hibernate.ddl-auto`: Hibernate schema management (set to `update`)
- `spring.h2.console.enabled`: Enable H2 web console (set to `true` for development)
- `alphavantage.api.key`: AlphaVantage API key for stock data
- `alphavantage.api.baseUrl`: AlphaVantage API base URL
- `ovtlyr.cookies.token`: Ovtlyr API token (in secure.properties)
- `ovtlyr.cookies.userid`: Ovtlyr user ID (in secure.properties)
- `spring.cache.type`: Cache provider (set to `caffeine`)
- `spring.mvc.async.request-timeout`: Async request timeout (1800000ms for long backtests)

### Frontend Environment Variables
Access via `useRuntimeConfig()`:
- Prefix with `NUXT_PUBLIC_` for client-side access
- Configure backend URL if not using default localhost:8080

---

## Troubleshooting

### Backend Issues
- **Strategy not appearing**: Check `@RegisteredStrategy` annotation and package location
- **Database issues**:
  - H2 database created automatically at `~/.trading-app/database/trading`
  - Check H2 console at http://localhost:8080/udgaard/h2-console
  - Database file permissions: ensure `~/.trading-app/database/` is writable
  - Locked database: H2 allows concurrent connections with `AUTO_SERVER=TRUE`
- **Build failures**: Try `./gradlew clean build`
- **Missing secure.properties**: Create file with Ovtlyr credentials
- **AlphaVantage API errors**:
  - Check API key is valid in `application.properties`
  - Free tier has rate limits (5 requests/minute, 500/day)
  - API may return error responses for invalid symbols or exhausted quota

### Frontend Issues
- **Type errors**: Run `npm run typecheck` to identify issues
- **ESLint errors**: Run `npm run lint` and fix issues
- **Component not found**: Ensure it's in `components/` directory for auto-import
- **API connection**: Check that backend is running on port 8080

### MCP Issues
- **Claude can't connect**: Verify JAR path in MCP settings
- **No data returned**: Check H2 database has stock data (use H2 console)
- **Tool not found**: Rebuild backend JAR after adding new tools
- **MCP server not responding**: Check http://localhost:8080/udgaard/mcp/messages endpoint

### Electron Issues
- **"Backend JAR not found"**: Build backend first with `cd udgaard && ./gradlew bootJar`
- **"Backend failed to start within timeout"**:
  - Check if port 8080 is already in use
  - Increase `BACKEND_STARTUP_TIMEOUT` in `electron/main.js`
  - Check backend logs in Electron console
- **Frontend not loading (dev mode)**: Ensure Nuxt dev server is running on port 3000
- **Frontend not loading (production)**: Build frontend with `cd asgaard && npm run build`
- **Shared library errors (Linux)**: Install system dependencies:
  ```bash
  sudo apt install libatk1.0-0 libatk-bridge2.0-0 libgdk-pixbuf2.0-0 \
    libgtk-3-0 libgbm-dev libnss3-dev libxss-dev libasound2t64 \
    libxkbcommon0 libxcomposite1 libxdamage1 libxfixes3 libxrandr2 \
    libdrm2 libcups2
  ```
- **Port conflicts**: Change `BACKEND_PORT` in `electron/main.js` and update Nuxt proxy config
- **Memory issues**: Add JVM memory limits in spawn args: `-Xmx512m`
- **App won't start**: Check Electron console for errors, verify all dependencies installed

---

## Next Steps & Future Work

### Potential Improvements
1. **Strategy versioning**: Support multiple versions of strategies
2. **Parameter optimization**: Grid search for optimal strategy parameters
3. **Live trading integration**: Connect to broker APIs
4. **Enhanced visualization**: More chart types, heatmaps, correlations
5. **Portfolio backtesting**: Multi-stock portfolio with position sizing
6. **Machine learning**: Train models on historical patterns
7. **Alert system**: Real-time alerts for strategy signals
8. **Strategy sharing**: Community marketplace for strategies

### Known Limitations
- Backtest assumes perfect fills at close prices
- No slippage or commission modeling (yet)
- Limited to daily timeframe data
- Requires manual data refresh from Ovtlyr

---

## Team Conventions

### Code Style

**Kotlin Conventions:**
- Follow Kotlin coding conventions, use data classes for DTOs
- **Prefer Kotlin extension functions** over manual operations:
  - Use `mapNotNull` instead of `filter { it != null }.map { it!! }`
  - Use `filterNotNull()` to filter out nulls from collections
  - Use `firstOrNull`, `lastOrNull`, `maxByOrNull`, `minByOrNull` over manual null checks
  - Use `let`, `run`, `apply`, `also`, `with` appropriately for scoping
  - Use `?.let { }` for null-safe operations instead of if-null checks
- Prefer `when` expressions over if-else chains
- Use sealed classes for type hierarchies
- Leverage destructuring declarations where appropriate
- Use named arguments for clarity when calling functions with multiple parameters

**Vue/TypeScript:**
- Use Composition API with `<script setup lang="ts">`
- TypeScript strict mode enabled
- Prefer `computed` over reactive getters
- Use `ref` for primitives, `reactive` for objects

**General:**
- **No trailing commas**: ESLint configured to disallow
- **1TBS brace style**: One True Brace Style for consistency
- Descriptive variable and function names

### Git Workflow
- Main branch: `main`
- Feature branches: Create for significant features
- Commit messages: Descriptive, present tense
- Include generated Claude Code attribution in commits

### Pre-Commit Checklist

Before committing any changes, **always** verify the following:

#### 1. All Backend Tests Must Pass

Run the full test suite even if your changes seem unrelated:

```bash
cd udgaard
./gradlew test
```

**Important:** If any tests fail (even if they appear unrelated to your changes):
- ✅ **Investigate and fix them** - They may be revealing issues with your changes
- ✅ **Never commit with failing tests** - This indicates broken functionality
- ✅ **Check test output carefully** - Some failures may be timing-dependent or environment-specific

**Example:** If you changed a strategy and an "unrelated" backtest test fails, it likely means your strategy change broke the backtest logic.

#### 2. Backend Code Linting (ktlint)

Run ktlint to ensure code style compliance:

```bash
cd udgaard
./gradlew ktlintCheck
```

**Important:**
- ✅ **Must pass ktlint checks before committing**
- ✅ **Auto-fix most issues with `./gradlew ktlintFormat`**
- ✅ **Check the output for any remaining issues**

ktlint enforces:
- Consistent indentation (2 spaces)
- No unused imports
- Final newline in files
- No empty first lines in class bodies
- No multiple consecutive spaces

#### 3. Frontend TypeScript Validation

Run typecheck to ensure no type errors:

```bash
cd asgaard
npm run typecheck
```

**Important:**
- ✅ **Must have ZERO typecheck errors before committing**
- ✅ **Fix ALL type errors, including pre-existing ones if needed**
- ✅ **Never commit with any TypeScript errors**
- ✅ **Verify your changes don't break existing types**

If you encounter pre-existing errors in files you didn't modify, you must still fix them before committing. This ensures the codebase stays in a clean, error-free state.

#### 4. Update Relevant Documentation

Update documentation to reflect your changes:

**Do update:**
- ✅ `CLAUDE.md` - Main project documentation (architecture, setup, workflows)
- ✅ `udgaard/claude.md` - Backend-specific patterns and guidelines
- ✅ `asgaard/claude.md` - Frontend-specific patterns and components
- ✅ `README.md` - If user-facing features or setup changed

**Do NOT update:**
- ❌ `claude_thoughts/` directory - Historical documentation, don't modify

**Examples of when to update docs:**
- Added new API endpoint → Update relevant controller documentation
- Created new strategy → Update strategy development workflow
- Changed configuration → Update environment/configuration section
- Added new component pattern → Update frontend patterns guide

### Testing
- Write unit tests for strategy logic
- Test backtesting results against known expected values
- TypeScript strict mode enabled
- **Always run `npm run typecheck` in asgaard** before completing frontend changes
- ESLint validation before commits

---

## Resources

### Documentation
- [Nuxt Documentation](https://nuxt.com/docs)
- [NuxtUI Documentation](https://ui.nuxt.com)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Model Context Protocol](https://modelcontextprotocol.io/)

### Libraries
- [ApexCharts](https://apexcharts.com/)
- [Unovis](https://unovis.dev/)
- [date-fns](https://date-fns.org/)
- [Zod](https://zod.dev/)
- [VueUse](https://vueuse.org/)

---

## Recent Sessions

### Session: 2024-11-30 - CI/CD Pipeline Implementation

**CI/CD Workflow Added:**
- **Created `.github/workflows/ci.yml`** for automated testing
- **4 parallel jobs**:
  1. `backend-test`: Gradle tests + JAR build
  2. `frontend-test`: TypeScript typecheck + ESLint + build
  3. `integration-test`: Full application build verification
  4. `code-quality`: Kotlin compilation checks
- **Triggers**: Runs on push to main and all pull requests
- **Caching**: Gradle and npm dependencies cached for speed
- **Artifacts**: Test results uploaded on failure (7-day retention)
- **Total runtime**: ~5-8 minutes per run

**Documentation Created:**
- `release/CI_WORKFLOW.md` - Comprehensive CI workflow guide
- Explains each job, common failures, troubleshooting
- Includes branch protection setup instructions

**CI/CD Status:**
- ✅ Continuous Integration: Complete (automated testing)
- ✅ Continuous Deployment: Complete (tag-based releases)
- ⏳ Code Signing: Pending (need certificates)
- ⏳ Auto-Updates: Pending (future enhancement)

**Files Created:**
- `.github/workflows/ci.yml` - CI workflow
- `release/CI_WORKFLOW.md` - CI documentation

---

### Session: 2024-11-30 - Database Migration & Portfolio Management

**Major Changes:**
1. **Database Migration MongoDB → H2**:
   - Migrated from MongoDB (NoSQL) to H2 (SQL) with JPA/Hibernate
   - Removed Docker dependency - H2 uses file-based storage
   - Database auto-created at `~/.trading-app/database/trading`
   - Added JPA entities with proper relationships and indexes
   - H2 Console available for database inspection
   - Updated all repositories to use Spring Data JPA

2. **Portfolio Management System**:
   - Complete portfolio tracking with multi-portfolio support
   - Live trade management (open, edit, close, delete)
   - Options trading support with strike, expiration, contracts, multiplier
   - Real-time statistics: win rate, proven edge, P/L, YTD returns
   - Equity curve visualization
   - Projected metrics (what-if all open trades closed at current prices)
   - Frontend: Portfolio page with modals for all operations

3. **ETF Analysis**:
   - ETF entities with quotes and holdings
   - AlphaVantage integration for ETF profile data
   - ETF holdings tracking with weights and sectors
   - Frontend ETF stats page

4. **AlphaVantage Integration**:
   - AlphaVantage API client for stock data and ATR
   - Time series data (daily, intraday)
   - ETF profile data
   - Rate limit handling (5/min, 500/day)

5. **Market Breadth Refactor**:
   - Renamed `MarketBreadthService` to `BreadthService`
   - New JPA entities: `Breadth`, `BreadthQuote`, `BreadthSymbol`
   - BreadthController with dedicated endpoints
   - Frontend market breadth page

6. **New Controllers**:
   - `PortfolioController` - Portfolio and trade management
   - `BreadthController` - Market breadth API
   - `EtfController` - ETF data API
   - `MonteCarloController` - Simulations
   - `CacheController` - Cache management
   - `DataController` - Data import/export
   - Split `UdgaardController` into `BacktestController` and `StockController`

**Files Modified:**
- Backend: 50+ files (entities, repositories, services, controllers)
- Frontend: `portfolio.vue`, `market-breadth.vue`, `etf-stats.vue`, `stock-data.vue`
- Config: `application.properties` (H2, AlphaVantage), `build.gradle` (H2 dependency)
- Types: Updated type definitions for Portfolio, PortfolioTrade, ETF entities

**Performance & Quality:**
- JPA query optimization with proper indexing
- Caffeine caching for stock and breadth data
- Quote indexing for 10-100x backtest performance
- EMA-based trading conditions optimization
- Fixed circular reference JSON serialization
- Fixed StockQuote symbol persistence
- Added no-argument constructors for Hibernate

---

### Session: 2025-11-12 - Performance Optimization & Caching

**Problem**: Backtesting API timing out with many stocks (Headers Timeout Error in Nuxt)

**Solutions Implemented**:
1. **Frontend Timeout Fixes**:
   - Increased Nuxt fetch timeout to 10 minutes (600,000ms)
   - Added Nitro keep-alive configuration
   - Enhanced loading states with progress indicators
   - Added toast notifications for backtest start/complete/error
   - Performance warnings when selecting "All Stocks"

2. **Backend Caching** (Caffeine):
   - Added Spring Cache with Caffeine dependency
   - Created `CacheConfig.kt` for cache configuration
   - Cached `getStock()` and `getAllStocks()` methods
   - 30-minute TTL, 1000 max entries per cache
   - Verified **43.7% performance improvement** (71s → 40s)

3. **Documentation Created**:
   - `claude_thoughts/PERFORMANCE_IMPROVEMENTS.md` - Detailed timeout fix documentation
   - `claude_thoughts/CACHE_PERFORMANCE_VERIFICATION.md` - Cache performance test results

**Performance Results**:
- First backtest (cold cache): 71 seconds
- Second backtest (warm cache): 40 seconds
- Performance gain: 1.78x faster, 43.7% improvement
- Stock data fetch: 300x faster with cache (30s → 0.1s)

**Files Modified**:
- `asgaard/nuxt.config.ts`
- `asgaard/app/pages/backtesting.vue`
- `asgaard/app/components/backtesting/ConfigModal.vue`
- `udgaard/build.gradle`
- `udgaard/src/main/kotlin/com/skrymer/udgaard/config/CacheConfig.kt` (new)
- `udgaard/src/main/kotlin/com/skrymer/udgaard/service/StockService.kt`
- `udgaard/src/main/resources/application.properties`

---

### Session: 2025-12-03 - AlphaVantage Primary Architecture Refactoring

**Major Architectural Change:**
Refactored stock data pipeline from Ovtlyr-primary to AlphaVantage-primary architecture. This change provides adjusted prices (accounting for splits/dividends), volume data, and full control over technical indicator calculations.

**New Architecture Flow:**
```
AlphaVantage (PRIMARY)
├─ Adjusted OHLCV data (TIME_SERIES_DAILY_ADJUSTED)
├─ Volume data
└─ ATR (Average True Range)
    ↓
Calculate Technical Indicators
├─ EMAs (5, 10, 20, 50, 200)
├─ Donchian channels
└─ Trend determination (EMA alignment)
    ↓
Ovtlyr Enrichment
├─ Buy/sell signals
├─ Fear & Greed heatmaps
├─ Sector sentiment
└─ Market/sector breadth
    ↓
Stock Entity (saved to H2)
```

**Files Created:**
1. `AlphaVantageTimeSeriesDailyAdjusted.kt` - DTO for adjusted daily endpoint
2. `TechnicalIndicatorService.kt` - EMA, Donchian, and trend calculations
3. `OvtlyrEnrichmentService.kt` - Enriches AlphaVantage quotes with Ovtlyr data
4. `claude_thoughts/ALPHAVANTAGE_REFACTORING_SUMMARY.md` - Comprehensive refactoring documentation

**Files Modified:**
1. `AlphaVantageClient.kt` - Added getDailyAdjustedTimeSeries(), removed unadjusted method
2. `StockFactory.kt` - Changed interface to use AlphaVantage as primary input
3. `DefaultStockFactory.kt` - Complete rewrite: Calculate → ATR → Ovtlyr pipeline
4. `StockService.kt` - Updated fetchStock() to use new AlphaVantage-first flow

**Key Design Decisions:**
- **Adjusted prices**: Uses AlphaVantage adjusted close for accurate backtesting
- **Calculated EMAs**: We calculate EMAs ourselves (no SMA, EMA only per user request)
- **AlphaVantage ATR**: ATR comes from AlphaVantage API (not calculated)
- **Fail-fast**: Returns null if AlphaVantage or Ovtlyr data unavailable (no default values)
- **Order blocks**: Only calculated from volume data (not from Ovtlyr)
- **Trend logic**: Uptrend = (EMA5 > EMA10 > EMA20) AND (Price > EMA50)

**Benefits:**
- ✅ Adjusted prices for stock splits and dividends
- ✅ Volume data for all stocks
- ✅ Transparent technical indicator calculations
- ✅ Independence from Ovtlyr's stock universe
- ✅ Hybrid approach: best of both data sources

**API Usage:**
- 2 API calls per stock: TIME_SERIES_DAILY_ADJUSTED + ATR
- AlphaVantage Premium: 75 requests/minute
- ~9 minutes to refresh 335 stocks

**Testing:**
- ✅ Build successful
- ⏳ Manual testing pending (user updating API key to premium tier)

**Next Steps:**
- Implement rate limiting (deferred per user request)
- Test with real stock symbols after API key upgrade
- Monitor API usage and performance

---

## Documentation Structure

This project uses a three-level documentation approach:

1. **CLAUDE.md** (this file) - High-level project context, architecture, and recent work
2. **asgaard/claude.md** - Frontend-specific patterns, NuxtUI components, and Vue best practices
3. **udgaard/claude.md** - Backend-specific patterns, Kotlin idioms, and Spring Boot conventions

---

_Last Updated: 2025-12-03_
_This file helps Claude understand the project structure, architecture, recent work, and key decisions across conversations._
