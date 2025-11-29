# Trading Platform - Claude Context

This file provides comprehensive context for the Trading Platform project. For technology-specific guides, see:
- `asgaard_nuxt/claude.md` - Frontend (Nuxt/Vue) development guide
- `udgaard/claude.md` - Backend (Kotlin/Spring Boot) development guide

## Project Overview

This is a stock trading backtesting platform with a Kotlin/Spring Boot backend (Udgaard), a Nuxt.js frontend (Asgaard), and an Electron desktop wrapper. The platform enables users to backtest trading strategies using historical stock data with advanced technical indicators and market sentiment analysis.

**Key Capabilities:**
- Historical stock data analysis with technical indicators (EMA, ATR, Donchian channels)
- Dynamic strategy system with DSL-based strategy creation
- Multiple entry/exit strategy combinations
- Market and sector breadth analysis
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
  - Loads frontend from built assets in `asgaard_nuxt/.output/`
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
- Kotlin 1.9.25
- Spring Boot 3.5.0
- MongoDB for data storage
- Gradle build system
- Spring AI MCP Server for Claude integration

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
   - `MarketBreadthService.kt`: Market breadth calculations (SPY, QQQ, IWM)
   - `StrategyRegistry.kt`: Dynamic strategy registration and discovery
   - `DynamicStrategyBuilder.kt`: Runtime strategy creation from configurations

3. **MCP Server** (`src/main/kotlin/com/skrymer/udgaard/mcp/`)
   - `StockMcpTools.kt`: MCP tools for Claude AI integration
   - Tools: getStockData, getMultipleStocksData, getMarketBreadth, getStockSymbols, runBacktest
   - Enables Claude to perform custom backtesting and analysis

4. **Integration** (`src/main/kotlin/com/skrymer/udgaard/integration/ovtlyr/`)
   - Integration with Ovtlyr stock data provider
   - DTOs for stock quotes and information

**API Endpoints:**
- `POST /api/backtest` - Run backtest with strategy parameters
- `GET /api/strategies/available` - Get list of available strategies
- `GET /api/stock-symbols` - Get all available stock symbols
- `GET /api/market-breadth/{symbol}` - Get market breadth for SPY/QQQ/IWM

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

2. **Strategy Components** (`app/components/strategy/`)
   - Strategy builder and configuration components

3. **Pages** (`app/pages/`)
   - `index.vue`: Dashboard/home page
   - `backtesting.vue`: Main backtesting interface
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

## Recent Work & Changes

### Dynamic Strategy System Implementation
- **Removed hardcoded strategy lists** from controller
- **Added `@RegisteredStrategy` annotation** for automatic strategy discovery
- **Created `StrategyRegistry`** service for centralized strategy management
- **Integrated frontend** to fetch strategies dynamically from API
- See `DYNAMIC_STRATEGY_SYSTEM.md` for full documentation

### Removed Strategies (Consolidated into DSL/Composite)
Old individual strategy classes have been deleted in favor of DSL composition:
- `Ovtlyr9EntryStrategy` (now uses composite entry strategy)
- `DonkeyEntryStrategy`
- `MainExitStrategy` (now uses composite exit strategy)
- Individual exit strategies: HalfAtr, Heatmap, LessGreedy, MarketAndSectorReverse, PriceUnder10Ema, PriceUnder50Ema, SellSignal, TenEmaCross, WithinOrderBlock

### Current Active Strategies

**Entry Strategies:**
- `PlanAlphaEntryStrategy`: Plan Alpha entry rules
- `PlanEtfEntryStrategy`: ETF-focused entry strategy
- `PlanBetaEntryStrategy`: Beta variant entry strategy
- `SimpleBuySignalEntryStrategy`: Basic buy signal entry
- `CompositeEntryStrategy`: Combines multiple conditions using DSL

**Exit Strategies:**
- `PlanMoneyExitStrategy`: Money management exit rules
- `PlanAlphaExitStrategy`: Plan Alpha exit rules
- `PlanEtfExitStrategy`: ETF-focused exit strategy
- `CompositeExitStrategy`: Combines multiple exit conditions using DSL

**Market Filters:**
- `MarketRegimeFilter`: Filters trades based on market conditions

### MCP Server Integration
- **Added Spring AI MCP Server** for Claude integration
- **Tools exposed**: getStockData, getMultipleStocksData, getMarketBreadth, getStockSymbols, runBacktest
- **Enables Claude** to perform custom backtesting and strategy analysis
- See `MCP_SERVER_README.md` for full documentation

### Frontend Enhancements
- **Added ApexCharts** for interactive charting
- **Added Unovis** for advanced visualizations
- **Created backtesting components** for comprehensive results display
- **Added trade chart visualization** with entry/exit markers
- **Integrated dynamic strategy selection** from backend

### Data Model Updates
- **Added Donchian upper band** to StockQuote model
- **Added high/low values** for better chart rendering
- **Enhanced market breadth** with Donchian channels
- **Removed unused API endpoints** (customers, mails, members, notifications, trades)

