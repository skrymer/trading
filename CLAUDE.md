# Trading Platform - Claude Context

This file provides comprehensive context for the Trading Platform project.

## Technology-Specific Context Files

**IMPORTANT:** When working on specific parts of the codebase, read the relevant technology-specific guide:

- **Frontend work** (Vue/Nuxt/UI components): Read `asgaard/claude.md`
  - Includes NuxtUI component patterns, Vue composition API examples, form validation, styling conventions

- **Backend work** (Kotlin/Spring Boot/Strategies): Read `udgaard/claude.md`
  - Includes strategy development patterns, backtesting engine details, service patterns, testing guidelines

## Project Overview

This is a stock trading backtesting platform with a Kotlin/Spring Boot backend (Udgaard) and a Nuxt.js frontend (Asgaard). The platform enables users to backtest trading strategies using historical stock data with advanced technical indicators and market sentiment analysis.

**Key Capabilities:**
- Historical stock data analysis with technical indicators (EMA, ATR, Donchian channels)
- Dynamic strategy system with DSL-based strategy creation
- Multiple entry/exit strategy combinations
- Market and sector breadth analysis
- Portfolio management with live trade tracking (stocks and options)
- Monte Carlo simulations for strategy validation
- MCP (Model Context Protocol) server for Claude AI integration
- Real-time backtesting with comprehensive performance metrics

---

## Architecture

### Backend: Udgaard (Kotlin/Spring Boot)

**Tech Stack:**
- Kotlin 2.3.0, Spring Boot 3.5.0
- H2 2.2.224 Database (server mode via TCP)
- jOOQ 3.19.23 for type-safe SQL queries
- Flyway for database migrations (via net.ltgt.flyway plugin)
- Gradle 9.1.0 build system
- Spring AI MCP Server for Claude integration
- ktlint 1.5.0 + Detekt 2.0.0-alpha.2 for code quality
- **AlphaVantage API as PRIMARY data source** (adjusted OHLCV, volume, ATR)
- Ovtlyr API for enrichment (sector sentiment, market breadth)

**Key Components (modularized into `backtesting/`, `data/`, `portfolio/` packages):**

1. **Backtesting** (`backtesting/`)
   - `BacktestService.kt`: Core backtesting engine
   - `StrategyRegistry.kt`: Dynamic strategy discovery via `@RegisteredStrategy`
   - `DynamicStrategyBuilder.kt`: Runtime strategy creation from API config
   - `StrategySignalService.kt`: Signal evaluation for individual stocks
   - `MonteCarloService.kt`: Monte Carlo simulations
   - Strategies: PlanAlpha, PlanMV, PlanQ, OvtlyrPlanEtf, ProjectX
   - DSL-based strategy builder (`StrategyDsl.kt`)
   - 22 entry conditions, 12 exit conditions

2. **Data** (`data/`)
   - `StockService.kt`: Stock data management
   - `StockIngestionService.kt`: Bulk stock data ingestion from AlphaVantage
   - `TechnicalIndicatorService.kt`: EMAs, Donchian channels, ATR, trend determination
   - `MarketBreadthService.kt` / `SectorBreadthService.kt`: Market & sector breadth
   - `OrderBlockCalculator.kt`: Order block detection via ROC momentum
   - `SymbolService.kt`: Stock symbol management (DB-backed with caching)

3. **Portfolio** (`portfolio/`)
   - `PortfolioService.kt`: Portfolio and position management
   - IBKR integration for broker sync
   - Options data via AlphaVantage

4. **MCP Server** (`mcp/`)
   - `StockMcpTools.kt`: Tools for Claude AI integration
   - Tools: getStockData, getMultipleStocksData, getMarketBreadth, getStockSymbols, runBacktest

5. **Integration** (`data/integration/`)
   - **AlphaVantage**: PRIMARY data source - Adjusted daily OHLCV, ATR
   - **Ovtlyr**: Enrichment - Sector sentiment, market breadth

**API Endpoints:**

**Backtesting:** `POST /api/backtest`, `GET /api/strategies/available`

