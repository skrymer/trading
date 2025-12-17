# BacktestService Performance Analysis

## Executive Summary

The `BacktestService.backtest()` method processes backtests by iterating through all trading dates chronologically and evaluating entry/exit strategies for each stock. With **552 trades** from a recent backtest, performance optimizations could significantly reduce execution time.

**Key Findings:**
- Main bottleneck: **O(n × m × d)** complexity where n=stocks, m=dates, d=quote lookups
- Quote indexing already optimized with O(1) lookups
- Opportunities exist in parallel processing, early termination, and data structure optimization

---

## Current Performance Characteristics

### Complexity Analysis

```kotlin
// Main loop: O(dates × stocks)
allTradingDates.forEachIndexed { index, currentDate ->  // O(d) where d = trading days
  val entriesForThisDate = stockPairs.mapNotNull { stockPair ->  // O(n) where n = stock pairs
    val strategyQuote = quoteIndexes[stockPair.strategyStock]?.getQuote(currentDate)  // O(1)
    val tradingQuote = quoteIndexes[stockPair.tradingStock]?.getQuote(currentDate)   // O(1)

    if (strategyQuote != null && tradingQuote != null) {
      if (entryStrategy.test(strategyStockDomain, strategyQuote)) {  // O(c) conditions
        PotentialEntry(...)
      }
    }
  }

  // Ranking: O(e log e) where e = entries
  val rankedEntries = entriesForThisDate
    .map { ... ranker.score(...) }  // O(e)
    .sortedByDescending { it.score }  // O(e log e)

  // Trade creation: O(selected × quotes)
  selectedEntries.forEach { rankedEntry ->
    val trade = createTradeFromEntry(...)  // O(quotes between entry and exit)
  }
}
```

**Total Complexity:** O(d × n × c) + O(d × e × log e) + O(t × q)
- **d** = trading days (~1,260 for 5 years)
- **n** = stock pairs (~335)
- **c** = entry conditions (varies by strategy)
- **e** = entries per day (typically 5-50)
- **t** = total trades (552 in recent backtest)
- **q** = quotes per trade (average ~5-10 days)

### Current Optimizations ✅

1. **Quote Indexing** (lines 99-105)
   - O(1) date lookups via `Map<LocalDate, StockQuoteDomain>`
   - Prevents O(n) linear scans through quotes
   - **Impact:** 10-100x speedup for quote access

2. **Stock Maps** (line 298)
   - `allStocksMap = allStocks.associateBy { it.symbol }`
   - O(1) symbol lookups instead of O(n) scans

3. **Pre-fetching Data** (lines 262-294)
   - SPY, market breadth, and underlying assets loaded once
   - Prevents repeated database queries

4. **Early Null Checks** (lines 344, 361)
   - Skip processing if quotes don't exist for date
   - Avoids unnecessary strategy evaluation

---

## Performance Bottlenecks

### 1. **Main Date Loop** (lines 329-417) - CRITICAL ⚠️

**Issue:** Sequential processing of all dates × all stocks

```kotlin
allTradingDates.forEachIndexed { index, currentDate ->  // ~1,260 iterations
  val entriesForThisDate = stockPairs.mapNotNull { stockPair ->  // ~335 iterations
    // Entry strategy evaluation
  }
}
```

**Total Iterations:** 1,260 × 335 = **421,800 iterations**

**Why It's Slow:**
- Single-threaded execution
- Must process every date sequentially (chronological constraint)
- Entry strategy evaluated even when quotes don't exist

**Impact:** 🔴 **HIGH** - Dominates execution time

---

### 2. **Entry Strategy Evaluation** (line 354)

**Issue:** Strategy conditions evaluated for every stock on every date

```kotlin
if (entryStrategy.test(strategyStockDomain, strategyQuote)) {
  // Create potential entry
}
```

**Why It's Slow:**
- `test()` calls multiple condition checks (5-10 conditions per strategy)
- Each condition may access multiple quote fields
- Evaluated even when early conditions fail

**Example from PlanAlpha:**
```kotlin
// PlanAlpha has 7 conditions - all evaluated even if first fails
marketInUptrend()  // Condition 1
sectorInUptrend()  // Condition 2
priceAboveEma(20)  // Condition 3
// ... 4 more conditions
```

**Impact:** 🟡 **MEDIUM** - Evaluated 421,800 times

---

### 3. **Ranking & Sorting** (lines 368-376)

