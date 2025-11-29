# MongoDB to H2 Embedded Database Migration Plan

## Executive Summary

This document outlines a comprehensive plan to migrate from MongoDB to H2, an embedded relational database that supports both in-memory and file-based persistence.

### Why H2?

- **Embedded**: No separate database server required
- **Zero Configuration**: Works out-of-the-box with Spring Boot
- **Fast**: In-memory mode for development, file-based for production
- **SQL Standard**: Full relational database features
- **Portable**: Single file database, easy backup/restore
- **Small Footprint**: ~2MB JAR file
- **Desktop App Friendly**: Perfect for Electron apps

---

## 1. Database Schema Design (ER Diagram)

### Entity-Relationship Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         CORE ENTITIES                                   │
└─────────────────────────────────────────────────────────────────────────┘

┌──────────────────┐         ┌──────────────────┐         ┌──────────────────┐
│     PORTFOLIO    │         │ PORTFOLIO_TRADE  │         │      STOCK       │
├──────────────────┤         ├──────────────────┤         ├──────────────────┤
│ id (PK)          │◄────────│ id (PK)          │         │ symbol (PK)      │
│ user_id          │ 1     * │ portfolio_id (FK)│         │ sector_symbol    │
│ name             │         │ symbol           │         │ ovtlyr_perf      │
│ initial_balance  │         │ instrument_type  │         │ created_at       │
│ current_balance  │         │ option_type      │         │ updated_at       │
│ currency         │         │ strike_price     │         └──────────────────┘
│ created_date     │         │ expiration_date  │                 │ 1
│ last_updated     │         │ contracts        │                 │
└──────────────────┘         │ multiplier       │                 │
                             │ entry_price      │                 │ *
┌──────────────────┐         │ entry_date       │         ┌──────────────────┐
│   ETF_ENTITY     │         │ exit_price       │         │   STOCK_QUOTE    │
├──────────────────┤         │ exit_date        │         ├──────────────────┤
│ symbol (PK)      │         │ quantity         │         │ id (PK)          │
│ name             │         │ entry_strategy   │         │ stock_symbol (FK)│
│ description      │         │ exit_strategy    │         │ quote_date       │
│ expense_ratio    │         │ currency         │         │ open_price       │
│ aum              │         │ status           │         │ close_price      │
│ inception_date   │         │ underlying_symbol│         │ high_price       │
│ created_at       │         │ entry_intrinsic  │         │ low_price        │
│ updated_at       │         │ entry_extrinsic  │         │ volume           │
└──────────────────┘         │ exit_intrinsic   │         │ ema_10           │
        │ 1                  │ exit_extrinsic   │         │ ema_20           │
        │                    └──────────────────┘         │ ema_50           │
        │ *                                               │ ema_200          │
┌──────────────────┐                                      │ rsi              │
│    ETF_QUOTE     │                                      │ atr              │
├──────────────────┤         ┌──────────────────┐         │ created_at       │
│ id (PK)          │         │   ORDER_BLOCK    │         └──────────────────┘
│ etf_symbol (FK)  │         ├──────────────────┤
│ quote_date       │         │ id (PK)          │
│ open_price       │         │ stock_symbol (FK)│─────────┐
│ close_price      │         │ type             │         │
│ high_price       │         │ source           │         │ *
│ low_price        │         │ start_date       │         │
│ volume           │         │ end_date         │         │ 1
│ bullish_pct      │         │ high             │         │
│ stocks_uptrend   │         │ low              │         │
│ stocks_downtrend │         │ created_at       │         │
│ is_uptrend       │         └──────────────────┘         │
│ created_at       │                                      │
└──────────────────┘         ┌──────────────────┐         │
        │ 1                  │     BREADTH      │         │
        │                    ├──────────────────┤         │
        │ *                  │ id (PK)          │         │
┌──────────────────┐         │ symbol_type      │         │
│   ETF_HOLDING    │         │ symbol_value     │         │
├──────────────────┤         │ created_at       │         │
│ id (PK)          │         │ updated_at       │         │
│ etf_symbol (FK)  │         └──────────────────┘         │
│ stock_symbol     │                 │ 1                  │
│ weight           │                 │                    │
│ shares           │                 │ *                  │
│ in_uptrend       │         ┌──────────────────┐         │
│ sector           │         │  BREADTH_QUOTE   │         │
│ created_at       │         ├──────────────────┤         │
└──────────────────┘         │ id (PK)          │         │
                             │ breadth_id (FK)  │         │
                             │ quote_date       │         │
                             │ heatmap          │         │
                             │ prev_heatmap     │         │
                             │ donkey_score     │         │
                             │ is_uptrend       │         │
                             │ created_at       │         │
                             └──────────────────┘         │
                                                          │