**Stocks:** `GET /api/stocks`, `GET /api/stocks/{symbol}`, `POST /api/stocks/refresh`, `GET /api/stock-symbols`

**Portfolio:** `GET/POST /api/portfolio`, `GET/DELETE /api/portfolio/{id}`, `GET /api/portfolio/{id}/stats`, `GET/POST /api/portfolio/{id}/trades`, `PUT/DELETE /api/portfolio/{id}/trades/{tradeId}`, `PUT /api/portfolio/{id}/trades/{tradeId}/close`, `GET /api/portfolio/{id}/equity-curve`

**Market Breadth:** `GET /api/breadth`, `GET /api/breadth/{symbol}`, `POST /api/breadth/refresh`

**Other:** `POST /api/monte-carlo/run`, `POST /api/cache/evict`, `POST /api/cache/evict-all`, `POST /api/data/import`

### Frontend: Asgaard (Nuxt.js)

**Tech Stack:** Nuxt 4.1.2, NuxtUI 4.0.1, TypeScript 5.9.3, Vue 3, Tailwind CSS, ApexCharts 5.3.5, Unovis 1.6.1, Lightweight Charts 5.0.9, date-fns 4.1.0, Zod 4.1.11, pnpm 10.24.0

**Key Components (50 Vue components):**
- **Backtesting** (`components/backtesting/`): Cards, ConfigModal, EquityCurve, SectorAnalysis, StockPerformance, ATRDrawdownStats, ExcursionAnalysis, ExitReasonAnalysis, MonteCarloResults, TradeChart, TradeDetailsModal
- **Portfolio** (`components/portfolio/`): CreateModal, PositionDetailsModal, ClosePositionModal, DeleteModal, EquityCurve, OpenTradeChart, OptionTradeChart, SyncPortfolioModal, RollChainModal
- **Charts** (`components/charts/`): BarChart, DonutChart, HistogramChart, LineChart, ScatterChart, StockChart, StrategySignalsTable
- **Data Management** (`components/data-management/`): DatabaseStatsCards, RefreshControlsCard, BreadthRefreshCard, RateLimitCard
- **Strategy** (`components/strategy/`): StrategyBuilder, StrategySelector, ConditionCard
- **Pages**: index, backtesting, portfolio, stock-data, data-manager, app-metrics, settings, test-chart

**Type Definitions:** `app/types/index.d.ts`, `app/types/enums.ts`

**Patterns:** Auto-imports, file-based routing, TypeScript strict mode, Composition API `<script setup>`, ESLint (no trailing commas, 1tbs brace style)

---

## Project Structure

```
trading/
├── CLAUDE.md                         # Project-wide context
├── .claude/                          # Claude configuration (commands, skills, settings)
├── claude_thoughts/                  # Documentation created by Claude
├── udgaard/                          # Backend (Kotlin/Spring Boot)
│   ├── src/main/kotlin/com/skrymer/udgaard/
│   │   ├── backtesting/              # Backtesting domain
│   │   │   ├── controller/           # BacktestController, MonteCarloController
│   │   │   ├── model/                # BacktestReport, Trade, BacktestContext
│   │   │   ├── service/              # BacktestService, StrategyRegistry, MonteCarloService
│   │   │   └── strategy/             # Strategies, DSL, conditions, rankers
│   │   ├── data/                     # Data domain
│   │   │   ├── controller/           # StockController, BreadthController, DataManagementController
│   │   │   ├── integration/          # AlphaVantage, Ovtlyr clients
│   │   │   ├── model/                # Stock, StockQuote, OrderBlock, MarketBreadth
│   │   │   ├── repository/           # StockJooqRepository, SymbolJooqRepository
│   │   │   └── service/              # StockService, TechnicalIndicatorService, OrderBlockCalculator
│   │   ├── portfolio/                # Portfolio domain
│   │   │   ├── controller/           # PortfolioController
│   │   │   ├── integration/          # IBKR, AlphaVantage options
│   │   │   ├── model/                # Portfolio, Position, Execution
│   │   │   ├── repository/           # PositionJooqRepository
│   │   │   └── service/              # PortfolioService
│   │   ├── controller/               # Shared controllers (Cache, Settings)
│   │   ├── mcp/                      # MCP server tools
│   │   └── config/                   # Configuration classes
│   ├── src/main/resources/           # Config (application.properties, secure.properties)
│   ├── src/test/kotlin/              # Unit tests (mirrors main structure)
│   ├── build.gradle                  # Gradle build config
│   ├── detekt.yml                    # Detekt configuration
│   └── detekt-baseline.xml           # Detekt baseline for existing issues
├── asgaard/                          # Frontend (Nuxt.js)
│   ├── app/
│   │   ├── components/               # Vue components (backtesting, portfolio, charts, strategy, data-management)
│   │   ├── layouts/                  # Layouts (default.vue)
│   │   ├── pages/                    # File-based routing (8 pages)
│   │   ├── plugins/                  # Nuxt plugins
│   │   ├── types/                    # TypeScript definitions
│   │   ├── app.vue                   # Root component
│   │   └── error.vue                 # Error page
│   ├── nuxt.config.ts                # Nuxt configuration
│   ├── package.json                  # Dependencies
│   └── claude.md                     # Nuxt-specific context
└── README.md                         # Main project README
```