**Issue:** Every entry is scored and sorted, even with no position limit

```kotlin
val rankedEntries = entriesForThisDate
  .map { entry ->
    val score = ranker.score(strategyStockDomain, strategyQuoteDomain)
    RankedEntry(entry, score)
  }.sortedByDescending { it.score }  // O(e log e)
```

**Why It's Slow:**
- Scoring called even when `maxPositions = null` (unlimited)
- Sorting overhead when only top N needed
- Repeated scoring calculations

**Impact:** 🟡 **MEDIUM** - O(e log e) per date with entries

---

### 4. **Trade Creation** (lines 176-221)

**Issue:** Exit strategy evaluation scans future quotes linearly

```kotlin
private fun createTradeFromEntry(...): Trade? {
  val exitReport = strategyStockDomain.testExitStrategy(strategyEntryQuoteDomain, exitStrategy)

  // Get all trading quotes between entry and exit
  val tradingQuotes = entry.stockPair.tradingStock.quotes.filter { quote ->
    val qDate = quote.date
    !qDate.isBefore(entryDate) && !qDate.isAfter(exitDate)
  }
}
```

**Why It's Slow:**
- `testExitStrategy()` iterates through quotes sequentially
- Filter operation scans all quotes (not indexed by date range)
- Called for both selected AND missed trades

**Impact:** 🟡 **MEDIUM** - Called ~800+ times (552 trades + missed trades)

---

### 5. **Excursion Metrics** (lines 424-434, 547-580)

**Issue:** Post-processing iteration over all trades

```kotlin
trades.forEach { trade ->
  trade.excursionMetrics = calculateExcursionMetrics(trade, trade.entryQuote)
  trade.marketConditionAtEntry = captureMarketCondition(...)
}
```

**Why It's Slow:**
- Sequential forEach over 552 trades
- Each trade's quotes iterated again (already processed during backtest)
- Could be calculated during trade creation

**Impact:** 🟢 **LOW** - Only 552 iterations, but sequential

---

### 6. **Market Condition Capture** (lines 706-726)

**Issue:** Linear scan through SPY and breadth quotes for each trade

```kotlin
val spyQuote = spyStock.quotes.firstOrNull { it.date == date }
val breadthQuote = marketBreadth?.quotes?.firstOrNull { it.quoteDate == date }
```

**Why It's Slow:**
- `firstOrNull` is O(n) linear scan
- Not using quote index for SPY/breadth
- Called 552 times (once per trade)

**Impact:** 🟢 **LOW** - Small quote lists, but inefficient

---

## Optimization Recommendations

### 🔴 HIGH PRIORITY (Biggest Impact)

#### 1. **Parallel Stock Processing** (80% speedup potential)

**Problem:** Main loop processes stocks sequentially

**Solution:** Parallelize stock evaluation within each date

```kotlin
import kotlinx.coroutines.*

allTradingDates.forEachIndexed { index, currentDate ->
  val entriesForThisDate = runBlocking {
    stockPairs.map { stockPair ->
      async(Dispatchers.Default) {
        // Get quotes and test entry strategy
        val strategyQuote = quoteIndexes[stockPair.strategyStock]?.getQuote(currentDate)
        val tradingQuote = quoteIndexes[stockPair.tradingStock]?.getQuote(currentDate)

        if (strategyQuote != null && tradingQuote != null) {
          if (entryStrategy.test(stockPair.strategyStock, strategyQuote)) {
            PotentialEntry(stockPair, strategyQuote, tradingQuote)
          } else null
        } else null
      }
    }.awaitAll().filterNotNull()
  }

  // Rest of processing remains sequential (ranking, trade creation)
}
```

**Why It Works:**
- Entry strategy evaluation is CPU-bound and parallelizable
- Each stock evaluation is independent (no shared state)
- Can use all CPU cores (8-16x speedup on modern hardware)

**Trade-offs:**
- Requires coroutines dependency
- Slightly more complex code
- Must ensure strategy classes are thread-safe

**Estimated Speedup:** **4-8x** (depending on CPU cores)

---

#### 2. **Short-Circuit Entry Strategy Evaluation** (30% speedup potential)

**Problem:** All conditions evaluated even when early ones fail

**Solution:** Add early termination to composite strategies

