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
│   │   └── ExternalConfigLoader.kt        # External configuration loading
│   ├── controller/
│   │   ├── QuoteController.kt             # GET /api/quotes/{symbol}, /api/quotes/bulk
│   │   ├── SymbolController.kt            # GET /api/symbols, /api/symbols/{symbol}
│   │   ├── EarningsController.kt          # GET /api/earnings/{symbol}
│   │   ├── OptionsController.kt           # GET /api/options/{symbol}, /api/options/{symbol}/find
│   │   ├── StatusController.kt            # GET /api/status
│   │   ├── IngestionController.kt         # POST /api/ingestion/initial|update/{symbol|all}
│   │   └── UiController.kt               # Thymeleaf admin UI (@ConditionalOnProperty app.ui.enabled)
│   ├── integration/
│   │   ├── Providers.kt                   # Provider interfaces (OhlcvProvider, IndicatorProvider, etc.)
│   │   ├── alphavantage/
│   │   │   ├── AlphaVantageProvider.kt    # Implements all 5 provider interfaces
│   │   │   └── dto/
│   │   └── massive/
│   │       ├── MassiveProvider.kt         # Polygon API - OhlcvProvider (daily updates)
│   │       └── dto/
│   ├── model/
│   │   ├── Models.kt                      # Quote, Symbol, Earning, RawBar, IngestionStatus, enums
│   │   └── OptionContractDto.kt
│   ├── repository/
│   │   ├── QuoteRepository.kt             # OHLCV + indicators (upsert, find, count)
│   │   ├── SymbolRepository.kt            # Symbol reference data
│   │   ├── EarningsRepository.kt          # Earnings data
│   │   └── IngestionStatusRepository.kt   # Ingestion tracking
│   └── service/
│       ├── IngestionService.kt            # Orchestrates initial + daily ingestion (async, semaphore)
│       ├── IndicatorCalculator.kt         # EMA, ATR, ADX, Donchian computation
│       └── RateLimiterService.kt          # Token bucket per provider
├── src/main/resources/
│   ├── application.properties
│   ├── secure.properties                  # API keys (not in git)
│   ├── db/migration/
│   │   ├── V1__Create_schema.sql          # quotes, earnings, symbols, ingestion_status tables
│   │   └── V2__Populate_symbols.sql       # 3,128 symbols
│   └── templates/                         # Thymeleaf admin UI (6 templates)
├── compose.yaml                           # PostgreSQL + Midgaard app
├── Dockerfile                             # Runtime image (eclipse-temurin:21-jre-alpine)
├── build.gradle
├── detekt.yml
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

1. **Initial Ingest**: AlphaVantage OHLCV + ATR/ADX indicators, local EMA/Donchian computation
2. **Daily Update**: Polygon/Massive API for recent bars, extend indicators from 250-bar seed
3. **Serving**: REST API returns enriched quotes with all indicators pre-computed

### Provider Interfaces (`integration/Providers.kt`)

- `OhlcvProvider` - Daily bars (AlphaVantage for initial, Massive for updates)
- `IndicatorProvider` - ATR, ADX
- `EarningsProvider` - Quarterly earnings
- `CompanyInfoProvider` - Company overview + sector
- `OptionsProvider` - Historical options pricing

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
```

## Development Checklist

1. PostgreSQL running (`docker compose up -d postgres`)
2. Database initialized (`./gradlew initDatabase`) — needed for jOOQ codegen
3. API keys in `src/main/resources/secure.properties`
4. `./gradlew ktlintCheck` passes
5. `./gradlew detekt` passes
6. `./gradlew compileKotlin` succeeds