┌─────────────────────────────────────────────────────────┘
│                    EMBEDDED COLLECTIONS                 │
└─────────────────────────────────────────────────────────┘

Embedded quotes/holdings will be normalized into separate tables with
foreign keys to maintain referential integrity and enable efficient queries.
```

### Key Design Decisions

1. **Normalized Structure**: Embedded documents (quotes, holdings, orderBlocks) become separate tables
2. **Foreign Keys**: Enforce referential integrity
3. **Indexes**: Add indexes on frequently queried columns (dates, symbols, portfolio_id)
4. **Auto-increment IDs**: Use BIGINT auto-increment for quote tables (many records)
5. **Composite Keys**: Use natural keys (symbol) where appropriate
6. **Timestamps**: Add created_at/updated_at for audit trail

---

## 2. Model Class Mapping

### 2.1 Portfolio Domain

#### Current MongoDB Model
```kotlin
@Document(collection = "portfolios")
data class Portfolio(
    @Id val id: String? = null,
    val userId: String? = null,
    val name: String,
    val initialBalance: Double,
    var currentBalance: Double,
    val currency: String,
    val createdDate: LocalDateTime = LocalDateTime.now(),
    var lastUpdated: LocalDateTime = LocalDateTime.now()
)
```

#### New JPA Entity
```kotlin
@Entity
@Table(name = "portfolios", indexes = [
    Index(name = "idx_portfolio_user", columnList = "user_id")
])
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
    var lastUpdated: LocalDateTime = LocalDateTime.now(),

    @OneToMany(mappedBy = "portfolio", cascade = [CascadeType.ALL], orphanRemoval = true)
    val trades: MutableList<PortfolioTrade> = mutableListOf()
)
```

### 2.2 Stock Domain

#### Current MongoDB Model
```kotlin
@Document(collection = "stocks")
class Stock {
    @Id var symbol: String? = null
    var sectorSymbol: String? = null
    var quotes: List<StockQuote> = emptyList()
    var orderBlocks: List<OrderBlock> = emptyList()
    var ovtlyrPerformance: Double? = 0.0
}
```

#### New JPA Entity
```kotlin
@Entity
@Table(name = "stocks")
data class Stock(
    @Id
    @Column(length = 20)
    val symbol: String,

    @Column(name = "sector_symbol", length = 20)
    var sectorSymbol: String? = null,

    @Column(name = "ovtlyr_performance")
    var ovtlyrPerformance: Double? = 0.0,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @OneToMany(mappedBy = "stock", cascade = [CascadeType.ALL], orphanRemoval = true)
    val quotes: MutableList<StockQuote> = mutableListOf(),

    @OneToMany(mappedBy = "stock", cascade = [CascadeType.ALL], orphanRemoval = true)
    val orderBlocks: MutableList<OrderBlock> = mutableListOf()
)

