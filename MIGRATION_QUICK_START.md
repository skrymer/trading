# MongoDB to H2 Migration - Quick Start Guide

## TL;DR

Moving from MongoDB (external server) to H2 (embedded database) for better desktop app performance and simpler deployment.

**Benefits**: 5-10x faster, 50% less memory, single-file database, no server required.

---

## Quick Comparison

| Aspect | MongoDB (Current) | H2 (Proposed) |
|--------|-------------------|---------------|
| **Deployment** | Separate server required | Embedded in app |
| **File Size** | ~100MB | ~50MB (compressed) |
| **Memory** | ~300MB total | ~150MB total |
| **Query Speed** | 5-10ms | 0.1-1ms (in-memory) or 1-5ms (file) |
| **Startup** | 3-5 seconds | 0.1-2 seconds |
| **Backup** | Complex (mongodump) | Simple (single file copy) |
| **Desktop App** | ❌ Requires separate install | ✅ Built-in |

---

## Migration Steps (High Level)

### 1. Add Dependencies (5 minutes)

```kotlin
// build.gradle.kts
dependencies {
    // Remove
    // implementation("org.springframework.boot:spring-boot-starter-data-mongodb")

    // Add
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.h2database:h2:2.2.224")
}
```

### 2. Configure H2 (10 minutes)

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:h2:file:~/.trading-app/database/trading;AUTO_SERVER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password:

  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: update
```

### 3. Convert Models (1-2 days)

**Before (MongoDB)**:
```kotlin
@Document(collection = "stocks")
class Stock {
    @Id var symbol: String? = null
    var quotes: List<StockQuote> = emptyList()
}
```

**After (JPA)**:
```kotlin
@Entity
@Table(name = "stocks")
data class Stock(
    @Id
    @Column(length = 20)
    val symbol: String,

    @OneToMany(mappedBy = "stock", cascade = [CascadeType.ALL])
    val quotes: MutableList<StockQuote> = mutableListOf()
)

@Entity
@Table(name = "stock_quotes")
data class StockQuote(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne
    @JoinColumn(name = "stock_symbol")
    val stock: Stock,

    @Column(name = "quote_date")
    val date: LocalDate,

    // ... other fields
)
```

### 4. Update Repositories (1 day)

**Before (MongoDB)**:
```kotlin
interface StockRepository : MongoRepository<Stock, String>
```

**After (JPA)**:
```kotlin
interface StockRepository : JpaRepository<Stock, String> {
    @Query("SELECT s FROM Stock s LEFT JOIN FETCH s.quotes WHERE s.symbol = :symbol")
    fun findBySymbolWithQuotes(symbol: String): Stock?
}
```

### 5. Run Migration Tool (1 hour)

```bash
# Export from MongoDB
./gradlew runMigration

# Verify data
./gradlew verifyMigration
```

### 6. Test & Deploy (2-3 days)

- Run all unit tests
- Run all integration tests
- Performance benchmarks
- User acceptance testing
- Deploy to production

**Total Time Estimate**: 1-2 weeks

---

## Key Architecture Changes

### Data Storage

**MongoDB**: Document-oriented, embedded arrays
```json
{
  "symbol": "AAPL",
  "quotes": [
    {"date": "2025-01-01", "close": 180.0},
    {"date": "2025-01-02", "close": 182.0}
  ]
}
```

**H2**: Relational, normalized tables
```
Stock Table          Stock_Quote Table
+---------+          +----+---------+----------+-------+
| symbol  |          | id | stock   | date     | close |
+---------+          +----+---------+----------+-------+
| AAPL    | <------> | 1  | AAPL    | 2025-01-01 | 180.0 |
+---------+          | 2  | AAPL    | 2025-01-02 | 182.0 |
                     +----+---------+----------+-------+
```

### Query Changes

**MongoDB**:
```kotlin
val stock = stockRepository.findById("AAPL")
val quotes = stock.quotes.filter { it.date in dateRange }
```

**H2 (JPA)**:
```kotlin
val stock = stockRepository.findBySymbolWithQuotes("AAPL", startDate, endDate)
val quotes = stock.quotes // Already filtered by query
```

---

## Performance Expectations

### Read Operations

| Operation | MongoDB | H2 (Memory) | H2 (File) | Improvement |
|-----------|---------|-------------|-----------|-------------|
| Get stock + 1000 quotes | 20ms | 2ms | 5ms | **4-10x faster** |
| Get portfolio + 100 trades | 15ms | 1ms | 3ms | **5-15x faster** |
| Calculate stats (1000 records) | 50ms | 5ms | 15ms | **3-10x faster** |
| Complex backtest query | 200ms | 20ms | 80ms | **2.5-10x faster** |

### Write Operations

| Operation | MongoDB | H2 (Memory) | H2 (File) | Improvement |
|-----------|---------|-------------|-----------|-------------|
| Insert 1 trade | 10ms | 0.5ms | 2ms | **5-20x faster** |
| Insert 100 quotes | 100ms | 10ms | 30ms | **3-10x faster** |
| Update portfolio | 15ms | 1ms | 3ms | **5-15x faster** |
| Batch insert 1000 quotes | 500ms | 50ms | 150ms | **3-10x faster** |

---

## Backup & Restore

### Current (MongoDB)

```bash
# Backup
mongodump --db trading --out /backup

# Restore
mongorestore --db trading /backup/trading

