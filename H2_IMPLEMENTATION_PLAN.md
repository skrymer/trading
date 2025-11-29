# MongoDB to H2 Implementation Plan (Development Environment)

## Overview

Since the app is still in development, we can **simply switch to H2** without data migration. This is much faster and simpler than migrating production data.

**Timeline**: 3-5 days
**Complexity**: Low-Medium
**Risk**: Low (development only)

---

## Why This Is Simple

✅ No production data to migrate
✅ No dual-write period needed
✅ No rollback concerns
✅ Just replace MongoDB with H2
✅ Start fresh with clean database

---

## Implementation Steps

### Step 1: Update Dependencies (15 minutes)

**Remove MongoDB dependencies:**

```kotlin
// build.gradle.kts or build.gradle
dependencies {
    // REMOVE these lines:
    // implementation("org.springframework.boot:spring-boot-starter-data-mongodb")

    // ADD these lines:
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("com.h2database:h2:2.2.224")

    // Optional but recommended for schema management:
    implementation("org.flywaydb:flyway-core")
}
```

### Step 2: Configure H2 (10 minutes)

**Update `application.properties` or `application.yml`:**

```yaml
# REMOVE MongoDB configuration
# spring:
#   data:
#     mongodb:
#       uri: mongodb://localhost:27017/trading

# ADD H2 configuration
spring:
  datasource:
    # File-based (persists data between restarts)
    url: jdbc:h2:file:./data/trading;AUTO_SERVER=TRUE;DB_CLOSE_DELAY=-1

    # OR In-memory (for development/testing)
    # url: jdbc:h2:mem:trading;DB_CLOSE_DELAY=-1

    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: create-drop  # Use 'create-drop' in dev, 'update' in prod
    properties:
      hibernate:
        format_sql: true
        show_sql: true
        jdbc:
          batch_size: 50

  h2:
    console:
      enabled: true  # Enable web console at http://localhost:8080/h2-console
      path: /h2-console
      settings:
        web-allow-others: false

logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

**Development vs Production Config:**

```yaml
# application-dev.yml (development)
spring:
  datasource:
    url: jdbc:h2:mem:trading  # In-memory, fast
  jpa:
    hibernate:
      ddl-auto: create-drop    # Recreate schema on startup
  h2:
    console:
      enabled: true

# application-prod.yml (production/desktop app)
spring:
  datasource:
    url: jdbc:h2:file:~/.trading-app/database/trading;AUTO_SERVER=TRUE
  jpa:
    hibernate:
      ddl-auto: update         # Only update schema, don't drop
  h2:
    console:
      enabled: false           # Disable console in production