@Entity
@Table(name = "stock_quotes", indexes = [
    Index(name = "idx_stock_quote_symbol_date", columnList = "stock_symbol, quote_date", unique = true),
    Index(name = "idx_stock_quote_date", columnList = "quote_date")
])
data class StockQuote(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_symbol", nullable = false)
    val stock: Stock,

    @Column(name = "quote_date", nullable = false)
    val date: LocalDate,

    @Column(name = "open_price", nullable = false)
    val openPrice: Double,

    @Column(name = "close_price", nullable = false)
    val closePrice: Double,

    @Column(name = "high_price", nullable = false)
    val highPrice: Double,

    @Column(name = "low_price", nullable = false)
    val lowPrice: Double,

    @Column(nullable = false)
    val volume: Long,

    // Technical indicators
    @Column(name = "ema_10")
    val closePriceEMA10: Double? = null,

    @Column(name = "ema_20")
    val closePriceEMA20: Double? = null,

    @Column(name = "ema_50")
    val closePriceEMA50: Double? = null,

    @Column(name = "ema_200")
    val closePriceEMA200: Double? = null,

    @Column
    val rsi: Double? = null,

    @Column
    val atr: Double? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
```

### 2.3 ETF Domain

```kotlin
@Entity
@Table(name = "etf_entities")
data class EtfEntity(
    @Id
    @Column(length = 20)
    val symbol: String,

    @Column(length = 255)
    var name: String? = null,

    @Column(length = 1000)
    var description: String? = null,

    @Column(name = "expense_ratio")
    var expenseRatio: Double? = null,

    @Column(name = "assets_under_management")
    var aum: Double? = null,

    @Column(name = "inception_date")
    var inceptionDate: LocalDate? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @OneToMany(mappedBy = "etf", cascade = [CascadeType.ALL], orphanRemoval = true)
    val quotes: MutableList<EtfQuote> = mutableListOf(),

    @OneToMany(mappedBy = "etf", cascade = [CascadeType.ALL], orphanRemoval = true)
    val holdings: MutableList<EtfHolding> = mutableListOf()
)
```

### 2.4 Breadth Domain

```kotlin
@Entity
@Table(name = "breadth")
data class Breadth(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "symbol_type", nullable = false, length = 20)
    val symbolType: String, // "MARKET" or "SECTOR"

    @Column(name = "symbol_value", nullable = false, length = 20)
    val symbolValue: String, // e.g., "SPY", "XLK"

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @OneToMany(mappedBy = "breadth", cascade = [CascadeType.ALL], orphanRemoval = true)
    val quotes: MutableList<BreadthQuote> = mutableListOf()
)
```

---

## 3. Performance Analysis

### 3.1 Expected Performance Changes

#### ✅ **Improvements**

| Aspect | MongoDB | H2 (In-Memory) | H2 (File-Based) | Reasoning |
|--------|---------|----------------|-----------------|-----------|
| **Read Speed** | ~5-10ms | ~0.1-1ms | ~1-5ms | In-memory data structures, no network |
| **Write Speed** | ~10-20ms | ~0.5-2ms | ~2-10ms | No network overhead, optimized B-trees |
| **Join Performance** | Poor (manual) | Excellent | Good | Native SQL joins vs. application-level |
| **Aggregate Queries** | Good | Excellent | Good | SQL aggregations optimized in C |
| **Memory Usage** | ~200MB | ~50MB | ~30MB | No separate process, efficient storage |
| **Startup Time** | ~3-5s | ~0.1-0.5s | ~0.5-2s | Embedded, no network initialization |
| **Cold Start** | N/A | Instant | ~1-2s | Load from disk on startup |

#### ⚠️ **Potential Regressions**

| Aspect | MongoDB | H2 | Impact | Mitigation |
|--------|---------|-----|--------|------------|
| **Schema Flexibility** | Excellent | Limited | Medium | Use @Lob for JSON columns if needed |
| **Horizontal Scaling** | Easy | Impossible | Low | Desktop app = single instance |
| **Document Embedding** | Native | Manual joins | Low | Eager/lazy loading strategies |
| **Array Queries** | Native | SQL arrays | Low | Use JPA collections |

### 3.2 Query Performance Comparison

#### Example: Get Stock with Quotes in Date Range

**MongoDB (Current)**
```kotlin
// Single query, embedded quotes
val stock = stockRepository.findById(symbol)
val quotes = stock.quotes.filter { it.date in dateRange }
// Time: ~5-10ms
```

**H2 (New)**
```kotlin
// Single query with JOIN and WHERE clause
@Query("""
    SELECT s FROM Stock s
    LEFT JOIN FETCH s.quotes q
    WHERE s.symbol = :symbol
    AND q.date BETWEEN :start AND :end
""")
fun findBySymbolWithQuotesInRange(symbol: String, start: LocalDate, end: LocalDate): Stock?
// Time: ~0.1-1ms (in-memory) or ~1-3ms (file-based)
```

**Performance**: H2 is **5-10x faster** due to:
- No network latency
- In-memory or local disk access
- Optimized index scans
- SQL query planner optimizations

### 3.3 Benchmark Estimates

Based on typical desktop application usage:

| Operation | Records | MongoDB | H2 (Memory) | H2 (File) | Speedup |
|-----------|---------|---------|-------------|-----------|---------|
| Load portfolio + trades | 1 + 100 | 15ms | 1ms | 3ms | 5-15x |
| Get stock + quotes | 1 + 1000 | 20ms | 2ms | 5ms | 4-10x |
| Calculate portfolio stats | 1000 trades | 50ms | 5ms | 15ms | 3-10x |
| Insert 100 stock quotes | 100 | 100ms | 10ms | 30ms | 3-10x |
| Complex backtest query | 10k records | 200ms | 20ms | 80ms | 2.5-10x |

### 3.4 Memory Footprint

**MongoDB Setup**
- MongoDB Server: ~200MB RAM
- JVM Application: ~100MB RAM
- **Total: ~300MB**

**H2 Setup**
- H2 In-Memory: ~50MB RAM (data in JVM heap)
- JVM Application: ~100MB RAM
- **Total: ~150MB** (50% reduction)

**H2 File-Based**
- H2 Database: ~10MB disk + ~30MB cache
- JVM Application: ~100MB RAM
- **Total: ~130MB** (57% reduction)

### 3.5 Disk Space

| Data Type | MongoDB (BSON) | H2 (Compressed) | Savings |
|-----------|----------------|-----------------|---------|
| 1000 stocks × 1000 quotes | ~80MB | ~40MB | 50% |
| 100 ETFs × 1000 quotes | ~8MB | ~4MB | 50% |
| Portfolio + 1000 trades | ~2MB | ~1MB | 50% |
| **Total Typical Dataset** | **~100MB** | **~50MB** | **50%** |

H2 uses more efficient binary storage compared to MongoDB's BSON format.

---

## 4. Data Backup Strategy

### 4.1 Automatic Backups

#### Daily Backup Script

```kotlin
@Service
class BackupService {

