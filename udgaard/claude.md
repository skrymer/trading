# Claude.md - Udgaard Backend (Kotlin/Spring Boot)

## Project Overview

Kotlin/Spring Boot backend for stock backtesting platform with PostgreSQL database, dynamic strategy system, and MCP server integration for Claude AI.

For complete project capabilities and overview, see the main CLAUDE.md file in the project root.

## Tech Stack

**Key Technologies:**
- **Language**: Kotlin 2.3.0
- **Framework**: Spring Boot 3.5.0
- **Database**: PostgreSQL 17 (Docker Compose)
- **Testing**: TestContainers 2.0.3 (PostgreSQL) for E2E tests
- **Database Access**: jOOQ 3.19.23 (type-safe SQL queries)
- **Database Migrations**: Flyway (via net.ltgt.flyway plugin)
- **Build Tool**: Gradle 9.1.0
- **Caching**: Caffeine (via Spring Cache)
- **MCP**: Spring AI MCP Server 1.0.3
- **Code Quality**: ktlint 1.5.0, Detekt 2.0.0-alpha.2
- **Coroutines**: Kotlinx Coroutines 1.9.0

## Project Structure

```
udgaard/
├── src/main/kotlin/com/skrymer/udgaard/
│   ├── backtesting/                  # Backtesting domain
│   │   ├── controller/               # REST endpoints
│   │   │   ├── BacktestController.kt
│   │   │   ├── BacktestReportController.kt  # GET /api/backtest/reports, DELETE /{id}, POST /batch-delete
│   │   │   ├── MonteCarloController.kt
│   │   │   └── RegimeController.kt       # GET /api/regime/readout?after=&before= (daily 5-label series) + GET /api/regime/current (latest read as of the NY trading day, 404 when none) + GET /api/regime/decomposition/{backtestId} (a stored backtest's trades bucketed by published regime at entry, 404 unknown id) + GET /api/regime/sector-matrix?after=&before= (strategy-blind regime×sector return matrix, 400 inverted window); read-out + descriptive decomposition/matrix, never a deploy/cash switch (ADR 0010/0023)
│   │   ├── dto/                      # DTOs
│   │   │   ├── StrategyConfigDto.kt     # BacktestRequest incl. costBps (net-by-default 10 bps; 0 = gross) + riskFreeRatePct + creditIdleCash (default-ON, ADR 0016); WalkForwardRequest also incl. creditIdleCash
│   │   │   ├── MonteCarloRequestDto.kt
│   │   │   ├── ConditionSignalDtos.kt   # ConditionEvaluationRequest, StockConditionSignals (entry); ExitConditionEvaluationRequest, StockExitConditionSignals (exit)
│   │   │   ├── ConditionEvaluationResult.kt
│   │   │   └── StockWithSignals.kt
│   │   ├── model/                    # Domain models
│   │   │   ├── BacktestReport.kt        # Persisted gzip-compressed in a bytea column; additive-only schema evolution
│   │   │   ├── BacktestReportMetadata.kt # Metadata + Summary + ListItem for backtest_reports table
│   │   │   ├── BacktestResponseDto.kt  # API response — adds riskMetrics, benchmarkComparison, cagr, drawdownEpisodes (populated when sized); grossMinusNetEdgeSpread (avg round-trip cost in return terms, 0 on a gross run); leadershipRegimeDiagnostics (null unless the strategy gates on the leadership-gap regime, issue #83)
│   │   │   ├── RiskMetrics.kt          # sharpeRatio, sortinoRatio, calmarRatio, sqn, tailRatio
│   │   │   ├── BenchmarkComparison.kt  # benchmarkSymbol, correlation, beta, activeReturnVsBenchmark (NOT Jensen's alpha) + benchmarkCagr/MaxDrawdownPct/Calmar/Sharpe (benchmark's standalone metrics; diagnostic leg of the SPY baseline gate, ADR 0013)
│   │   │   ├── DrawdownEpisode.kt      # peak/trough/recoveryDate, maxDrawdownPct, declineDays/recoveryDays/totalDays
│   │   │   ├── BacktestContext.kt    # incl. costBps (round-trip transaction cost in bps; net-by-default 10, 0 = gross) + creditIdleCash + idleCashExpensePct (~0.10% SGOV haircut, subtracted once; ADR 0016) + sectorEtfQuoteMap (sector-ETF factor series for multi-factor residual rankers, warmup-loaded; ADR 0018) + leadershipRegimeMap + getLeadershipRegimeOn(date) (leadership-gap deploy/cash gate, missing date → cash; empty unless gated on; issue #83) + regimeReadoutMap + getRegimeLabel(date) (ADR 0023 5-label read-out, published label, null/missing → fail-closed; empty unless a strategy gates on a regime label)
│   │   │   ├── LeadershipRegimeDaily.kt # One day's leadership-gap read: gap/gapSmoothed/schmittOn/washoutActive/regimeOn + contributingN/standardError/trustworthy observability (issue #83)
│   │   │   ├── LeadershipRegimeDiagnostics.kt # In-window regime observability (onFraction, flipCount, spell lengths, onFractionByYear, untrustworthyDays, minContributingN) (issue #83)
│   │   │   ├── LeadershipRegimeParams.kt # Frozen pre-registered gate spec (FROZEN); lookbackBars/emaPeriod/deadBand/washout*/trust thresholds — changing a value is a methodology change, not a config knob (issue #83)
│   │   │   ├── RegimeLabel.kt        # The five canonical market-regime labels (THRUST/GRIND/NARROW/CHOP/CRISIS, CONTEXT.md "Market regimes"); a day with no defensible read carries no label (fail-closed null) (ADR 0023)
│   │   │   ├── RegimeReadoutDaily.kt # One day's regime read: instantaneous rawLabel + dwell-debounced publishedLabel (both null on a fail-closed undefensible day) (ADR 0023)
│   │   │   ├── RegimeReadoutParams.kt # Frozen pre-registered read-out spec (FROZEN); breadth/slope/gap/vol bands + dwell + washout + trust thresholds — changing a value is a methodology change, not a config knob (ADR 0023)
│   │   │   ├── Trade.kt              # Trade (w/ costPerShare netted out of profit) + EntryDecisionContext (cash/notional/cohort snapshot at decision time)
│   │   │   ├── PositionSizingConfig.kt  # startingCapital, sizer: SizerConfig, leverageRatio, drawdownScaling
│   │   │   ├── WalkForwardResult.kt    # WalkForwardWindow.outOfSampleStatsByEntryMonth: Map<"yyyy-MM", TradeStatsSummary> for sub-window regime gates (ADR 0006); spyBaselineComparison: SpyBaselineComparison? — SPY buy-and-hold Calmar baseline gate verdict (ADR 0013)
│   │   │   ├── SpyBaselineComparison.kt # SPY buy-and-hold Calmar baseline gate (ADR 0013): verdict (PASS/FAIL/INCONCLUSIVE), strategyCalmar, benchmark Calmar/CAGR/maxDD/Sharpe; stitched-OOS, Calmar-only, INCONCLUSIVE < 60 OOS days or strategy maxDD < 3%
│   │   │   ├── TradeStatsSummary.kt     # Month-agnostic closed-trade summary w/ additive raw fields; re-aggregate arbitrary month ranges + recompute Edge/Win rate/Profit factor (ADR 0006)
│   │   │   └── TradePerformanceMetrics.kt
│   │   ├── repository/               # jOOQ repositories
│   │   │   └── BacktestReportJooqRepository.kt  # save / findById / listAll / deleteById / deleteByIds; stores/reads the report as ByteArray
│   │   ├── service/                  # Business logic
│   │   │   ├── BacktestService.kt    # Core backtesting engine w/ capital-aware selection; records EntryDecisionContext on selected + missed trades; nets costBps round-trip cost into per-share Trade.profit at close (net-by-default)
│   │   │   ├── RiskMetricsService.kt # Computes Sharpe/Sortino/Calmar/SQN/tailRatio + SPY benchmark comparison + drawdown episodes from position-sized equity curve (USD-only); per-day rf via RiskFreeRateProvider keeps idle-cash crediting Sharpe-neutral (ADR 0016)
│   │   │   ├── RiskFreeRateService.kt # Loads the Midgaard treasury series, builds the single rf_step(t) RiskFreeRateProvider, loud 0% fallback when missing (ADR 0016)
│   │   │   ├── RiskFreeRateProvider.kt # Gross-yield series + SGOV expense haircut (subtracted once); most-recent-on-or-before (no look-ahead), ACT/360 stepRate fed to both the cash credit and the Sharpe rf (ADR 0016)
│   │   │   ├── StrategyRegistry.kt   # Strategy discovery/management
│   │   │   ├── StrategySignalService.kt  # Signal evaluation
│   │   │   ├── DynamicStrategyBuilder.kt # Runtime strategy creation
│   │   │   ├── MonteCarloService.kt
│   │   │   ├── PositionSizingService.kt  # Orchestrator: daily M2M drawdown + drawdown-responsive scaling via PositionSizer.scale(); full daily spine + idle-cash interest credit on max(0, cash − openNotional) when enabled (ADR 0016)
│   │   │   ├── sizer/                # Pluggable position sizers
│   │   │   │   ├── PositionSizer.kt          # Interface + SizingContext
│   │   │   │   ├── SizerConfig.kt           # Polymorphic DTO (atrRisk|percentEquity|kelly|volTarget)
│   │   │   │   ├── AtrRiskSizer.kt          # Risk = riskPct * equity / (nAtr * ATR)
│   │   │   │   ├── PercentEquitySizer.kt    # Notional = pct * equity
│   │   │   │   ├── KellySizer.kt            # Fractional Kelly from win rate + win/loss ratio
│   │   │   │   ├── VolatilityTargetSizer.kt # Target daily vol% with kATR proxy
│   │   │   │   └── LeverageCap.kt           # Portfolio-level leverage cap (applied outside sizer)
│   │   │   ├── WalkForwardService.kt    # Walk-forward validation (IS/OOS windows); bucketByEntryMonth() builds per-window OOS monthly TradeStatsSummary buckets (ADR 0006); computeSpyBaseline() stitches a SPY buy-and-hold curve through the identical OOS path (ADR 0013/0005) → spyBaselineComparison (Calmar-only gate); shares one window-independent leadership-gap regime series across all windows
│   │   │   ├── LeadershipRegimeService.kt # Pre-computes the leadership-gap regime (issue #83, ADR 0010 cash read-out): pure computeRegimeSeries (GAP = SPY 20-bar return − equal-weight universe mean, EMA10, Schmitt ±0.5% dead-band, sustained-washout crisis veto → regimeOn) + impure loadRegimeMap (warmup-loads SPY + LeadershipGapRepository over ~180 calendar days so EMA/Schmitt/washout seed before the window); only built when a strategy gates on it; diagnostics() summarises the in-window series
│   │   │   ├── RegimeReadoutService.kt   # Pre-registered 5-label regime read-out (ADR 0023): pure computeReadoutSeries (breadth level+slope / leadership-gap / SPY realized-vol decision table + sustained-washout CRISIS classifier → RegimeLabel; dwell-debounced publishedLabel; fail-closed null when undefensible) + impure loadReadoutSeries (warmup-loads SPY + equal-weight aggregate + breadth over ~180 calendar days) + loadReadoutMapIfGated(entry, exit, after, before) (loads the BacktestContext.regimeReadoutMap only when a strategy gates on a regime label via getConditions() — recurses into nested condition groups — else empty at zero cost; BacktestService + WalkForwardService call it at context-build time, WF shares one window-independent series); strategy-blind market classifier, never an engine-level deploy/cash switch — strategies opt in via the regimeLabelIn/regimeLabelExit conditions + RegimeController surfaces it over REST (ADR 0010)
│   │   │   ├── RegimeDecompositionService.kt # Descriptive-only per-regime decomposition of a stored backtest's closed trades (ADR 0023): published-label bucketing at entry date, per-bucket edge (mean per-trade % return, net of cost) + entry-month-clustered CR0 SE, 30-trade insufficient-N floor (stats nulled + flagged), unlabeled bucket, raw-vs-published divergence count, per-sector drill-down cells with per-cell floor; DTOs RegimeTradeSample/RegimeDecomposition(Row)/SectorCell; never a gate
│   │   │   ├── RegimeSectorMatrixService.kt # Strategy-blind regime×sector return matrix (ADR 0023): each sector ETF's daily returns bucketed by published label, annualized mean ×252, spell-clustered CR0 SE + spellCount; impure loadMatrix(after, before) over the sector-breadth-map universe; pure matrix(); never a gate
│   │   │   ├── BacktestResultStore.kt    # Backtest result store (backtest_reports table); gzip-compresses the report into a bytea column (high-candidate backtests overflow Postgres's ~256 MB jsonb cap), decompresses on read
│   │   │   ├── ConditionRegistry.kt     # Indexes Spring-discovered conditions by getMetadata().type; routes ConditionConfig to per-condition parseConfig
│   │   │   ├── ConditionConfigParsing.kt # numberOr/intOr/stringOr helpers used by every condition's parseConfig override
│   │   │   └── ScriptPredicateCompiler.kt # Compiles user Kotlin scripts into entry/exit predicates (the `script` conditions); needs the app run exploded — see Dockerfile
│   │   └── strategy/                 # Trading strategies
│   │       ├── EntryStrategy.kt      # Entry interface
│   │       ├── ExitStrategy.kt       # Exit interface
│   │       ├── DetailedEntryStrategy.kt
│   │       ├── CompositeEntryStrategy.kt
│   │       ├── CompositeExitStrategy.kt
│   │       ├── StrategyDsl.kt        # DSL builder
│   │       ├── StockRanker.kt        # Ranking implementations + warmupTradingDays() (pre-window history a trailing ranker needs loaded; default 0, overridden by Trailing/MarketResidual/MultiFactorResidual; ADR 0018) + rankCohort() (same-day cohort ranking hook for cross-sectional rankers e.g. FundamentalQualityRanker; default delegates to per-stock score → byte-identical; ADR 0020)
│   │       ├── RankerFactory.kt     # Ranker creation + RankerMetadata catalog (served by /api/backtest/rankers); incl. fundamentalquality → FundamentalQualityRanker
│   │       ├── RegisteredStrategy.kt # Auto-discovery annotation
│   │       ├── *EntryStrategy.kt     # Strategy implementations (discoverable via API)
│   │       ├── *ExitStrategy.kt
│   │       └── condition/            # Entry/exit conditions
│   │           ├── entry/            # Entry conditions (discoverable via getAvailableConditions MCP tool); incl. RegimeLabelCondition (type regimeLabelIn, labels: stringList, category Market — permits entries only when the published 5-label read-out labels the day in the allowed set, fail-closed on unlabeled days; ADR 0023)
│   │           └── exit/             # Exit conditions; incl. RegimeLabelExitCondition (type regimeLabelExit, labels: stringList, category Signal — exits when the published label is in the exit set, holds on unlabeled days; ADR 0023)
│   ├── data/                         # Data domain
│   │   ├── controller/
│   │   │   ├── StockController.kt
│   │   │   ├── BreadthController.kt
│   │   │   └── DataManagementController.kt
│   │   ├── integration/              # External API integrations
│   │   │   ├── StockProvider.kt      # Interface for OHLCV data + live quotes + earnings + fundamentals (LatestQuote, getLatestQuote, getLatestQuotes, getEarnings, getFundamentals); getEarnings/getFundamentals return null on provider failure (caller must fall back), empty list when symbol genuinely has none (ADR 0019)
│   │   │   ├── midgaard/             # OHLCV + pre-computed indicators + earnings + fundamentals + ovtlyr signals from Midgaard service (implements StockProvider)
│   │   │   │   ├── MidgaardClient.kt  # incl. getOvtlyrSignals(symbol) — ovtlyr buy/sell calls (vendor-specific, not on StockProvider) + getFundamentals(symbol) → point-in-time quarterly fundamentals (ADR 0019)
│   │   │   │   ├── MidgaardHttpConfig.kt  # RestClientCustomizer with connect (5s) + read (30s) timeouts; bounded so a Midgaard outage fails fast instead of hanging on infinite socket reads
│   │   │   │   └── dto/              # MidgaardDtos.kt includes MidgaardEarningDto, MidgaardFundamentalDto, MidgaardOvtlyrSignalDto
│   │   │   └── ovtlyr/              # Legacy (being removed)
│   │   │       ├── OvtlyrClient.kt
│   │   │       └── dto/
│   │   ├── mapper/                   # Data mappers
│   │   │   └── StockMapper.kt
│   │   ├── model/                    # Domain models
│   │   │   ├── Stock.kt              # Includes ovtlyrSignals + fundamentals: List<Fundamental>; point-in-time TTM accessors grossProfitTtmAsOf / operatingMarginTtmAsOf / operatingMarginTtmPriorYearAsOf / latestFundamentalAsOf (gated on filing_date, never fiscalDateEnding; ADR 0019)
│   │   │   ├── StockQuote.kt         # Includes qualityPercentile: Double? (cross-sectional GP/TA percentile from Midgaard; ADR 0019)
│   │   │   ├── OrderBlock.kt
│   │   │   ├── Earning.kt
│   │   │   ├── Fundamental.kt        # Point-in-time quarterly financial-statement line items (filingDate visibility key, isVisibleAsOf; loaded from Midgaard during ingestion; ADR 0019)
│   │   │   ├── OvtlyrSignal.kt       # Ovtlyr buy/sell signal (loaded from Midgaard during ingestion)
│   │   │   ├── AssetType.kt
│   │   │   ├── MarketSymbol.kt
│   │   │   ├── MarketBreadthDaily.kt
│   │   │   ├── SectorBreadthDaily.kt
│   │   │   └── EwReturnDaily.kt       # One day's equal-weight cross-section of trailing 20-bar returns (mean/stdev/contributingN) — the equal-weight leg of the leadership gap (issue #83)
│   │   ├── repository/               # jOOQ repositories
│   │   │   ├── StockJooqRepository.kt  # Includes findEarnings(symbol) + findFundamentals(symbol) used by ingestion fallback + findAllSymbolRecords() (the stocks-derived universe, ADR 0011)
│   │   │   ├── LeadershipGapRepository.kt  # ewReturnByDate(): full-universe equal-weight 20-bar-return aggregate (mean/stdev/contributingN) over the point-in-time STOCK-or-null universe (same population as breadth, ADR 0011); feeds the leadership-gap regime (issue #83)
│   │   │   ├── MarketBreadthRepository.kt
│   │   │   └── SectorBreadthRepository.kt
│   │   └── service/
│   │       ├── StockService.kt       # Stock data management
│   │       ├── StockIngestionService.kt  # Bulk data ingestion; loads ovtlyr signals via MidgaardClient.getOvtlyrSignals; resolveEarnings(symbol) + resolveFundamentals(symbol) helpers fall back to stockRepository.findEarnings/findFundamentals on provider failure (stale-but-present beats empty-because-we-failed; otherwise filters like noEarningsWithinDays / the quality gate silently invert; ADR 0019)
│   │       ├── TechnicalIndicatorService.kt  # EMAs, ATR, Donchian
│   │       ├── MarketBreadthService.kt
│   │       ├── SectorBreadthService.kt
│   │       ├── OrderBlockCalculator.kt
│   │       ├── SymbolService.kt
│   │       ├── DataStatsService.kt
│   │       └── ScheduledRefreshService.kt  # Scheduled automatic data refresh
│   ├── portfolio/                    # Portfolio domain
│   │   ├── controller/
│   │   │   ├── PortfolioController.kt
│   │   │   ├── PositionController.kt
│   │   │   └── OptionController.kt
│   │   ├── dto/                      # Request/response DTOs
│   │   ├── integration/
│   │   │   ├── broker/               # Broker adapter pattern (BrokerAdapter, TradeLot/RollPair/RollChain w/ companion-object factories)
│   │   │   ├── ibkr/                 # Interactive Brokers (client, adapter, mapper, dto/)
│   │   │   └── options/              # Options data (Midgaard)
│   │   ├── mapper/                   # Entity/DTO mappers
│   │   ├── model/                    # Portfolio.kt rich-domain (create()/withBalanceUpdated()/withSyncCompleted()/withRealizedPnlApplied()); PositionStats.kt also defines PositionWithExecutions aggregate root (realizedPnl/realizedPnlBase/totalCommissions getters; withClosed(closeDate)/withExecutionAdded(execution)/recalculated() transitions) per ADR 0001, plus StrategyBreakdownStats (fromPositions factory owns win-rate/edge/profit-factor math) surfaced as PositionStats.byStrategy; Position.kt has strategyGroupKey for per-strategy grouping; Execution.kt has closingFor(position, exitPrice, exitDate) factory
│   │   ├── repository/
│   │   │   ├── PortfolioJooqRepository.kt   # Portfolio CRUD lives here; controller + BrokerIntegrationService use it directly (no PortfolioService layer)
│   │   │   ├── PositionJooqRepository.kt    # Includes findWithExecutionsById(id) returning the PositionWithExecutions aggregate
│   │   │   ├── ExecutionJooqRepository.kt
│   │   │   ├── ForexLotJooqRepository.kt
│   │   │   ├── ForexDisposalJooqRepository.kt
│   │   │   └── CashTransactionJooqRepository.kt
│   │   └── service/
│   │       ├── PortfolioStatsService.kt
│   │       ├── PositionService.kt           # Thin orchestration; closePosition/closeManualPosition/recalculatePositionAggregates delegate to PositionWithExecutions + Portfolio.withRealizedPnlApplied
│   │       ├── BrokerIntegrationService.kt
│   │       ├── OptionPriceService.kt
│   │       ├── UnrealizedPnlService.kt
│   │       ├── ForexTrackingService.kt
│   │       └── CashTransactionService.kt
│   ├── scanner/                      # Scanner domain
│   │   ├── controller/
│   │   │   └── ScannerController.kt  # incl. GET /api/scanner/cohort-divergence
│   │   ├── dto/
│   │   │   └── ScannerDtos.kt        # ScanRequest, AddScannerTradeRequest, RollScannerTradeRequest, UpdateScannerTradeRequest, CloseScannerTradeRequest, OptionContractsRequest, OptionContractResponse, DrawdownStatsResponse, ValidateEntriesRequest, StrategyClosedStats, ClosedTradeStatsResponse, DivergenceConfig, CohortDivergenceReport, TodayMetrics, RollingMetrics, Alerts
│   │   ├── mapper/
│   │   │   └── ScannerTradeMapper.kt
│   │   ├── model/
│   │   │   ├── ScannerTrade.kt       # ScannerTrade (TradeStatus enum, close fields: exitPrice, exitDate, realizedPnl, closedAt; signalDate + signalSnapshot:EntrySignalDetails persisted verbatim as JSONB per ADR 0004 — immutable, never recomputed on read) — rich-domain methods: computeRealizedPnl(exitPrice), withClosed(...), withNotes(...)
│   │   │   ├── ScanResult.kt         # ScanResult, ScanResponse (latestDataDate), NearMissCandidate, ConditionFailureSummary, ExitCheckResult (usedLiveData, maxProximity, nearExits), ExitProximity, ExitCheckResponse, EntryValidationResult, EntryValidationResponse
│   │   │   ├── ScanRun.kt            # ScanRun + MatchedSymbol (persisted scan history)
│   │   │   └── CohortWindow.kt       # Aggregate root over a rolling window of ScanRuns (per ADR 0001)
│   │   ├── repository/
│   │   │   ├── ScannerTradeJooqRepository.kt  # incl. findBySignalDateBetween
│   │   │   └── ScanRunJooqRepository.kt       # ScanRun persistence
│   │   └── service/
│   │       ├── ScannerService.kt              # persists a ScanRun after each scan
│   │       └── CohortDivergenceService.kt     # Today vs rolling-window cohort-divergence diagnostic
│   ├── service/                     # Shared services
│   │   ├── SettingsService.kt       # Settings (DB-backed via UserSettingsJooqRepository)
│   │   └── UserSettingsJooqRepository.kt
│   ├── controller/                   # Shared controllers
│   │   ├── AuthController.kt
│   │   ├── CacheController.kt
│   │   └── SettingsController.kt
│   ├── mcp/                          # MCP server tools
│   │   ├── config/
│   │   │   └── McpConfiguration.kt
│   │   └── service/
│   │       └── StockMcpTools.kt
│   ├── config/                       # Configuration classes
│   │   ├── ApiKeyAuthenticationFilter.kt  # API key auth filter
│   │   ├── AppUserDetailsService.kt  # Spring Security user details
│   │   ├── CacheConfig.kt
│   │   ├── ClockConfig.kt            # NY-pinned Clock bean (America/New_York) for deterministic scan-date semantics
│   │   ├── ExternalConfigLoader.kt   # External config loading
│   │   ├── GlobalExceptionHandler.kt # Global exception handler
│   │   ├── SecurityConfig.kt         # Spring Security configuration
│   │   ├── SecurityProperties.kt     # Security properties
│   │   ├── UserRepository.kt         # User data access
│   │   └── UserSeeder.kt             # Initial user seeding
│   └── UdgaardApplication.kt        # Main application
├── src/main/resources/
│   ├── application.properties        # Configuration
│   ├── secure.properties             # Credentials (not in git)
│   └── db/migration/                 # Flyway migrations (V1-V31)
│       ├── V1__initial_schema.sql
│       ├── V2__Populate_symbols.sql
│       ├── V3__Add_sector_symbols.sql
│       ├── V4__Add_scanner_trades.sql
│       ├── V5__Add_users_table.sql
│       ├── V6__Move_sector_to_symbols.sql
│       ├── V7__Add_sector_to_stocks.sql
│       ├── V8__Drop_sector_from_symbols.sql
│       ├── V9__Add_user_settings.sql
│       ├── V10__Add_option_details_to_scanner_trades.sql
│       ├── V11__Add_fx_tracking.sql
│       ├── V12__Add_initial_fx_rate.sql
│       ├── V13__Add_cash_transactions.sql
│       ├── V14__Add_converted_amount_to_cash_transactions.sql
│       ├── V15__Add_order_block_trigger_date.sql
│       ├── V16__Add_listing_dates.sql
│       ├── V17__Add_close_fields_to_scanner_trades.sql
│       ├── V18__Add_delisted_symbols.sql
│       ├── V19__create_backtest_reports.sql
│       ├── V20__cleanup_earnings_schema.sql  # Drop legacy reportedeps/estimatedeps/symbol; add UNIQUE(stock_symbol, fiscal_date_ending)
│       ├── V21__Add_signal_snapshot_to_scanner_trades.sql  # signal_date + signal_snapshot JSONB columns per ADR 0004
│       ├── V22__Add_scan_runs.sql                          # scan_runs table for cohort-divergence diagnostic
│       ├── V23__Add_ovtlyr_signals.sql                     # ovtlyr_signals table (mirrors earnings: FK to stocks ON DELETE CASCADE, UNIQUE(stock_symbol, signal_date))
│       ├── V24__Compress_backtest_report.sql               # backtest_reports.report switched from jsonb to gzip-compressed bytea (jsonb ~256 MB cap overflow); clears existing rows
│       ├── V25__Add_leveraged_sector_basket_symbols.sql    # idempotent INSERTs adding 9 ETF/leveraged-ETF symbols to symbols table
│       ├── V26__Add_sma_and_52week_indicators.sql          # Adds sma_50/150/200 + 52-week high/low columns to stock_quotes (ingested from Midgaard)
│       ├── V27__Add_relative_strength_percentile.sql       # Adds relative_strength_percentile column to stock_quotes (ingested from Midgaard; ADR 0009)
│       ├── V28__Drop_symbols_table_move_asset_type_to_stocks.sql  # Drops the symbols catalogue; asset_type moves to stocks, backfilled in place (ADR 0011)
│       ├── V29__Add_fundamentals.sql                       # fundamentals table (one row per (stock_symbol, fiscal_date_ending), filing_date visibility key + income-statement/balance-sheet line items, FK to stocks; mirrored from Midgaard; ADR 0019)
│       ├── V30__Add_quality_percentile.sql                 # Adds quality_percentile column to stock_quotes (ingested from Midgaard; ADR 0019)
│       └── V31__Widen_fundamentals_numeric_columns.sql     # Widens fundamentals line-item columns DECIMAL(19,4)→(38,4) to tolerate EODHD bad prints (~1e16) without aborting the symbol's batch upsert (mirrors Midgaard V23; ADR 0019)
├── src/test/kotlin/                  # Unit + E2E tests
│   └── e2e/                          # E2E tests (TestContainers)
│       ├── AbstractIntegrationTest.kt  # Shared PostgreSQL container
│       ├── BacktestTestDataGenerator.kt  # 50-stock test data generator (per-range fixtures via populate(dsl, startDate, endDate); ConcurrentHashMap-keyed dedup; reset() to drop fixture state between scenarios)
│       ├── BacktestApiE2ETest.kt       # Backtest API E2E tests
│       ├── BacktestInvariantsE2ETest.kt # Engine invariant E2E tests
│       ├── BacktestPositionSizingE2ETest.kt  # Capital-aware position sizing E2E tests
│       ├── BacktestReportControllerE2ETest.kt   # GET/DELETE /api/backtest/reports E2E tests
│       ├── BacktestResultStorePersistenceE2ETest.kt # gzip-compressed store roundtrip + listing E2E tests
│       ├── BacktestRiskMetricsE2ETest.kt        # Sharpe/Sortino/Calmar/SPY benchmark E2E tests
│       ├── ExitConditionSignalsApiE2ETest.kt    # /api/stocks/{symbol}/exit-condition-signals E2E tests
│       ├── MonteCarloE2ETest.kt        # Monte Carlo simulation E2E tests
│       ├── WalkForwardE2ETest.kt       # Walk-forward validation E2E tests
│       ├── CashTransactionE2ETest.kt   # Cash transaction E2E tests
│       ├── ForexTrackingE2ETest.kt     # Forex tracking E2E tests
│       ├── IBKRBrokerImportE2ETest.kt  # IBKR broker import E2E tests
│       ├── PortfolioControllerE2ETest.kt  # Portfolio CRUD + sync E2E tests
│       ├── PositionControllerE2ETest.kt    # Position CRUD + close + roll-chain E2E tests
│       ├── ScannerCheckExitsE2ETest.kt    # Scanner check-exits E2E (live + stored bar)
│       ├── ScannerOptionContractsE2ETest.kt  # Scanner /option-contracts E2E
│       ├── ScannerRollE2ETest.kt          # Scanner roll-trade E2E
│       ├── ScannerScanE2ETest.kt          # Scanner /scan E2E
│       ├── ScannerStatsE2ETest.kt         # Scanner closed-trade stats + drawdown stats E2E
│       ├── ScannerTradeLifecycleE2ETest.kt  # Trades CRUD + close E2E
│       ├── ScannerTradeJooqRepositoryE2ETest.kt  # signalDate + signalSnapshot JSONB roundtrip per ADR 0004
│       ├── ScannerValidateEntriesE2ETest.kt  # Scanner /validate-entries E2E (incl. cap-rejection cases)
│       ├── StockJooqRepositoryVolumeFilterE2ETest.kt  # Volume-based filter behavior E2E
│       ├── TestDetailedEntryStrategy.kt  # Detailed-entry test fixture
│       ├── TestEntryStrategy.kt        # Test entry strategy fixture
│       └── TestExitStrategy.kt         # Test exit strategy fixture
├── src/test/kotlin/.../backtesting/
│   ├── model/                          # BacktestReportSectorStatsTest, BootstrapResamplingTechniqueTest, EdgeConsistencyScoreTest, TradeShufflingTechniqueTest
│   ├── service/                        # BacktestServiceTest, MonteCarloServiceTest, PositionSizingServiceTest
│   └── strategy/                       # CompositeEntryStrategyTest, CompositeExitStrategyTest, condition/
├── src/test/resources/
│   └── application-test.properties   # Test profile config
├── compose.yaml                      # Docker Compose (PostgreSQL for local dev)
├── Dockerfile                        # Runtime image (eclipse-temurin:25-jre-alpine)
├── init-databases.sql                # Init script for prod PostgreSQL (creates both trading + datastore DBs)
├── build.gradle                      # Dependencies & build config (includes springBoot { buildInfo() })
├── detekt.yml                        # Detekt static analysis config
└── detekt-baseline.xml               # Detekt baseline for existing issues
```

