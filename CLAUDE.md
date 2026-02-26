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
- PostgreSQL 17 (Docker Compose for local dev)
- jOOQ 3.19.23 for type-safe SQL queries
- Flyway for database migrations (via net.ltgt.flyway plugin)
- Gradle 9.1.0 build system
- Spring AI MCP Server for Claude integration
- ktlint 1.5.0 + Detekt 2.0.0-alpha.2 for code quality
- **Midgaard** (standalone reference data service on port 8081) for OHLCV data with pre-computed indicators (ATR, ADX, EMAs, Donchian)
- **AlphaVantage API** for earnings and company overview (used by Midgaard for initial data load)
- Ovtlyr API (legacy, being removed — breadth data now computed from DB tables)

**Key Components (modularized into `backtesting/`, `data/`, `portfolio/`, `scanner/` packages):**

1. **Backtesting** (`backtesting/`)
   - `BacktestService.kt`: Core backtesting engine
   - `StrategyRegistry.kt`: Dynamic strategy discovery via `@RegisteredStrategy`
   - `DynamicStrategyBuilder.kt`: Runtime strategy creation from API config
   - `StrategySignalService.kt`: Signal evaluation for individual stocks
   - `MonteCarloService.kt`: Monte Carlo simulations
   - `PositionSizingService.kt`: Position sizing calculations
   - `BacktestResultStore.kt`: In-memory store for backtest results
   - DSL-based strategy builder (`StrategyDsl.kt`)
   - Strategies and conditions are discoverable via MCP tools (`getAvailableStrategies`, `getAvailableConditions`)

2. **Data** (`data/`)
   - `StockService.kt`: Stock data management
   - `StockIngestionService.kt`: Bulk stock data ingestion from Midgaard
   - `TechnicalIndicatorService.kt`: EMAs, Donchian channels, ATR, trend determination
   - `MarketBreadthService.kt` / `SectorBreadthService.kt`: Market & sector breadth
   - `OrderBlockCalculator.kt`: Order block detection via ROC momentum
   - `SymbolService.kt`: Stock symbol management (DB-backed with caching)

3. **Portfolio** (`portfolio/`)
   - `PortfolioService.kt`: Portfolio management
   - `PositionService.kt`: Position lifecycle management
   - `BrokerIntegrationService.kt`: Broker sync orchestration
   - `OptionPriceService.kt`: Options pricing data
   - `UnrealizedPnlService.kt`: Real-time P/L calculations
   - IBKR integration via broker adapter pattern (`broker/`, `ibkr/`)
   - Options data via AlphaVantage

4. **Scanner** (`scanner/`)
   - `ScannerService.kt`: Scan for entry signals, check exits, CRUD trades, roll trades
   - `ScannerController.kt`: REST API for scanner operations
   - `ScannerTradeJooqRepository.kt`: jOOQ persistence for scanner trades
   - Lightweight trade tracking separate from portfolio positions
   - Uses `StrategyRegistry` for predefined strategy lookup

5. **MCP Server** (`mcp/`)
   - `StockMcpTools.kt`: Tools for Claude AI integration
   - Tools: getStockData, getMultipleStocksData, getMarketBreadth, getStockSymbols, runBacktest

6. **Integration** (`data/integration/`)
   - **Midgaard**: OHLCV data with pre-computed indicators (ATR, ADX, EMAs, Donchian) via REST client
   - **AlphaVantage**: Earnings, company overview (used by Midgaard; AlphaVantageClient kept in Udgaard for options data)
   - **Ovtlyr**: Legacy integration (being removed — breadth now computed from DB)

**API Endpoints:**

**Backtesting:** `POST /api/backtest`, `GET /api/strategies/available`

**Stocks:** `GET /api/stocks`, `GET /api/stocks/{symbol}`, `POST /api/stocks/refresh`, `GET /api/stock-symbols`

**Portfolio:** `GET/POST /api/portfolio`, `GET/DELETE /api/portfolio/{id}`, `GET /api/portfolio/{id}/stats`, `GET/POST /api/portfolio/{id}/trades`, `PUT/DELETE /api/portfolio/{id}/trades/{tradeId}`, `PUT /api/portfolio/{id}/trades/{tradeId}/close`, `GET /api/portfolio/{id}/equity-curve`