```kotlin
// Current: All conditions evaluated
class CompositeEntryStrategy(private val conditions: List<EntryCondition>) : EntryStrategy {
  override fun test(stock: StockDomain, quote: StockQuoteDomain): Boolean {
    return conditions.all { it.test(stock, quote) }  // ❌ Evaluates ALL
  }
}

// Optimized: Stop at first failure
class CompositeEntryStrategy(private val conditions: List<EntryCondition>) : EntryStrategy {
  override fun test(stock: StockDomain, quote: StockQuoteDomain): Boolean {
    for (condition in conditions) {
      if (!condition.test(stock, quote)) {
        return false  // ✅ Early return
      }
    }
    return true
  }
}
```

**Why It Works:**
- Kotlin's `all()` doesn't guarantee short-circuiting
- Manual loop ensures early termination
- Most entry conditions fail early (e.g., market not in uptrend)

**Estimated Speedup:** **20-40%** (depends on condition order)

---

#### 3. **Optimize Ranking for Unlimited Positions** (10% speedup potential)

**Problem:** Scoring and sorting happen even when `maxPositions = null`

**Solution:** Skip ranking when position limit is unlimited

```kotlin
if (entriesForThisDate.isNotEmpty()) {
  val (selectedEntries, notSelectedEntries) = if (maxPositions != null) {
    // Rank and select top N
    val rankedEntries = entriesForThisDate
      .map { entry ->
        val score = ranker.score(entry.stockPair.strategyStock, entry.strategyEntryQuote)
        RankedEntry(entry, score)
      }.sortedByDescending { it.score }

    val selected = rankedEntries.take(maxPositions)
    val notSelected = rankedEntries.drop(maxPositions)
    Pair(selected.map { it.entry }, notSelected.map { it.entry })
  } else {
    // No ranking needed - take all entries
    Pair(entriesForThisDate, emptyList())
  }

  // Create trades from selected entries
  selectedEntries.forEach { entry ->
    // ...
  }
}
```

**Why It Works:**
- Eliminates unnecessary ranking overhead
- Skips sorting when all entries will be selected
- Preserves ranking logic when position limit exists

**Estimated Speedup:** **10-15%** (when maxPositions = null)

---

### 🟡 MEDIUM PRIORITY (Moderate Impact)

#### 4. **Use Partial Sorting Instead of Full Sort** (5% speedup potential)

**Problem:** Full sort when only top N needed

**Solution:** Use partial sort (selection algorithm)

```kotlin
// Current: Full sort O(e log e)
val rankedEntries = entriesForThisDate
  .map { ... }
  .sortedByDescending { it.score }
  .take(effectiveMaxPositions)

// Optimized: Partial sort O(e log k) where k = maxPositions
val rankedEntries = entriesForThisDate
  .map { ... }
  .asSequence()
  .sortedByDescending { it.score }
  .take(effectiveMaxPositions)
  .toList()
```

**Better Alternative:** Use heap-based selection

```kotlin
import java.util.PriorityQueue

val topEntries = PriorityQueue<RankedEntry>(
  maxPositions,
  compareBy { it.score }
)

entriesForThisDate.forEach { entry ->
  val score = ranker.score(...)
  val ranked = RankedEntry(entry, score)

  if (topEntries.size < maxPositions) {
    topEntries.add(ranked)
  } else if (ranked.score > topEntries.peek().score) {
    topEntries.poll()
    topEntries.add(ranked)
  }
}

val selectedEntries = topEntries.sortedByDescending { it.score }
```

**Why It Works:**
- Only keeps top N in memory
- O(e log k) vs O(e log e) complexity
- Significant when e >> k (many entries, few positions)

**Estimated Speedup:** **5-10%** (when entries >> maxPositions)

---

#### 5. **Index SPY and Breadth Quotes** (5% speedup potential)

**Problem:** Linear scan through SPY/breadth quotes

**Solution:** Create quote indexes for SPY and market breadth

```kotlin
// Add to initialization (lines 262-273)
val spyQuoteIndex = spyStock?.let { stock ->
  stock.quotes.associateBy { it.date }
}

val breadthQuoteIndex = marketBreadth?.let { breadth ->
  breadth.quotes.associateBy { it.quoteDate }
}

// Update captureMarketCondition (lines 706-726)
private fun captureMarketCondition(
  date: LocalDate,
  spyQuoteIndex: Map<LocalDate, StockQuoteDomain>?,
  breadthQuoteIndex: Map<LocalDate, BreadthQuoteDomain>?,
): MarketConditionSnapshot? {
  if (spyQuoteIndex == null) return null

  val spyQuote = spyQuoteIndex[date] ?: return null  // O(1) instead of O(n)
  val breadthQuote = breadthQuoteIndex?.get(date)    // O(1) instead of O(n)

  return MarketConditionSnapshot(...)
}
```