    @Value("\${app.backup.directory}")
    private lateinit var backupDirectory: String

    @Scheduled(cron = "0 0 2 * * *") // 2 AM daily
    fun performDailyBackup() {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val backupFile = File("$backupDirectory/backup_$timestamp.zip")

        // H2 has built-in BACKUP command
        dataSource.connection.use { conn ->
            conn.prepareStatement("BACKUP TO '${backupFile.absolutePath}'").execute()
        }

        logger.info("Database backup created: ${backupFile.name}")

        // Keep only last 30 days of backups
        cleanOldBackups(30)
    }

    private fun cleanOldBackups(daysToKeep: Int) {
        val cutoffDate = LocalDateTime.now().minusDays(daysToKeep.toLong())
        File(backupDirectory).listFiles()
            ?.filter { it.name.startsWith("backup_") }
            ?.filter { extractBackupDate(it) < cutoffDate }
            ?.forEach { it.delete() }
    }
}
```

#### Configuration

```yaml
# application.properties
app:
  backup:
    directory: ${user.home}/.trading-app/backups
    enabled: true
    retention-days: 30
```

### 4.2 Manual Backup (User-Initiated)

```kotlin
@RestController
@RequestMapping("/api/admin/backup")
class BackupController(private val backupService: BackupService) {

    @PostMapping
    fun createBackup(): ResponseEntity<BackupResponse> {
        val backupFile = backupService.createBackup()
        return ResponseEntity.ok(BackupResponse(
            filename = backupFile.name,
            size = backupFile.length(),
            timestamp = LocalDateTime.now()
        ))
    }

    @GetMapping
    fun listBackups(): List<BackupInfo> {
        return backupService.listBackups()
    }

    @PostMapping("/restore/{filename}")
    fun restoreBackup(@PathVariable filename: String): ResponseEntity<String> {
        backupService.restoreFromBackup(filename)
        return ResponseEntity.ok("Database restored successfully")
    }
}
```

### 4.3 Export to Portable Formats

Allow users to export data in standard formats:

```kotlin
@Service
class DataExportService {

    fun exportToJson(portfolioId: Long): File {
        val portfolio = portfolioRepository.findById(portfolioId)
        val trades = portfolioTradeRepository.findByPortfolioId(portfolioId)

        val exportData = mapOf(
            "portfolio" to portfolio,
            "trades" to trades,
            "exportDate" to LocalDateTime.now()
        )

        val file = File.createTempFile("portfolio_export_", ".json")
        objectMapper.writeValue(file, exportData)
        return file
    }