## Development Commands

```bash
# Database (must be running for build/migrations)
docker compose up -d postgres   # Start PostgreSQL on port 5432
./gradlew initDatabase          # Run Flyway migrations + jOOQ codegen

# Build & Run
./gradlew build                 # Full build (compile + test + ktlint + detekt)
./gradlew bootRun               # Start application
./gradlew compileKotlin         # Compile only (fast validation)
./gradlew bootJar               # Build JAR for production

# Testing
./gradlew test                  # Run all tests
./gradlew test --tests BacktestServiceTest  # Run specific test

# Code Quality
./gradlew ktlintCheck           # Check Kotlin formatting
./gradlew ktlintFormat          # Auto-fix formatting issues
./gradlew detekt                # Run static analysis
./gradlew detektBaseline        # Regenerate baseline for existing issues

# Cleanup
./gradlew clean build           # Clean and rebuild
```

## Key Features & Patterns

### 1. Dynamic Strategy System

Strategies are auto-discovered using the `@RegisteredStrategy` annotation:

```kotlin
@RegisteredStrategy(name = "MyStrategy", type = StrategyType.ENTRY)
class MyEntryStrategy : EntryStrategy {
    override fun test(stock: Stock, quote: StockQuote): Boolean {
        return quote.closePrice > quote.closePriceEMA20 && quote.isInUptrend
    }

    override fun description() = "Enters when price is above EMA20 and in uptrend"
}
```

