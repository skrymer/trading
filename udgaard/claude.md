# Claude.md - Udgaard Backend (Kotlin/Spring Boot)

## Project Overview

Kotlin/Spring Boot backend for stock backtesting platform with H2 database storage, dynamic strategy system, and MCP server integration for Claude AI.

For complete project capabilities and overview, see the main CLAUDE.md file in the project root.

## Tech Stack

For complete tech stack details, see the main CLAUDE.md file in the project root.

**Key Technologies:**
- **Language**: Kotlin 2.1.21
- **Framework**: Spring Boot 3.5.0
- **Database**: H2 (file-based SQL database with JPA/Hibernate)
- **Build Tool**: Gradle 8.14.2
- **Caching**: Caffeine (via Spring Cache)
- **MCP**: Spring AI MCP Server

## Project Structure

```
udgaard/
├── src/main/kotlin/com/skrymer/udgaard/
│   ├── controller/              # REST API endpoints
│   │   ├── BacktestController.kt
│   │   ├── StockController.kt
│   │   ├── EtfController.kt
│   │   ├── BreadthController.kt
│   │   └── dto/                 # Request/Response DTOs
│   ├── service/                 # Business logic layer
│   │   ├── BacktestService.kt   # Core backtesting engine
│   │   ├── StockService.kt      # Stock data management
│   │   ├── StrategyRegistry.kt  # Strategy discovery/management
│   │   ├── StrategySignalService.kt  # Signal evaluation
│   │   ├── DynamicStrategyBuilder.kt # Runtime strategy creation
│   │   └── EtfStatsService.kt
│   ├── model/                   # Domain models
│   │   ├── Stock.kt             # Stock entity with quotes
│   │   ├── StockQuote.kt        # Price quote with indicators
│   │   ├── BacktestReport.kt    # Backtest results
│   │   ├── Trade.kt             # Individual trade
│   │   ├── EtfEntity.kt         # ETF with holdings
│   │   └── strategy/            # Trading strategies
│   │       ├── EntryStrategy.kt
│   │       ├── ExitStrategy.kt
│   │       ├── CompositeEntryStrategy.kt
│   │       ├── CompositeExitStrategy.kt
│   │       ├── RegisteredStrategy.kt  # Annotation
│   │       ├── StrategyDsl.kt         # DSL builder
│   │       ├── StockRanker.kt
│   │       └── condition/             # Strategy conditions
│   ├── repository/              # JPA repositories
│   │   ├── StockRepository.kt
│   │   ├── EtfRepository.kt
│   │   └── BreadthRepository.kt
│   ├── integration/             # External API integrations
│   │   ├── ovtlyr/              # Ovtlyr stock data provider
│   │   │   ├── OvtlyrClient.kt
│   │   │   └── dto/             # API response DTOs
│   │   └── alphavantage/        # Alpha Vantage API
│   │       ├── AlphaVantageClient.kt
│   │       └── dto/
│   ├── mcp/                     # MCP server tools
│   │   └── StockMcpTools.kt     # Claude AI integration
│   ├── config/                  # Configuration classes
│   │   └── CacheConfig.kt       # Caffeine cache setup
│   └── UdgaardApplication.kt    # Main application
├── src/main/resources/
│   ├── application.properties   # Configuration
│   └── secure.properties        # Credentials (not in git)
├── src/test/kotlin/             # Unit tests
├── build.gradle                 # Dependencies & build config
└── compose.yaml                 # Docker compose (deprecated - was for MongoDB)
```

## Development Commands

```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun

# Run tests
./gradlew test

# Compile Kotlin only (faster for validation)
./gradlew compileKotlin

# Build JAR for production
./gradlew bootJar

# Clean build artifacts
./gradlew clean

# Run with specific profile
./gradlew bootRun --args='--spring.profiles.active=dev'
```

## Key Features & Patterns

### 1. Dynamic Strategy System

Strategies are auto-discovered using the `@RegisteredStrategy` annotation:

```kotlin
@RegisteredStrategy(name = "MyStrategy", type = StrategyType.ENTRY)
class MyEntryStrategy : EntryStrategy {
    override fun test(stock: Stock, quote: StockQuote): Boolean {
        // Entry logic
        return quote.closePrice > quote.ema20 && quote.buySignal
    }

    override fun description() = "Enters when price is above EMA20 and buy signal is present"
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
    buySignal()
    marketInUptrend()
    sectorInUptrend()
    priceAboveEma(20)
    marketHeatmapAbove(50)
}

val exitStrategy = exitStrategy {
    stopLoss(0.5)  // 0.5 ATR
    priceBelowEma(10)
    heatmapThreshold()
}
```

**Available Conditions** (see `StrategyDsl.kt`):
- Entry: `buySignal()`, `priceAboveEma(period)`, `marketInUptrend()`, `sectorInUptrend()`, `marketHeatmapAbove(threshold)`, etc.
- Exit: `sellSignal()`, `priceBelowEma(period)`, `stopLoss(atr)`, `heatmapThreshold()`, etc.

### 3. Backtesting Engine

Core backtesting with advanced features:

```kotlin
val report = backtestService.backtest(
    entryStrategy = myEntryStrategy,
    exitStrategy = myExitStrategy,
    stocks = stockList,
    after = LocalDate.parse("2020-01-01"),
    before = LocalDate.parse("2024-01-01"),
    maxPositions = 5,                    // Max concurrent positions per day
    ranker = HeatmapRanker(),            // How to rank entry candidates
    useUnderlyingAssets = true,          // Use TQQQ signals on QQQ data
    customUnderlyingMap = mapOf("TQQQ" to "QQQ"),
    cooldownDays = 10                    // Wait 10 days after exit
)
```

**Key Features:**
- **Chronological processing** - No look-ahead bias
- **Position limits** - Rank and select best N stocks per day
- **Cooldown periods** - Prevent overtrading after exits
- **Underlying assets** - Evaluate strategy on one symbol, trade another
- **Missed trades tracking** - Track opportunities that were skipped

### 4. Cooldown Period Logic

**Important:** Cooldown blocks entries for the full number of days specified.

**Example:** `cooldownDays = 5`
- Exit on Day 0
- Blocked on Days 1, 2, 3, 4, 5
- Can trade again on Day 6

**Implementation:**
- `BacktestService.kt:139` - Uses `tradingDaysSinceExit <= cooldownDays`
- `StrategySignalService.kt:103` - Sets `cooldownRemaining = cooldownDays + 1`

### 5. MCP Integration

Expose tools for Claude AI to perform backtesting and analysis:

```kotlin
@McpTool(
    name = "getStockData",
    description = "Fetch stock data with technical indicators"
)
fun getStockData(
    symbol: String,
    startDate: String,
    endDate: String
): Stock {
    return stockService.getStock(symbol) ?: throw IllegalArgumentException("Stock not found")
}
```

**Available MCP Tools:**
- `getStockData` - Fetch single stock with indicators
- `getMultipleStocksData` - Fetch multiple stocks
- `getMarketBreadth` - Get market breadth for SPY/QQQ/IWM
- `getStockSymbols` - List all available symbols
- `runBacktest` - Run full backtest with strategies

### 6. Caching Strategy

Use Caffeine cache for performance:

```kotlin
@Service
class StockService(
    private val stockRepository: StockRepository
) {
    @Cacheable("stocks")
    fun getStock(symbol: String): Stock? {
        return stockRepository.findBySymbol(symbol)
    }

    @Cacheable("stocks")
    fun getAllStocks(): List<Stock> {
        return stockRepository.findAll()
    }
}
```

**Cache Configuration** (`application.properties`):
```properties
spring.cache.type=caffeine
spring.cache.cache-names=stocks,backtests,marketBreadth
spring.cache.caffeine.spec=maximumSize=1000,expireAfterWrite=30m
```

**Performance Impact:**
- First call: ~30s to fetch 1000+ stocks
- Cached calls: ~0.1s (300x faster!)
- Overall backtest: 43.7% improvement with cache

## Code Style Guidelines

### Kotlin Best Practices

**Use Kotlin extension functions instead of manual operations:**

```kotlin
// ✅ Good - Use Kotlin stdlib
val validStocks = stocks.filterNotNull()
val bestStock = stocks.maxByOrNull { it.heatmap }
val symbols = stocks.mapNotNull { it.symbol }

// ❌ Bad - Manual null checks
val validStocks = stocks.filter { it != null }.map { it!! }
val bestStock = stocks.filter { it != null }.maxBy { it!!.heatmap }
```

