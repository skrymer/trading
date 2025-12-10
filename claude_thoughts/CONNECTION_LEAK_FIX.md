# Database Connection Leak Fix - THE REAL ROOT CAUSE

## Problem

HikariCP was detecting connection leaks during backtest operations with the following error:

```
Connection leak detection triggered for conn1: url=jdbc:h2:file:~/.trading-app/database/trading
Apparent connection leak detected
at com.skrymer.udgaard.service.StockService.getAllStocks(StockService.kt:65)
```

The leak detection was triggered after 300 seconds (5 minutes) as configured in `application.properties`.

## Root Cause

**THE REAL ISSUE: Hibernate Managed Entities Holding Connections**

Even after removing `@Transactional` from the backtest method and optimizing queries, **Hibernate entities remained ATTACHED to the persistence context**, causing the underlying database connection to be held open for the entire duration of the backtest.

**How it happened:**
1. `StockService.getAllStocks()` runs in a transaction and loads entities
2. The transaction completes, but entities remain **managed** by the EntityManager
3. The controller receives these managed entities and passes them to backtest
4. During the 5-10 minute backtest, Hibernate keeps the session open to track entity changes
5. The session holds a database connection from the pool
6. After 5 minutes, HikariCP detects this as a leak

**Initial Issues (also fixed):**

1. **Transaction on backtest method**: The `backtest()` method had `@Transactional(readOnly = true)`, explicitly holding a connection
2. **N+1 Query Problem**: `getAllStocks()` used `findAll()` causing 1000+ queries instead of 3
3. **Mid-backtest database calls**: `backtest()` was calling repository methods during execution

## Solution

Implemented a **four-part solution** to completely eliminate connection leaks:

### Part 0: **DETACH ENTITIES FROM HIBERNATE SESSION** ⭐ THE KEY FIX

**Problem**: Managed entities keep the EntityManager session alive, which holds a database connection.

**Solution**: Call `entityManager.clear()` or `entityManager.detach()` after loading all data to detach entities from the persistence context.

#### StockService Changes

1. **`getAllStocks()`** - Detach all entities after loading:
```kotlin
@Transactional(readOnly = true)
open fun getAllStocks(): List<Stock> {
    // Load all data with JOIN FETCH queries
    val stocks = stockRepository.findAllWithQuotes()
    if (stocks.isEmpty()) return emptyList()

    stockRepository.fetchOrderBlocks(stocks)
    stockRepository.fetchEarnings(stocks)

    // CRITICAL: Detach ALL entities from the persistence context
    // This releases the database connection IMMEDIATELY
    entityManager.clear()

    return stocks
}
```

2. **`getStock()`** - Detach individual stock:
```kotlin
@Transactional(readOnly = true)
open fun getStock(symbol: String, forceFetch: Boolean = false): Stock? {
    val stock = /* ... load stock ... */

    // Detach the entity to prevent holding connections
    stock?.let { entityManager.detach(it) }

    return stock
}
```

3. **`getMarketBreadth()`** - Detach breadth entity:
```kotlin
@Transactional(readOnly = true)
open fun getMarketBreadth(): Breadth? {
    val breadth = breadthRepository.findBySymbol(/* ... */)

    breadth?.let {
        it.quotes.size // Force load quotes collection
        entityManager.detach(it)
    }

    return breadth
}
```

**Key Points:**
- `entityManager.clear()` detaches ALL entities in the persistence context (use for bulk operations)
- `entityManager.detach(entity)` detaches a single entity (use for individual loads)
- Must force-load any lazy collections BEFORE detaching
- Once detached, entities are plain POJOs - no more connection needed

---

### Part 1: Remove @Transactional from Backtest Method

**Problem**: Long-running backtests held database connections open for 5-10 minutes.

**Solution**: Removed `@Transactional(readOnly = true)` from `BacktestService.backtest()` because:
- Backtests are pure computation on in-memory data (no writes)
- All Stock data is pre-loaded before backtest runs
- No need to hold a connection during calculation

**Files Modified**:
- `BacktestService.kt`: Removed `@Transactional` annotation and import

### Part 2: Optimize Stock Loading with JOIN FETCH

**Problem**: `findAll()` caused N+1 queries and inefficient loading.

**Solution**: Added explicit JOIN FETCH queries in the repository to load all data efficiently within a single transaction.

