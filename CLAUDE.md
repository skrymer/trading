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
- **Midgaard** (standalone reference data service on port 8081) for OHLCV data with pre-computed indicators (ATR, ADX, EMAs, Donchian), live quotes (Finnhub), options data, FX rates, and ovtlyr buy/sell signals
- Ovtlyr direct API integration in Udgaard (legacy, being removed — breadth data now computed from DB tables; ovtlyr buy/sell signals are now ingested via Midgaard)

**Key Components (modularized into `backtesting/`, `data/`, `portfolio/`, `scanner/` packages):**

1. **Backtesting** (`backtesting/`)
   - `BacktestService.kt`: Core backtesting engine with capital-aware trade selection; records `EntryDecisionContext` on every trade (including skipped/missed) for post-hoc analysis
   - `StrategyRegistry.kt`: Dynamic strategy discovery via `@RegisteredStrategy`
   - `DynamicStrategyBuilder.kt`: Runtime strategy creation from API config
   - `StrategySignalService.kt`: Signal evaluation for individual stocks
   - `MonteCarloService.kt`: Monte Carlo simulations
   - `PositionSizingService.kt`: Position sizing orchestrator with daily mark-to-market drawdown tracking; delegates share calculation to pluggable `PositionSizer` implementations
   - `service/sizer/`: Pluggable sizer package — `PositionSizer` interface + polymorphic `SizerConfig` DTO; implementations: `AtrRiskSizer`, `PercentEquitySizer`, `KellySizer`, `VolatilityTargetSizer`; `LeverageCap` helper applies portfolio-level leverage constraint outside sizer
   - `WalkForwardService.kt`: Walk-forward validation with rolling IS/OOS windows and IS-derived sector ranking for OOS; buckets per-window OOS trades by entry month (`outOfSampleStatsByEntryMonth`) for sub-window regime gates (ADR 0006)
   - `BacktestResultStore.kt`: Postgres store for backtest results (`backtest_reports` table); the report is gzip-compressed into a `bytea` column (high-candidate backtests overflow Postgres's ~256 MB jsonb cap), scalar summary fields extracted to columns; retention "day or two", manual cleanup via `BacktestReportController`
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
   - Portfolio CRUD: `PortfolioController` talks to `PortfolioJooqRepository` directly; rich-domain methods (`Portfolio.create()`, `withBalanceUpdated()`, `withSyncCompleted()`, `withRealizedPnlApplied()`) live on the `Portfolio` model
   - `PositionWithExecutions` (in `model/PositionStats.kt`): rich aggregate root for Position + executions; owns derived P&L (`realizedPnl`, `realizedPnlBase`, `totalCommissions`) and state transitions (`withClosed(closeDate)`, `withExecutionAdded(execution)`, `recalculated()`) per ADR 0001
   - `StrategyBreakdownStats` (in `model/PositionStats.kt`): per-strategy slice of closed positions; owns the breakdown math (win rate, edge, profit factor) via `fromPositions` factory. Exposed as the `byStrategy` field on `PositionStats`, populated by `PortfolioStatsService.calculateStats`; positions are grouped by `Position.strategyGroupKey`
   - `Execution.Companion.closingFor(position, exitPrice, exitDate)`: factory for closing executions
   - `PositionService.kt`: thin orchestration — close/recalculate operations delegate to `PositionWithExecutions` and `Portfolio.withRealizedPnlApplied`; uses `PositionJooqRepository.findWithExecutionsById(id)`
   - `BrokerIntegrationService.kt`: Broker sync orchestration (uses `PortfolioJooqRepository` directly)
   - `ForexTrackingService.kt`: FIFO forex lot tracking for multi-currency portfolios
   - `CashTransactionService.kt`: Deposits/withdrawals tracking (IBKR import + balance adjustment)
   - `OptionPriceService.kt`: Options pricing data
   - `PortfolioStatsService.kt`: Portfolio statistics and metrics
   - `UnrealizedPnlService.kt`: Real-time P/L calculations
   - IBKR integration via broker adapter pattern (`broker/`, `ibkr/`)
   - Options data via Midgaard (`options/MidgaardOptionsProvider.kt`)

4. **Scanner** (`scanner/`)
   - `ScannerService.kt`: Scan for entry signals, check exits, validate entries against live quotes, CRUD trades, roll trades, close trades, drawdown stats; persists a `ScanRun` after each scan
   - `CohortDivergenceService.kt`: Cohort-divergence diagnostic comparing today's matched symbols against a rolling baseline (today vs N-day window)
   - `ScannerController.kt`: REST API for scanner operations (scan, validate entries, trades CRUD, close, roll, exits, drawdown stats, cohort-divergence)
   - `ScannerTradeJooqRepository.kt`: jOOQ persistence for scanner trades (incl. `findBySignalDateBetween`)
   - `ScanRunJooqRepository.kt`: jOOQ persistence for `ScanRun` history
   - `CohortWindow.kt`: aggregate root over a rolling window of scan runs (per ADR 0001)
   - Lightweight trade tracking separate from portfolio positions
   - Uses `StrategyRegistry` for predefined strategy lookup

5. **MCP Server** (`mcp/`)
   - `mcp/config/McpConfiguration.kt`: MCP server configuration
   - `mcp/service/StockMcpTools.kt`: Tools for Claude AI integration
   - Tools: getAvailableSymbols, getAvailableStrategies, getAvailableRankers, getAvailableConditions, getStrategyDetails, explainBacktestMetrics, getSystemStatus

6. **Integration** (`data/integration/`)
   - `StockProvider.kt`: Interface for OHLCV data + live quotes + earnings (`LatestQuote`, `getLatestQuote`, `getLatestQuotes`, `getEarnings`); used by ScannerService, StockController, UnrealizedPnlService, and StockIngestionService
   - **Midgaard**: Implements `StockProvider`; OHLCV data with pre-computed indicators (ATR, ADX, EMAs, Donchian) via REST client; also provides live quotes (via Finnhub) for scanner exit checks, earnings (via `MidgaardEarningDto`), and ovtlyr buy/sell signals (via `MidgaardClient.getOvtlyrSignals` / `MidgaardOvtlyrSignalDto`, loaded into `Stock.ovtlyrSignals` during ingestion); HTTP timeouts configured in `MidgaardHttpConfig` (RestClientCustomizer)
   - **Ovtlyr**: Legacy direct-API integration (being removed — breadth now computed from DB; ovtlyr signals now flow through Midgaard)
   - Options data now provided by Midgaard (via `portfolio/integration/options/MidgaardOptionsProvider.kt`)

**API Endpoints:**

**Backtesting:** `POST /api/backtest`, `POST /api/backtest/walk-forward`, `GET /api/backtest/{backtestId}/trades`, `GET /api/backtest/{backtestId}/missed-trades`, `GET /api/backtest/strategies`, `GET /api/backtest/rankers`, `GET /api/backtest/conditions`, `GET /api/backtest/reports`, `DELETE /api/backtest/reports/{backtestId}`, `POST /api/backtest/reports/batch-delete`

**Stocks:** `GET /api/stocks`, `GET /api/stocks/symbols`, `GET /api/stocks/symbols/search`, `GET /api/stocks/{symbol}`, `GET /api/stocks/{symbol}/signals`, `GET /api/stocks/{symbol}/evaluate-date/{date}`, `GET /api/stocks/{symbol}/evaluate-exit/{date}`, `POST /api/stocks/{symbol}/condition-signals`, `POST /api/stocks/{symbol}/exit-condition-signals`, `GET /api/stocks/{symbol}/latest-quote`

**Portfolio:** `GET/POST /api/portfolio`, `GET/PUT/DELETE /api/portfolio/{id}`, `POST /api/portfolio/import`, `POST /api/portfolio/{id}/sync`, `GET /api/portfolio/{id}/forex/lots`, `GET /api/portfolio/{id}/forex/disposals`, `GET /api/portfolio/{id}/forex/summary`, `GET /api/portfolio/{id}/cash-transactions`, `GET /api/portfolio/{id}/cash-transactions/summary`, `POST /api/portfolio/broker/test`

**Positions:** `GET /api/positions/{portfolioId}`, `GET /api/positions/{portfolioId}/{positionId}`, `POST /api/positions/{portfolioId}`, `PUT /api/positions/{portfolioId}/{positionId}/close`, `PUT /api/positions/{portfolioId}/{positionId}/metadata`, `DELETE /api/positions/{portfolioId}/{positionId}`, `GET /api/positions/{portfolioId}/stats`, `GET /api/positions/{portfolioId}/unrealized-pnl`, `GET /api/positions/{portfolioId}/equity-curve`, `POST /api/positions/{portfolioId}/recalculate-balance`, `GET /api/positions/{portfolioId}/{positionId}/roll-chain`

**Scanner:** `POST /api/scanner/scan`, `POST /api/scanner/check-exits`, `POST /api/scanner/validate-entries`, `GET/POST /api/scanner/trades`, `PUT/DELETE /api/scanner/trades/{id}`, `PUT /api/scanner/trades/{id}/close`, `POST /api/scanner/trades/reset`, `GET /api/scanner/trades/closed`, `GET /api/scanner/trades/closed/stats`, `GET /api/scanner/drawdown-stats`, `POST /api/scanner/trades/{id}/roll`, `POST /api/scanner/option-contracts`, `GET /api/scanner/cohort-divergence`

**Market Breadth:** `GET /api/breadth/market-daily`, `GET /api/breadth/sector-daily/{symbol}`

**Data Management:** `GET /api/data-management/stats`, `GET /api/data-management/latest-date`, `POST /api/data-management/refresh/stocks`, `POST /api/data-management/refresh/all-stocks`, `POST /api/data-management/refresh/recalculate-breadth`, `GET /api/data-management/breadth-coverage`, `GET /api/data-management/refresh/progress`, `POST /api/data-management/refresh/clear`

**Conditions:** `POST /api/conditions/screen` (diagnostic condition pre-screen — forward-return lift, firing rate, ARS parameter sweep, SPY-regime lift, Jaccard overlap; `endDate` hard-capped at Block C's 2021-01-01 start per ADR 0007)

**Monte Carlo:** `POST /api/monte-carlo/simulate`

**Options:** `GET /api/options/historical-prices`

**Settings:** `GET/POST /api/settings/credentials`, `GET /api/settings/credentials/status`, `GET/POST /api/settings/position-sizing`

**Auth:** `GET /api/auth/check`

**Cache:** `GET /api/cache/status`

### Frontend: Asgaard (Nuxt.js)

**Tech Stack:** Nuxt 4.1.2, NuxtUI 4.0.1, TypeScript 5.9.3, Vue 3, Tailwind CSS, ApexCharts 5.3.5, Unovis 1.6.1, Lightweight Charts 5.0.9, date-fns 4.1.0, Zod 4.1.11, pnpm 10.24.0

**Key Components (68 Vue components):**
- **Backtesting** (`components/backtesting/`): Cards, ConfigModal, SectorAnalysis, StockPerformance, ATRDrawdownStats, ExcursionAnalysis, ExitReasonAnalysis, MonteCarloResults, MonteCarloEquityCurve.client, MonteCarloMetrics, TimeBasedStats, MarketConditions, TradeChart.client, TradeDetailsModal, DataCard
- **Backtest Reports** (`components/backtest-reports/`): ReportsTable, DeleteConfirmModal
- **Portfolio** (`components/portfolio/`): CreateModal, CreateFromBrokerModal, PositionDetailsModal, ClosePositionModal, DeleteModal, DeletePositionModal, EditPositionMetadataModal, BatchEditStrategyModal, AddExecutionModal, EquityCurve.client, OpenTradeChart.client, OptionTradeChart.client, SyncPortfolioModal, RollChainModal
- **Charts** (`components/charts/`): BarChart.client, BreadthChart.client, DonutChart.client, HistogramChart.client, LineChart.client, ScatterChart.client, StockChart.client, SignalDetailsModal, StrategySignalsTable
- **Data Management** (`components/data-management/`): DatabaseStatsCards, RefreshControlsCard, BreadthRefreshCard, RateLimitCard
- **Strategy** (`components/strategy/`): StrategyBuilder, StrategySelector, ConditionCard
- **Scanner** (`components/scanner/`): ScanConfigModal, ScanResultsTable, AddTradeModal, BatchAddTradesModal, DeleteTradeModal, RollTradeModal, TradeDetailsModal, ExitAlerts, StatsCards, NearMissAnalysis, CohortDivergenceCard
- **Settings** (`components/settings/`): MembersList
- **Root-level**: EquityCurve.client, StockPriceChart.client, SymbolLink, SymbolSearch, UserMenu, ConditionConfigModal, ConditionSignalsTable, ExitConditionConfigModal, ExitConditionSignalsTable
- **Pages**: index, backtesting, backtest-reports, portfolio, portfolio-old, mission-control, stock-data/[[symbol]], breadth, data-manager, app-metrics, settings, login, test-chart

**Type Definitions:** `app/types/index.d.ts`, `app/types/enums.ts`

**Patterns:** Auto-imports, file-based routing, TypeScript strict mode, Composition API `<script setup>`, ESLint (no trailing commas, 1tbs brace style)

---

## Project Structure

```
trading/
├── CLAUDE.md                         # Project-wide context
├── CONTEXT.md                        # Domain glossary (Edge, Win rate, Profit factor, Unassigned)
├── .claude/                          # Claude configuration (commands, skills, settings)
├── claude_thoughts/                  # Documentation created by Claude
├── docs/                             # Architectural decision records (adr/) + design docs (architecture/)
├── udgaard/                          # Backend (Kotlin/Spring Boot)
│   ├── src/main/kotlin/com/skrymer/udgaard/
│   │   ├── backtesting/              # Backtesting domain
│   │   │   ├── controller/           # BacktestController, BacktestReportController, MonteCarloController
│   │   │   ├── model/                # BacktestReport (persisted gzip-compressed in bytea), BacktestReportMetadata (Metadata + Summary + ListItem), BacktestResponseDto (riskMetrics/benchmarkComparison/cagr/drawdownEpisodes), RiskMetrics, BenchmarkComparison, DrawdownEpisode, Trade (w/ EntryDecisionContext), BacktestContext, PositionSizingConfig (DrawdownScaling, DrawdownThreshold), WalkForwardResult (WalkForwardWindow w/ outOfSampleStatsByEntryMonth per ADR 0006), TradeStatsSummary (additive raw fields for re-aggregating monthly OOS buckets, per ADR 0006), MonteCarloResult, TradeShufflingTechnique, BootstrapResamplingTechnique (CBB w/ optional blockSize)
│   │   │   ├── dto/                  # DTOs (StrategyConfigDto incl. riskFreeRatePct, MonteCarloRequestDto incl. drawdownThresholds + blockSize, ConditionSignalDtos, etc.)
│   │   │   ├── repository/           # BacktestReportJooqRepository (save/findById/listAll/deleteById/deleteByIds)
│   │   │   ├── service/              # BacktestService, BacktestResultStore (gzip-compressed bytea), StrategyRegistry, ScriptPredicateCompiler (compiles user-supplied Kotlin scripts into entry/exit predicates for the `script` conditions), MonteCarloService, RiskMetricsService (Sharpe/Sortino/Calmar/SQN/tailRatio + benchmark vs SPY + drawdown episodes), PositionSizingService, WalkForwardService + sizer/ (PositionSizer, SizerConfig, AtrRiskSizer, PercentEquitySizer, KellySizer, VolatilityTargetSizer, LeverageCap), ConditionScreenService + ConditionScreenStats (diagnostic condition pre-screen: entry-anchored forward-return lift, date-clustered estimates, ARS parameter sweep, SPY-regime lift, Jaccard overlap — raw stats only, no verdict; ADR 0007)
│   │   │   └── strategy/             # Strategies, DSL, conditions, rankers
│   │   ├── data/                     # Data domain
│   │   │   ├── controller/           # StockController, BreadthController, DataManagementController
│   │   │   ├── integration/          # Midgaard (incl. MidgaardHttpConfig for connect/read timeouts + getOvtlyrSignals), legacy Ovtlyr clients + StockProvider interface (LatestQuote, getLatestQuote, getLatestQuotes, getEarnings)
│   │   │   ├── mapper/               # StockMapper
│   │   │   ├── model/                # Stock (w/ ovtlyrSignals), StockQuote, OrderBlock, MarketBreadthDaily, SectorBreadthDaily, Earning, OvtlyrSignal, AssetType
│   │   │   ├── repository/           # StockJooqRepository (incl. findEarnings(symbol) for ingestion fallback), SymbolJooqRepository, MarketBreadthRepository, SectorBreadthRepository
│   │   │   └── service/              # StockService, StockIngestionService (resolveEarnings(symbol) falls back to stockRepository.findEarnings on provider failure — stale-but-present beats empty-because-we-failed), TechnicalIndicatorService, OrderBlockCalculator, MarketBreadthService, SectorBreadthService, SymbolService, DataStatsService, ScheduledRefreshService
│   │   ├── portfolio/                # Portfolio domain
│   │   │   ├── controller/           # PortfolioController, PositionController, OptionController
│   │   │   ├── dto/                  # Request/response DTOs
│   │   │   ├── integration/          # Broker adapters (broker/), IBKR (ibkr/), options providers (options/MidgaardOptionsProvider)
│   │   │   ├── mapper/               # Entity/DTO mappers
│   │   │   ├── model/                # Portfolio (rich domain: create/withBalanceUpdated/withSyncCompleted/withRealizedPnlApplied), Position (w/ strategyGroupKey), Execution (w/ closingFor() factory), PortfolioStats, PositionStats (incl. PositionWithExecutions aggregate root w/ realizedPnl, withClosed, withExecutionAdded, recalculated; StrategyBreakdownStats w/ fromPositions factory; byStrategy field on PositionStats), CashTransaction, ForexLot, ForexDisposal, EquityCurveData
│   │   │   ├── repository/           # PortfolioJooqRepository, PositionJooqRepository (incl. findWithExecutionsById), ExecutionJooqRepository, ForexLotJooqRepository, ForexDisposalJooqRepository, CashTransactionJooqRepository
│   │   │   └── service/              # PortfolioStatsService, PositionService (thin orchestration; delegates close/recalculate to PositionWithExecutions + Portfolio.withRealizedPnlApplied), BrokerIntegrationService, OptionPriceService, UnrealizedPnlService, ForexTrackingService, CashTransactionService (Portfolio CRUD: controller→PortfolioJooqRepository directly; rich-domain methods on Portfolio.kt)
│   │   ├── scanner/                  # Scanner domain
│   │   │   ├── controller/           # ScannerController
│   │   │   ├── dto/                  # Request/response DTOs (incl. StrategyClosedStats, ClosedTradeStatsResponse, DivergenceConfig, CohortDivergenceReport, TodayMetrics, RollingMetrics, Alerts)
│   │   │   ├── mapper/               # ScannerTradeMapper
│   │   │   ├── model/                # ScannerTrade (TradeStatus, close fields, signalDate + signalSnapshot:EntrySignalDetails persisted JSONB per ADR 0004), ScanResult, ScanResponse (latestDataDate), NearMissCandidate, ConditionFailureSummary, ExitCheckResult (usedLiveData, maxProximity, nearExits), ExitProximity, ExitCheckResponse, EntryValidationResult, EntryValidationResponse, ScanRun (+ MatchedSymbol), CohortWindow (aggregate root per ADR 0001)
│   │   │   ├── repository/           # ScannerTradeJooqRepository (incl. findBySignalDateBetween), ScanRunJooqRepository
│   │   │   └── service/              # ScannerService (persists ScanRun after each scan), CohortDivergenceService
│   │   ├── controller/               # Shared controllers (Auth, Cache, Settings)
│   │   ├── service/                  # Shared services (SettingsService, UserSettingsJooqRepository)
│   │   ├── mcp/                      # MCP server (config/McpConfiguration, service/StockMcpTools)
│   │   └── config/                   # Configuration classes (Security, Cache, ApiKeyAuth, UserSeeder, GlobalExceptionHandler, ClockConfig — NY-pinned Clock bean)
│   ├── src/main/resources/           # Config, migrations (V1-V25)
│   ├── src/test/kotlin/              # Unit + E2E tests (TestContainers)
│   ├── compose.yaml                  # Docker Compose (PostgreSQL for local dev)
│   ├── Dockerfile                    # Runtime image (eclipse-temurin:25-jre-alpine)
│   ├── init-databases.sql            # Init script for prod PostgreSQL (creates both trading + datastore DBs)
│   ├── build.gradle                  # Gradle build config (includes springBoot { buildInfo() })
│   ├── detekt.yml                    # Detekt configuration
│   └── detekt-baseline.xml           # Detekt baseline for existing issues
├── midgaard/                         # Reference data service (Kotlin/Spring Boot, port 8081)
│   ├── src/main/kotlin/com/skrymer/midgaard/
│   │   ├── integration/              # Provider abstractions + implementations (AlphaVantage, Massive, Finnhub, EODHD); self-rate-limiting; selection via ProviderConfiguration @ConditionalOnProperty; ovtlyr/ (OvtlyrClient + payload mapper for ovtlyr.com buy/sell signals)
│   │   ├── service/                  # IngestionService, IndicatorCalculator, RateLimiterService, OvtlyrBackfillService, ApiKeyService, ScheduledIngestionService
│   │   ├── repository/               # jOOQ repositories (quotes, earnings, symbols, ingestion status, provider config, ovtlyr signals)
│   │   ├── controller/               # REST API + Thymeleaf UI controllers
│   │   ├── integrity/                # Data integrity framework — SectorIntegrityValidator (I1-I5), BadPrintIntegrityValidator (V1 CRITICAL V-shape bad-print, V2 HIGH split-adjustment failure with normal-priced prev, V3 HIGH split-adjustment failure with sub-cent prev confirmed by real history via bar_position floor); Spring auto-wires List<DataIntegrityValidator>
│   │   ├── model/                    # Domain models (Models.kt, OptionContractDto)
│   │   └── config/                   # Configuration classes (Security, ProviderConfiguration, ExternalConfigLoader, VersionAdvice)
│   ├── src/main/resources/           # Config, migrations, Thymeleaf templates
│   ├── build.gradle                  # Gradle build config
│   └── detekt.yml                    # Detekt configuration
├── asgaard/                          # Frontend (Nuxt.js)
│   ├── app/
│   │   ├── components/               # Vue components (backtesting, portfolio, scanner, charts, strategy, data-management)
│   │   ├── layouts/                  # Layouts (default.vue)
│   │   ├── pages/                    # File-based routing (13 pages + 1 dynamic route)
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
├── reference_check/                  # Python cross-validators for Midgaard/Udgaard calculations (EMA/Donchian diff, VCP condition verifier)
├── compose.prod.yaml                 # Production Docker Compose (all services: postgres, midgaard, udgaard, asgaard, adminer)
├── deploy-prd.fish                   # Production deployment script (version bump, build JARs, deploy containers)
├── deploy-dev.fish                   # Development deployment script
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

### Backtest Skills

User-invocable skills orchestrate the full backtest workflows end-to-end and delegate interpretation to specialized sub-agents:

- `/condition-screen` → `condition-screen-analyst` (diagnostic, design-time pre-screen of a single entry condition / stack *before* it is wired into a strategy: forward-return lift, firing-rate-per-year, ARS parameter sweep, SPY-regime lift, Jaccard overlap; emits raw stats + no verdict — reject-or-proceed, then hands clean conditions to `/strategy-screen`; `endDate` hard-capped at Block C per ADR 0007)
- `/backtest` → `post-backtest-analyst` (Sharpe / Sortino / drawdown duration / SPY correlation)
- `/walk-forward` → `walk-forward-analyst` (WFE + per-window stability)
- `/monte-carlo` → `monte-carlo-analyst` (path risk + edge confidence)
- `/strategy-screen` → `strategy-screen-analyst` (fast 10y 2005-2015 walk-forward screen with relaxed gates to filter candidate sweeps; cross-candidate failure-mode bucketing; hands surviving candidates to `/validate-candidate`)
- `/validate-candidate` → `firewall-analyst` (3-block firewall: Block A 2000-2014, Block B 2014-2021-H1 incl COVID, Block C 2021-2025; strict v4 gates + design-isolation + cross-block edge-decay + G14 implementation-invariance pre-check; emits TRADABLE / PROVISIONAL / INCONCLUSIVE_G11 / NEAR_MISS / REJECTED verdict)
- `/verify-promotion` (no sub-agent) — G14 Implementation Invariance: diffs a promoted first-class condition's trade list against the inline-`script` research candidate's, by `(entry_date, symbol)` over 25y; emits PASS / DIFFERS / ERROR. Reused by `/validate-candidate` as its G14 gate.
- `/strategy-exploration` (no sub-agent; **non-executing** state-machine, ADR 0008) — orchestrates the funnel above by tracking each candidate in a crash-safe append-only JSONL dossier (`strategy_exploration/dossier/<candidate>.jsonl`), printing the next leaf-skill to run, and **hard-refusing to advance a config that already failed a binding firewall layer** (a dead `config_hash` or any single-step G13 neighbour of one — no override). Never fires a backtest itself; the leaf skills keep all execution + approval gates. Deterministic mechanics in `scripts/explore.py` (`status` / `check` / `fire` / `record`); judgment + analyst-spawning (firewall-analyst death post-mortem, quant-analyst `DISTINCT` successor-lineage gate, per-`record` analyst) are Claude-driven per its SKILL.md. Enforces `LIVE_READY` = TRADABLE/PROVISIONAL + recorded Monte Carlo + (inline-script only) G14 PASS.

All skills call the Udgaard HTTP API directly.

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

**ALWAYS run `/pre-commit` before committing.** This skill runs all 5 code quality checks (backend tests, ktlint, detekt, frontend lint, frontend typecheck), verifies CLAUDE.md files are up to date, and — when backend changes touch code that the public-API skills document — verifies the affected skill files (`backtest`, `walk-forward`, `monte-carlo`) are still accurate via `.claude/scripts/skill-impact-check.sh`. All checks must pass before committing. Do NOT modify `claude_thoughts/`.

### Testing
- Write unit tests for strategy logic
- Test backtesting results against known values
- E2E tests use TestContainers (PostgreSQL) — requires Docker running
- `AbstractIntegrationTest` provides shared PostgreSQL container for integration tests

---

_Last Updated: 2026-05-30_

---

## Agent skills

### Issue tracker

Issues and PRDs are tracked as GitHub issues on `skrymer/trading` via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical triage roles mapped to repo labels (`needs-info` → `question`, others as-named). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