**Scanner:** `POST /api/scanner/scan`, `POST /api/scanner/check-exits`, `GET/POST /api/scanner/trades`, `PUT/DELETE /api/scanner/trades/{id}`, `POST /api/scanner/trades/{id}/roll`

**Market Breadth:** `GET /api/breadth`, `GET /api/breadth/{symbol}`, `POST /api/breadth/refresh`

**Data Management:** `POST /api/data-management/populate-sectors`

**Other:** `POST /api/monte-carlo/run`, `POST /api/cache/evict`, `POST /api/cache/evict-all`, `POST /api/data/import`

### Frontend: Asgaard (Nuxt.js)

**Tech Stack:** Nuxt 4.1.2, NuxtUI 4.0.1, TypeScript 5.9.3, Vue 3, Tailwind CSS, ApexCharts 5.3.5, Unovis 1.6.1, Lightweight Charts 5.0.9, date-fns 4.1.0, Zod 4.1.11, pnpm 10.24.0

**Key Components (58 Vue components):**
- **Backtesting** (`components/backtesting/`): Cards, ConfigModal, EquityCurve.client, SectorAnalysis, StockPerformance, ATRDrawdownStats, ExcursionAnalysis, ExitReasonAnalysis, MonteCarloResults, MonteCarloEquityCurve.client, MonteCarloMetrics, TimeBasedStats, MarketConditions, TradeChart.client, TradeDetailsModal, DataCard
- **Portfolio** (`components/portfolio/`): CreateModal, CreateFromBrokerModal, PositionDetailsModal, ClosePositionModal, DeleteModal, DeletePositionModal, EditPositionMetadataModal, AddExecutionModal, EquityCurve.client, OpenTradeChart.client, OptionTradeChart.client, SyncPortfolioModal, RollChainModal
- **Charts** (`components/charts/`): BarChart.client, BreadthChart.client, DonutChart.client, HistogramChart.client, LineChart.client, ScatterChart.client, StockChart.client, SignalDetailsModal, StrategySignalsTable
- **Data Management** (`components/data-management/`): DatabaseStatsCards, RefreshControlsCard, BreadthRefreshCard, RateLimitCard
- **Strategy** (`components/strategy/`): StrategyBuilder, StrategySelector, ConditionCard
- **Scanner** (`components/scanner/`): ScanConfigModal, ScanResultsTable, AddTradeModal, DeleteTradeModal, RollTradeModal, TradeDetailsModal, ExitAlerts, StatsCards, NearMissAnalysis
- **Settings** (`components/settings/`): MembersList
- **Pages**: index, backtesting, portfolio, scanner, stock-data, breadth, data-manager, app-metrics, settings, login, test-chart

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
│   │   │   ├── model/                # BacktestReport, Trade, BacktestContext, PositionSizingConfig
│   │   │   ├── service/              # BacktestService, StrategyRegistry, MonteCarloService, PositionSizingService
│   │   │   └── strategy/             # Strategies, DSL, conditions, rankers
│   │   ├── data/                     # Data domain
│   │   │   ├── controller/           # StockController, BreadthController, DataManagementController
│   │   │   ├── integration/          # Midgaard, AlphaVantage, Ovtlyr clients
│   │   │   ├── model/                # Stock, StockQuote, OrderBlock, MarketBreadth
│   │   │   ├── repository/           # StockJooqRepository, SymbolJooqRepository
│   │   │   └── service/              # StockService, TechnicalIndicatorService, OrderBlockCalculator
│   │   ├── portfolio/                # Portfolio domain
│   │   │   ├── controller/           # PortfolioController, PositionController, OptionController
│   │   │   ├── dto/                  # Request/response DTOs
│   │   │   ├── integration/          # Broker adapters, IBKR, options providers
│   │   │   ├── mapper/               # Entity/DTO mappers
│   │   │   ├── model/                # Portfolio, Position, Execution
│   │   │   ├── repository/           # PortfolioJooqRepository, PositionJooqRepository, ExecutionJooqRepository
│   │   │   └── service/              # PortfolioService, PositionService, BrokerIntegrationService, OptionPriceService, UnrealizedPnlService
│   │   ├── scanner/                  # Scanner domain
│   │   │   ├── controller/           # ScannerController
│   │   │   ├── dto/                  # Request DTOs
│   │   │   ├── mapper/               # ScannerTradeMapper
│   │   │   ├── model/                # ScannerTrade, ScanResult, ScanResponse
│   │   │   ├── repository/           # ScannerTradeJooqRepository
│   │   │   └── service/              # ScannerService
│   │   ├── controller/               # Shared controllers (Auth, Cache, Settings)
│   │   ├── mcp/                      # MCP server tools
│   │   └── config/                   # Configuration classes (Security, Cache, Providers, StockRefresh)
│   ├── src/main/resources/           # Config, migrations (V1-V8)
│   ├── src/test/kotlin/              # Unit + E2E tests (TestContainers)
│   ├── compose.yaml                  # Docker Compose (PostgreSQL)
│   ├── build.gradle                  # Gradle build config
│   ├── detekt.yml                    # Detekt configuration
│   └── detekt-baseline.xml           # Detekt baseline for existing issues
├── midgaard/                         # Reference data service (Kotlin/Spring Boot, port 8081)
│   ├── src/main/kotlin/com/skrymer/midgaard/
│   │   ├── provider/                 # Provider abstractions + implementations (AlphaVantage, Massive)
│   │   ├── indicator/                # Indicator calculator (ATR, ADX, EMAs, Donchian)
│   │   ├── ingestion/                # Ingestion service (initial load + daily updates)
│   │   ├── repository/               # jOOQ repositories (quotes, earnings, symbols, ingestion status)
│   │   ├── controller/               # REST API + Thymeleaf UI controllers
│   │   └── config/                   # Configuration classes
│   ├── src/main/resources/           # Config, migrations, Thymeleaf templates
│   └── build.gradle.kts              # Kotlin DSL build config
├── asgaard/                          # Frontend (Nuxt.js)
│   ├── app/
│   │   ├── components/               # Vue components (backtesting, portfolio, scanner, charts, strategy, data-management)
│   │   ├── layouts/                  # Layouts (default.vue)
│   │   ├── pages/                    # File-based routing (12 pages)
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

