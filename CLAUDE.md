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
- **Midgaard** (standalone reference data service on port 8081) for OHLCV data with pre-computed indicators (ATR, ADX, EMAs, Donchian), live quotes (Finnhub), options data, and FX rates
- Ovtlyr API (legacy, being removed — breadth data now computed from DB tables)

**Key Components (modularized into `backtesting/`, `data/`, `portfolio/`, `scanner/` packages):**

1. **Backtesting** (`backtesting/`)
   - `BacktestService.kt`: Core backtesting engine
   - `StrategyRegistry.kt`: Dynamic strategy discovery via `@RegisteredStrategy`
   - `DynamicStrategyBuilder.kt`: Runtime strategy creation from API config
   - `StrategySignalService.kt`: Signal evaluation for individual stocks
   - `MonteCarloService.kt`: Monte Carlo simulations
   - `PositionSizingService.kt`: Position sizing with daily mark-to-market drawdown and drawdown-responsive risk scaling
   - `WalkForwardService.kt`: Walk-forward validation with rolling IS/OOS windows
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
   - `ScheduledRefreshService.kt`: Scheduled automatic data refresh

3. **Portfolio** (`portfolio/`)
   - `PortfolioService.kt`: Portfolio management
   - `PositionService.kt`: Position lifecycle management
   - `BrokerIntegrationService.kt`: Broker sync orchestration
   - `ForexTrackingService.kt`: FIFO forex lot tracking for multi-currency portfolios
   - `CashTransactionService.kt`: Deposits/withdrawals tracking (IBKR import + balance adjustment)
   - `OptionPriceService.kt`: Options pricing data
   - `PortfolioStatsService.kt`: Portfolio statistics and metrics
   - `UnrealizedPnlService.kt`: Real-time P/L calculations
   - IBKR integration via broker adapter pattern (`broker/`, `ibkr/`)
   - Options data via Midgaard (`options/MidgaardOptionsProvider.kt`)

4. **Scanner** (`scanner/`)
   - `ScannerService.kt`: Scan for entry signals, check exits, CRUD trades, roll trades, close trades, drawdown stats
   - `ScannerController.kt`: REST API for scanner operations (scan, trades CRUD, close, roll, exits, drawdown stats)
   - `ScannerTradeJooqRepository.kt`: jOOQ persistence for scanner trades
   - Lightweight trade tracking separate from portfolio positions
   - Uses `StrategyRegistry` for predefined strategy lookup

5. **MCP Server** (`mcp/`)
   - `mcp/config/McpConfiguration.kt`: MCP server configuration
   - `mcp/service/StockMcpTools.kt`: Tools for Claude AI integration
   - Tools: getStockData, getMultipleStocksData, getMarketBreadth, getStockSymbols, runBacktest

6. **Integration** (`data/integration/`)
   - **Midgaard**: OHLCV data with pre-computed indicators (ATR, ADX, EMAs, Donchian) via REST client; also provides live quotes (via Finnhub) for scanner exit checks
   - **Ovtlyr**: Legacy integration (being removed — breadth now computed from DB)
   - Options data now provided by Midgaard (via `portfolio/integration/options/MidgaardOptionsProvider.kt`)

**API Endpoints:**

**Backtesting:** `POST /api/backtest`, `POST /api/backtest/walk-forward`, `GET /api/backtest/{backtestId}/trades`, `GET /api/backtest/strategies`, `GET /api/backtest/rankers`, `GET /api/backtest/conditions`

**Stocks:** `GET /api/stocks`, `GET /api/stocks/symbols`, `GET /api/stocks/symbols/search`, `GET /api/stocks/{symbol}`, `GET /api/stocks/{symbol}/signals`, `GET /api/stocks/{symbol}/evaluate-date/{date}`, `GET /api/stocks/{symbol}/evaluate-exit/{date}`, `POST /api/stocks/{symbol}/condition-signals`

**Portfolio:** `GET/POST /api/portfolio`, `GET/PUT/DELETE /api/portfolio/{id}`, `POST /api/portfolio/import`, `POST /api/portfolio/{id}/sync`, `GET /api/portfolio/{id}/forex/lots`, `GET /api/portfolio/{id}/forex/disposals`, `GET /api/portfolio/{id}/forex/summary`, `GET /api/portfolio/{id}/cash-transactions`, `GET /api/portfolio/{id}/cash-transactions/summary`, `POST /api/portfolio/broker/test`

