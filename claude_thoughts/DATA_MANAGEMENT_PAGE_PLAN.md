# Data Management Page - Implementation Plan

## Overview

Create a comprehensive data management page that:
1. **Respects AlphaVantage API rate limits** (configurable based on subscription tier)
2. **Shows database statistics** (data coverage, freshness, completeness)
3. **Provides controlled data refresh** (bulk updates with progress tracking)
4. **Monitors API usage** (track daily quota consumption)
5. **Displays subscription tier** (FREE, PREMIUM, or ULTIMATE)

---

## 1. AlphaVantage Rate Limit Strategy

### Subscription Tiers

AlphaVantage offers different rate limits based on subscription:

| Tier | Requests/Minute | Requests/Day | Cost |
|------|----------------|--------------|------|
| **Free** | 5 | 500 | $0 |
| **Premium** | 75 | 75,000 | $49.99/mo |
| **Ultimate** | 120 | 120,000 | $249.99/mo |

### Configuration-Based Rate Limiting

Rate limits should be **configurable** via `application.properties` to support different subscription tiers:

**Free Tier (Default):**
```properties
# AlphaVantage API Configuration
alphavantage.api.key=YOUR_API_KEY
alphavantage.api.baseUrl=https://www.alphavantage.co/query
alphavantage.ratelimit.requestsPerMinute=5
alphavantage.ratelimit.requestsPerDay=500
```

**Premium Tier:**
```properties
alphavantage.api.key=YOUR_PREMIUM_API_KEY
alphavantage.api.baseUrl=https://www.alphavantage.co/query
alphavantage.ratelimit.requestsPerMinute=75
alphavantage.ratelimit.requestsPerDay=75000
```

**Ultimate Tier:**
```properties
alphavantage.api.key=YOUR_ULTIMATE_API_KEY
alphavantage.api.baseUrl=https://www.alphavantage.co/query
alphavantage.ratelimit.requestsPerMinute=120
alphavantage.ratelimit.requestsPerDay=120000
```

**Benefits of Configuration:**
- No code changes when upgrading subscription
- Easy testing with different rate limits
- Environment-specific limits (dev vs prod)
- Prevents accidental API quota exhaustion

### Performance Comparison

**Refresh time for 150 stocks:**

| Tier | Requests/Min | Delay Between Requests | Total Time |
|------|-------------|----------------------|-----------|
| Free | 5 | 12 seconds | **30 minutes** |
| Premium | 75 | 800ms | **2 minutes** |
| Ultimate | 120 | 500ms | **1.25 minutes** |

**Key Insight:** Upgrading from Free to Premium reduces refresh time from **30 minutes to 2 minutes** - a **15x speedup**!

### Rate Limiting Implementation

#### Backend: Configuration Class
```kotlin
@Configuration
@ConfigurationProperties(prefix = "alphavantage.ratelimit")
data class AlphaVantageRateLimitConfig(
    var requestsPerMinute: Int = 5,
    var requestsPerDay: Int = 500
)
```

#### Backend: Rate Limit DTOs
```kotlin
data class RateLimitStats(
    val requestsLastMinute: Int,
    val requestsLastDay: Int,
    val remainingMinute: Int,
    val remainingDaily: Int,
    val minuteLimit: Int,
    val dailyLimit: Int,
    val resetMinute: Long,  // seconds until reset
    val resetDaily: Long    // seconds until reset
)

data class RateLimitConfigDto(
    val requestsPerMinute: Int,
    val requestsPerDay: Int,
    val tier: String  // "FREE", "PREMIUM", or "ULTIMATE"
)
```