### Desktop App Implementation
- **Added Electron wrapper** for desktop distribution
- **Implemented process management** for backend subprocess
- **Backend health check** with 30s timeout on startup
- **Security configuration** with context isolation and preload script
- **Build configuration** for Windows (NSIS), macOS (DMG), and Linux (AppImage, .deb)
- **Dev mode support** with hot reload from Nuxt dev server
- **Graceful shutdown** with process cleanup using tree-kill
- See `electron/README.md` for full documentation

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
│   ├── CACHE_PERFORMANCE_VERIFICATION.md
│   └── PERFORMANCE_IMPROVEMENTS.md
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
│   │   │   ├── UdgaardController.kt  # Main API controller
│   │   │   └── dto/                  # Request/response DTOs
│   │   ├── integration/              # External integrations
│   │   │   └── ovtlyr/              # Ovtlyr stock data provider
│   │   ├── mcp/                      # MCP server tools
│   │   │   └── StockMcpTools.kt     # Claude AI integration tools
│   │   ├── model/                    # Domain models
│   │   │   ├── BacktestReport.kt    # Backtest results
│   │   │   ├── Stock.kt             # Stock entity
│   │   │   ├── StockQuote.kt        # Price quote with indicators
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
│   │   │   ├── MarketBreadthService.kt
│   │   │   ├── StockService.kt
│   │   │   └── StrategyRegistry.kt       # Strategy management
│   │   └── UdgaardApplication.kt    # Main application
│   ├── src/main/resources/
│   │   ├── application.properties    # Config (MongoDB, etc.)
│   │   └── secure.properties        # Ovtlyr credentials (not in git)
│   ├── src/test/kotlin/             # Unit tests
│   ├── build.gradle                 # Gradle build config
│   ├── compose.yaml                 # Docker compose for MongoDB
│   └── *.md                         # Documentation files
│
├── asgaard_nuxt/                    # Frontend (Nuxt.js)
│   ├── app/
│   │   ├── components/              # Vue components
│   │   │   ├── backtesting/         # Backtesting UI components
│   │   │   ├── charts/              # Chart components
│   │   │   └── strategy/            # Strategy builder components
│   │   ├── layouts/
│   │   │   └── default.vue          # Main layout with sidebar
│   │   ├── pages/                   # File-based routing
│   │   │   ├── index.vue            # Dashboard
│   │   │   ├── backtesting.vue      # Backtesting page
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
   - Docker (for MongoDB)
   - Java 21+
   - Create `udgaard/src/main/resources/secure.properties` with Ovtlyr credentials:
     ```
     ovtlyr.cookies.token=XXX
     ovtlyr.cookies.userid=XXX
     ```

2. **Start MongoDB:**
   ```bash
   cd udgaard
   docker compose up -d
   ```

3. **Build and Run:**
   ```bash
   ./gradlew build
   java -jar build/libs/udgaard-0.0.1-SNAPSHOT.jar
   ```
   Or simply: `./gradlew bootRun`

### Running the Frontend

1. **Install dependencies:**
   ```bash
   cd asgaard_nuxt
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
cd asgaard_nuxt
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
cd asgaard_nuxt
npm run typecheck  # TypeScript validation
npm run lint       # ESLint
```

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
- **Price data**: open, close, high, low
- **Technical indicators**: EMA (5/10/20/50), ATR, Donchian bands
- **Sentiment**: Stock/sector/market heatmaps (0-100 scale)
- **Signals**: Buy/sell signals, trend direction
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

---

## Important Files to Reference

### Documentation
- `udgaard/DYNAMIC_STRATEGY_SYSTEM.md` - How the strategy system works
- `udgaard/MCP_SERVER_README.md` - MCP server setup and usage
- `udgaard/README.MD` - Backend setup instructions
- `asgaard_nuxt/claude.md` - Frontend development guide
- `udgaard/MARKET_REGIME_FILTER_IMPLEMENTATION.md` - Market filter details
- `udgaard/PLAN_BETA_STRATEGY_README.md` - Plan Beta strategy docs
- Various performance reports in `udgaard/*.md`

### Configuration
- `udgaard/src/main/resources/application.properties` - Backend config
- `asgaard_nuxt/nuxt.config.ts` - Frontend config
- `asgaard_nuxt/package.json` - Frontend dependencies
- `udgaard/build.gradle` - Backend dependencies

### Core Code
- `udgaard/src/main/kotlin/com/skrymer/udgaard/service/StrategyRegistry.kt` - Strategy management
- `udgaard/src/main/kotlin/com/skrymer/udgaard/model/strategy/StrategyDsl.kt` - Strategy DSL
- `udgaard/src/main/kotlin/com/skrymer/udgaard/service/StockService.kt` - Backtesting logic
- `udgaard/src/main/kotlin/com/skrymer/udgaard/mcp/StockMcpTools.kt` - MCP tools
- `asgaard_nuxt/app/pages/backtesting.vue` - Main backtesting UI

---

## Recent Git Changes