**Positions:** `GET /api/positions/{portfolioId}`, `GET /api/positions/{portfolioId}/{positionId}`, `POST /api/positions/{portfolioId}`, `PUT /api/positions/{portfolioId}/{positionId}/close`, `PUT /api/positions/{portfolioId}/{positionId}/metadata`, `DELETE /api/positions/{portfolioId}/{positionId}`, `GET /api/positions/{portfolioId}/stats`, `GET /api/positions/{portfolioId}/unrealized-pnl`, `GET /api/positions/{portfolioId}/equity-curve`, `POST /api/positions/{portfolioId}/recalculate-balance`, `GET /api/positions/{portfolioId}/{positionId}/roll-chain`

**Scanner:** `POST /api/scanner/scan`, `POST /api/scanner/check-exits`, `GET/POST /api/scanner/trades`, `PUT/DELETE /api/scanner/trades/{id}`, `PUT /api/scanner/trades/{id}/close`, `POST /api/scanner/trades/reset`, `GET /api/scanner/trades/closed`, `GET /api/scanner/drawdown-stats`, `POST /api/scanner/trades/{id}/roll`, `POST /api/scanner/option-contracts`

**Market Breadth:** `GET /api/breadth/market-daily`, `GET /api/breadth/sector-daily/{symbol}`

**Data Management:** `GET /api/data-management/stats`, `POST /api/data-management/refresh/stocks`, `POST /api/data-management/refresh/all-stocks`, `POST /api/data-management/refresh/recalculate-breadth`, `GET /api/data-management/breadth-coverage`, `GET /api/data-management/refresh/progress`, `POST /api/data-management/refresh/clear`

**Monte Carlo:** `POST /api/monte-carlo/simulate`

**Options:** `GET /api/options/historical-prices`

**Settings:** `GET/POST /api/settings/credentials`, `GET /api/settings/credentials/status`, `GET/POST /api/settings/position-sizing`

**Auth:** `GET /api/auth/check`

**Cache:** `GET /api/cache/status`

### Frontend: Asgaard (Nuxt.js)

**Tech Stack:** Nuxt 4.1.2, NuxtUI 4.0.1, TypeScript 5.9.3, Vue 3, Tailwind CSS, ApexCharts 5.3.5, Unovis 1.6.1, Lightweight Charts 5.0.9, date-fns 4.1.0, Zod 4.1.11, pnpm 10.24.0