    fun exportToCsv(portfolioId: Long): File {
        // Export trades as CSV for Excel
        val trades = portfolioTradeRepository.findByPortfolioId(portfolioId)
        val file = File.createTempFile("trades_export_", ".csv")

        CsvWriter(file).use { writer ->
            writer.writeHeader("Symbol", "Entry Date", "Exit Date", "Profit", "...")
            trades.forEach { trade ->
                writer.writeRow(trade.symbol, trade.entryDate, trade.exitDate, trade.profit)
            }
        }

        return file
    }
}
```

### 4.4 Cloud Sync (Optional)

For users who want cloud backups:

```kotlin
@Service
class CloudBackupService {

    fun uploadToDropbox(backupFile: File) {
        // Use Dropbox API
    }

    fun uploadToGoogleDrive(backupFile: File) {
        // Use Google Drive API
    }

    fun uploadToS3(backupFile: File) {
        // Use AWS S3
    }
}
```

### 4.5 Backup File Structure

```
~/.trading-app/
├── database/
│   └── trading.mv.db          # Main H2 database file
├── backups/
│   ├── backup_20250129_020000.zip
│   ├── backup_20250130_020000.zip
│   └── backup_20250131_020000.zip
└── exports/
    ├── portfolio_1_20250131.json
    └── trades_20250131.csv