**Staged for commit:**
- New `.idea/` IntelliJ IDEA project files
- Frontend updates to backtesting pages and components
- Type definitions and configuration updates
- Removed old API endpoints (customers, mails, members, notifications, trades)
- Backend updates to strategy system and services
- Application properties updates

**New untracked files:**
- `.claude/` directory with this context file
- New backtesting components (ConfigModal, EquityCurve, SectorAnalysis, TradeChart, TradeDetailsModal)
- Chart components and strategy components
- Various documentation files (MD reports)
- MCP server implementation
- New strategy files (Composite, MarketRegimeFilter, PlanBeta, etc.)
- Dynamic strategy system files

**Deleted files:**
- Old strategy implementations (consolidated into DSL/Composite system)
- Unused API endpoints
- Legacy logic evaluator files

---

## Common Tasks

### Adding a New Entry Strategy
1. Create class implementing `EntryStrategy`
2. Add `@RegisteredStrategy(name = "StrategyName", type = StrategyType.ENTRY)`
3. Implement `test()` and `description()` methods
4. Strategy automatically appears in UI!

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

## Technologies & Libraries

### Backend
- **Spring Boot 3.5.0**: Web framework
- **Kotlin 1.9.25**: Programming language
- **MongoDB**: Database for stock quotes and market data
- **Spring AI MCP**: Model Context Protocol server
- **FastCSV**: CSV parsing
- **Jackson**: JSON serialization

### Frontend
- **Nuxt 4**: Vue.js framework with SSR
- **NuxtUI 4**: Component library built on Tailwind
- **TypeScript**: Type-safe JavaScript
- **ApexCharts**: Interactive charts
- **Unovis**: Advanced data visualization
- **date-fns**: Date manipulation
- **Zod**: Runtime validation
- **VueUse**: Vue composition utilities

### Desktop
- **Electron 28**: Desktop app framework
- **electron-builder**: Build and package for distribution
- **tree-kill**: Cross-platform process management
- **Node.js**: Main process runtime

---

## Environment & Configuration

### Backend Environment Variables
Configured in `application.properties`:
- `spring.data.mongodb.uri`: MongoDB connection string
- `ovtlyr.cookies.token`: Ovtlyr API token (in secure.properties)
- `ovtlyr.cookies.userid`: Ovtlyr user ID (in secure.properties)

### Frontend Environment Variables
Access via `useRuntimeConfig()`:
- Prefix with `NUXT_PUBLIC_` for client-side access
- Configure backend URL if not using default localhost:8080

---

## Troubleshooting

### Backend Issues
- **Strategy not appearing**: Check `@RegisteredStrategy` annotation and package location
- **MongoDB connection**: Verify Docker container is running (`docker compose up -d`)
- **Build failures**: Try `./gradlew clean build`
- **Missing secure.properties**: Create file with Ovtlyr credentials

### Frontend Issues
- **Type errors**: Run `npm run typecheck` to identify issues
- **ESLint errors**: Run `npm run lint` and fix issues
- **Component not found**: Ensure it's in `components/` directory for auto-import
- **API connection**: Check that backend is running on port 8080

### MCP Issues
- **Claude can't connect**: Verify JAR path in MCP settings
- **No data returned**: Check MongoDB has stock data
- **Tool not found**: Rebuild backend JAR after adding new tools

### Electron Issues
- **"Backend JAR not found"**: Build backend first with `cd udgaard && ./gradlew bootJar`
- **"Backend failed to start within timeout"**:
  - Check if port 8080 is already in use
  - Increase `BACKEND_STARTUP_TIMEOUT` in `electron/main.js`
  - Check backend logs in Electron console
- **Frontend not loading (dev mode)**: Ensure Nuxt dev server is running on port 3000
- **Frontend not loading (production)**: Build frontend with `cd asgaard_nuxt && npm run build`
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

### Testing
- Write unit tests for strategy logic
- Test backtesting results against known expected values
- TypeScript strict mode enabled
- **Always run `npm run typecheck` in asgaard_nuxt** before completing frontend changes
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
- `asgaard_nuxt/nuxt.config.ts`
- `asgaard_nuxt/app/pages/backtesting.vue`
- `asgaard_nuxt/app/components/backtesting/ConfigModal.vue`
- `udgaard/build.gradle`
- `udgaard/src/main/kotlin/com/skrymer/udgaard/config/CacheConfig.kt` (new)
- `udgaard/src/main/kotlin/com/skrymer/udgaard/service/StockService.kt`
- `udgaard/src/main/resources/application.properties`

---

## Documentation Structure

This project uses a three-level documentation approach:

1. **CLAUDE.md** (this file) - High-level project context, architecture, and recent work
2. **asgaard_nuxt/claude.md** - Frontend-specific patterns, NuxtUI components, and Vue best practices
3. **udgaard/claude.md** - Backend-specific patterns, Kotlin idioms, and Spring Boot conventions

---

_Last Updated: 2025-11-28_
_This file helps Claude understand the project structure, architecture, recent work, and key decisions across conversations._