#### Backend: Rate Limiter Service
```kotlin
@Service
class RateLimiterService(
    private val config: AlphaVantageRateLimitConfig
) {
    private val requestQueue = ConcurrentLinkedQueue<Instant>()

    fun canMakeRequest(): Boolean {
        val now = Instant.now()
        cleanOldRequests(now)

        val lastMinute = requestQueue.count { it.isAfter(now.minus(1, ChronoUnit.MINUTES)) }
        val lastDay = requestQueue.count { it.isAfter(now.minus(24, ChronoUnit.HOURS)) }

        return lastMinute < config.requestsPerMinute && lastDay < config.requestsPerDay
    }

    fun recordRequest() {
        requestQueue.add(Instant.now())
    }

    fun getUsageStats(): RateLimitStats {
        val now = Instant.now()
        cleanOldRequests(now)

        val lastMinute = requestQueue.count { it.isAfter(now.minus(1, ChronoUnit.MINUTES)) }
        val lastDay = requestQueue.count { it.isAfter(now.minus(24, ChronoUnit.HOURS)) }

        return RateLimitStats(
            requestsLastMinute = lastMinute,
            requestsLastDay = lastDay,
            remainingMinute = config.requestsPerMinute - lastMinute,
            remainingDaily = config.requestsPerDay - lastDay,
            minuteLimit = config.requestsPerMinute,
            dailyLimit = config.requestsPerDay,
            resetMinute = calculateResetTime(1, ChronoUnit.MINUTES),
            resetDaily = calculateResetTime(24, ChronoUnit.HOURS)
        )
    }
}
```

#### Backend: Queued Refresh Service
```kotlin
@Service
class DataRefreshService(
    private val rateLimiter: RateLimiterService,
    private val config: AlphaVantageRateLimitConfig,
    private val stockService: StockService,
    private val breadthService: BreadthService,
    private val etfService: EtfService
) {
    private val refreshQueue = ConcurrentLinkedQueue<RefreshTask>()
    private var isProcessing = false
    private var currentProgress = RefreshProgress()

    suspend fun queueStockRefresh(symbols: List<String>) {
        symbols.forEach { symbol ->
            refreshQueue.add(RefreshTask(
                type = RefreshType.STOCK,
                identifier = symbol
            ))
        }
        currentProgress.total = refreshQueue.size
        startProcessing()
    }

    private suspend fun startProcessing() = coroutineScope {
        if (isProcessing) return@coroutineScope
        isProcessing = true

        launch {
            while (refreshQueue.isNotEmpty()) {
                if (!rateLimiter.canMakeRequest()) {
                    // Dynamic delay based on configured rate limit
                    // For 5 req/min: 60000ms / 5 = 12000ms
                    // For 75 req/min: 60000ms / 75 = 800ms
                    val delayMs = (60000.0 / config.requestsPerMinute).toLong()
                    delay(delayMs)
                    continue
                }

                val task = refreshQueue.poll() ?: break

                try {
                    when (task.type) {
                        RefreshType.STOCK -> stockService.getStock(task.identifier, forceFetch = true)
                        RefreshType.BREADTH -> breadthService.refreshBreadth(task.identifier)
                        RefreshType.ETF -> etfService.getEtf(task.identifier, forceFetch = true)
                    }
                    rateLimiter.recordRequest()
                    currentProgress.completed++
                    currentProgress.lastSuccess = task.identifier
                } catch (e: Exception) {
                    currentProgress.failed++
                    currentProgress.lastError = "${task.identifier}: ${e.message}"
                }
            }

            isProcessing = false
        }
    }

    fun getProgress(): RefreshProgress = currentProgress
    fun pauseProcessing() { isProcessing = false }
    fun resumeProcessing() { startProcessing() }
}
```

---

## 2. Database Statistics to Track

### Stock Data Statistics
```kotlin
data class StockDataStats(
    val totalStocks: Int,
    val totalQuotes: Long,
    val totalEarnings: Long,
    val totalOrderBlocks: Long,
    val dateRange: DateRange?,
    val averageQuotesPerStock: Double,
    val stocksWithEarnings: Int,
    val stocksWithOrderBlocks: Int,
    val lastUpdatedStock: StockUpdateInfo?,
    val oldestDataStock: StockUpdateInfo?,
    val recentlyUpdated: List<StockUpdateInfo>  // Last 10 updated
)

data class DateRange(
    val earliest: LocalDate,
    val latest: LocalDate,
    val days: Long
)

data class StockUpdateInfo(
    val symbol: String,
    val lastQuoteDate: LocalDate,
    val quoteCount: Int,
    val hasEarnings: Boolean,
    val orderBlockCount: Int
)
```