---

## Development Workflow

### Running the Backend

**Prerequisites:**
- Java 21+
- Gradle 9.1.0 (included via wrapper)
- Create `udgaard/src/main/resources/secure.properties`:
  ```properties
  ovtlyr.cookies.token=XXX
  ovtlyr.cookies.userid=XXX
  ```
- AlphaVantage API key in `application.properties` (free key: https://www.alphavantage.co/support/#api-key)

**Database:**
- H2 2.2.224 Database (server mode via TCP, no Docker required)
- Location: `~/.trading-app/database/trading`
- H2 Console: http://localhost:8080/udgaard/h2-console
- JDBC URL: `jdbc:h2:tcp://localhost:9092/trading`, Username: `sa`, Password: (empty)

**First-Time Setup:**
```bash
cd udgaard

# Terminal 1: Start H2 database server
./gradlew startH2Server

# Terminal 2: Initialize database and build
./gradlew initDatabase  # Runs Flyway migrations
./gradlew build         # Generates jOOQ code and builds project
./gradlew bootRun       # Start application
```

**Regular Development:**
```bash
# Terminal 1: Start H2 server (if not already running)
./gradlew startH2Server

# Terminal 2: Run application
./gradlew bootRun
```

**Health Check:** http://localhost:8080/udgaard/actuator/health

### Running the Frontend

```bash
cd asgaard
pnpm install
pnpm dev  # Runs on http://localhost:3000
```

### Running Tests

**Backend:** `cd udgaard && ./gradlew test`

**Code Quality:** `cd udgaard && ./gradlew ktlintCheck && ./gradlew detekt`

**Frontend:** `cd asgaard && pnpm typecheck && pnpm lint`

### CI/CD Pipeline

**Continuous Integration (`.github/workflows/ci.yml`):**
- Triggers: Push to main, pull requests
- Jobs: Backend tests, Frontend tests, Integration build, Code quality
- Runtime: ~5-8 minutes

See `release/CI_WORKFLOW.md` for details.

---

## Key Concepts

### Strategy DSL

Build trading strategies declaratively:

```kotlin
val myEntryStrategy = entryStrategy {
    uptrend()
    priceAbove(20)
    marketUptrend()
    sectorUptrend()
    volumeAboveAverage(1.3, 20)
    minimumPrice(10.0)
}

val myExitStrategy = exitStrategy {
    stopLoss(2.0)          // 2.0 ATR
    trailingStopLoss(2.7)  // 2.7 ATR trailing
    priceBelowEma(10)
    marketAndSectorDowntrend()
}
```

### Strategy Registration

Strategies are auto-discovered via annotation:

```kotlin
@RegisteredStrategy(name = "MyStrategy", type = StrategyType.ENTRY)
class MyEntryStrategy: EntryStrategy {
    override fun test(stock: Stock, quote: StockQuote): Boolean { /* logic */ }
    override fun description() = "My custom entry strategy"
}
```

### Stock Quote Data Structure

Each quote includes:
- **Price data**: open, close, high, low, volume (AlphaVantage adjusted daily)
- **Technical indicators**: EMA (5/10/20/50/100/200 calculated), ATR (AlphaVantage), Donchian bands, ADX
- **Trend determination**: Uptrend when (EMA5 > EMA10 > EMA20) AND (Price > EMA50)
- **Order blocks**: Bullish/bearish order blocks via ROC momentum detection
- **Context**: Market/sector breadth, uptrend status, earnings dates

### Backtest Report

Results include:
- Win/loss statistics (count, rate, averages)
- Edge (expected % gain per trade)
- Trade list with entry/exit dates and prices
- P/L breakdown by stock
- Exit reason analysis
- Daily profit summary

### Portfolio Management

**Portfolio Features:** Multiple portfolios, independent balances/currencies, real-time P/L, YTD/annualized returns, win rate, proven edge

**Trade Types:** Stocks, Leveraged ETFs, Options (strike, expiration, type, contracts, multiplier)

**Trade Lifecycle:** Open → Edit → Close → Delete

**Statistics:** Total/open/closed trades, win/loss counts/%, average win/loss, proven edge, total profit, largest win/loss, projected metrics

**Equity Curve:** Cumulative balance visualization showing portfolio growth

---

## Important Files to Reference

### Documentation
- `claude_thoughts/SESSIONS_HISTORY.md` - Historical session notes and major changes
- `claude_thoughts/ALPHAVANTAGE_REFACTORING_SUMMARY.md` - AlphaVantage-primary architecture
- `claude_thoughts/DYNAMIC_STRATEGY_SYSTEM.md` - Strategy system details
- `claude_thoughts/MCP_SERVER_README.md` - MCP server setup
- `udgaard/claude.md` - Backend development guide
- `asgaard/claude.md` - Frontend development guide

### Configuration
- `udgaard/src/main/resources/application.properties` - Backend config
- `udgaard/detekt.yml` - Detekt static analysis config
- `asgaard/nuxt.config.ts` - Frontend config

### Core Code
- `udgaard/src/main/kotlin/com/skrymer/udgaard/backtesting/service/StrategyRegistry.kt` - Strategy management
- `udgaard/src/main/kotlin/com/skrymer/udgaard/backtesting/strategy/StrategyDsl.kt` - Strategy DSL
- `udgaard/src/main/kotlin/com/skrymer/udgaard/backtesting/service/BacktestService.kt` - Backtesting engine
- `udgaard/src/main/kotlin/com/skrymer/udgaard/data/service/StockService.kt` - Stock data management
- `asgaard/app/pages/backtesting.vue` - Main backtesting UI

---

## Common Tasks

### Adding a New Entry Strategy
1. Create class implementing `EntryStrategy` (or `DetailedEntryStrategy` for diagnostics)
2. Add `@RegisteredStrategy(name = "StrategyName", type = StrategyType.ENTRY)`
3. Implement `test()` and `description()` methods
4. Strategy automatically appears in UI

### Adding a New Exit Strategy
1. Create class implementing `ExitStrategy`
2. Add `@RegisteredStrategy(name = "StrategyName", type = StrategyType.EXIT)`
3. Implement `match()`, `reason()`, and `description()` methods
4. Strategy automatically appears in UI

### Running a Backtest via MCP
Claude can use the `runBacktest` tool to execute backtests programmatically.

### Querying Stock Data via MCP
Claude can use `getStockData` or `getMultipleStocksData` to retrieve historical data.

---

## Environment & Configuration

### Backend Environment Variables
Configured in `application.properties`:
- `spring.datasource.url`: H2 database location (server mode: `jdbc:h2:tcp://localhost:9092/trading`)
- `spring.h2.console.enabled`: H2 web console (set to `true` for dev)
- `alphavantage.api.key`: AlphaVantage API key (configured via Settings UI or secure.properties)
- `ovtlyr.cookies.token/userid`: Ovtlyr credentials (configured via Settings UI or secure.properties)
- `spring.cache.type`: Cache provider (set to `caffeine`)
- `spring.mvc.async.request-timeout`: Async timeout (1800000ms for long backtests)

### Frontend Environment Variables
Access via `useRuntimeConfig()`. Prefix with `NUXT_PUBLIC_` for client-side access.

---

## Troubleshooting

### Backend Issues
- **Strategy not appearing**: Check `@RegisteredStrategy` annotation
- **Database connection errors**: Ensure H2 server is running (`./gradlew startH2Server`)
- **jOOQ code generation fails**: H2 server must be running before build
- **Database issues**: Check H2 console at http://localhost:8080/udgaard/h2-console
- **Build failures**: Ensure H2 server is running, then try `./gradlew clean build`
- **AlphaVantage API errors**: Check API key, rate limits (5/min, 500/day)

### Frontend Issues
- **Type errors**: Run `pnpm typecheck`
- **ESLint errors**: Run `pnpm lint`
- **API connection**: Verify backend on port 8080

### MCP Issues
- **Claude can't connect**: Verify JAR path in MCP settings
- **No data**: Check H2 database has stock data
- **Tool not found**: Rebuild backend JAR

---

## Next Steps & Future Work

**Potential Improvements:**
Strategy versioning, parameter optimization, live trading integration, enhanced visualization, portfolio backtesting, machine learning, alert system, strategy sharing

**Known Limitations:**
Perfect fills assumed, no slippage/commission modeling, daily timeframe only

---

## Team Conventions

### Code Style

**Kotlin:** Follow Kotlin conventions, prefer extension functions (`mapNotNull`, `filterNotNull`, `firstOrNull`, etc.), use `when` over if-else chains, sealed classes for type hierarchies

**Vue/TypeScript:** Composition API with `<script setup lang="ts">`, TypeScript strict mode, prefer `computed` over reactive getters

**General:** No trailing commas, 1TBS brace style, descriptive names

### Git Workflow
- Main branch: `main`
- Feature branches for significant features
- Descriptive commit messages (present tense)
- Include Claude Code attribution in commits

### Pre-Commit Checklist

**ALWAYS run `/pre-commit` before committing.** This skill runs all 5 code quality checks (backend tests, ktlint, detekt, frontend lint, frontend typecheck) and verifies CLAUDE.md files are up to date. All checks must pass before committing. Do NOT modify `claude_thoughts/`.

### Testing
- Write unit tests for strategy logic
- Test backtesting results against known values

---

## Resources

**Documentation:** [Nuxt](https://nuxt.com/docs), [NuxtUI](https://ui.nuxt.com), [Spring Boot](https://spring.io/projects/spring-boot), [Kotlin](https://kotlinlang.org/docs/home.html), [Model Context Protocol](https://modelcontextprotocol.io/)

**Libraries:** [ApexCharts](https://apexcharts.com/), [Unovis](https://unovis.dev/), [date-fns](https://date-fns.org/), [Zod](https://zod.dev/), [VueUse](https://vueuse.org/)

---

## Recent Work

See `claude_thoughts/SESSIONS_HISTORY.md` for detailed session history.

**Latest:** Ovtlyr dependency removal & code quality (2026-02) - Removed direct Ovtlyr data dependencies (buy/sell signals, heatmaps). Added Detekt 2.0.0-alpha.2 static analysis. Modularized backend into backtesting/data/portfolio packages. Consolidated DB migrations. Added market/sector breadth from dedicated tables.

---

## Documentation Structure

This project uses a three-level documentation approach:

1. **CLAUDE.md** (this file) - High-level project context, architecture, and recent work
2. **asgaard/claude.md** - Frontend-specific patterns, NuxtUI components, and Vue best practices
3. **udgaard/claude.md** - Backend-specific patterns, Kotlin idioms, and Spring Boot conventions

---

_Last Updated: 2026-02-17_
_This file helps Claude understand the project structure, architecture, recent work, and key decisions across conversations._