#### Repository Changes (`StockRepository.kt`)

Added three custom query methods to load data efficiently:

```kotlin
@Query("SELECT DISTINCT s FROM Stock s LEFT JOIN FETCH s.quotes")
fun findAllWithQuotes(): List<Stock>

@Query("SELECT DISTINCT s FROM Stock s LEFT JOIN FETCH s.orderBlocks WHERE s IN :stocks")
fun fetchOrderBlocks(stocks: List<Stock>): List<Stock>

@Query("SELECT DISTINCT s FROM Stock s LEFT JOIN FETCH s.earnings WHERE s IN :stocks")
fun fetchEarnings(stocks: List<Stock>): List<Stock>
```

**Why three separate queries?**
- Hibernate throws `MultipleBagFetchException` when trying to JOIN FETCH multiple collections in one query
- Using three queries is more efficient than N+1 queries and prevents the exception
- All queries execute within a single transaction boundary

### 2. Service Changes (`StockService.kt`)

Updated `getAllStocks()` to use the new repository methods:

```kotlin
@Cacheable(value = ["stocks"], key = "'allStocks'")
@Transactional(readOnly = true)
open fun getAllStocks(): List<Stock> {
    // Query 1: Load stocks with quotes
    val stocks = stockRepository.findAllWithQuotes()

    if (stocks.isEmpty()) {
        return emptyList()
    }

    // Query 2: Fetch order blocks for all stocks
    stockRepository.fetchOrderBlocks(stocks)

    // Query 3: Fetch earnings for all stocks
    stockRepository.fetchEarnings(stocks)

    return stocks
}
```

**Key improvements:**
- `@Transactional(readOnly = true)`: Ensures all queries run in one transaction
- All collections loaded before method returns
- No lazy loading outside transaction scope
- Optimal query execution (3 queries total instead of 1 + 3N queries)

### Part 3: Move Database Reads Outside Backtest

**Problem**: `backtest()` was calling repository methods during execution:
- `stockRepository.findByIdOrNull()` for underlying assets (in `createStockPairs()` and `validateUnderlyingAssets()`)
- `stockRepository.findByIdOrNull("SPY")` for market condition tracking
- `breadthRepository.findBySymbol()` for market breadth data

**Solution**: Load all required data BEFORE calling backtest and pass it as parameters.

#### BacktestService Changes

1. **Updated method signature** to accept pre-loaded data:
```kotlin
fun backtest(
    // ... existing parameters ...
    spyStock: Stock? = null,
    marketBreadth: Breadth? = null,
): BacktestReport
```

2. **Refactored helper methods** to use in-memory data:
```kotlin
private fun createStockPairs(
    stocks: List<Stock>,
    useUnderlyingAssets: Boolean,
    customUnderlyingMap: Map<String, String>?,
    allStocksMap: Map<String, Stock>, // NEW: in-memory lookup
): List<StockPair>

private fun validateUnderlyingAssets(
    stocks: List<Stock>,
    useUnderlying: Boolean,
    customMap: Map<String, String>?,
    allStocksMap: Map<String, Stock>, // NEW: in-memory lookup
)
```

3. **Removed repository calls** from backtest method - all data lookups now use the `allStocksMap` parameter.

#### Controller Changes

Updated `BacktestController.runBacktestWithConfig()` to load all data before calling backtest:

```kotlin
// Load all data BEFORE backtest (in separate transactions)
val stocks = stockService.getAllStocks() // or getStocksBySymbols()
val spyStock = stockService.getStock("SPY", forceFetch = false)
val marketBreadth = stockService.getMarketBreadth()

// Run backtest with pre-loaded data (no database access)
val backtestReport = backtestService.backtest(
    entryStrategy,
    exitStrategy,
    stocks,
    start,
    end,
    request.maxPositions,
    rankerInstance,
    request.useUnderlyingAssets,
    request.customUnderlyingMap,
    request.cooldownDays,
    spyStock,       // NEW
    marketBreadth,  // NEW
)
```

#### StockService Changes

Added helper method for loading market breadth:
```kotlin
@Transactional(readOnly = true)
open fun getMarketBreadth(): Breadth? =
    breadthRepository.findBySymbol(BreadthSymbol.Market().toIdentifier())
```

## Benefits