```

### Step 3: Convert Model Classes (2-3 days)

For each MongoDB document, convert to JPA entity:

#### Example 1: Portfolio

**Before (MongoDB):**
```kotlin
@Document(collection = "portfolios")
data class Portfolio(
    @Id
    val id: String? = null,
    val userId: String? = null,
    val name: String,
    val initialBalance: Double,
    var currentBalance: Double,
    val currency: String,
    val createdDate: LocalDateTime = LocalDateTime.now(),
    var lastUpdated: LocalDateTime = LocalDateTime.now()
)
```

**After (JPA):**
```kotlin
@Entity
@Table(name = "portfolios")
data class Portfolio(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", length = 100)
    val userId: String? = null,

    @Column(nullable = false, length = 255)
    val name: String,

    @Column(name = "initial_balance", nullable = false)
    val initialBalance: Double,

    @Column(name = "current_balance", nullable = false)
    var currentBalance: Double,

    @Column(nullable = false, length = 3)
    val currency: String,

    @Column(name = "created_date", nullable = false)
    val createdDate: LocalDateTime = LocalDateTime.now(),

    @Column(name = "last_updated", nullable = false)
    var lastUpdated: LocalDateTime = LocalDateTime.now()
)
```

**Key Changes:**
- `@Document` → `@Entity` + `@Table`
- `@Id` String → `@Id` Long with `@GeneratedValue`
- Add `@Column` annotations with constraints
- Use snake_case for column names

#### Example 2: Stock with Embedded Quotes

**Before (MongoDB):**
```kotlin
@Document(collection = "stocks")
class Stock {
    @Id
    var symbol: String? = null
    var sectorSymbol: String? = null
    var quotes: List<StockQuote> = emptyList()
    var orderBlocks: List<OrderBlock> = emptyList()
}
```

**After (JPA) - Normalized:**

```kotlin
@Entity
@Table(name = "stocks")
data class Stock(
    @Id
    @Column(length = 20)
    val symbol: String,

    @Column(name = "sector_symbol", length = 20)
    var sectorSymbol: String? = null,

    @OneToMany(
        mappedBy = "stock",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    val quotes: MutableList<StockQuote> = mutableListOf(),

    @OneToMany(
        mappedBy = "stock",
        cascade = [CascadeType.ALL],
        orphanRemoval = true
    )
    val orderBlocks: MutableList<OrderBlock> = mutableListOf()
) {
    // Keep all your existing methods!
    fun getQuoteByDate(date: LocalDate) = quotes.find { it.date == date }
    // ... etc
}

@Entity
@Table(
    name = "stock_quotes",
    indexes = [
        Index(name = "idx_symbol_date", columnList = "stock_symbol, quote_date", unique = true)
    ]
)
data class StockQuote(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_symbol", nullable = false)
    val stock: Stock,

    @Column(name = "quote_date", nullable = false)
    val date: LocalDate,

    @Column(name = "close_price", nullable = false)
    val closePrice: Double,

    @Column(name = "open_price", nullable = false)
    val openPrice: Double,

    // ... other fields
)
```

**Key Changes:**
- Embedded `quotes` list → Separate `StockQuote` entity
- `@OneToMany` relationship with `mappedBy`
- `cascade = [CascadeType.ALL]` - saves quotes when saving stock
- `orphanRemoval = true` - deletes quotes when removed from stock
- Add `@ManyToOne` back-reference in `StockQuote`

#### Example 3: ETF Entity

**Before (MongoDB):**
```kotlin
@Document(collection = "etfs")
class EtfEntity {
    @Id
    var symbol: String? = null
    var name: String? = null
    var quotes: List<EtfQuote> = emptyList()
    var holdings: List<EtfHolding> = emptyList()
    var metadata: EtfMetadata? = null
}
```

**After (JPA):**

```kotlin
@Entity
@Table(name = "etf_entities")
data class EtfEntity(
    @Id
    @Column(length = 20)
    val symbol: String,

    @Column(length = 255)
    var name: String? = null,

    // Flatten metadata into main table
    @Column(name = "expense_ratio")
    var expenseRatio: Double? = null,

    @Column(name = "assets_under_management")
    var aum: Double? = null,

    @OneToMany(mappedBy = "etf", cascade = [CascadeType.ALL])
    val quotes: MutableList<EtfQuote> = mutableListOf(),

    @OneToMany(mappedBy = "etf", cascade = [CascadeType.ALL])
    val holdings: MutableList<EtfHolding> = mutableListOf()
)

@Entity
@Table(name = "etf_quotes")
data class EtfQuote(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "etf_symbol")
    val etf: EtfEntity,

    @Column(name = "quote_date")
    val date: LocalDate,

    // ... other fields
)
```

**Key Changes:**
- Flatten `metadata` object into main table columns
- Convert embedded lists to `@OneToMany` relationships
- Each child entity needs `@ManyToOne` back-reference

### Step 4: Update Repositories (1 day)

**Before (MongoDB):**
```kotlin
interface StockRepository : MongoRepository<Stock, String>
```

**After (JPA):**
```kotlin
interface StockRepository : JpaRepository<Stock, String> {

    // Custom query with JOIN FETCH to avoid N+1 problem
    @Query("""
        SELECT DISTINCT s FROM Stock s
        LEFT JOIN FETCH s.quotes
        WHERE s.symbol = :symbol
    """)
    fun findBySymbolWithQuotes(symbol: String): Stock?