**How it works:**
- `StrategyRegistry` scans for `@RegisteredStrategy` annotations on startup
- Strategies are automatically available in the API
- No need to manually register in controllers or configuration

### 2. Strategy DSL

Build complex composite strategies using Kotlin DSL:

```kotlin
val entryStrategy = entryStrategy {
    uptrend()
    priceAbove(20)
    marketUptrend()
    sectorUptrend()
    marketBreadthAbove(60.0)
    volumeAboveAverage(1.3, 20)
    minimumPrice(10.0)
}

val exitStrategy = exitStrategy {
    stopLoss(2.0)              // 2.0 ATR
    trailingStopLoss(2.7)      // 2.7 ATR trailing
    priceBelowEma(10)
    marketAndSectorDowntrend()
    exitBeforeEarnings(1)
}
```

**Available Conditions:** Use the `getAvailableConditions` MCP tool to discover all entry/exit conditions with their parameters. Conditions are defined in `strategy/condition/entry/` and `strategy/condition/exit/`, and added to the DSL in `StrategyDsl.kt`.

### 3. Backtesting Engine

Core backtesting with advanced features:

```kotlin
val report = backtestService.backtest(
    entryStrategy = myEntryStrategy,
    exitStrategy = myExitStrategy,
    stocks = stockList,
    after = LocalDate.parse("2020-01-01"),
    before = LocalDate.parse("2024-01-01"),
    maxPositions = 5,
    ranker = CompositeRanker(),
    cooldownDays = 10
)
```