# Result: Complex, slow, requires MongoDB tools
```

### New (H2)

```bash
# Backup (automatic)
# Just copy the file!
cp ~/.trading-app/database/trading.mv.db ~/backups/

# Restore
cp ~/backups/trading.mv.db ~/.trading-app/database/

# Result: Simple, instant, no special tools
```

### Automated Backups

```kotlin
@Scheduled(cron = "0 0 2 * * *") // 2 AM daily
fun performDailyBackup() {
    dataSource.connection.use { conn ->
        conn.prepareStatement("BACKUP TO 'backup_${timestamp}.zip'").execute()
    }
}
```

---

## Migration Checklist

### Pre-Migration

- [ ] Review migration plan documents
- [ ] Back up current MongoDB database
- [ ] Set up H2 in development environment
- [ ] Create and test new JPA entities
- [ ] Update all repository interfaces
- [ ] Update service layer code
- [ ] Write/update unit tests
- [ ] Write/update integration tests

### Migration

- [ ] Run migration tool on development data
- [ ] Verify data integrity
- [ ] Run performance benchmarks
- [ ] Test all application features
- [ ] Fix any bugs found
- [ ] Run migration on production data
- [ ] Verify production migration
- [ ] Monitor application for 24-48 hours

### Post-Migration

- [ ] Archive MongoDB data
- [ ] Decommission MongoDB server
- [ ] Update deployment documentation
- [ ] Update user documentation
- [ ] Train users on new backup process
- [ ] Set up automated backups
- [ ] Monitor performance metrics

---

## Common Issues & Solutions

### Issue 1: "Embedded lists not supported"

**Problem**: MongoDB embedded arrays don't map directly to JPA.

**Solution**: Create separate entity with @OneToMany relationship.

```kotlin
// Before
var quotes: List<StockQuote> = emptyList()

// After
@OneToMany(mappedBy = "stock", cascade = [CascadeType.ALL])
val quotes: MutableList<StockQuote> = mutableListOf()
```

### Issue 2: "N+1 query problem"

**Problem**: Loading entities causes many individual queries.

**Solution**: Use JOIN FETCH or @EntityGraph.

```kotlin
@Query("SELECT s FROM Stock s LEFT JOIN FETCH s.quotes WHERE s.symbol = :symbol")
fun findBySymbolWithQuotes(symbol: String): Stock?
```

### Issue 3: "Slow queries"

**Problem**: Missing indexes on frequently queried columns.

**Solution**: Add indexes.

```kotlin
@Table(
    name = "stock_quotes",
    indexes = [
        Index(name = "idx_symbol_date", columnList = "stock_symbol, quote_date")
    ]
)
```

### Issue 4: "Database locked"

**Problem**: H2 file-based DB accessed by multiple processes.

**Solution**: Use AUTO_SERVER mode or ensure only one process.

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/trading;AUTO_SERVER=TRUE
```

---

## Testing Strategy

### Unit Tests

```kotlin
@DataJpaTest
class StockRepositoryTest {
    @Autowired
    lateinit var stockRepository: StockRepository

    @Test
    fun `should save and retrieve stock with quotes`() {
        val stock = Stock(symbol = "AAPL")
        stock.quotes.add(StockQuote(stock = stock, date = LocalDate.now(), closePrice = 180.0))

        stockRepository.save(stock)

        val found = stockRepository.findById("AAPL")
        assertThat(found).isPresent
        assertThat(found.get().quotes).hasSize(1)
    }
}
```

### Integration Tests

```kotlin
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BacktestServiceTest {
    @Autowired
    lateinit var backtestService: BacktestService

    @Test
    fun `should run backtest with H2 database`() {
        val report = backtestService.runBacktest(/* params */)
        assertThat(report.trades).isNotEmpty()
    }
}
```

### Performance Tests

```kotlin
@Test
fun `should load 1000 quotes in under 5ms`() {
    val start = System.currentTimeMillis()

    val stock = stockRepository.findBySymbolWithQuotes("AAPL")

    val elapsed = System.currentTimeMillis() - start
    assertThat(elapsed).isLessThan(5)
}
```

---

## Rollback Plan

If migration fails, rollback is simple:

1. **Stop the application**
2. **Restore from backup**:
   ```bash
   cp backup_before_migration.mv.db ~/.trading-app/database/trading.mv.db
   ```
3. **Restart application**

OR

1. **Revert code changes**:
   ```bash
   git revert <migration-commit>
   ```
2. **Restore MongoDB connection string**
3. **Deploy previous version**

---

## Getting Help

- **Full Migration Plan**: `MONGODB_TO_H2_MIGRATION_PLAN.md`
- **ER Diagram**: `DATABASE_ER_DIAGRAM.md`
- **H2 Documentation**: https://h2database.com/
- **Spring Data JPA**: https://docs.spring.io/spring-data/jpa/docs/current/reference/html/

---

## Success Metrics

After migration, expect to see:

- ✅ **Faster queries**: 5-10x improvement in query times
- ✅ **Lower memory**: 50% reduction in total memory usage
- ✅ **Simpler deployment**: No MongoDB server to manage
- ✅ **Easier backups**: Single file copy instead of mongodump
- ✅ **Better desktop app**: Embedded database perfect for Electron
- ✅ **Smaller footprint**: 50% smaller database files

---

**Ready to start?** See `MONGODB_TO_H2_MIGRATION_PLAN.md` for detailed steps.