    @Query("""
        SELECT s FROM Stock s
        LEFT JOIN FETCH s.quotes q
        WHERE s.symbol = :symbol
        AND q.date BETWEEN :startDate AND :endDate
    """)
    fun findBySymbolWithQuotesInRange(
        symbol: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Stock?

    fun findBySectorSymbol(sectorSymbol: String): List<Stock>
}
```

**Key Changes:**
- `MongoRepository` → `JpaRepository`
- Add custom `@Query` methods for complex queries
- Use `JOIN FETCH` to eagerly load relationships
- Avoid N+1 queries with proper fetch strategies

### Step 5: Update Service Layer (1 day)

Most service code will remain the same, but some query patterns change:

**Before (MongoDB - Application-level filtering):**
```kotlin
fun getStockQuotesInRange(symbol: String, start: LocalDate, end: LocalDate): List<StockQuote> {
    val stock = stockRepository.findById(symbol).orElseThrow()
    return stock.quotes.filter { it.date in start..end }
}
```

**After (JPA - Database-level filtering):**
```kotlin
fun getStockQuotesInRange(symbol: String, start: LocalDate, end: LocalDate): List<StockQuote> {
    val stock = stockRepository.findBySymbolWithQuotesInRange(symbol, start, end)
        ?: throw NotFoundException("Stock not found: $symbol")
    return stock.quotes
}
```

**Better performance**: Filtering happens in database, not in application memory.

### Step 6: Update Tests (1 day)

**Before (MongoDB tests):**
```kotlin
@DataMongoTest
class StockRepositoryTest {
    @Autowired
    lateinit var stockRepository: StockRepository

    @Test
    fun `should save stock`() {
        val stock = Stock().apply {
            symbol = "AAPL"
            quotes = listOf(StockQuote(/* ... */))
        }
        stockRepository.save(stock)
        // ...
    }
}
```

**After (JPA tests):**
```kotlin
@DataJpaTest
class StockRepositoryTest {
    @Autowired
    lateinit var stockRepository: StockRepository