**Use scope functions appropriately:**

```kotlin
// ✅ Good - Null-safe operations with let
stock?.let { s ->
    logger.info("Processing ${s.symbol}")
    repository.save(s)
}

// ✅ Good - Object configuration with apply
val config = BacktestConfig().apply {
    maxPositions = 5
    cooldownDays = 10
    startDate = LocalDate.parse("2020-01-01")
}

// ✅ Good - Transform with run
val report = stock.run {
    BacktestReport(
        symbol = symbol,
        trades = trades,
        winRate = calculateWinRate()
    )
}
```

**Prefer when expressions over if-else chains:**

```kotlin
// ✅ Good
val ranker = when (rankerName.lowercase()) {
    "heatmap" -> HeatmapRanker()
    "relativestrength" -> RelativeStrengthRanker()
    "volatility" -> VolatilityRanker()
    else -> throw IllegalArgumentException("Unknown ranker")
}

// ❌ Bad
val ranker = if (rankerName == "heatmap") {
    HeatmapRanker()
} else if (rankerName == "relativestrength") {
    RelativeStrengthRanker()
} else {
    throw IllegalArgumentException("Unknown ranker")
}
```

**Use data classes for DTOs:**

```kotlin
data class BacktestRequest(
    val entryStrategy: StrategyConfig,
    val exitStrategy: StrategyConfig,
    val stockSymbols: List<String>? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val maxPositions: Int = 1,
    val ranker: String = "Heatmap",
    val cooldownDays: Int = 0
)
```

### Spring Boot Patterns

**Use constructor injection (not @Autowired):**

```kotlin
// ✅ Good - Constructor injection
@RestController
@RequestMapping("/api/stocks")
class StockController(
    private val stockService: StockService,
    private val strategySignalService: StrategySignalService
) {
    // Methods here
}

// ❌ Bad - Field injection
@RestController
class StockController {
    @Autowired
    private lateinit var stockService: StockService
}
```

**Services should be stateless:**

```kotlin
// ✅ Good - Stateless service
@Service
class BacktestService(
    private val stockRepository: StockRepository
) {
    fun backtest(
        entryStrategy: EntryStrategy,
        exitStrategy: ExitStrategy,
        stocks: List<Stock>
        // ... parameters
    ): BacktestReport {
        // Logic uses only parameters, no instance state
    }
}

// ❌ Bad - Stateful service
@Service
class BacktestService {
    private var currentStocks: List<Stock> = emptyList()  // Don't do this!
}
```

**Handle errors gracefully in controllers:**

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

## Common Patterns

### Data Fetching with Caching

```kotlin
@Service
class StockService(
    private val stockRepository: StockRepository,
    private val ovtlyrClient: OvtlyrClient
) {
    @Cacheable("stocks")
    fun getStock(symbol: String, refresh: Boolean = false): Stock? {
        if (refresh) {
            // Force refresh from external API
            return ovtlyrClient.fetchStock(symbol)
                ?.also { stockRepository.save(it) }
        }

        return stockRepository.findBySymbol(symbol)
    }

    fun getStocksBySymbols(
        symbols: List<String>,
        refresh: Boolean = false
    ): List<Stock> {
        return symbols.mapNotNull { getStock(it, refresh) }
    }
}
```

### Error Handling

```kotlin
fun processStock(symbol: String): Stock {
    return stockRepository.findBySymbol(symbol)
        ?: throw IllegalArgumentException("Stock $symbol not found")
}

// Or with custom error response
fun getStock(@PathVariable symbol: String): ResponseEntity<Stock> {
    return stockService.getStock(symbol)
        ?.let { ResponseEntity.ok(it) }
        ?: ResponseEntity.notFound().build()
}
```

### Testing Strategies

