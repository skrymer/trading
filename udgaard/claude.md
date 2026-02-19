# Claude.md - Udgaard Backend (Kotlin/Spring Boot)

## Project Overview

Kotlin/Spring Boot backend for stock backtesting platform with H2 database storage, dynamic strategy system, and MCP server integration for Claude AI.

For complete project capabilities and overview, see the main CLAUDE.md file in the project root.

## Tech Stack

**Key Technologies:**
- **Language**: Kotlin 2.3.0
- **Framework**: Spring Boot 3.5.0
- **Database**: H2 2.2.224 (server mode via TCP)
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
│   │   │   └── MonteCarloController.kt
│   │   ├── model/                    # Domain models
│   │   │   ├── BacktestReport.kt
│   │   │   ├── BacktestContext.kt
│   │   │   └── TradePerformanceMetrics.kt
│   │   ├── service/                  # Business logic
│   │   │   ├── BacktestService.kt    # Core backtesting engine
│   │   │   ├── StrategyRegistry.kt   # Strategy discovery/management
│   │   │   ├── StrategySignalService.kt  # Signal evaluation
│   │   │   ├── DynamicStrategyBuilder.kt # Runtime strategy creation
│   │   │   ├── MonteCarloService.kt
│   │   │   └── ConditionRegistry.kt
│   │   └── strategy/                 # Trading strategies
│   │       ├── EntryStrategy.kt      # Entry interface
│   │       ├── ExitStrategy.kt       # Exit interface
│   │       ├── DetailedEntryStrategy.kt
│   │       ├── CompositeEntryStrategy.kt
│   │       ├── CompositeExitStrategy.kt
│   │       ├── StrategyDsl.kt        # DSL builder
│   │       ├── StockRanker.kt        # Ranking implementations
│   │       ├── RegisteredStrategy.kt # Auto-discovery annotation
│   │       ├── *EntryStrategy.kt     # Strategy implementations (discoverable via API)
│   │       ├── *ExitStrategy.kt
│   │       └── condition/            # Entry/exit conditions
│   │           ├── entry/            # Entry conditions (discoverable via getAvailableConditions MCP tool)
│   │           └── exit/             # Exit conditions
│   ├── data/                         # Data domain
│   │   ├── controller/
│   │   │   ├── StockController.kt
│   │   │   ├── BreadthController.kt
│   │   │   └── DataManagementController.kt
│   │   ├── integration/              # External API integrations
│   │   │   ├── alphavantage/         # PRIMARY data source
│   │   │   │   ├── AlphaVantageClient.kt
│   │   │   │   └── dto/
│   │   │   └── ovtlyr/              # Legacy (being removed)
│   │   │       ├── OvtlyrClient.kt
│   │   │       └── dto/
│   │   ├── model/                    # Domain models
│   │   │   ├── Stock.kt
│   │   │   ├── StockQuote.kt
│   │   │   ├── OrderBlock.kt
│   │   │   ├── MarketBreadthDaily.kt
│   │   │   └── SectorBreadthDaily.kt
│   │   ├── repository/               # jOOQ repositories
│   │   │   ├── StockJooqRepository.kt
│   │   │   ├── SymbolJooqRepository.kt
│   │   │   ├── MarketBreadthRepository.kt
│   │   │   └── SectorBreadthRepository.kt
│   │   └── service/
│   │       ├── StockService.kt       # Stock data management
│   │       ├── StockIngestionService.kt  # Bulk data ingestion
│   │       ├── TechnicalIndicatorService.kt  # EMAs, ATR, Donchian
│   │       ├── MarketBreadthService.kt
│   │       ├── SectorBreadthService.kt
│   │       ├── OrderBlockCalculator.kt
│   │       ├── SymbolService.kt
│   │       ├── DataStatsService.kt
│   │       └── RateLimiterService.kt
│   ├── portfolio/                    # Portfolio domain
│   │   ├── controller/
│   │   │   └── PortfolioController.kt
│   │   ├── integration/
│   │   │   ├── ibkr/                 # Interactive Brokers
│   │   │   └── options/              # Options data (AlphaVantage)
│   │   ├── model/
│   │   ├── repository/
│   │   └── service/
│   │       └── PortfolioService.kt
│   ├── controller/                   # Shared controllers
│   │   ├── CacheController.kt
│   │   └── SettingsController.kt
│   ├── mcp/                          # MCP server tools
│   │   └── StockMcpTools.kt
│   ├── config/                       # Configuration classes
│   │   └── CacheConfig.kt
│   └── UdgaardApplication.kt        # Main application
├── src/main/resources/
│   ├── application.properties        # Configuration
│   ├── secure.properties             # Credentials (not in git)
│   └── db/migration/                 # Flyway migrations
│       ├── V1__initial_schema.sql
│       ├── V2__Populate_symbols.sql
│       └── V3__Add_sector_symbols.sql
├── src/test/kotlin/                  # Unit tests (mirrors main structure)
├── build.gradle                      # Dependencies & build config
├── detekt.yml                        # Detekt static analysis config
└── detekt-baseline.xml               # Detekt baseline for existing issues
```

## Development Commands

```bash
# Database (must be running for build/migrations)
./gradlew startH2Server         # Start H2 TCP server on port 9092
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
VolatilityRanker()          // ATR as % of price (higher volatility = better)
DistanceFrom10EmaRanker()   // Distance from 10 EMA (closer = better)
CompositeRanker()           // Combines Vol (40%) + Dist10EMA (30%) + Sector (30%)
SectorStrengthRanker()      // Rank by sector bull percentage
RandomRanker()              // Random selection (baseline)
AdaptiveRanker()            // Volatility in trends, DistanceFrom10Ema in chop
```

### 5. Cooldown Period Logic

**Important:** Cooldown blocks entries for the full number of days specified.

**Example:** `cooldownDays = 5`
- Exit on Day 0
- Blocked on Days 1, 2, 3, 4, 5
- Can trade again on Day 6

### 6. MCP Integration

Expose tools for Claude AI to perform backtesting and analysis:

**Available MCP Tools:**
- `getStockData` - Fetch single stock with indicators
- `getMultipleStocksData` - Fetch multiple stocks
- `getMarketBreadth` - Get market breadth data
- `getStockSymbols` - List all available symbols
- `runBacktest` - Run full backtest with strategies

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
    val strategy = PlanAlphaEntryStrategy()

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
2. **New Condition**: Create in `condition/entry/` or `condition/exit/`, add DSL method in `StrategyDsl.kt`
3. **Services**: Make them stateless, use constructor injection
4. **Testing**: Write unit tests for all strategy logic
5. **Caching**: Consider `@Cacheable` for expensive operations
6. **Code quality**: Run `./gradlew ktlintCheck` and `./gradlew detekt` before committing

### Performance Considerations

- **Cache aggressively**: Stock data rarely changes, cache with 30min TTL
- **Batch queries**: Use jOOQ batch operations for bulk saves
- **Parallel processing**: Use Kotlin coroutines for independent operations
- **Database server mode**: Required for concurrent access from Flyway/jOOQ codegen and application

### Strategy Development Workflow

1. **Create condition class** in `backtesting/strategy/condition/`
2. **Implement interface** (`EntryCondition` or `ExitCondition`)
3. **Add to DSL** in `StrategyDsl.kt` for composability
4. **Write tests** to verify logic
5. **Use in composite strategy** or create standalone strategy with `@RegisteredStrategy`

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

# H2 Database (server mode)
spring.datasource.url=jdbc:h2:tcp://localhost:9092/trading
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# AlphaVantage API (configured via Settings UI or secure.properties)
alphavantage.api.baseUrl=https://www.alphavantage.co/query

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