    @Test
    fun `should save stock with quotes`() {
        val stock = Stock(symbol = "AAPL")
        val quote = StockQuote(
            stock = stock,
            date = LocalDate.now(),
            closePrice = 180.0,
            // ... other fields
        )
        stock.quotes.add(quote)

        stockRepository.save(stock)

        val found = stockRepository.findById("AAPL")
        assertThat(found).isPresent
        assertThat(found.get().quotes).hasSize(1)
    }
}
```

**Key Changes:**
- `@DataMongoTest` → `@DataJpaTest`
- Must set bidirectional relationships correctly
- H2 in-memory database used for tests automatically

### Step 7: Remove MongoDB Dependencies (15 minutes)

1. **Remove MongoDB Docker/local instance** (if running)
2. **Delete MongoDB configuration classes** (if any)
3. **Remove MongoDB annotations** from models
4. **Clean up imports**

```bash
# Remove MongoDB Docker container (if using Docker)
docker stop mongodb
docker rm mongodb

# Clean build
./gradlew clean build
```

---

## Quick Migration Checklist

### Phase 1: Setup (Day 1)
- [ ] Update build.gradle - remove MongoDB, add H2 and JPA
- [ ] Update application.yml - add H2 config, remove MongoDB
- [ ] Run `./gradlew clean build` to verify dependencies
- [ ] Access H2 console at http://localhost:8080/h2-console

### Phase 2: Models (Day 2-3)
- [ ] Convert Portfolio model to JPA
- [ ] Convert PortfolioTrade model to JPA
- [ ] Convert Stock model to JPA
- [ ] Create StockQuote entity with @ManyToOne
- [ ] Create OrderBlock entity with @ManyToOne
- [ ] Convert EtfEntity model to JPA
- [ ] Create EtfQuote entity with @ManyToOne
- [ ] Create EtfHolding entity with @ManyToOne
- [ ] Convert Breadth model to JPA
- [ ] Create BreadthQuote entity with @ManyToOne

### Phase 3: Repositories (Day 3-4)
- [ ] Update PortfolioRepository
- [ ] Update PortfolioTradeRepository
- [ ] Update StockRepository with custom queries
- [ ] Update EtfRepository with custom queries
- [ ] Update BreadthRepository with custom queries

### Phase 4: Services (Day 4)
- [ ] Update PortfolioService
- [ ] Update StockService
- [ ] Update EtfService
- [ ] Update BacktestService
- [ ] Update any other services using repositories

### Phase 5: Tests (Day 5)
- [ ] Update repository tests
- [ ] Update service tests
- [ ] Update integration tests
- [ ] Run full test suite
- [ ] Fix any failures

### Phase 6: Cleanup (Day 5)
- [ ] Remove MongoDB dependencies
- [ ] Remove MongoDB configuration
- [ ] Clean up imports
- [ ] Update documentation
- [ ] Test app end-to-end

---

## Common Pitfalls & Solutions

### 1. N+1 Query Problem

**Problem:**
```kotlin
val stocks = stockRepository.findAll()
stocks.forEach { stock ->
    println(stock.quotes.size) // Each access triggers a new query!
}
```

**Solution:**
```kotlin
@Query("SELECT DISTINCT s FROM Stock s LEFT JOIN FETCH s.quotes")
fun findAllWithQuotes(): List<Stock>
```

### 2. Bidirectional Relationship Not Set

**Problem:**
```kotlin
val stock = Stock(symbol = "AAPL")
val quote = StockQuote(date = LocalDate.now(), closePrice = 180.0)
stock.quotes.add(quote)
stockRepository.save(stock) // quote.stock is null!
```

**Solution:**
```kotlin
val stock = Stock(symbol = "AAPL")
val quote = StockQuote(
    stock = stock,  // Set parent reference!
    date = LocalDate.now(),
    closePrice = 180.0
)
stock.quotes.add(quote)
stockRepository.save(stock)
```

Or add helper method:
```kotlin
fun Stock.addQuote(quote: StockQuote) {
    quotes.add(quote)
    quote.stock = this
}
```

### 3. LazyInitializationException

**Problem:**
```kotlin
@Transactional
fun getStock(symbol: String): Stock {
    return stockRepository.findById(symbol).orElseThrow()
}

// Later, outside transaction:
val stock = getStock("AAPL")
stock.quotes.forEach { ... } // LazyInitializationException!
```

**Solution - Option 1: Eager Fetch**
```kotlin
@Query("SELECT s FROM Stock s LEFT JOIN FETCH s.quotes WHERE s.symbol = :symbol")
fun findBySymbolWithQuotes(symbol: String): Stock?
```

**Solution - Option 2: Keep in Transaction**
```kotlin
@Transactional
fun processStock(symbol: String) {
    val stock = stockRepository.findById(symbol).orElseThrow()
    stock.quotes.forEach { ... } // Works inside transaction
}
```

### 4. Cascading Deletes

**Problem:**
```kotlin
val stock = stockRepository.findById("AAPL").orElseThrow()
stock.quotes.clear()
stockRepository.save(stock) // Quotes still in database!
```

**Solution:**
```kotlin
@OneToMany(
    mappedBy = "stock",
    cascade = [CascadeType.ALL],
    orphanRemoval = true  // This is key!
)
val quotes: MutableList<StockQuote> = mutableListOf()
```

---

## Testing Strategy

### Unit Tests
```kotlin
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StockRepositoryTest {
    @Test
    fun `should save and retrieve stock`() {
        // Test JPA operations
    }
}
```

### Integration Tests
```kotlin
@SpringBootTest
@Transactional
class BacktestServiceTest {
    @Test
    fun `should run backtest with H2`() {
        // Full end-to-end test
    }
}
```

### Performance Tests
```kotlin
@Test
fun `should load 1000 quotes quickly`() {
    val start = System.currentTimeMillis()
    val stock = stockRepository.findBySymbolWithQuotes("AAPL")
    val elapsed = System.currentTimeMillis() - start

    assertThat(elapsed).isLessThan(100) // Should be under 100ms
}
```

---

## Development Workflow

### 1. Start with In-Memory H2
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:trading
  jpa:
    hibernate:
      ddl-auto: create-drop
```

**Advantages:**
- Fast restarts
- Clean slate every time
- Perfect for development

### 2. Switch to File-Based for Testing
```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/trading
  jpa:
    hibernate:
      ddl-auto: update
```

**Advantages:**
- Data persists between restarts
- Test data loading
- Closer to production

### 3. Use H2 Console for Debugging

Access: http://localhost:8080/h2-console

**JDBC URL**: `jdbc:h2:mem:trading` (or your file path)
**Username**: `sa`
**Password**: (empty)

You can:
- View all tables
- Execute SQL queries
- Inspect data
- Debug schema issues

---

## Performance Optimization

### 1. Use Indexes
```kotlin
@Table(
    name = "stock_quotes",
    indexes = [
        Index(name = "idx_symbol_date", columnList = "stock_symbol, quote_date"),
        Index(name = "idx_date", columnList = "quote_date")
    ]
)
```

### 2. Batch Inserts
```kotlin
@Transactional
fun saveStockQuotes(quotes: List<StockQuote>) {
    quotes.chunked(50).forEach { batch ->
        stockQuoteRepository.saveAll(batch)
        entityManager.flush()
        entityManager.clear()
    }
}
```

### 3. Read-Only Queries
```kotlin
@Transactional(readOnly = true)
fun getStockQuotes(symbol: String): List<StockQuote> {
    // Optimized for reads
}
```

### 4. Projection for Large Queries
```kotlin
interface StockProjection {
    fun getSymbol(): String
    fun getSectorSymbol(): String
}

interface StockRepository : JpaRepository<Stock, String> {
    fun findAllProjectedBy(): List<StockProjection>
}
```

---

## Configuration Tips

### Enable SQL Logging
```yaml
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

### Connection Pool
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 20000
```

### H2 Performance Tuning
```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/trading;CACHE_SIZE=65536;PAGE_SIZE=2048;DB_CLOSE_DELAY=-1
```

---

## Backup Strategy (Even for Dev)

### Automatic Export on Shutdown
```kotlin
@Component
class BackupOnShutdown {

    @PreDestroy
    fun backup() {
        dataSource.connection.use { conn ->
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            conn.prepareStatement("BACKUP TO 'backup_$timestamp.zip'").execute()
        }
    }
}
```

### Manual Backup Endpoint
```kotlin
@RestController
@RequestMapping("/api/admin")
class AdminController {

    @PostMapping("/backup")
    fun createBackup(): String {
        dataSource.connection.use { conn ->
            val filename = "manual_backup_${System.currentTimeMillis()}.zip"
            conn.prepareStatement("BACKUP TO '$filename'").execute()
            return filename
        }
    }
}
```

---

## Next Steps After Implementation

1. **Load Test Data**
   - Create seed data for development
   - Use Flyway migrations or data.sql

2. **Performance Benchmark**
   - Compare query times with MongoDB
   - Ensure 5-10x improvement

3. **Update Documentation**
   - README setup instructions
   - Developer onboarding docs

4. **Team Training**
   - JPA basics
   - H2 console usage
   - Query optimization

---

## Estimated Timeline

| Phase | Time | Complexity |
|-------|------|------------|
| Setup (dependencies + config) | 1 hour | Easy |
| Convert 10 model classes | 2 days | Medium |
| Update 5 repositories | 1 day | Easy |
| Update service layer | 1 day | Easy |
| Update tests | 1 day | Easy |
| **TOTAL** | **3-5 days** | **Low-Medium** |

---

## Success Criteria

✅ Application starts without MongoDB
✅ All models converted to JPA entities
✅ All repositories use JPA
✅ All tests passing
✅ H2 console accessible
✅ Data persists between restarts (file mode)
✅ Performance is faster than MongoDB

---

## Getting Help

- **H2 Documentation**: https://h2database.com/
- **Spring Data JPA**: https://spring.io/guides/gs/accessing-data-jpa/
- **JPA Best Practices**: https://vladmihalcea.com/tutorials/hibernate/
- **Kotlin + JPA**: https://spring.io/guides/tutorials/spring-boot-kotlin/

---

**Ready to start?** Begin with Step 1: Update Dependencies!
