# Claude.md - Midgaard Reference Data Service (Kotlin/Spring Boot)

## Project Overview

Standalone reference data service providing OHLCV stock data with pre-computed technical indicators. Serves as the data backbone for Udgaard. Runs on port 8081.

For complete project context, see the main CLAUDE.md in the project root.

## Tech Stack

- **Language**: Kotlin 2.3.0
- **Framework**: Spring Boot 3.5.0
- **Database**: PostgreSQL 17 (Docker Compose, port 5433)
- **Database Access**: jOOQ 3.19.23 (type-safe SQL)
- **Database Migrations**: Flyway (via net.ltgt.flyway plugin)
- **Build Tool**: Gradle 9.1.0
- **Security**: Spring Security with API key authentication
- **Coroutines**: Kotlinx Coroutines 1.9.0
- **Code Quality**: ktlint 1.5.0, Detekt 2.0.0-alpha.2
- **UI**: Thymeleaf (dev-only admin dashboard)

## Project Structure

```
midgaard/
├── src/main/kotlin/com/skrymer/midgaard/
│   ├── MidgaardApplication.kt
│   ├── config/
│   │   ├── SecurityConfig.kt              # Spring Security filter chain (conditional API key auth)
│   │   ├── SecurityProperties.kt          # app.security.enabled, app.security.api-key-hash
│   │   ├── ApiKeyAuthenticationFilter.kt  # X-API-Key header filter (SHA-256 + constant-time compare)
│   │   ├── ProviderConfiguration.kt       # Provider bean definitions
│   │   ├── ExternalConfigLoader.kt        # External configuration loading
│   │   ├── GlobalExceptionHandler.kt      # Global exception handler
│   │   ├── CacheConfiguration.kt          # Spring Cache (`CaffeineCacheManager`) — hosts `eodhdFundamentals` (no TTL) plus `fxCurrent` (1h TTL) and `fxHistoricalSeries` (24h TTL) used by the FX provider series cache
│   │   ├── DnsPrewarmer.kt                # Pre-resolves provider hostnames on ApplicationReadyEvent to avoid JVM negative-DNS-cache races during bulk ingest
│   │   └── VersionAdvice.kt              # Build version info via @ControllerAdvice
│   ├── controller/
│   │   ├── QuoteController.kt             # GET /api/quotes/{symbol}, /api/quotes/bulk, /api/quotes/{symbol}/latest
│   │   ├── SymbolController.kt            # GET /api/symbols, /api/symbols/{symbol}
│   │   ├── EarningsController.kt          # GET /api/earnings/{symbol}
│   │   ├── OptionsController.kt           # GET /api/options/{symbol}, /api/options/{symbol}/find
│   │   ├── ExchangeRateController.kt      # GET /api/fx/rate, /api/fx/rate/historical — depends on `@Qualifier("fx") FxProvider`, wraps suspend calls with `runBlocking`
│   │   ├── StatusController.kt            # GET /api/status
│   │   ├── IngestionController.kt         # POST /api/ingestion/initial|update/{symbol|all}
│   │   ├── IntegrityController.kt         # POST /api/integrity/validate, GET /api/integrity/violations
│   │   └── UiController.kt               # Thymeleaf admin UI (@ConditionalOnProperty app.ui.enabled) — adds /integrity page + violation badge on /ingestion
│   ├── integration/
│   │   ├── Providers.kt                   # Provider interfaces (OhlcvProvider, IndicatorProvider, EarningsProvider, CompanyInfoProvider, FxProvider, QuoteProvider, OptionsProvider)
│   │   ├── ProviderIds.kt                 # Shared provider ID constants ("alphavantage", "eodhd", "massive", "finnhub")
│   │   ├── FxCacheNames.kt                # Shared cache-name constants (`fxCurrent`, `fxHistoricalSeries`) + `closestRateAtOrBefore` walk-back-5-days helper used by both FX providers
│   │   ├── alphavantage/
│   │   │   ├── AlphaVantageProvider.kt    # Implements OhlcvProvider, IndicatorProvider, EarningsProvider, CompanyInfoProvider, FxProvider, OptionsProvider; FX delegates to AlphaVantageFxClient
│   │   │   ├── AlphaVantageFxClient.kt    # Sibling-class for cross-boundary @Cacheable interception; wraps CURRENCY_EXCHANGE_RATE + FX_DAILY?outputsize=full into series cache
│   │   │   └── dto/
│   │   ├── finnhub/
│   │   │   ├── FinnhubProvider.kt         # Implements QuoteProvider (live quotes); self-rate-limits
│   │   │   └── dto/
│   │   ├── massive/
│   │   │   ├── MassiveProvider.kt         # Polygon API - OhlcvProvider impl; @Component registers with rate limiter but currently NOT wired into any @Bean (kept for future re-enable)
│   │   │   └── dto/
│   │   └── eodhd/
│   │       ├── EodhdProvider.kt           # Implements OhlcvProvider, IndicatorProvider, EarningsProvider, CompanyInfoProvider, FxProvider; FX delegates to EodhdFxClient
│   │       ├── EodhdFxClient.kt           # Sibling-class for cross-boundary @Cacheable interception; wraps `/real-time/{pair}.FOREX` + `/eod/{pair}.FOREX` into series cache
│   │       └── dto/
│   ├── model/
│   │   ├── Models.kt                      # Quote, Symbol, Earning, RawBar, IngestionStatus, MarketHoliday, enums
│   │   └── OptionContractDto.kt
│   ├── repository/
│   │   ├── QuoteRepository.kt             # OHLCV + indicators (upsert, find, count)
│   │   ├── SymbolRepository.kt            # Symbol reference data
│   │   ├── EarningsRepository.kt          # Earnings data
│   │   ├── IngestionStatusRepository.kt   # Ingestion tracking
│   │   ├── ProviderConfigRepository.kt    # Provider configuration data
│   │   └── MarketHolidayRepository.kt     # Read-only US exchange holiday lookup (used by IngestionService to drop phantom bars)
│   ├── integrity/                         # Data integrity framework (Spring auto-wires List<DataIntegrityValidator>)
│   │   ├── DataIntegrityValidator.kt      # Interface — implementations are @Component
│   │   ├── Violation.kt                   # Rolled-up: 1 Violation per (validator, invariant) tuple per run; carries count + sampleSymbols (top 10)
│   │   ├── SectorIntegrityValidator.kt    # I1-I5: sector canonical, sector_symbol canonical, sector↔sector_symbol consistency, delisted⇒sector non-null, active+OHLCV⇒sector non-null
│   │   ├── DataIntegrityService.kt        # runAll() = fresh snapshot of all validators + truncate-and-replace persistence
│   │   └── ViolationRepository.kt         # jOOQ; findAll() ordered by severity asc; truncate-all on replace
│   └── service/
│       ├── IngestionService.kt            # Provider-agnostic orchestrator; **delisted-immutable rule** (skip sector update when delistedAt != null) + SectorNormalizer for active rows + drift warn-log
│       ├── DelistedIngestionService.kt    # V6 baseline service; defensively normalizes via SectorNormalizer.canonicalize before storing
│       ├── sector/
│       │   ├── SectorNormalizer.kt        # Canonicalize raw provider sector → 1 of 11 UPPERCASE GICS names OR null. VARIANTS map: Financials/Financial → FINANCIAL SERVICES, Materials → BASIC MATERIALS. UNCLASSIFIED: Other/NONE/empty → null
│       │   └── SicToGicsMapping.kt        # SEC SIC → GICS sector for V6 EDGAR-derived classification
│       ├── IndicatorsMode.kt              # LOCAL vs API enum for app.ingest.indicators knob
│       ├── IndicatorCalculator.kt         # EMA, ATR, ADX, Donchian computation (used by LOCAL indicator mode)
│       ├── RateLimiterService.kt          # Token bucket per provider (providers self-acquire permits)
│       ├── ApiKeyService.kt              # API key management
│       └── ScheduledIngestionService.kt  # Scheduled automatic data ingestion
├── src/main/resources/
│   ├── application.properties
│   ├── secure.properties                  # API keys (not in git)
│   ├── db/migration/
│   │   ├── V1__Create_schema.sql          # quotes, earnings, symbols, ingestion_status tables
│   │   ├── V2__Populate_symbols.sql       # 3,128 symbols
│   │   ├── V3__Add_sector_symbol.sql      # Add sector to symbols
│   │   ├── V4__Add_provider_config.sql    # Provider config table
│   │   ├── V5__Add_delisted_columns_to_symbols.sql
│   │   ├── V6__Add_delisted_symbols.sql
│   │   ├── V7__Add_market_holidays.sql     # 349 US exchange holidays 1995-2030 (EODHD seed; revisit before 2030)
│   │   ├── V8__Restore_clobbered_v6_sectors.sql  # Re-INSERT V6 with ON CONFLICT DO UPDATE — restores sectors clobbered to 'Other' / variants by IngestionService; adds CHECK constraints for I1+I2 at the DB layer
│   │   └── V9__Create_data_integrity_violations.sql  # Storage for DataIntegrityValidator framework
│   └── templates/                         # Thymeleaf admin UI (7 templates incl. integrity.html)
├── compose.yaml                           # PostgreSQL + Midgaard app
├── Dockerfile                             # Runtime image (eclipse-temurin:25-jre-alpine)
├── build.gradle                           # Gradle build config
├── detekt.yml                             # Detekt configuration
└── settings.gradle
```