### Breadth Data Statistics
```kotlin
data class BreadthDataStats(
    val totalBreadthSymbols: Int,
    val totalBreadthQuotes: Long,
    val breadthSymbols: List<BreadthSymbolInfo>,
    val dateRange: DateRange?
)

data class BreadthSymbolInfo(
    val symbol: String,
    val quoteCount: Int,
    val lastQuoteDate: LocalDate
)
```

### ETF Data Statistics
```kotlin
data class EtfDataStats(
    val totalEtfs: Int,
    val totalEtfQuotes: Long,
    val totalHoldings: Long,
    val dateRange: DateRange?,
    val etfsWithHoldings: Int,
    val averageHoldingsPerEtf: Double
)
```

### Overall Database Statistics
```kotlin
data class DatabaseStats(
    val stockStats: StockDataStats,
    val breadthStats: BreadthDataStats,
    val etfStats: EtfDataStats,
    val totalDataPoints: Long,
    val estimatedSizeKB: Long,
    val generatedAt: LocalDateTime
)
```

---

## 3. Backend Implementation

### Controller Endpoints

```kotlin
@RestController
@RequestMapping("/api/data-management")
class DataManagementController(
    private val dataStatsService: DataStatsService,
    private val dataRefreshService: DataRefreshService,
    private val rateLimiter: RateLimiterService
) {

    @GetMapping("/stats")
    fun getDatabaseStats(): DatabaseStats {
        return dataStatsService.calculateStats()
    }

    @GetMapping("/rate-limit")
    fun getRateLimitStatus(): RateLimitStats {
        return rateLimiter.getUsageStats()
    }

    @GetMapping("/rate-limit/config")
    fun getRateLimitConfig(): RateLimitConfigDto {
        val stats = rateLimiter.getUsageStats()
        val tier = when {
            stats.minuteLimit <= 5 -> "FREE"
            stats.minuteLimit <= 75 -> "PREMIUM"
            else -> "ULTIMATE"
        }
        return RateLimitConfigDto(
            requestsPerMinute = stats.minuteLimit,
            requestsPerDay = stats.dailyLimit,
            tier = tier
        )
    }

    @PostMapping("/refresh/stocks")
    suspend fun refreshStocks(@RequestBody symbols: List<String>): RefreshResponse {
        dataRefreshService.queueStockRefresh(symbols)
        return RefreshResponse(
            queued = symbols.size,
            message = "Refresh queued successfully"
        )
    }

    @PostMapping("/refresh/all-stocks")
    suspend fun refreshAllStocks(): RefreshResponse {
        val allSymbols = stockRepository.findAll().map { it.symbol }
        dataRefreshService.queueStockRefresh(allSymbols)
        return RefreshResponse(
            queued = allSymbols.size,
            message = "All stocks queued for refresh"
        )
    }

    @PostMapping("/refresh/breadth")
    suspend fun refreshBreadth(): RefreshResponse {
        val symbols = listOf("SPY", "QQQ", "IWM")
        dataRefreshService.queueBreadthRefresh(symbols)
        return RefreshResponse(
            queued = symbols.size,
            message = "Breadth data queued for refresh"
        )
    }

    @GetMapping("/refresh/progress")
    fun getRefreshProgress(): RefreshProgress {
        return dataRefreshService.getProgress()
    }

    @PostMapping("/refresh/pause")
    fun pauseRefresh(): ResponseEntity<String> {
        dataRefreshService.pauseProcessing()
        return ResponseEntity.ok("Refresh paused")
    }

    @PostMapping("/refresh/resume")
    suspend fun resumeRefresh(): ResponseEntity<String> {
        dataRefreshService.resumeProcessing()
        return ResponseEntity.ok("Refresh resumed")
    }
}
```