```

---

## 5. Data Migration Strategy

### 5.1 Migration Approaches

We'll support **three migration paths**:

#### Option A: One-Time Migration (Recommended)
- Run migration script once
- Copy all data from MongoDB to H2
- Switch application to H2
- Archive MongoDB data

#### Option B: Dual-Write Migration
- Write to both MongoDB and H2 temporarily
- Verify data consistency
- Switch reads to H2
- Decommission MongoDB

#### Option C: Import from Export
- Export MongoDB data to JSON
- User imports JSON into H2 app
- Manual process, user-controlled

### 5.2 Migration Tool Implementation

```kotlin
@Component
class MongoToH2MigrationTool(
    private val mongoTemplate: MongoTemplate,
    private val portfolioRepository: PortfolioRepository,
    private val stockRepository: StockRepository,
    private val etfRepository: EtfRepository,
    private val breadthRepository: BreadthRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun migrateAll() {
        logger.info("Starting MongoDB to H2 migration...")

        try {
            migratePortfolios()
            migrateStocks()
            migrateEtfs()
            migrateBreadth()

            logger.info("Migration completed successfully!")
        } catch (e: Exception) {
            logger.error("Migration failed", e)
            throw MigrationException("Migration failed: ${e.message}", e)
        }
    }

    @Transactional
    fun migratePortfolios() {
        logger.info("Migrating portfolios...")

        val mongoPortfolios = mongoTemplate.findAll(
            com.skrymer.udgaard.model.mongo.Portfolio::class.java,
            "portfolios"
        )

        var count = 0
        mongoPortfolios.forEach { mongoPortfolio ->
            val h2Portfolio = Portfolio(
                id = null, // Auto-generate new ID
                userId = mongoPortfolio.userId,
                name = mongoPortfolio.name,
                initialBalance = mongoPortfolio.initialBalance,
                currentBalance = mongoPortfolio.currentBalance,
                currency = mongoPortfolio.currency,
                createdDate = mongoPortfolio.createdDate,
                lastUpdated = mongoPortfolio.lastUpdated
            )

            portfolioRepository.save(h2Portfolio)
            count++

            // Migrate associated trades
            migratePortfolioTrades(mongoPortfolio.id, h2Portfolio.id!!)
        }

        logger.info("Migrated $count portfolios")
    }

    @Transactional
    fun migrateStocks() {
        logger.info("Migrating stocks...")

        val mongoStocks = mongoTemplate.findAll(
            com.skrymer.udgaard.model.mongo.Stock::class.java,
            "stocks"
        )

        var stockCount = 0
        var quoteCount = 0
        var orderBlockCount = 0

        mongoStocks.forEach { mongoStock ->
            // Create H2 stock entity
            val h2Stock = Stock(
                symbol = mongoStock.symbol!!,
                sectorSymbol = mongoStock.sectorSymbol,
                ovtlyrPerformance = mongoStock.ovtlyrPerformance,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )

            stockRepository.save(h2Stock)
            stockCount++

            // Migrate quotes
            mongoStock.quotes.forEach { mongoQuote ->
                val h2Quote = StockQuote(
                    stock = h2Stock,
                    date = mongoQuote.date!!,
                    openPrice = mongoQuote.openPrice,
                    closePrice = mongoQuote.closePrice,
                    highPrice = mongoQuote.highPrice,
                    lowPrice = mongoQuote.lowPrice,
                    volume = mongoQuote.volume,
                    closePriceEMA10 = mongoQuote.closePriceEMA10,
                    closePriceEMA20 = mongoQuote.closePriceEMA20,
                    closePriceEMA50 = mongoQuote.closePriceEMA50,
                    closePriceEMA200 = mongoQuote.closePriceEMA200,
                    rsi = mongoQuote.rsi,
                    atr = mongoQuote.atr
                )

                h2Stock.quotes.add(h2Quote)
                quoteCount++
            }

            // Migrate order blocks
            mongoStock.orderBlocks.forEach { mongoOrderBlock ->
                val h2OrderBlock = OrderBlock(
                    stock = h2Stock,
                    type = mongoOrderBlock.orderBlockType,
                    source = mongoOrderBlock.source,
                    startDate = mongoOrderBlock.startDate,
                    endDate = mongoOrderBlock.endDate,
                    high = mongoOrderBlock.high,
                    low = mongoOrderBlock.low
                )

                h2Stock.orderBlocks.add(h2OrderBlock)
                orderBlockCount++
            }

            // Batch commit every 100 stocks to avoid memory issues
            if (stockCount % 100 == 0) {
                stockRepository.flush()
                logger.info("Migrated $stockCount stocks, $quoteCount quotes, $orderBlockCount order blocks...")
            }
        }

        logger.info("Migrated $stockCount stocks with $quoteCount quotes and $orderBlockCount order blocks")
    }

    @Transactional
    fun migrateEtfs() {
        logger.info("Migrating ETFs...")

        val mongoEtfs = mongoTemplate.findAll(
            com.skrymer.udgaard.model.mongo.EtfEntity::class.java,
            "etfs"
        )

        var etfCount = 0
        var quoteCount = 0
        var holdingCount = 0

        mongoEtfs.forEach { mongoEtf ->
            val h2Etf = EtfEntity(
                symbol = mongoEtf.symbol!!,
                name = mongoEtf.name,
                description = mongoEtf.description,
                expenseRatio = mongoEtf.metadata?.expenseRatio,
                aum = mongoEtf.metadata?.aum,
                inceptionDate = mongoEtf.metadata?.inceptionDate
            )

            etfRepository.save(h2Etf)
            etfCount++

            // Migrate quotes
            mongoEtf.quotes.forEach { mongoQuote ->
                val h2Quote = EtfQuote(
                    etf = h2Etf,
                    date = mongoQuote.date,
                    openPrice = mongoQuote.openPrice,
                    closePrice = mongoQuote.closePrice,
                    highPrice = mongoQuote.highPrice,
                    lowPrice = mongoQuote.lowPrice,
                    volume = mongoQuote.volume,
                    bullishPercentage = mongoQuote.bullishPercentage,
                    stocksInUptrend = mongoQuote.stocksInUptrend,
                    stocksInDowntrend = mongoQuote.stocksInDowntrend,
                    isInUptrend = mongoQuote.isInUptrend()
                )

                h2Etf.quotes.add(h2Quote)
                quoteCount++
            }

            // Migrate holdings
            mongoEtf.holdings.forEach { mongoHolding ->
                val h2Holding = EtfHolding(
                    etf = h2Etf,
                    stockSymbol = mongoHolding.symbol,
                    weight = mongoHolding.weight,
                    shares = mongoHolding.shares,
                    inUptrend = mongoHolding.inUptrend,
                    sector = mongoHolding.sector
                )

                h2Etf.holdings.add(h2Holding)
                holdingCount++
            }

            if (etfCount % 10 == 0) {
                etfRepository.flush()
                logger.info("Migrated $etfCount ETFs...")
            }
        }

        logger.info("Migrated $etfCount ETFs with $quoteCount quotes and $holdingCount holdings")
    }

    fun generateMigrationReport(): MigrationReport {
        return MigrationReport(
            portfolios = portfolioRepository.count(),
            trades = portfolioTradeRepository.count(),
            stocks = stockRepository.count(),
            stockQuotes = stockQuoteRepository.count(),
            etfs = etfRepository.count(),
            etfQuotes = etfQuoteRepository.count(),
            breadth = breadthRepository.count(),
            timestamp = LocalDateTime.now()
        )
    }
}
```

### 5.3 Migration Command Line Tool

```kotlin
@SpringBootApplication
class MigrationApp