### Quick Start

```bash
# Backend (see udgaard/claude.md for full commands)
cd udgaard
docker compose up -d postgres   # Start PostgreSQL
./gradlew initDatabase          # First-time: Flyway migrations + jOOQ codegen
./gradlew bootRun               # Start application (http://localhost:8080/udgaard/actuator/health)

# Frontend (see asgaard/claude.md for details)
cd asgaard
pnpm install && pnpm dev        # Runs on http://localhost:3000
```

### Running Tests

**Backend:** `cd udgaard && ./gradlew test`

**Code Quality:** `cd udgaard && ./gradlew ktlintCheck && ./gradlew detekt`

**Frontend:** `cd asgaard && pnpm typecheck && pnpm lint`

---

## Key Concepts

### Strategy System

Strategies use a DSL for declarative composition, auto-discovered via `@RegisteredStrategy` annotation. See `udgaard/claude.md` for DSL examples, registration patterns, and the strategy development workflow.

### Portfolio Management

**Portfolio Features:** Multiple portfolios, independent balances/currencies, real-time P/L, YTD/annualized returns, win rate, proven edge

**Trade Types:** Stocks, Leveraged ETFs, Options (strike, expiration, type, contracts, multiplier)

**Trade Lifecycle:** Open → Edit → Close → Delete

### MCP Tools

Claude can use MCP tools (`runBacktest`, `getStockData`, `getMultipleStocksData`, `getMarketBreadth`, `getStockSymbols`) for programmatic backtesting and analysis. See the `/run-backtest` skill for detailed usage.

---

## Known Limitations

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
- E2E tests use TestContainers (PostgreSQL) — requires Docker running
- `AbstractIntegrationTest` provides shared PostgreSQL container for integration tests

---

_Last Updated: 2026-02-26_