**Why It Works:**
- O(1) lookup instead of O(n) scan
- Consistent with quote indexing pattern
- Small memory overhead for large time savings

**Estimated Speedup:** **3-5%** (called 552 times)

---

#### 6. **Calculate Excursion Metrics During Trade Creation** (3% speedup potential)

**Problem:** Trades iterated twice (creation + metrics calculation)

**Solution:** Calculate metrics inside `createTradeFromEntry()`

```kotlin
private fun createTradeFromEntry(
  entry: PotentialEntry,
  exitStrategy: ExitStrategy,
  quoteIndexes: Map<StockDomain, StockQuoteIndex>,
): Trade? {
  // ... existing logic ...

  if (tradingExitQuote != null) {
    val profit = tradingExitQuote.closePrice - entry.tradingEntryQuote.closePrice

    val entryDate = entry.tradingEntryQuote.date
    val tradingQuotes = ...

    // Calculate excursion metrics HERE instead of later
    val excursionMetrics = calculateExcursionMetrics(tradingQuotes, entry.tradingEntryQuote)

    return Trade(
      ...,
      excursionMetrics = excursionMetrics  // Set during creation
    )
  }
}
```

**Why It Works:**
- Eliminates second iteration over trades
- Data is already in CPU cache during trade creation
- No functional change, just reorganization

**Estimated Speedup:** **2-5%**

---

### 🟢 LOW PRIORITY (Minor Impact)

#### 7. **Use Quote Index for Trade Quote Filtering** (1% speedup)

**Problem:** `filter()` scans all quotes for date range

**Solution:** Use binary search or sorted quote index

```kotlin
// Current: O(n) filter
val tradingQuotes = entry.stockPair.tradingStock.quotes.filter { quote ->
  val qDate = quote.date
  !qDate.isBefore(entryDate) && !qDate.isAfter(exitDate)
}

// Optimized: O(log n) binary search + slice
val allQuotes = entry.stockPair.tradingStock.quotes.sortedBy { it.date }
val startIndex = allQuotes.binarySearchBy(entryDate) { it.date }.let {
  if (it < 0) -(it + 1) else it
}
val endIndex = allQuotes.binarySearchBy(exitDate) { it.date }.let {
  if (it < 0) -(it + 1) else it + 1
}
val tradingQuotes = allQuotes.subList(startIndex, endIndex)
```

**Why It Works:**
- Binary search is O(log n) vs O(n) linear scan
- Assumes quotes are already sorted (they usually are)

**Estimated Speedup:** **1-2%**

---

#### 8. **Cache AssetMapper Results** (1% speedup)

**Problem:** `getStrategySymbol()` calls `AssetMapper` repeatedly

**Solution:** Cache underlying symbol mappings

```kotlin
// Add memoization
private val underlyingSymbolCache = mutableMapOf<String, String>()

private fun getStrategySymbol(
  tradingSymbol: String,
  useUnderlying: Boolean,
  customMap: Map<String, String>?,
): String {
  if (!useUnderlying) return tradingSymbol
  customMap?.get(tradingSymbol)?.let { return it }

  return underlyingSymbolCache.getOrPut(tradingSymbol) {
    AssetMapper.getUnderlyingSymbol(tradingSymbol)
  }
}
```

**Why It Works:**
- Eliminates repeated AssetMapper lookups
- O(1) cache lookup vs function call overhead

**Estimated Speedup:** **<1%**

---

## Cumulative Impact Estimate

| Optimization | Priority | Complexity | Speedup | Cumulative |
|-------------|----------|------------|---------|------------|
| **Parallel Stock Processing** | 🔴 High | Medium | 4-8x | **4-8x** |
| **Short-Circuit Conditions** | 🔴 High | Low | 1.3-1.4x | **5-11x** |
| **Skip Ranking (Unlimited)** | 🔴 High | Low | 1.1x | **5.5-12x** |
| **Partial Sorting** | 🟡 Medium | Medium | 1.05x | **5.8-12.6x** |
| **Index SPY/Breadth** | 🟡 Medium | Low | 1.03x | **6-13x** |
| **Inline Metrics Calc** | 🟡 Medium | Low | 1.03x | **6.2-13.4x** |