## Development Commands

```bash
# Database (must be running for build/migrations)
docker compose up -d postgres   # Start PostgreSQL on port 5433
./gradlew initDatabase          # Run Flyway migrations + jOOQ codegen

# Build & Run
./gradlew build                 # Full build (compile + test + ktlint + detekt)
./gradlew bootRun               # Start application (http://localhost:8081)
./gradlew compileKotlin         # Compile only (fast validation)
./gradlew bootJar               # Build JAR for Docker

# Code Quality
./gradlew ktlintCheck           # Check Kotlin formatting
./gradlew ktlintFormat          # Auto-fix formatting issues
./gradlew detekt                # Run static analysis

# Docker
./gradlew bootJar && docker compose up -d  # Full stack deployment
```

## Architecture

### Data Flow

1. **Initial Ingest**: OHLCV + ATR/ADX indicators from the active `ohlcv`/`indicators` provider (AlphaVantage or EODHD), local EMA/Donchian computation. Bars stamped to US market-holiday dates (per `market_holidays` table) and zero-volume synthetic-filler bars are dropped before persistence.
2. **Daily Update**: Same `ohlcv` provider as initial ingest — fetches recent bars and extends indicators from the 250-bar seed (no separate daily-update provider; the `dailyUpdateOhlcv` qualifier was removed). Same holiday + zero-volume filter is applied.
3. **Serving**: REST API returns enriched quotes with all indicators pre-computed