fun main(args: Array<String>) {
    val context = SpringApplication.run(MigrationApp::class.java, *args)
    val migrationTool = context.getBean(MongoToH2MigrationTool::class.java)

    println("=== MongoDB to H2 Migration Tool ===")
    println("1. Migrate All Data")
    println("2. Migrate Portfolios Only")
    println("3. Migrate Stocks Only")
    println("4. Migrate ETFs Only")
    println("5. Generate Report")
    println("0. Exit")

    when (readLine()?.toIntOrNull()) {
        1 -> migrationTool.migrateAll()
        2 -> migrationTool.migratePortfolios()
        3 -> migrationTool.migrateStocks()
        4 -> migrationTool.migrateEtfs()
        5 -> println(migrationTool.generateMigrationReport())
        else -> System.exit(0)
    }
}
```

### 5.4 Migration Progress Tracking

```kotlin
@Service
class MigrationProgressService {

    private val progress = AtomicInteger(0)
    private val total = AtomicInteger(0)

    fun startMigration(totalRecords: Int) {
        total.set(totalRecords)
        progress.set(0)
    }

    fun recordProgress(count: Int = 1) {
        val current = progress.addAndGet(count)
        val percentage = (current.toDouble() / total.get() * 100).toInt()

        if (current % 100 == 0) {
            println("Migration progress: $current / ${total.get()} ($percentage%)")
        }
    }

    fun getProgress(): MigrationProgress {
        return MigrationProgress(
            completed = progress.get(),
            total = total.get(),
            percentage = (progress.get().toDouble() / total.get() * 100)
        )
    }
}
```

### 5.5 Rollback Strategy

```kotlin
@Service
class MigrationRollbackService {

    fun createRollbackPoint() {
        // Create H2 backup before migration
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "BACKUP TO 'backup_before_migration_${System.currentTimeMillis()}.zip'"
            ).execute()
        }
    }

    fun rollback(backupFile: String) {
        // Restore from backup
        dataSource.connection.use { conn ->
            conn.prepareStatement("DROP ALL OBJECTS").execute()
            conn.prepareStatement("RUNSCRIPT FROM '$backupFile'").execute()
        }
    }
}
```

---

## 6. Implementation Phases

### Phase 1: Setup (Week 1)
- [ ] Add H2 dependency to build.gradle
- [ ] Create new JPA entity models
- [ ] Set up dual repository layer (Mongo + JPA)
- [ ] Configure H2 datasource

### Phase 2: Migration Tool (Week 2)
- [ ] Implement migration service
- [ ] Create CLI migration tool
- [ ] Add progress tracking
- [ ] Test with sample data

### Phase 3: Application Updates (Week 2-3)
- [ ] Update services to use JPA repositories
- [ ] Replace MongoDB queries with JPA/Criteria API
- [ ] Add transaction management
- [ ] Update tests

### Phase 4: Backup & Export (Week 3)
- [ ] Implement automatic backups
- [ ] Add manual backup API
- [ ] Create export functionality
- [ ] Add restore capability

### Phase 5: Testing (Week 4)
- [ ] Unit test all repositories
- [ ] Integration test migration
- [ ] Performance benchmarks
- [ ] User acceptance testing

### Phase 6: Deployment (Week 5)
- [ ] Run migration on production data
- [ ] Monitor performance
- [ ] Archive MongoDB
- [ ] Update documentation

---

## 7. Configuration

### 7.1 H2 Configuration

```yaml
# application.yml
spring:
  datasource:
    # File-based (Production - Desktop App)
    url: jdbc:h2:file:~/.trading-app/database/trading;AUTO_SERVER=TRUE

    # In-memory (Development/Testing)
    # url: jdbc:h2:mem:trading

    driver-class-name: org.h2.Driver
    username: sa
    password:

  h2:
    console:
      enabled: true # Enable H2 web console
      path: /h2-console
      settings:
        web-allow-others: false

  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: update # or 'validate' for production
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
        jdbc:
          batch_size: 50
        order_inserts: true
        order_updates: true
    show-sql: false

  # Remove MongoDB configuration
  # data:
  #   mongodb:
  #     uri: mongodb://localhost:27017/trading

# H2 specific settings
h2:
  database:
    compression: true # Enable compression for file-based DB
    cache-size: 65536 # 64MB cache (default is 16MB)
    page-size: 2048 # Default page size