### DataStatsService

```kotlin
@Service
class DataStatsService(
    private val stockRepository: StockRepository,
    private val breadthRepository: BreadthRepository,
    private val etfRepository: EtfRepository
) {

    fun calculateStats(): DatabaseStats {
        return DatabaseStats(
            stockStats = calculateStockStats(),
            breadthStats = calculateBreadthStats(),
            etfStats = calculateEtfStats(),
            totalDataPoints = calculateTotalDataPoints(),
            estimatedSizeKB = estimateDatabaseSize(),
            generatedAt = LocalDateTime.now()
        )
    }

    private fun calculateStockStats(): StockDataStats {
        val stocks = stockRepository.findAll()

        val totalQuotes = stocks.sumOf { it.quotes.size.toLong() }
        val totalEarnings = stocks.sumOf { it.earnings.size.toLong() }
        val totalOrderBlocks = stocks.sumOf { it.orderBlocks.size.toLong() }

        val allQuotes = stocks.flatMap { stock ->
            stock.quotes.map { it.date }
        }

        val dateRange = if (allQuotes.isNotEmpty()) {
            DateRange(
                earliest = allQuotes.minOrNull()!!,
                latest = allQuotes.maxOrNull()!!,
                days = ChronoUnit.DAYS.between(allQuotes.minOrNull()!!, allQuotes.maxOrNull()!!)
            )
        } else null

        val recentlyUpdated = stocks
            .filter { it.quotes.isNotEmpty() }
            .sortedByDescending { it.quotes.maxOf { q -> q.date } }
            .take(10)
            .map { stock ->
                StockUpdateInfo(
                    symbol = stock.symbol,
                    lastQuoteDate = stock.quotes.maxOf { it.date },
                    quoteCount = stock.quotes.size,
                    hasEarnings = stock.earnings.isNotEmpty(),
                    orderBlockCount = stock.orderBlocks.size
                )
            }

        return StockDataStats(
            totalStocks = stocks.size,
            totalQuotes = totalQuotes,
            totalEarnings = totalEarnings,
            totalOrderBlocks = totalOrderBlocks,
            dateRange = dateRange,
            averageQuotesPerStock = if (stocks.isNotEmpty()) totalQuotes.toDouble() / stocks.size else 0.0,
            stocksWithEarnings = stocks.count { it.earnings.isNotEmpty() },
            stocksWithOrderBlocks = stocks.count { it.orderBlocks.isNotEmpty() },
            lastUpdatedStock = recentlyUpdated.firstOrNull(),
            oldestDataStock = recentlyUpdated.lastOrNull(),
            recentlyUpdated = recentlyUpdated
        )
    }

    // Similar methods for breadth and ETF stats...
}
```

---

## 4. Frontend Implementation

### TypeScript Type Definitions

Add to `asgaard_nuxt/app/types/index.d.ts`:

```typescript
export interface RateLimitStats {
  requestsLastMinute: number
  requestsLastDay: number
  remainingMinute: number
  remainingDaily: number
  minuteLimit: number
  dailyLimit: number
  resetMinute: number
  resetDaily: number
}

export interface RateLimitConfig {
  requestsPerMinute: number
  requestsPerDay: number
  tier: 'FREE' | 'PREMIUM' | 'ULTIMATE'
}

export interface DatabaseStats {
  stockStats: StockDataStats
  breadthStats: BreadthDataStats
  etfStats: EtfDataStats
  totalDataPoints: number
  estimatedSizeKB: number
  generatedAt: string
}

export interface StockDataStats {
  totalStocks: number
  totalQuotes: number
  totalEarnings: number
  totalOrderBlocks: number
  dateRange: DateRange | null
  averageQuotesPerStock: number
  stocksWithEarnings: number
  stocksWithOrderBlocks: number
  lastUpdatedStock: StockUpdateInfo | null
  oldestDataStock: StockUpdateInfo | null
  recentlyUpdated: StockUpdateInfo[]
}

export interface DateRange {
  earliest: string
  latest: string
  days: number
}

export interface StockUpdateInfo {
  symbol: string
  lastQuoteDate: string
  quoteCount: number
  hasEarnings: boolean
  orderBlockCount: number
}

export interface BreadthDataStats {
  totalBreadthSymbols: number
  totalBreadthQuotes: number
  breadthSymbols: BreadthSymbolInfo[]
  dateRange: DateRange | null
}

export interface BreadthSymbolInfo {
  symbol: string
  quoteCount: number
  lastQuoteDate: string
}

export interface EtfDataStats {
  totalEtfs: number
  totalEtfQuotes: number
  totalHoldings: number
  dateRange: DateRange | null
  etfsWithHoldings: number
  averageHoldingsPerEtf: number
}

export interface RefreshProgress {
  total: number
  completed: number
  failed: number
  lastSuccess: string | null
  lastError: string | null
}

export interface RefreshResponse {
  queued: number
  message: string
}
```

### Page Structure: `/data-manager.vue`

```vue
<template>
  <UDashboardPanel>
    <UDashboardNavbar title="Data Management">
      <!-- ... -->
    </UDashboardNavbar>

    <UDashboardPanelContent>
      <!-- Section 1: Rate Limit Status -->
      <RateLimitCard :rate-limit="rateLimitStats" />

      <!-- Section 2: Database Statistics -->
      <DatabaseStatsCards :stats="dbStats" />

      <!-- Section 3: Refresh Controls -->
      <RefreshControlsCard
        :progress="refreshProgress"
        @refresh-stocks="handleRefreshStocks"
        @refresh-breadth="handleRefreshBreadth"
        @pause="handlePause"
        @resume="handleResume"
      />

      <!-- Section 4: Data Coverage Table -->
      <DataCoverageTable :stocks="dbStats.stockStats.recentlyUpdated" />
    </UDashboardPanelContent>
  </UDashboardPanel>
</template>
```

### Component 1: RateLimitCard

```vue
<script setup lang="ts">
const props = defineProps<{
  rateLimit: RateLimitStats
}>()

const minuteUsagePercentage = computed(() =>
  (props.rateLimit.requestsLastMinute / props.rateLimit.minuteLimit) * 100
)

const dailyUsagePercentage = computed(() =>
  (props.rateLimit.requestsLastDay / props.rateLimit.dailyLimit) * 100
)

// Determine subscription tier based on limits
const subscriptionTier = computed(() => {
  const minuteLimit = props.rateLimit.minuteLimit
  if (minuteLimit <= 5) return 'FREE'
  if (minuteLimit <= 75) return 'PREMIUM'
  return 'ULTIMATE'
})
</script>

<template>
  <UCard>
    <template #header>
      <div class="flex items-center justify-between">
        <h3 class="text-lg font-semibold">AlphaVantage Rate Limits</h3>
        <UBadge
          :color="subscriptionTier === 'FREE' ? 'neutral' : subscriptionTier === 'PREMIUM' ? 'primary' : 'success'"
          size="lg"
        >
          {{ subscriptionTier }}
        </UBadge>
      </div>
    </template>

    <div class="grid grid-cols-2 gap-4">
      <!-- Per Minute Limit -->
      <div>
        <div class="flex justify-between mb-2">
          <span class="text-sm text-muted">Minute Limit</span>
          <span class="text-sm font-medium">
            {{ rateLimit.requestsLastMinute }} / {{ rateLimit.minuteLimit }}
          </span>
        </div>
        <UProgress :value="minuteUsagePercentage" :color="minuteUsagePercentage > 80 ? 'error' : 'primary'" />
        <p class="text-xs text-muted mt-1">
          Resets in {{ rateLimit.resetMinute }}s
        </p>
      </div>

      <!-- Daily Limit -->
      <div>
        <div class="flex justify-between mb-2">
          <span class="text-sm text-muted">Daily Limit</span>
          <span class="text-sm font-medium">
            {{ rateLimit.requestsLastDay }} / {{ rateLimit.dailyLimit }}
          </span>
        </div>
        <UProgress :value="dailyUsagePercentage" :color="dailyUsagePercentage > 80 ? 'error' : 'primary'" />
        <p class="text-xs text-muted mt-1">
          {{ rateLimit.remainingDaily }} requests remaining
        </p>
      </div>
    </div>
  </UCard>
</template>
```