### Provider Interfaces (`integration/Providers.kt`)

- `OhlcvProvider` - Daily bars for both initial ingest and daily updates (AlphaVantage or EODHD via the `app.ingest.provider` toggle; single `ohlcv` bean serves both code paths)
- `IndicatorProvider` - ATR, ADX (AlphaVantage or EODHD via the toggle; only used when `app.ingest.indicators=API`)
- `EarningsProvider` - Quarterly earnings (AlphaVantage or EODHD via the toggle)
- `CompanyInfoProvider` - Company overview + sector (AlphaVantage or EODHD via the toggle)
- `FxProvider` - Currency exchange rates (current + historical), backs `ExchangeRateController`. Toggleable via `app.fx.provider` (defaults to `eodhd` via `matchIfMissing`); FX quota is operationally separate from OHLCV/indicator quota so it has its own toggle.
- `QuoteProvider` - Live/latest quotes (Finnhub)
- `OptionsProvider` - Historical options pricing (AlphaVantage)

**Provider selection is centralized in `ProviderConfiguration`.** `IngestionService` injects bare interface beans (`@Bean("ohlcv")`, `@Bean("indicators")`, `@Bean("earnings")`, `@Bean("companyInfo")`) — no per-provider branching inside the service. `@ConditionalOnProperty` on each bean reads `app.ingest.provider` to pick the active implementation. When `app.ingest.provider=eodhd` is set, **all four** roles (OHLCV + indicators + earnings + company info) route to EODHD via its All-In-One plan; the same `ohlcv` bean handles both initial ingest and daily updates (the `dailyUpdateOhlcv` qualifier was removed, so Massive/Polygon is no longer wired into the daily-update path). Midgaard's in-process default (SpEL fallback in `ProviderConfiguration`) is `alphavantage`, but both `udgaard/compose.yaml` and `compose.prod.yaml` set `APP_INGEST_PROVIDER=eodhd` by default.

**Indicator computation mode.** `app.ingest.indicators=LOCAL` (default) recomputes ATR/ADX from raw OHLCV via `IndicatorCalculator`. Set `app.ingest.indicators=API` to call the indicator provider's API instead.

**Rate limiting.** Each provider self-rate-limits via `rateLimiterService.acquirePermit(PROVIDER_ID)` using the shared `ProviderIds` constants ("alphavantage", "eodhd", "massive", "finnhub"). `IngestionService` no longer acquires permits.

API keys for all providers (including EODHD) live in the `provider_config` table and are managed through the admin UI at `/providers`. `ApiKeyService` exposes `getEodhdApiKey()` and falls back to the `eodhd.api.key` property when the row is absent.

### Indicator Computation (`service/IndicatorCalculator.kt`)

- **EMA**: Periods [5, 10, 20, 50, 100, 200], bootstraps with SMA
- **ATR**: 14-period, Wilder's smoothing
- **ADX**: 14-period, directional movement, Wilder's smoothing
- **Donchian**: 5-period upper band (highest high)

### Rate Limiting (`service/RateLimiterService.kt`)

Token bucket per provider with per-second, per-minute, and per-day limits. Coroutine-safe with Mutex.

### Security (`config/SecurityConfig.kt`)