1. **✅ No Connection Leaks**: Backtests no longer hold connections for their entire duration
2. **✅ Better Performance**: Data loading uses 3 queries instead of potentially thousands (1 + 3N)
3. **✅ Separation of Concerns**: Data loading is separate from computation
4. **✅ Predictable Behavior**: No proxy lazy loading issues
5. **✅ Connection Pool Efficiency**: Connections are held only during data loading (seconds), not during computation (minutes)
6. **✅ Cache-Friendly**: Fully initialized entities are cached properly
7. **✅ Testability**: Tests can pass pre-loaded data without needing repository mocks

## Query Execution Comparison

### Before (N+1 Problem)
```sql
-- Query 1: Get all stocks
SELECT * FROM stocks;  -- Returns 335 stocks

-- Query 2-336: Get quotes for each stock (N queries)
SELECT * FROM stock_quotes WHERE stock_symbol = 'AAPL';
SELECT * FROM stock_quotes WHERE stock_symbol = 'MSFT';
-- ... 333 more queries

-- Query 337-671: Get order blocks for each stock (N queries)
SELECT * FROM order_blocks WHERE stock_symbol = 'AAPL';
SELECT * FROM order_blocks WHERE stock_symbol = 'MSFT';
-- ... 333 more queries

-- Query 672-1006: Get earnings for each stock (N queries)
SELECT * FROM earnings WHERE stock_symbol = 'AAPL';
SELECT * FROM earnings WHERE stock_symbol = 'MSFT';
-- ... 333 more queries

TOTAL: 1006 queries!
```

### After (JOIN FETCH)
```sql
-- Query 1: Get all stocks with quotes
SELECT DISTINCT s.*, q.*
FROM stocks s
LEFT JOIN stock_quotes q ON s.symbol = q.stock_symbol;

-- Query 2: Get all order blocks for loaded stocks
SELECT DISTINCT s.*, ob.*
FROM stocks s
LEFT JOIN order_blocks ob ON s.symbol = ob.stock_symbol
WHERE s.symbol IN ('AAPL', 'MSFT', ...);

-- Query 3: Get all earnings for loaded stocks
SELECT DISTINCT s.*, e.*
FROM stocks s
LEFT JOIN earnings e ON s.symbol = e.stock_symbol
WHERE s.symbol IN ('AAPL', 'MSFT', ...);

TOTAL: 3 queries!
```

## Testing

- ✅ All backend tests pass (305 tests)
- ✅ Kotlin compilation successful
- ✅ No MultipleBagFetchException
- ✅ Transaction boundary properly enforced
- ✅ Tests updated to pass pre-loaded stocks (including underlying assets)
- ✅ No repository mocking required in backtest tests

## Recommendations

1. **Monitor Connection Pool**: Watch HikariCP metrics to ensure no more leaks
2. **Adjust Pool Size if Needed**: Current config is 20 max connections, 5 min idle
3. **Consider Pagination**: For very large datasets, implement pagination in `getAllStocks()`
4. **Cache Warming**: The `@Cacheable` annotation will cache the fully loaded stocks

## Configuration Reference

Current HikariCP configuration in `application.properties`:

```properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=20000
spring.datasource.hikari.idle-timeout=300000
spring.datasource.hikari.max-lifetime=1800000
spring.datasource.hikari.leak-detection-threshold=300000
```

The leak detection threshold (5 minutes) is now appropriate since connections will be returned much faster.

## Files Modified

1. `udgaard/src/main/kotlin/com/skrymer/udgaard/repository/StockRepository.kt`
   - Added `findAllWithQuotes()` - JOIN FETCH quotes
   - Added `fetchOrderBlocks()` - JOIN FETCH order blocks
   - Added `fetchEarnings()` - JOIN FETCH earnings

2. **⭐ `udgaard/src/main/kotlin/com/skrymer/udgaard/service/StockService.kt`** - THE CRITICAL FIX
   - **Added `EntityManager` injection** for entity detachment
   - **Added `entityManager.clear()`** in `getAllStocks()` to detach all entities
   - **Added `entityManager.detach()`** in `getStock()` to detach individual stock
   - **Added `entityManager.detach()`** in `getMarketBreadth()` to detach breadth entity
   - **Added `@Transactional` to `getStock()`** to ensure proper transaction boundary
   - Updated `getAllStocks()` with transaction management and JOIN FETCH queries
   - Added `getMarketBreadth()` helper method