### Component 2: DatabaseStatsCards

```vue
<script setup lang="ts">
const props = defineProps<{
  stats: DatabaseStats
}>()
</script>

<template>
  <div>
    <h3 class="text-sm font-semibold text-muted mb-3">Database Statistics</h3>

    <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
      <!-- Total Stocks -->
      <UCard>
        <div>
          <p class="text-sm text-muted">Total Stocks</p>
          <p class="text-2xl font-bold">{{ stats.stockStats.totalStocks }}</p>
          <p class="text-xs text-muted mt-1">
            {{ stats.stockStats.averageQuotesPerStock.toFixed(0) }} avg quotes/stock
          </p>
        </div>
      </UCard>

      <!-- Total Data Points -->
      <UCard>
        <div>
          <p class="text-sm text-muted">Total Quotes</p>
          <p class="text-2xl font-bold">{{ stats.stockStats.totalQuotes.toLocaleString() }}</p>
          <p class="text-xs text-muted mt-1">
            {{ stats.stockStats.dateRange?.days }} days range
          </p>
        </div>
      </UCard>

      <!-- Breadth Data -->
      <UCard>
        <div>
          <p class="text-sm text-muted">Breadth Symbols</p>
          <p class="text-2xl font-bold">{{ stats.breadthStats.totalBreadthSymbols }}</p>
          <p class="text-xs text-muted mt-1">
            {{ stats.breadthStats.totalBreadthQuotes }} quotes
          </p>
        </div>
      </UCard>

      <!-- Last Updated -->
      <UCard>
        <div>
          <p class="text-sm text-muted">Last Updated</p>
          <p class="text-xl font-bold">
            {{ stats.stockStats.lastUpdatedStock?.symbol || 'N/A' }}
          </p>
          <p class="text-xs text-muted mt-1">
            {{ stats.stockStats.lastUpdatedStock?.lastQuoteDate || 'Never' }}
          </p>
        </div>
      </UCard>
    </div>

    <!-- Date Range -->
    <UCard v-if="stats.stockStats.dateRange" class="mt-4">
      <div class="flex items-center justify-between">
        <div>
          <p class="text-sm text-muted">Data Coverage</p>
          <p class="text-lg font-semibold">
            {{ stats.stockStats.dateRange.earliest }} to {{ stats.stockStats.dateRange.latest }}
          </p>
        </div>
        <UBadge color="success" size="lg">
          {{ stats.stockStats.dateRange.days }} days
        </UBadge>
      </div>
    </UCard>
  </div>
</template>
```

### Component 3: RefreshControlsCard