```

### 7.2 Gradle Dependencies

```kotlin
// build.gradle.kts
dependencies {
    // Replace MongoDB
    // implementation("org.springframework.boot:spring-boot-starter-data-mongodb")

    // Add H2 and JPA
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.h2database:h2:2.2.224")

    // Migration support
    implementation("org.flywaydb:flyway-core") // Optional: for schema versioning
}
```

---

## 8. Advantages Summary

### ✅ **Benefits of Migration**

1. **Simplified Deployment**
   - No external database server
   - Single JAR deployment
   - Perfect for desktop apps

2. **Better Performance**
   - 5-10x faster queries (in-memory)
   - 2-5x faster queries (file-based)
   - Native SQL optimizations

3. **Reduced Complexity**
   - No database server management
   - Standard SQL instead of MongoDB queries
   - Familiar JPA/Hibernate patterns

4. **Lower Resource Usage**
   - 50% less memory
   - 50% less disk space
   - No network overhead

5. **Better for Desktop Apps**
   - Single file database
   - Easy backup/restore
   - Works offline perfectly

6. **Data Integrity**
   - Foreign key constraints
   - Transaction support (ACID)
   - Schema validation

7. **Portability**
   - Database is a single file
   - Easy to copy/share
   - Cross-platform compatible

8. **Developer Experience**
   - Standard SQL
   - Better IDE support
   - Familiar tooling (JPA, Hibernate)

### ⚠️ **Trade-offs**

1. **No Horizontal Scaling**
   - Not an issue for desktop apps

2. **Schema Rigidity**
   - Changes require migrations
   - Less flexible than MongoDB

3. **No Native Array/Document Support**
   - Need to normalize data
   - More JOIN operations

4. **File Locking**
   - Only one process can access file-based H2
   - Not an issue for single-user desktop app

---

## 9. Next Steps

1. **Review and Approve Plan**
2. **Set Up Development Environment**
3. **Create JPA Entity Models**
4. **Implement Migration Tool**
5. **Test Migration with Sample Data**
6. **Update Application Code**
7. **Performance Testing**
8. **Production Migration**

---

## Appendix A: Sample Flyway Migration

```sql
-- V1__initial_schema.sql
CREATE TABLE portfolios (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(100),
    name VARCHAR(255) NOT NULL,
    initial_balance DOUBLE NOT NULL,
    current_balance DOUBLE NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_date TIMESTAMP NOT NULL,
    last_updated TIMESTAMP NOT NULL
);

CREATE INDEX idx_portfolio_user ON portfolios(user_id);

CREATE TABLE stocks (
    symbol VARCHAR(20) PRIMARY KEY,
    sector_symbol VARCHAR(20),
    ovtlyr_performance DOUBLE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE stock_quotes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_symbol VARCHAR(20) NOT NULL,
    quote_date DATE NOT NULL,
    open_price DOUBLE NOT NULL,
    close_price DOUBLE NOT NULL,
    high_price DOUBLE NOT NULL,
    low_price DOUBLE NOT NULL,
    volume BIGINT NOT NULL,
    ema_10 DOUBLE,
    ema_20 DOUBLE,
    ema_50 DOUBLE,
    ema_200 DOUBLE,
    rsi DOUBLE,
    atr DOUBLE,
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (stock_symbol) REFERENCES stocks(symbol) ON DELETE CASCADE,
    UNIQUE (stock_symbol, quote_date)
);

CREATE INDEX idx_stock_quote_date ON stock_quotes(quote_date);
CREATE INDEX idx_stock_quote_symbol_date ON stock_quotes(stock_symbol, quote_date);

-- Add more tables...
```

## Appendix B: Repository Examples

```kotlin
// JPA Repository with custom queries
interface StockRepository : JpaRepository<Stock, String> {

    @Query("""
        SELECT s FROM Stock s
        LEFT JOIN FETCH s.quotes q
        WHERE s.symbol = :symbol
        AND q.date BETWEEN :startDate AND :endDate
        ORDER BY q.date ASC
    """)
    fun findBySymbolWithQuotes(
        symbol: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Stock?

    @Query("""
        SELECT s FROM Stock s
        WHERE s.sectorSymbol = :sectorSymbol
    """)
    fun findBySector(sectorSymbol: String): List<Stock>
}
```

---

**End of Migration Plan**

For questions or assistance, contact the development team.