**Key Features:**
- **Chronological processing** - No look-ahead bias
- **Position limits** - Rank and select best N stocks per day
- **Cooldown periods** - Prevent overtrading after exits
- **BacktestContext** - Provides market/sector breadth data to strategies and rankers
- **Missed trades tracking** - Track opportunities that were skipped

### 4. Stock Rankers

Used to pick the top N stocks when position limits apply:

```kotlin
VolatilityRanker()              // ATR as % of price (higher volatility = better)
DistanceFrom10EmaRanker()       // Distance from 10 EMA (closer = better)
CompositeRanker()               // Combines Vol (40%) + Dist10EMA (30%) + Sector (30%)
SectorStrengthRanker()          // Rank by sector bull percentage
RollingSectorStrengthRanker()   // Avg sector bull % over a trailing window (persistent strength)
SectorStrengthMomentumRanker()  // Δ sector bull % over a window (sectors gaining breadth)
TrailingReturnRanker()          // 12-1 cross-sectional momentum (252d return ending 21d ago, higher = better)
MarketResidualMomentumRanker()  // SPY-beta-stripped residual momentum (504d regression, 252-21d residual accum)
MultiFactorResidualMomentumRanker() // market+sector residual momentum (504d regress on SPY + sector ETF, 252-21d accum)
NearnessTo52WeekHighRanker()    // Nearness to own 52-week high (min(close / 52wk-high, 1.0), closer = better)
FundamentalQualityRanker()      // Cross-sectional GP/TA quality: 0.5·z-level (GP/TA) + 0.5·z-trend (op-margin YoY), each z-scored intra-subset — overrides rankCohort, not score (ADR 0019/0020)
SectorEdgeRanker()              // Rank by user-supplied sector priority order (Sector-Priority category)
SectorEdgeWithTightnessRanker() // Sector edge + base-tightness (ATR/close) tie-breaker within a sector
RandomRanker()                  // Random selection (baseline)
AdaptiveRanker()                // Volatility in trends, DistanceFrom10Ema in chop
```