```vue
<script setup lang="ts">
const props = defineProps<{
  progress: RefreshProgress
}>()

const emit = defineEmits<{
  'refresh-stocks': []
  'refresh-breadth': []
  'pause': []
  'resume': []
}>()

const progressPercentage = computed(() => {
  if (props.progress.total === 0) return 0
  return (props.progress.completed / props.progress.total) * 100
})

const isActive = computed(() => props.progress.total > 0 && props.progress.completed < props.progress.total)
</script>

<template>
  <UCard>
    <template #header>
      <h3 class="text-lg font-semibold">Data Refresh</h3>
    </template>

    <!-- Refresh Buttons -->
    <div class="flex gap-2 mb-4">
      <UButton
        label="Refresh All Stocks"
        icon="i-lucide-refresh-cw"
        :disabled="isActive"
        @click="emit('refresh-stocks')"
      />
      <UButton
        label="Refresh Breadth"
        icon="i-lucide-activity"
        variant="outline"
        :disabled="isActive"
        @click="emit('refresh-breadth')"
      />
    </div>

    <!-- Progress Bar -->
    <div v-if="isActive" class="space-y-2">
      <div class="flex justify-between text-sm">
        <span>Progress: {{ progress.completed }} / {{ progress.total }}</span>
        <span>{{ progressPercentage.toFixed(0) }}%</span>
      </div>
      <UProgress :value="progressPercentage" />

      <div class="flex justify-between text-xs text-muted">
        <span v-if="progress.lastSuccess">Last: {{ progress.lastSuccess }}</span>
        <span v-if="progress.failed > 0" class="text-red-600">
          Failed: {{ progress.failed }}
        </span>
      </div>

      <!-- Pause/Resume -->
      <div class="flex gap-2">
        <UButton
          label="Pause"
          icon="i-lucide-pause"
          size="sm"
          variant="outline"
          @click="emit('pause')"
        />
        <UButton
          label="Resume"
          icon="i-lucide-play"
          size="sm"
          variant="outline"
          @click="emit('resume')"
        />
      </div>

      <!-- Error Display -->
      <UAlert v-if="progress.lastError" color="error" :title="progress.lastError" />
    </div>
  </UCard>
</template>
```

---

## 5. Implementation Steps

### Phase 1: Backend Foundation (Day 1-2)
1. ✅ Create `RateLimiterService` with minute/daily tracking
2. ✅ Create DTOs for stats and progress
3. ✅ Create `DataStatsService` with calculation methods
4. ✅ Add repository methods for aggregation queries
5. ✅ Write unit tests for rate limiter

### Phase 2: Refresh Service (Day 2-3)
1. ✅ Create `DataRefreshService` with queue management
2. ✅ Implement coroutine-based processing with delays
3. ✅ Add pause/resume functionality
4. ✅ Add progress tracking
5. ✅ Test with small batch of stocks

### Phase 3: Controller Layer (Day 3)
1. ✅ Create `DataManagementController`
2. ✅ Add all endpoints (stats, rate-limit, refresh, progress)
3. ✅ Test with Postman/curl
4. ✅ Add error handling

### Phase 4: Frontend Page (Day 4-5)
1. ✅ Create `/data-manager.vue` page
2. ✅ Create `RateLimitCard` component
3. ✅ Create `DatabaseStatsCards` component
4. ✅ Create `RefreshControlsCard` component
5. ✅ Add page to sidebar navigation

### Phase 5: Real-time Updates (Day 5)
1. ✅ Add polling for progress updates (every 2 seconds)
2. ✅ Add polling for rate limit status
3. ✅ Add auto-refresh for stats after refresh completes
4. ✅ Add toast notifications for completion/errors

### Phase 6: Polish & Testing (Day 6)
1. ✅ Add data coverage table
2. ✅ Add filters for stale data
3. ✅ Add manual symbol selection for refresh
4. ✅ End-to-end testing
5. ✅ Documentation

---

## 6. Advanced Features (Optional)

### Scheduled Refreshes
```kotlin
@Service
class ScheduledRefreshService {
    @Scheduled(cron = "0 0 6 * * *") // 6 AM daily
    suspend fun dailyRefresh() {
        // Refresh all stocks over several hours
        dataRefreshService.queueStockRefresh(stockRepository.findAll().map { it.symbol })
    }
}
```

### Stale Data Detection
```kotlin
fun getStaleStocks(daysOld: Int = 7): List<String> {
    val cutoffDate = LocalDate.now().minusDays(daysOld.toLong())
    return stockRepository.findAll()
        .filter { stock ->
            stock.quotes.isEmpty() || stock.quotes.maxOf { it.date }.isBefore(cutoffDate)
        }
        .map { it.symbol }
}
```