```kotlin
@Test
fun `test entry strategy with buy signal and uptrend`() {
    // Arrange
    val stock = Stock(
        symbol = "AAPL",
        quotes = listOf(
            StockQuote(
                date = LocalDate.now(),
                closePrice = 150.0,
                ema20 = 145.0,
                buySignal = true,
                marketUptrend = true
            )
        )
    )
    val strategy = VegardPlanEtfEntryStrategy()

    // Act
    val result = strategy.test(stock, stock.quotes.first())

    // Assert
    assertTrue(result)
}

@Test
fun `test exit strategy with stop loss`() {
    val stock = createTestStock()
    val entryQuote = createQuote(closePrice = 100.0, atr = 2.0)
    val exitQuote = createQuote(closePrice = 99.0)  // Below stop loss

    val strategy = CompositeExitStrategy(listOf(StopLossCondition(0.5)))

    assertTrue(strategy.match(stock, entryQuote, exitQuote))
    assertEquals("Stop Loss (0.5 ATR)", strategy.reason(stock, entryQuote, exitQuote))
}
```

### JPA Repository Queries

```kotlin
interface StockRepository : JpaRepository<Stock, Long> {
    fun findBySymbol(symbol: String): Stock?

    fun findBySymbolIn(symbols: List<String>): List<Stock>

    @Query("SELECT s FROM Stock s JOIN s.quotes q WHERE q.date >= :startDate AND q.date <= :endDate")
    fun findByDateRange(@Param("startDate") startDate: LocalDate, @Param("endDate") endDate: LocalDate): List<Stock>
}
```

### Building Dynamic Strategies

```kotlin
@Service
class DynamicStrategyBuilder {
    fun buildEntryStrategy(config: StrategyConfig): EntryStrategy? {
        return when {
            config.predefined != null -> {
                // Use predefined strategy
                strategyRegistry.createEntryStrategy(config.predefined)
            }
            config.conditions != null -> {
                // Build composite strategy from conditions
                val conditions = config.conditions.mapNotNull { conditionConfig ->
                    createEntryCondition(conditionConfig)
                }
                CompositeEntryStrategy(conditions)
            }
            else -> null
        }
    }
}
```

## Important Notes

### When Adding New Features

1. **New Strategy**: Add `@RegisteredStrategy` annotation - it will be auto-discovered
2. **API Endpoints**: Add CORS origins in `@CrossOrigin` if needed
3. **Services**: Make them stateless, use constructor injection
4. **Testing**: Write unit tests for all strategy logic
5. **Caching**: Consider `@Cacheable` for expensive operations
6. **Compilation**: Always run `./gradlew compileKotlin` before committing

### Performance Considerations

- **Cache aggressively**: Stock data rarely changes, cache with 30min TTL
- **Batch queries**: Use `findBySymbolIn()` instead of multiple `findBySymbol()` calls
- **Index MongoDB**: Ensure indexes on `symbol` and `quotes.date`
- **Lazy loading**: Don't load all quotes if you only need recent ones
- **Parallel processing**: Consider Kotlin coroutines for independent operations

### Cooldown Period Behavior

**Critical:** The cooldown period blocks entries for the FULL number of days specified.

**Example with cooldownDays = 5:**
- Day 0: Exit trade
- Days 1-5: **Blocked** (in cooldown)
- Day 6: **Can trade** (cooldown expired)

**Implementation Details:**
- `BacktestService.isInCooldown()`: Uses `tradingDaysSinceExit <= cooldownDays`
- `StrategySignalService.evaluateQuotes()`: Sets `cooldownRemaining = cooldownDays + 1`

Both implementations ensure the exact same behavior.

### Strategy Development Workflow

1. **Create condition class** in `model/strategy/condition/`
2. **Implement interface** (`EntryCondition` or `ExitCondition`)
3. **Add to DSL** in `StrategyDsl.kt` for composability
4. **Write tests** to verify logic
5. **Use in composite strategy** or create standalone strategy with `@RegisteredStrategy`

### Testing Best Practices

- Test strategies with edge cases (null values, extreme prices, etc.)
- Verify backtests produce expected number of trades
- Use known data sets with predictable outcomes
- Test cooldown logic with sequential dates
- Mock external API calls in unit tests

### Backend Development Checklist

When developing new backend functionality, follow this checklist:

1. **All functionality has tests** - Every new feature, service method, strategy, or endpoint must have corresponding unit tests
2. **Verify all tests pass after implementation** - Run `./gradlew test` after implementing a new feature. If "unrelated" tests fail, investigate and fix them - they may be revealing issues with your changes
3. **Tests should follow Given-When-Then structure** - Structure your tests clearly:
   ```kotlin
   @Test
   fun `test name describing expected behavior`() {
       // Given (Arrange) - Set up test data and preconditions
       val stock = Stock(symbol = "AAPL")
       val quote = StockQuote(closePrice = 100.0)

       // When (Act) - Execute the functionality being tested
       val result = strategy.test(stock, quote)

       // Then (Assert) - Verify the expected outcome
       assertTrue(result)
   }
   ```
4. **Commit and push after tests pass** - Once all tests pass (including unrelated ones), commit your changes with a descriptive message and push to the repository

## Useful Links

- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Data MongoDB](https://spring.io/projects/spring-data-mongodb)
- [Caffeine Cache](https://github.com/ben-manes/caffeine)
- [Spring AI MCP Server](https://docs.spring.io/spring-ai/reference/api/mcp-server.html)
- [Gradle Documentation](https://docs.gradle.org/)

## Quick Reference

### Common Service Methods

```kotlin
// Stock operations
stockService.getStock(symbol)
stockService.getStock(symbol, refresh = true)  // Force refresh
stockService.getAllStocks()
stockService.getStocksBySymbols(listOf("AAPL", "MSFT"))

// Strategy registry
strategyRegistry.getAvailableEntryStrategies()
strategyRegistry.getAvailableExitStrategies()
strategyRegistry.createEntryStrategy("VegardPlanEtf")
strategyRegistry.createExitStrategy("PlanMoney")

// Backtesting
backtestService.backtest(
    entryStrategy,
    exitStrategy,
    stocks,
    startDate,
    endDate,
    maxPositions,
    ranker,
    useUnderlyingAssets,
    customUnderlyingMap,
    cooldownDays
)

// Strategy signals
strategySignalService.evaluateStrategies(
    stock,
    entryStrategyName = "VegardPlanEtf",
    exitStrategyName = "PlanMoney",
    cooldownDays = 10
)
```

### Configuration Properties

```properties
# Application
spring.application.name=udgaard
server.servlet.context-path=/udgaard

# H2 Database (file-based, no Docker required)
spring.datasource.url=jdbc:h2:file:~/.trading-app/database/trading
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=update
spring.h2.console.enabled=true

# Ovtlyr API (in secure.properties)
ovtlyr.cookies.token=XXX
ovtlyr.cookies.userid=XXX

# Alpha Vantage API
alphavantage.api.key=YOUR_KEY
alphavantage.api.baseUrl=https://www.alphavantage.co/query

# MCP Server
spring.ai.mcp.server.name=backtesting-mcp-server
spring.ai.mcp.server.version=1.0.0
spring.ai.mcp.server.type=SYNC

# Caching
spring.cache.type=caffeine
spring.cache.cache-names=stocks,backtests,marketBreadth
spring.cache.caffeine.spec=maximumSize=1000,expireAfterWrite=30m

# Timeouts (for long backtests)
spring.mvc.async.request-timeout=1800000
server.tomcat.connection-timeout=1800000
```

### Stock Rankers

```kotlin
// Available rankers for position selection
HeatmapRanker()           // Rank by stock heatmap (momentum)
RelativeStrengthRanker()  // Rank by relative strength
VolatilityRanker()        // Rank by volatility (higher = better)
DistanceFrom10EmaRanker() // Rank by distance from 10 EMA
CompositeRanker()         // Combines multiple rankers
SectorStrengthRanker()    // Rank by sector strength
RandomRanker()            // Random selection (for testing)
AdaptiveRanker()          // Adapts based on market conditions
```

### Logging

```kotlin
private val logger = LoggerFactory.getLogger(MyClass::class.java)

logger.info("Processing stock ${stock.symbol}")
logger.warn("Strategy returned no trades")
logger.error("Failed to fetch data: ${e.message}", e)
logger.debug("Intermediate calculation: $value")
```

### Common Gradle Tasks

```bash
./gradlew tasks                    # List all available tasks
./gradlew dependencies             # Show dependency tree
./gradlew bootRun --args='--debug' # Run with debug logging
./gradlew test --tests StockServiceTest  # Run specific test
./gradlew build -x test            # Build without running tests
./gradlew clean build              # Clean and rebuild
```