- `app.security.enabled=false` (default): All endpoints open for local dev
- `app.security.enabled=true`: `/api/**` requires `X-API-Key` header, `/actuator/health` always public
- API key hashed with SHA-256, compared with `MessageDigest.isEqual()` (constant-time)
- Stateless sessions, CSRF disabled, JSON 401 response

### Admin UI (`controller/UiController.kt`)

- Controlled by `@ConditionalOnProperty("app.ui.enabled")`, enabled by default
- Set `APP_UI_ENABLED=false` in production to disable entirely
- Pages: dashboard, symbols, symbol-detail, ingestion progress, providers

## Database Schema

### Tables (Flyway V1)

- **quotes**: OHLCV + indicators (symbol + quote_date PK)
  - Indicators: atr, adx, ema_5/10/20/50/100/200, donchian_upper_5, indicator_source
- **earnings**: Quarterly earnings (symbol + fiscal_date_ending PK)
- **symbols**: Reference data with asset_type and sector (3,128 entries from V2)
- **ingestion_status**: Per-symbol tracking (bar_count, last_bar_date, status)
- **market_holidays** (V7): US exchange holiday calendar (exchange + holiday_date PK), 349 rows for 1995-2030 sourced from EODHD `/exchange-details/US`. Static seed; revisit before 2030. `IngestionService` filters out provider bars stamped to these dates (initial ingest + daily update) to avoid phantom rows skewing breadth queries.

### jOOQ Codegen

Generated to `build/generated-src/jooq/main` package `com.skrymer.midgaard.jooq`. Requires PostgreSQL running. `generateJooq` depends on `flywayMigrate`.

## Code Style

### Kotlin Conventions

- Constructor injection (no `@Autowired`)
- Extension functions: `mapNotNull`, `filterNotNull`, `firstOrNull`
- `when` over if-else chains
- Sealed classes for type hierarchies
- Scope functions: `let` for null-safe, `apply` for configuration

### Class Member Ordering (enforced by Detekt)

1. Properties
2. Init blocks
3. Secondary constructors
4. Methods (public, then private)
5. Companion object (always last)

### Spring Boot Patterns

```kotlin
@RestController
@RequestMapping("/api/quotes")
class QuoteController(
    private val quoteRepository: QuoteRepository,
) {
    @GetMapping("/{symbol}")
    fun getQuotes(
        @PathVariable symbol: String,
        @RequestParam(required = false) startDate: LocalDate?,
        @RequestParam(required = false) endDate: LocalDate?,
    ): List<Quote> = quoteRepository.findBySymbol(symbol, startDate, endDate)
}
```

### Repository Pattern (jOOQ)

```kotlin
@Repository
class QuoteRepository(private val dsl: DSLContext) {
    fun findBySymbol(symbol: String): List<Quote> =
        dsl.selectFrom(QUOTES)
            .where(QUOTES.SYMBOL.eq(symbol))
            .orderBy(QUOTES.QUOTE_DATE)
            .fetchInto(Quote::class.java)

    fun upsertQuotes(quotes: List<Quote>) {
        dsl.batched { ctx ->
            quotes.forEach { quote ->
                ctx.dsl().insertInto(QUOTES)
                    .set(/* ... */)
                    .onConflict(QUOTES.SYMBOL, QUOTES.QUOTE_DATE)
                    .doUpdate()
                    .set(/* ... */)
                    .execute()
            }
        }
    }
}
```

## Configuration Reference

```properties
# Server
server.port=8081

# Database (local dev uses port 5433, Docker internal uses 5432)
spring.datasource.url=jdbc:postgresql://localhost:5433/datastore
spring.datasource.username=trading
spring.datasource.password=trading

# Security (disabled for local dev)
app.security.enabled=false
app.security.api-key-hash=

# Admin UI (enabled for local dev, disabled in Docker)
app.ui.enabled=true

# Rate Limits
provider.alphavantage.requestsPerSecond=5
provider.alphavantage.requestsPerMinute=75
provider.alphavantage.requestsPerDay=75000
provider.massive.requestsPerSecond=80
provider.massive.requestsPerMinute=1000
provider.massive.requestsPerDay=100000

# Provider toggles
# app.ingest.provider picks OHLCV/indicators/earnings/companyInfo (defaults to alphavantage in code; both compose files pin eodhd).
# app.fx.provider picks the FxProvider behind /api/fx/rate (defaults to eodhd via matchIfMissing).
app.ingest.provider=eodhd
app.fx.provider=eodhd
```

## Development Checklist

1. PostgreSQL running (`docker compose up -d postgres`)
2. Database initialized (`./gradlew initDatabase`) — needed for jOOQ codegen
3. API keys in `src/main/resources/secure.properties`
4. `./gradlew ktlintCheck` passes
5. `./gradlew detekt` passes
6. `./gradlew compileKotlin` succeeds