**Final Estimated Speedup:** **6-13x faster** with all optimizations

---

## Implementation Priority

### Phase 1: Quick Wins (1-2 hours)
1. ✅ Short-circuit entry strategy evaluation
2. ✅ Skip ranking when maxPositions = null
3. ✅ Index SPY and breadth quotes

**Expected Impact:** 1.5-2x speedup

### Phase 2: Major Performance (4-6 hours)
1. ✅ Implement parallel stock processing with coroutines
2. ✅ Use partial sorting for ranking
3. ✅ Calculate metrics during trade creation

**Expected Impact:** 5-8x speedup (cumulative)

### Phase 3: Polish (1-2 hours)
1. ✅ Optimize quote filtering with binary search
2. ✅ Cache AssetMapper results

**Expected Impact:** 6-10x speedup (cumulative)

---

## Trade-offs & Considerations

### Parallelization Caveats
- **Thread Safety:** Strategy classes must be immutable/thread-safe
- **Memory:** Multiple concurrent operations increase memory usage
- **Debugging:** Parallel bugs harder to reproduce
- **Complexity:** Requires coroutines knowledge

### Early Termination Caveats
- **Condition Order Matters:** Put most restrictive conditions first
- **Logging:** May lose visibility into which condition failed
- **Testing:** Need to verify all conditions still evaluated when needed

### Caching Caveats
- **Memory Usage:** Caches consume memory (minimal for symbol mapping)
- **Invalidation:** Cached values must be cleared if data changes
- **Thread Safety:** Shared caches need synchronization in parallel mode

---

## Benchmark Recommendations

### Before Optimization
```kotlin
val startTime = System.currentTimeMillis()
val report = backtestService.backtest(...)
val duration = System.currentTimeMillis() - startTime
logger.info("Backtest completed in ${duration}ms")
```

### After Each Optimization
- Measure total execution time
- Measure time per section (loop, ranking, trade creation)
- Compare against baseline
- Look for regressions in other areas

### Profiling Tools
- JProfiler / YourKit for JVM profiling
- IntelliJ IDEA built-in profiler
- VisualVM for CPU/memory analysis
- Custom timing logs for specific sections

---

## Monitoring & Metrics

### Add Performance Logging

```kotlin
fun backtest(...): BacktestReport {
  val startTime = System.currentTimeMillis()
  logger.info("Backtest starting: ${stocks.size} stocks, ${after} to ${before}")

  // ... existing code ...

  val indexingTime = System.currentTimeMillis()
  logger.debug("Quote indexing: ${indexingTime - startTime}ms")

  // Main loop
  allTradingDates.forEach { ... }

  val loopTime = System.currentTimeMillis()
  logger.debug("Main loop: ${loopTime - indexingTime}ms")

  // Metrics calculation
  trades.forEach { ... }

  val metricsTime = System.currentTimeMillis()
  logger.debug("Metrics calculation: ${metricsTime - loopTime}ms")

  val totalTime = metricsTime - startTime
  logger.info("Backtest complete: ${trades.size} trades in ${totalTime}ms")

  return BacktestReport(...)
}
```

---

## Long-Term Architectural Improvements

### 1. **Pre-computed Entry Signals**
- Store entry signals in database during stock refresh
- Backtest only needs to check pre-computed flags
- Reduces runtime computation

### 2. **Vectorized Operations**
- Use Apache Arrow or similar for columnar data
- SIMD operations for bulk calculations
- Requires architecture change

### 3. **Incremental Backtesting**
- Cache backtest results
- Only recalculate when strategy/data changes
- Requires cache invalidation logic

### 4. **Distributed Processing**
- Split stocks across multiple servers
- Aggregate results at the end
- Requires infrastructure changes

---

## Conclusion

The BacktestService has **significant optimization opportunities**, with potential for **6-13x speedup** through parallelization, early termination, and algorithmic improvements. The quick wins (Phase 1) can be implemented in 1-2 hours for immediate 1.5-2x improvement, while full implementation could reduce backtest time from ~5 minutes to ~30 seconds.

**Recommended Next Steps:**
1. Implement Phase 1 optimizations (short-circuit, skip ranking, index quotes)
2. Benchmark and verify improvements
3. Implement parallel processing (Phase 2)
4. Consider long-term architectural changes for 100x+ speedups

---

_Last Updated: 2025-12-13_
_Backtest Baseline: 552 trades, ~335 stocks, 5-year period_