**Key Components (62 Vue components):**
- **Backtesting** (`components/backtesting/`): Cards, ConfigModal, EquityCurve.client, SectorAnalysis, StockPerformance, ATRDrawdownStats, ExcursionAnalysis, ExitReasonAnalysis, MonteCarloResults, MonteCarloEquityCurve.client, MonteCarloMetrics, TimeBasedStats, MarketConditions, TradeChart.client, TradeDetailsModal, DataCard
- **Portfolio** (`components/portfolio/`): CreateModal, CreateFromBrokerModal, PositionDetailsModal, ClosePositionModal, DeleteModal, DeletePositionModal, EditPositionMetadataModal, BatchEditStrategyModal, AddExecutionModal, EquityCurve.client, OpenTradeChart.client, OptionTradeChart.client, SyncPortfolioModal, RollChainModal
- **Charts** (`components/charts/`): BarChart.client, BreadthChart.client, DonutChart.client, HistogramChart.client, LineChart.client, ScatterChart.client, StockChart.client, SignalDetailsModal, StrategySignalsTable
- **Data Management** (`components/data-management/`): DatabaseStatsCards, RefreshControlsCard, BreadthRefreshCard, RateLimitCard
- **Strategy** (`components/strategy/`): StrategyBuilder, StrategySelector, ConditionCard
- **Scanner** (`components/scanner/`): ScanConfigModal, ScanResultsTable, AddTradeModal, BatchAddTradesModal, DeleteTradeModal, RollTradeModal, TradeDetailsModal, ExitAlerts, StatsCards, NearMissAnalysis
- **Settings** (`components/settings/`): MembersList
- **Root-level**: StockPriceChart.client, SymbolSearch, UserMenu, ConditionConfigModal, ConditionSignalsTable
- **Pages**: index, backtesting, portfolio, scanner, stock-data/[[symbol]], breadth, data-manager, app-metrics, settings, login, test-chart

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
│   │   │   ├── model/                # BacktestReport, Trade, BacktestContext, PositionSizingConfig (DrawdownScaling, DrawdownThreshold), WalkForwardResult, MonteCarloResult, TradeShufflingTechnique, BootstrapResamplingTechnique
│   │   │   ├── dto/                  # DTOs (StrategyConfigDto, MonteCarloRequestDto, ConditionSignalDtos, etc.)
│   │   │   ├── service/              # BacktestService, StrategyRegistry, MonteCarloService, PositionSizingService, WalkForwardService
│   │   │   └── strategy/             # Strategies, DSL, conditions, rankers
│   │   ├── data/                     # Data domain
│   │   │   ├── controller/           # StockController, BreadthController, DataManagementController
│   │   │   ├── integration/          # Midgaard, Ovtlyr clients + StockProvider interface
│   │   │   ├── mapper/               # StockMapper
│   │   │   ├── model/                # Stock, StockQuote, OrderBlock, MarketBreadthDaily, SectorBreadthDaily, Earning, AssetType
│   │   │   ├── repository/           # StockJooqRepository, SymbolJooqRepository, MarketBreadthRepository, SectorBreadthRepository
│   │   │   └── service/              # StockService, StockIngestionService, TechnicalIndicatorService, OrderBlockCalculator, MarketBreadthService, SectorBreadthService, SymbolService, DataStatsService, ScheduledRefreshService
│   │   ├── portfolio/                # Portfolio domain
│   │   │   ├── controller/           # PortfolioController, PositionController, OptionController
│   │   │   ├── dto/                  # Request/response DTOs
│   │   │   ├── integration/          # Broker adapters (broker/), IBKR (ibkr/), options providers (options/MidgaardOptionsProvider)
│   │   │   ├── mapper/               # Entity/DTO mappers
│   │   │   ├── model/                # Portfolio, Position, Execution, PortfolioStats, CashTransaction, ForexLot, ForexDisposal, EquityCurveData
│   │   │   ├── repository/           # PortfolioJooqRepository, PositionJooqRepository, ExecutionJooqRepository, ForexLotJooqRepository, ForexDisposalJooqRepository, CashTransactionJooqRepository
│   │   │   └── service/              # PortfolioService, PortfolioStatsService, PositionService, BrokerIntegrationService, OptionPriceService, UnrealizedPnlService, ForexTrackingService, CashTransactionService
│   │   ├── scanner/                  # Scanner domain
│   │   │   ├── controller/           # ScannerController
│   │   │   ├── dto/                  # Request DTOs
│   │   │   ├── mapper/               # ScannerTradeMapper
│   │   │   ├── model/                # ScannerTrade (TradeStatus, close fields), ScanResult, ScanResponse, NearMissCandidate, ConditionFailureSummary, ExitCheckResult (usedLiveData), ExitCheckResponse
│   │   │   ├── repository/           # ScannerTradeJooqRepository
│   │   │   └── service/              # ScannerService
│   │   ├── controller/               # Shared controllers (Auth, Cache, Settings)
│   │   ├── service/                  # Shared services (SettingsService, UserSettingsJooqRepository)
│   │   ├── mcp/                      # MCP server (config/McpConfiguration, service/StockMcpTools)
│   │   └── config/                   # Configuration classes (Security, Cache, ApiKeyAuth, UserSeeder, MidgaardHealthIndicator)
│   ├── src/main/resources/           # Config, migrations (V1-V17)
│   ├── src/test/kotlin/              # Unit + E2E tests (TestContainers)
│   ├── compose.yaml                  # Docker Compose (PostgreSQL for local dev)
│   ├── Dockerfile                    # Runtime image (eclipse-temurin:25-jre-alpine)
│   ├── init-databases.sql            # Init script for prod PostgreSQL (creates both trading + datastore DBs)
│   ├── build.gradle                  # Gradle build config (includes springBoot { buildInfo() })
│   ├── detekt.yml                    # Detekt configuration
│   └── detekt-baseline.xml           # Detekt baseline for existing issues
├── midgaard/                         # Reference data service (Kotlin/Spring Boot, port 8081)
│   ├── src/main/kotlin/com/skrymer/midgaard/
│   │   ├── integration/              # Provider abstractions + implementations (AlphaVantage, Massive, Finnhub)
│   │   ├── service/                  # IngestionService, IndicatorCalculator, RateLimiterService, ApiKeyService, ScheduledIngestionService
│   │   ├── repository/               # jOOQ repositories (quotes, earnings, symbols, ingestion status, provider config)
│   │   ├── controller/               # REST API + Thymeleaf UI controllers
│   │   ├── model/                    # Domain models (Models.kt, OptionContractDto)
│   │   └── config/                   # Configuration classes (Security, ProviderConfiguration, ExternalConfigLoader, VersionAdvice)
│   ├── src/main/resources/           # Config, migrations, Thymeleaf templates
│   ├── build.gradle                  # Gradle build config
│   └── detekt.yml                    # Detekt configuration
├── asgaard/                          # Frontend (Nuxt.js)
│   ├── app/
│   │   ├── components/               # Vue components (backtesting, portfolio, scanner, charts, strategy, data-management)
│   │   ├── layouts/                  # Layouts (default.vue)
│   │   ├── pages/                    # File-based routing (11 pages + 1 dynamic route)
│   │   ├── plugins/                  # Nuxt plugins (apexcharts.client, auth-interceptor.client)
│   │   ├── types/                    # TypeScript definitions
│   │   ├── app.vue                   # Root component
│   │   └── error.vue                 # Error page
│   ├── nuxt.config.ts                # Nuxt configuration (proxy target via NUXT_BACKEND_URL env var)
│   ├── Dockerfile                    # Multi-stage build (node:24-alpine)
│   ├── .dockerignore                 # Docker build exclusions
│   ├── package.json                  # Dependencies
│   └── claude.md                     # Nuxt-specific context
├── pinescripts/                      # TradingView Pine Script strategies
├── strategy_exploration/             # Strategy research and development notes
├── compose.prod.yaml                 # Production Docker Compose (all services: postgres, midgaard, udgaard, asgaard, adminer)
├── deploy-prd.fish                   # Production deployment script (version bump, build JARs, deploy containers)
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