`StockRanker` has two ranking modes (ADR 0020): the per-stock `score(stock, quote, context)` that most rankers override, and `rankCohort(candidates, context)` which scores the whole same-day cohort at once. The default `rankCohort` delegates to per-stock `score`, so every per-stock ranker is byte-identical; a *cross-sectional* ranker that standardizes or blends across the day's candidates (`FundamentalQualityRanker`) overrides `rankCohort` instead — a ranker overrides **at most one** of the two. The engine calls `rankCohort` at the single co-resident selection site in `BacktestService` (`resolvedEntries` for the current day), immediately before tie-break jitter + the capital-aware sort, never in the batched Pass-1 scan.

### 5. Cooldown Period Logic

**Important:** Cooldown blocks entries for the full number of days specified.

**Example:** `cooldownDays = 5`
- Exit on Day 0
- Blocked on Days 1, 2, 3, 4, 5
- Can trade again on Day 6

### 6. MCP Integration

Expose tools for Claude AI to perform backtesting and analysis:

**Available MCP Tools:**
- `getAvailableSymbols` - List symbols available for backtesting
- `getAvailableStrategies` - List discoverable entry/exit strategies
- `getAvailableRankers` - List ranker implementations + RankerMetadata
- `getAvailableConditions` - List discoverable entry/exit conditions + parameter shapes
- `getStrategyDetails` - Detailed strategy description by name
- `explainBacktestMetrics` - Human-readable explanation of risk/return metrics
- `getSystemStatus` - Health snapshot (DB + Midgaard reachability)