### Priority Queue (High-priority symbols first)
```kotlin
data class RefreshTask(
    val type: RefreshType,
    val identifier: String,
    val priority: Int = 0 // Higher = more important
)

// Use PriorityQueue instead of regular queue
private val refreshQueue = PriorityQueue<RefreshTask>(compareByDescending { it.priority })
```

---

## 7. Testing Strategy

### Unit Tests
- `RateLimiterService`: Test minute/daily limits
- `DataStatsService`: Test stat calculations with mock data
- `DataRefreshService`: Test queue management, pause/resume

### Integration Tests
- Test full refresh workflow with rate limiting
- Test concurrent requests don't exceed limits
- Test progress tracking accuracy

### Manual Testing
- Refresh 10 stocks, verify rate limiting works
- Pause and resume mid-refresh
- Verify stats update correctly
- Test with exceeded rate limits

---

## 8. Expected UI Layout

```
┌─────────────────────────────────────────────────────────────┐
│  Data Management                                     [Refresh Stats] │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────────────────────────────────────────┐  │
│  │ AlphaVantage Rate Limits              [FREE] Badge  │  │
│  │ ┌─────────────┐ ┌─────────────┐                   │  │
│  │ │ Minute: 2/5 │ │ Daily:45/500│                   │  │
│  │ │ ▓▓▓▓░░░░░░░ │ │ ▓▓░░░░░░░░░ │                   │  │
│  │ └─────────────┘ └─────────────┘                   │  │
│  └─────────────────────────────────────────────────────┘  │
│                                                             │
│  Database Statistics                                        │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐                      │
│  │ 150  │ │ 45K  │ │  3   │ │ AAPL │                      │
│  │Stocks│ │Quotes│ │Breadth│ │ Last │                      │
│  └──────┘ └──────┘ └──────┘ └──────┘                      │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐  │
│  │ Data Refresh                                       │  │
│  │ [Refresh All Stocks] [Refresh Breadth]            │  │
│  │                                                    │  │
│  │ Progress: 25 / 150 (17%)                          │  │
│  │ ▓▓▓░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░               │  │
│  │ Last: MSFT | Failed: 2                            │  │
│  │ [Pause] [Resume]                                   │  │
│  └─────────────────────────────────────────────────────┘  │
│                                                             │
│  Recent Updates                                             │
│  ┌─────────────────────────────────────────────────────┐  │
│  │ Symbol  Last Update    Quotes  Earnings  Status    │  │
│  │ AAPL    2024-12-04    1,234    120       ✓ Fresh  │  │
│  │ MSFT    2024-12-04    1,200    118       ✓ Fresh  │  │
│  │ TSLA    2024-11-28     980      45       ⚠ Stale  │  │
│  └─────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## Summary

This plan provides:
- ✅ **Configuration-based rate limiting** supporting FREE, PREMIUM, and ULTIMATE tiers
- ✅ **Rate-limited data refresh** that respects API limits dynamically
- ✅ **Comprehensive database statistics** for monitoring data quality
- ✅ **Queue-based processing** with pause/resume capability
- ✅ **Real-time progress tracking** with visual feedback
- ✅ **Stale data detection** to identify what needs updating
- ✅ **Subscription tier display** showing current AlphaVantage plan
- ✅ **Extensible architecture** for scheduled refreshes and prioritization

**Estimated Implementation Time:** 4-6 days

**Key Benefits:**
1. **No code changes when upgrading subscription** - just update configuration
2. **Dynamic delay calculation** - faster refreshes with Premium/Ultimate tiers
3. **Clear visibility into data freshness** and subscription status
4. **Safe bulk updates** without exceeding limits
5. **Easy monitoring and troubleshooting**
6. **Environment-specific configuration** (dev vs prod)
