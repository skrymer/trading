# Performance Improvements - Backtest Timeout Fix

## Problem
The backtesting API was timing out when processing many stocks, causing "Headers Timeout Error" in the Nuxt frontend. This occurred because:
- Adding more stocks increased processing time significantly
- Default Nuxt fetch timeout was too short for large backtests
- No caching was in place for repeated queries
- Users had no visibility into long-running operations

## Solutions Implemented

### 1. Frontend Timeout Configuration

**File: `asgaard_nuxt/nuxt.config.ts`**
- Added Nitro configuration with extended keep-alive timeout (600s/10 minutes)
- Configured route-specific headers for the API proxy

**File: `asgaard_nuxt/app/pages/backtesting.vue`**
- Increased fetch timeout to 600,000ms (10 minutes)
- This allows large backtests to complete without timing out

```typescript
const report = await $fetch<{ trades: Trade[] }>('/udgaard/api/backtest', {
  method: 'POST',
  body: config,
  timeout: 600000 // 10 minutes
})
```

### 2. Enhanced User Experience

**Loading States & Progress Indicators:**
- Added full-screen loading state with animated spinner
- Displays helpful messages about what's happening during backtest
- Shows estimated time and progress indicators
- Prevents users from accidentally closing the page

**Toast Notifications:**
- "Backtest Started" notification with stock count
- "Backtest Complete" notification with trade count and duration
- "Backtest Failed" notification with error details
- Helps users understand when operations begin and end

**Performance Warnings:**
- Added warning alert when "All Stocks" is selected
- Informs users that processing may take 5-10 minutes
- Suggests selecting specific stocks for faster results
- Prevents user frustration from unexpected wait times

### 3. Backend Caching

**New Files:**
- `udgaard/src/main/kotlin/com/skrymer/udgaard/config/CacheConfig.kt`

**Dependencies Added:**
- `spring-boot-starter-cache` - Spring Cache support
- `caffeine:3.1.8` - High-performance caching library

**Cache Configuration:**
- Cache names: `stocks`, `backtests`, `marketBreadth`
- Maximum size: 1000 entries per cache
- TTL: 30 minutes (expireAfterWrite)
- Statistics recording enabled

**Cached Methods:**
- `StockService.getStock()` - Individual stock retrieval
- `StockService.getAllStocks()` - All stocks retrieval
- Cache is bypassed when `forceFetch=true` or `refresh=true`

**Configuration in `application.properties`:**
```properties
spring.cache.type=caffeine
spring.cache.cache-names=stocks,backtests,marketBreadth
spring.cache.caffeine.spec=maximumSize=1000,expireAfterWrite=30m
```

### 4. Backend Performance Notes

The backend already uses efficient parallel processing:
- **Coroutines** with `async`/`await` for concurrent stock fetching
- **Limited parallelism** (10 concurrent fetches) to avoid overwhelming the API
- **Supervisor scope** for graceful error handling
- **Date-by-date processing** ensures accurate backtesting chronology

## Performance Improvements

### Before:
- ❌ Timeout after ~30 seconds with many stocks
- ❌ No visibility into progress
- ❌ Repeated queries refetch all data
- ❌ Users confused about wait times

### After:
- ✅ 10-minute timeout allows large backtests to complete
- ✅ Clear loading indicators and progress messages
- ✅ Cached stock data speeds up subsequent backtests
- ✅ Users warned about long-running operations
- ✅ Toast notifications provide feedback
- ✅ 30-minute cache reduces database load

## Expected Performance Gains

1. **First Backtest (Cold Cache):**
   - Still takes full time to fetch and process
   - But now completes instead of timing out
   - User has visibility into progress

2. **Subsequent Backtests (Warm Cache):**
   - Stock data served from cache (instant)
   - Backtest processing time remains the same
   - Overall: ~30-50% faster for repeated queries

3. **Partial Backtests:**
   - Selecting specific stocks is much faster
   - Recommended for iterative strategy testing
   - Cache still benefits these queries

## Usage Recommendations

### For Users:
1. **Start small**: Test with 5-10 stocks first
2. **Use cache**: Run multiple backtests with same stocks to benefit from cache
3. **Be patient**: Large backtests (all stocks) take 5-10+ minutes
4. **Watch notifications**: Toast messages show progress and completion

### For Developers:
1. **Monitor cache**: Use Spring Actuator to view cache statistics
2. **Adjust TTL**: Change `expireAfterWrite` if data freshness requirements change
3. **Clear cache**: Restart app or add cache eviction endpoint if needed
4. **Add more caching**: Consider caching backtest results with strategy hash as key

## Future Enhancements

### Streaming Results (Advanced):
- Implement Server-Sent Events (SSE) for real-time progress
- Stream trades as they're calculated
- Show partial results before completion

### Background Processing:
- Move backtests to async job queue
- Return job ID immediately
- Poll for results or use websockets
- Allows users to navigate away and return later

### Database Optimization:
- Add indexes on frequently queried fields
- Denormalize data for faster reads
- Consider read replicas for heavy loads

### Result Caching:
- Cache backtest results by strategy configuration hash
- Dramatically speed up identical backtests
- Implement cache invalidation when data updates

## Testing

To verify the improvements:

1. **Test timeout fix:**
   ```bash
   # Frontend
   cd asgaard_nuxt
   pnpm dev

   # Backend
   cd udgaard
   ./gradlew build
   ./gradlew bootRun

   # Run backtest with "All Stocks" and verify it completes
   ```

2. **Test caching:**
   - Run same backtest twice
   - Second run should be faster
   - Check logs for cache hits

3. **Test user experience:**
   - Verify loading states appear
   - Check toast notifications
   - Confirm performance warnings show

## Configuration

All timeout and cache settings can be adjusted:

**Frontend Timeout:**
```typescript
// In backtesting.vue
timeout: 600000 // Change as needed
```

**Cache TTL:**
```properties
# In application.properties
spring.cache.caffeine.spec=maximumSize=1000,expireAfterWrite=30m
# Change 30m to desired duration (e.g., 1h, 2h)
```

**Cache Size:**
```properties
spring.cache.caffeine.spec=maximumSize=1000,expireAfterWrite=30m
# Change 1000 to desired max entries
```

## Monitoring

Check cache effectiveness:
```bash
# Access actuator endpoint
curl http://localhost:8080/actuator/caches
```

View cache statistics in logs (if enabled):
```kotlin
cacheManager.setCaffeine(
  Caffeine.newBuilder()
    .recordStats() // Already enabled
)
```

---

**Date:** 2025-11-12
**Status:** Completed
**Impact:** High - Resolves timeout issues and improves overall performance