### 7. Caching Strategy

Use Caffeine cache for performance:

```kotlin
@Service
class StockService(
    private val stockRepository: StockJooqRepository
) {
    @Cacheable("stocks")
    fun getStock(symbol: String): Stock? {
        return stockRepository.findBySymbol(symbol)
    }
}
```

**Cache Configuration** (`application.properties`):
```properties
spring.cache.type=caffeine
spring.cache.cache-names=stocks,backtests
spring.cache.caffeine.spec=maximumSize=1000,expireAfterWrite=30m
```

## Code Style Guidelines

### Kotlin Best Practices

**Use Kotlin extension functions instead of manual operations:**

```kotlin
// Good
val validStocks = stocks.filterNotNull()
val bestStock = stocks.maxByOrNull { it.score }
val symbols = stocks.mapNotNull { it.symbol }

// Bad
val validStocks = stocks.filter { it != null }.map { it!! }
```

**Use scope functions appropriately:**

```kotlin
// Null-safe operations with let
stock?.let { s ->
    logger.info("Processing ${s.symbol}")
    repository.save(s)
}

// Object configuration with apply
val config = BacktestConfig().apply {
    maxPositions = 5
    cooldownDays = 10
}
```

**Prefer when expressions over if-else chains:**

```kotlin
val ranker = when (rankerName.lowercase()) {
    "volatility" -> VolatilityRanker()
    "composite" -> CompositeRanker()
    "adaptive" -> AdaptiveRanker()
    else -> throw IllegalArgumentException("Unknown ranker")
}
```