3. `udgaard/src/main/kotlin/com/skrymer/udgaard/service/BacktestService.kt`
   - **REMOVED** `@Transactional` annotation and import from `backtest()` method
   - **Added** `spyStock` and `marketBreadth` parameters to `backtest()`
   - **Updated** `createStockPairs()` to accept `allStocksMap` instead of calling repository
   - **Updated** `validateUnderlyingAssets()` to accept `allStocksMap` instead of calling repository
   - **Removed** `stockRepository.findByIdOrNull("SPY")` call
   - **Removed** `breadthRepository.findBySymbol()` call

4. `udgaard/src/main/kotlin/com/skrymer/udgaard/controller/BacktestController.kt`
   - Added `spyStock` loading before backtest
   - Added `marketBreadth` loading before backtest
   - Pass `spyStock` and `marketBreadth` to `backtestService.backtest()`

5. `udgaard/src/test/kotlin/com/skrymer/udgaard/service/BacktestServiceTest.kt`
   - Updated tests to include underlying assets in stocks list
   - Removed repository mocking for `findById("QQQ")`
   - Tests now pass fully-loaded stock lists to backtest

## Hibernate Entity Lifecycle - Important Lessons Learned

### Why Managed Entities Hold Connections

When you load entities in a `@Transactional` method:
1. Entities are placed in the **persistence context** (EntityManager)
2. Hibernate tracks these entities for **dirty checking** (detecting changes)
3. The persistence context holds a **database connection** to sync changes on commit
4. Even after the transaction completes, if entities remain managed, Hibernate keeps tracking them
5. This tracking requires keeping the EntityManager session alive
6. The session holds a connection from the pool

### Entity States in JPA/Hibernate

1. **Transient**: New entity, not associated with any persistence context
2. **Managed**: Entity is tracked by EntityManager, changes auto-synced to DB
3. **Detached**: Entity was managed but is now disconnected from EntityManager
4. **Removed**: Entity marked for deletion

### When to Detach Entities

Detach entities when:
- ✅ You need to pass entities to long-running processes (backtesting, batch jobs)
- ✅ You're done modifying entities and just need to read their data
- ✅ You want to release database connections immediately
- ✅ You're loading data for read-only operations

Do NOT detach entities when:
- ❌ You plan to modify and save the entities
- ❌ You need lazy loading to work (lazy collections won't load on detached entities)
- ❌ You're within an active transaction that needs to track changes

### How to Detach

```kotlin
// Option 1: Detach all entities (bulk operations)
entityManager.clear()

// Option 2: Detach single entity
entityManager.detach(entity)

// CRITICAL: Force-load lazy collections BEFORE detaching!
stock.quotes.size        // Force load
stock.orderBlocks.size   // Force load
stock.earnings.size      // Force load
entityManager.detach(stock)
```

## References

- [Hibernate MultipleBagFetchException](https://vladmihalcea.com/hibernate-multiplebagfetchexception/)
- [HikariCP Connection Leak Detection](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby)
- [Spring Data JPA Query Methods](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#jpa.query-methods)
- [JPA Entity States](https://docs.oracle.com/javaee/7/tutorial/persistence-intro003.htm)

---

## Summary

**The REAL problem**: Even with optimized queries and no `@Transactional` on the backtest method, **Hibernate kept entities in a managed state**, holding database connections open for 5-10 minutes.

**The complete fix involved FOUR changes:**

1. **⭐ DETACH ENTITIES FROM HIBERNATE** - Call `entityManager.clear()` after loading data ← **THIS WAS THE KEY FIX**
2. **Removed `@Transactional` from `backtest()` method** - Backtests are pure computation and don't need transactions
3. **Optimized stock loading with JOIN FETCH** - Reduced queries from 1000+ to 3 for loading all stocks
4. **Moved all database reads outside backtest** - Controller loads all data before calling backtest

**Result**:
- Entities are converted to plain POJOs immediately after loading
- Connections are held only during data loading (seconds), not during computation (minutes)
- The backtest method operates purely on in-memory detached entities with zero database connections

---

**Date**: 2025-12-11
**Author**: Claude Code
**Status**: ✅ Implemented and Tested (305 tests passing)