### Production Deployment

The platform can be deployed as a full Docker stack using `compose.prod.yaml`. All services (PostgreSQL, Midgaard, Udgaard, Asgaard, Adminer) run in containers with production ports (9000, 9080, 9081, 9083, 9432).

```bash
# Deploy to production (bumps versions, builds JARs, builds/starts containers)
./deploy-prd.fish

# Or with explicit versions
./deploy-prd.fish --midgaard 1.0.5 --udgaard 1.0.1

# Manual steps
cd udgaard && ./gradlew bootJar -x test -x generateJooq   # Build Udgaard JAR
cd midgaard && ./gradlew bootJar -x test -x generateJooq  # Build Midgaard JAR
docker compose -f compose.prod.yaml build                   # Build Docker images
docker compose -f compose.prod.yaml up -d                   # Start all services
```

**Production URLs:** Asgaard http://localhost:9000, Udgaard http://localhost:9080, Midgaard http://localhost:9081, Adminer http://localhost:9083, PostgreSQL localhost:9432

**Environment:** Optional `.env.prod` file for API keys and overrides. Asgaard uses `NUXT_BACKEND_URL` env var (defaults to `http://udgaard:8080` in Docker) for backend proxy target.

### Running Tests

**Backend:** `cd udgaard && ./gradlew test`

**Code Quality:** `cd udgaard && ./gradlew ktlintCheck && ./gradlew detekt`

**Frontend:** `cd asgaard && pnpm typecheck && pnpm lint`

---

## Key Concepts

### Strategy System

Strategies use a DSL for declarative composition, auto-discovered via `@RegisteredStrategy` annotation. See `udgaard/claude.md` for DSL examples, registration patterns, and the strategy development workflow.

### Portfolio Management

**Portfolio Features:** Multiple portfolios, independent balances/currencies, real-time P/L, YTD/annualized returns, win rate, proven edge, multi-currency FX tracking (USD/AUD), deposits/withdrawals tracking

**Balance Formula:** `currentBalance = initialBalance + totalRealizedPnl - totalCommissions + totalDeposits - totalWithdrawals`

**Cash Transactions:** Deposits and withdrawals imported from IBKR Flex Query (`CashTransactions` section, type `Deposits/Withdrawals`). Stored with `fxRateToBase` for AUD-equivalent tracking. Deduplication via `brokerTransactionId`.

**FX Tracking:** Portfolios with different base/trade currencies track FX impact. `initialFxRate` stored at portfolio creation, live rates fetched from Midgaard. Per-transaction FIFO forex lot tracking for tax purposes. Stats include `effectiveBalance` (currentBalance + FX P&L) and `currentFxRate` for currency toggle display.

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

_Last Updated: 2026-03-30_