### Spring Boot Patterns

**Use constructor injection (not @Autowired):**

```kotlin
@RestController
@RequestMapping("/api/stocks")
class StockController(
    private val stockService: StockService
) {
    // Methods here
}
```

**Services should be stateless:**

```kotlin
@Service
class BacktestService(
    private val stockRepository: StockJooqRepository
) {
    fun backtest(
        entryStrategy: EntryStrategy,
        exitStrategy: ExitStrategy,
        stocks: List<Stock>
    ): BacktestReport {
        // Logic uses only parameters, no instance state
    }
}
```

### Class Member Ordering (enforced by Detekt ClassOrdering)

```kotlin
class MyService {
    // 1. Properties
    private val logger = LoggerFactory.getLogger(MyService::class.java)

    // 2. Init blocks
    init { /* ... */ }

    // 3. Secondary constructors

    // 4. Methods (public first, then private)
    fun publicMethod() { /* ... */ }
    private fun privateHelper() { /* ... */ }

    // 5. Companion object (ALWAYS last)
    companion object {
        const val SOME_CONSTANT = 42
    }
}
```

## Common Patterns

### Error Handling in Controllers

```kotlin
@PostMapping
fun runBacktest(@RequestBody request: BacktestRequest): ResponseEntity<BacktestReport> {
    try {
        val report = backtestService.backtest(/* ... */)
        return ResponseEntity.ok(report)
    } catch (e: IllegalArgumentException) {
        logger.error("Validation failed: ${e.message}", e)
        return ResponseEntity.badRequest().build()
    } catch (e: Exception) {
        logger.error("Unexpected error: ${e.message}", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
    }
}
```

### Testing Strategies

```kotlin
@Test
fun `test entry strategy with uptrend and price above EMA`() {
    // Given
    val stock = Stock(symbol = "AAPL", quotes = listOf(
        StockQuote(
            date = LocalDate.now(),
            closePrice = 150.0,
            closePriceEMA20 = 145.0,
            isInUptrend = true,
            symbol = "AAPL"  // IMPORTANT: always set symbol on quotes
        )
    ))
    val strategy = MyEntryStrategy()  // @RegisteredStrategy-annotated class

    // When
    val result = strategy.test(stock, stock.quotes.first())

    // Then
    assertTrue(result)
}
```

### DetailedEntryStrategy Interface

For strategies that need condition-level diagnostics:

```kotlin
@RegisteredStrategy(name = "MyStrategy", type = StrategyType.ENTRY)
class MyEntryStrategy: DetailedEntryStrategy {
    private val compositeStrategy = entryStrategy {
        uptrend()
        priceAbove(20)
        marketUptrend()
    }

    override fun description() = "My entry strategy"

    override fun test(stock: Stock, quote: StockQuote): Boolean =
        compositeStrategy.test(stock, quote)

    override fun testWithDetails(stock: Stock, quote: StockQuote) =
        compositeStrategy.testWithDetails(stock, quote)
}
```

**Benefits:** Condition-level diagnostics, actual values vs thresholds in UI, detailed pass/fail messages.

## Important Notes

### When Adding New Features

1. **New Strategy**: Add `@RegisteredStrategy` annotation - it will be auto-discovered
2. **New Condition**: Create in `condition/entry/` or `condition/exit/`, annotate with `@Component`, override `parseConfig` (uses `numberOr`/`intOr`/`stringOr` helpers, defaults from `this.field`), add DSL method in `StrategyDsl.kt`. Spring auto-discovery + `ConditionRegistry` route by `getMetadata().type` — no `DynamicStrategyBuilder` edit needed.
3. **Services**: Make them stateless, use constructor injection
4. **Testing**: Write unit tests for all strategy logic
5. **Caching**: Consider `@Cacheable` for expensive operations
6. **Code quality**: Run `./gradlew ktlintCheck` and `./gradlew detekt` before committing

### Performance Considerations

- **Cache aggressively**: Stock data rarely changes, cache with 30min TTL
- **Batch queries**: Use jOOQ batch operations for bulk saves
- **Parallel processing**: Use Kotlin coroutines for independent operations
- **Docker for tests**: TestContainers requires Docker running for E2E tests

### Strategy Development Workflow

1. **Create condition class** in `backtesting/strategy/condition/{entry,exit}/`
2. **Annotate with `@Component`** so Spring auto-discovers it (registered into `ConditionRegistry` by `getMetadata().type`)
3. **Implement interface** (`EntryCondition` or `ExitCondition`):
   - `evaluate` / `shouldExit`, `description()`, `getMetadata()`, `evaluateWithDetails`
   - `parseConfig(parameters: Map<String, Any>)` — return a new instance using `parameters.numberOr("key", this.field)` (and `intOr` / `stringOr`) from `service/ConditionConfigParsing.kt`. Constructor defaults are the single source of truth — missing parameters fall back to `this.field`. No separate `when` branch in `DynamicStrategyBuilder` is needed.
4. **Add to DSL** in `StrategyDsl.kt` for in-code composability
5. **Write tests** to verify logic (and per-condition `parseConfig` round-trip)
6. **Use in composite strategy** or create standalone strategy with `@RegisteredStrategy`

### Backend Development Checklist

1. **All functionality has tests** - Every new feature must have corresponding unit tests
2. **Verify all tests pass** - Run `./gradlew test` after implementation
3. **Code quality passes** - Run `./gradlew ktlintCheck` and `./gradlew detekt`
4. **Tests follow Given-When-Then structure**:
   ```kotlin
   @Test
   fun `test name describing expected behavior`() {
       // Given - Set up test data
       val stock = Stock(symbol = "AAPL")

       // When - Execute functionality
       val result = strategy.test(stock, quote)

       // Then - Verify outcome
       assertTrue(result)
   }
   ```

## Quick Reference

### Configuration Properties

```properties
# Application
spring.application.name=udgaard
server.servlet.context-path=/udgaard

# PostgreSQL Database
spring.datasource.url=jdbc:postgresql://localhost:5432/trading
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.username=trading
spring.datasource.password=trading

# Midgaard Reference Data Service (OHLCV + pre-computed indicators, options, FX rates)
midgaard.base-url=http://localhost:8081

# Ovtlyr API (configured via Settings UI or secure.properties)
ovtlyr.header.projectId=Ovtlyr.com_project1

# MCP Server
spring.ai.mcp.server.name=backtesting-mcp-server
spring.ai.mcp.server.version=1.0.0

# Caching
spring.cache.type=caffeine
spring.cache.cache-names=stocks,backtests
spring.cache.caffeine.spec=maximumSize=1000,expireAfterWrite=30m

# Timeouts (for long backtests)
spring.mvc.async.request-timeout=1800000
```

### Logging

```kotlin
private val logger = LoggerFactory.getLogger(MyClass::class.java)

logger.info("Processing stock ${stock.symbol}")
logger.warn("Strategy returned no trades")
logger.error("Failed to fetch data: ${e.message}", e)
logger.debug("Intermediate calculation: $value")
```

## Useful Links

- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [jOOQ Documentation](https://www.jooq.org/doc/latest/manual/)
- [Caffeine Cache](https://github.com/ben-manes/caffeine)
- [Spring AI MCP Server](https://docs.spring.io/spring-ai/reference/api/mcp-server.html)
- [Detekt Documentation](https://detekt.dev/)
- [Gradle Documentation](https://docs.gradle.org/)
