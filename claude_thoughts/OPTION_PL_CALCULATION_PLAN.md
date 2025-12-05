# Option P/L Calculation Implementation Plan

**Date:** 2025-12-05
**Status:** Planning Phase
**Scope:** Historical data only (no real-time)

## Problem Statement

Currently, option trades are entered manually with premium prices, but:

**Issues:**
1. **No historical option price data** - Can't calculate accurate P/L for closed option trades
2. **Trade charts show stock prices** - Not helpful for option trades, should show option premium movement
3. **No Greeks data** - Can't analyze why a trade won/lost (delta, theta decay, IV changes)
4. **Manual P/L tracking** - User must trust their entered prices are correct

**Approach:**
Since users enter trades manually (current date has no historical data yet):
- Use historical data **only for closed/past trades**
- Fetch historical option prices to calculate accurate P/L
- Display trade charts with actual option prices (not stock prices)
- Store Greeks data for analysis
- No real-time data needed
- No "Fetch Price" buttons for opening new trades

## Solution: AlphaVantage Historical Options API

**API Endpoint:** `HISTORICAL_OPTIONS`
**Documentation:** https://www.alphavantage.co/documentation/#historical-options

### API Parameters:
```
symbol=IBM (underlying stock)
date=2021-01-04 (optional - specific date)
```

### API Response Format:
```json
{
  "symbol": "SPY",
  "data": [
    {
      "contractID": "SPY251219C00600000",
      "symbol": "SPY251219C00600000",
      "expiration": "2025-12-19",
      "strike": "600.000000",
      "type": "call",
      "last": "5.72",
      "mark": "5.72",
      "bid": "5.70",
      "bid_size": 138,
      "ask": "5.74",
      "ask_size": 223,
      "volume": 1863,
      "open_interest": 7024,
      "date": "2025-12-04",
      "implied_volatility": "0.14438",
      "delta": "0.52869",
      "gamma": "0.03056",
      "theta": "-0.08661",
      "vega": "0.24102",
      "rho": "0.11799"
    }
  ]
}
```

## Use Cases

### 1. Opening an Option Trade
**User Action:** Manually enters all details
- Underlying symbol, strike, expiration, option type
- Entry date and entry premium (from their broker)
- Contracts, multiplier
- Trade stored with user-entered prices

### 2. Viewing Option Trade Chart
**Current:** Shows stock price movement
**Enhanced:**
- Fetches historical option prices for dates between entry and exit
- Displays chart with actual option premium movement
- Shows how premium changed over the trade duration
- Includes Greeks overlay (delta, theta decay, IV)

### 3. Analyzing Closed Option Trades
**Current:** Just shows entry/exit prices entered by user
**Enhanced:**
- Fetches historical option prices for entry/exit dates
- Compares user-entered prices with market prices
- Shows Greeks at entry and exit
- Calculates actual P/L based on market data
- Analyzes profit attribution (intrinsic vs. extrinsic value change)

### 4. Historical Trade Verification
**Use Case:** User wants to verify their manually entered prices were accurate
**Enhanced:**
- "Verify Prices" button on closed trades
- Fetches historical option data for entry/exit dates
- Shows comparison: "You entered $5.50, market was $5.48"
- Updates Greeks data if missing

---

## Implementation Plan

---

## Phase 1: Backend - AlphaVantage Integration

### 1.1 Create HistoricalOptionsClient

**File:** `udgaard/src/main/kotlin/com/skrymer/udgaard/integration/alphavantage/AlphaVantageOptionsClient.kt`

```kotlin
@Service
class AlphaVantageOptionsClient(
    @Value("\${alphavantage.api.key}") private val apiKey: String,
    @Value("\${alphavantage.api.baseUrl}") private val baseUrl: String
) {
    companion object {
        private val logger = LoggerFactory.getLogger(AlphaVantageOptionsClient::class.java)
    }

    /**
     * Fetch historical option data for a specific date
     * @param symbol Underlying stock symbol (e.g., "SPY")
     * @param date Date to fetch (e.g., "2025-12-04")
     * @return List of option contracts with pricing data
     */
    fun getHistoricalOptions(symbol: String, date: String): List<OptionContract>? {
        // Implementation
    }

    /**
     * Find a specific option contract by strike, expiration, type
     * @param symbol Underlying symbol
     * @param strike Strike price
     * @param expiration Expiration date (yyyy-MM-dd)
     * @param optionType CALL or PUT
     * @param date Date to fetch pricing for
     * @return Matching option contract or null
     */
    fun findOptionContract(
        symbol: String,
        strike: Double,
        expiration: String,
        optionType: OptionType,
        date: String
    ): OptionContract? {
        // Filter from getHistoricalOptions()
    }
}
```

### 1.2 Create OptionContract DTOs

**File:** `udgaard/src/main/kotlin/com/skrymer/udgaard/integration/alphavantage/dto/OptionContract.kt`

```kotlin
data class OptionContract(
    val contractID: String,
    val symbol: String,
    val expiration: String,
    val strike: Double,
    val type: String, // "call" or "put"
    val last: Double,
    val mark: Double,
    val bid: Double,
    val bidSize: Int,
    val ask: Double,
    val askSize: Int,
    val volume: Int,
    val openInterest: Int,
    val date: String,
    val impliedVolatility: Double?,
    val delta: Double?,
    val gamma: Double?,
    val theta: Double?,
    val vega: Double?,
    val rho: Double?
)

data class HistoricalOptionsResponse(
    val symbol: String,
    val data: List<OptionContract>
)
```

### 1.3 Add Option Price Service

**File:** `udgaard/src/main/kotlin/com/skrymer/udgaard/service/OptionPriceService.kt`

```kotlin
@Service
class OptionPriceService(
    private val alphaVantageOptionsClient: AlphaVantageOptionsClient
) {
    /**
     * Get historical option price for a specific date
     * Used for price verification and historical analysis
     */
    fun getHistoricalOptionPrice(
        underlyingSymbol: String,
        strike: Double,
        expiration: LocalDate,
        optionType: OptionType,
        date: LocalDate
    ): OptionContract? {
        return alphaVantageOptionsClient.findOptionContract(
            symbol = underlyingSymbol,
            strike = strike,
            expiration = expiration.toString(),
            optionType = optionType,
            date = date.toString()
        )
    }

    /**
     * Get option price and Greeks for a trade at a specific date
     * Returns full contract details including Greeks
     */
    fun getOptionDataForTrade(
        trade: PortfolioTrade,
        date: LocalDate
    ): OptionContract? {
        if (trade.instrumentType != InstrumentType.OPTION) return null

        val underlying = trade.underlyingSymbol ?: trade.symbol

        return getHistoricalOptionPrice(
            underlyingSymbol = underlying,
            strike = trade.strikePrice!!,
            expiration = trade.expirationDate!!,
            optionType = trade.optionType!!,
            date = date
        )
    }
}
```

---

## Phase 2: Backend - Store Greeks Data

### 2.1 Update PortfolioTrade Entity

Add fields to store Greeks at entry and exit:

```kotlin
// Greeks at entry
@Column(name = "entry_delta")
val entryDelta: Double? = null,

@Column(name = "entry_gamma")
val entryGamma: Double? = null,

@Column(name = "entry_theta")
val entryTheta: Double? = null,

@Column(name = "entry_vega")
val entryVega: Double? = null,

@Column(name = "entry_implied_volatility")
val entryImpliedVolatility: Double? = null,

// Greeks at exit
@Column(name = "exit_delta")
val exitDelta: Double? = null,

@Column(name = "exit_gamma")
val exitGamma: Double? = null,

@Column(name = "exit_theta")
val exitTheta: Double? = null,

@Column(name = "exit_vega")
val exitVega: Double? = null,

@Column(name = "exit_implied_volatility")
val exitImpliedVolatility: Double? = null
```

---

## Phase 3: Backend - API Endpoints

### 3.1 Fetch Historical Option Prices (Date Range)

**File:** `udgaard/src/main/kotlin/com/skrymer/udgaard/controller/OptionController.kt` (new)

```kotlin
@RestController
@RequestMapping("/api/options")
class OptionController(
    private val optionPriceService: OptionPriceService
) {
    /**
     * Fetch historical option prices for a date range (for chart display)
     *
     * Example: GET /api/options/historical-prices?symbol=SPY&strike=600&expiration=2025-12-19&type=CALL&startDate=2025-11-01&endDate=2025-12-04
     */
    @GetMapping("/historical-prices")
    fun getHistoricalPrices(
        @RequestParam symbol: String,
        @RequestParam strike: Double,
        @RequestParam expiration: String,
        @RequestParam type: String,
        @RequestParam startDate: String,
        @RequestParam endDate: String
    ): ResponseEntity<List<OptionPricePoint>> {
        val optionType = OptionType.valueOf(type.uppercase())
        val expirationDate = LocalDate.parse(expiration)
        val start = LocalDate.parse(startDate)
        val end = LocalDate.parse(endDate)

        val prices = optionPriceService.getHistoricalOptionPrices(
            underlyingSymbol = symbol,
            strike = strike,
            expiration = expirationDate,
            optionType = optionType,
            startDate = start,
            endDate = end
        )

        return ResponseEntity.ok(prices)
    }

    /**
     * Verify trade prices against historical market data
     *
     * Example: GET /api/options/verify-trade/123
     */
    @GetMapping("/verify-trade/{tradeId}")
    fun verifyTradePrices(
        @PathVariable tradeId: Long
    ): ResponseEntity<TradeVerificationResult> {
        val result = optionPriceService.verifyTradePrices(tradeId)
        return ResponseEntity.ok(result)
    }
}

data class OptionPricePoint(
    val date: LocalDate,
    val price: Double,
    val delta: Double?,
    val gamma: Double?,
    val theta: Double?,
    val vega: Double?,
    val impliedVolatility: Double?
)

data class TradeVerificationResult(
    val tradeId: Long,
    val entryComparison: PriceComparison,
    val exitComparison: PriceComparison?,
    val entryGreeks: Greeks,
    val exitGreeks: Greeks?,
    val profitAttribution: ProfitAttribution?
)

data class PriceComparison(
    val userEntered: Double,
    val marketPrice: Double,
    val difference: Double,
    val percentageDiff: Double,
    val isAccurate: Boolean // within 5% tolerance
)

data class Greeks(
    val delta: Double?,
    val gamma: Double?,
    val theta: Double?,
    val vega: Double?,
    val impliedVolatility: Double?
)

data class ProfitAttribution(
    val totalProfit: Double,
    val intrinsicValueChange: Double,
    val extrinsicValueChange: Double
)
```

---

## Phase 4: Frontend - Option Trade Chart

### 4.1 Create OptionTradeChart Component

**File:** `asgaard_nuxt/app/components/portfolio/OptionTradeChart.vue`

```vue
<template>
  <UCard>
    <template #header>
      <div class="flex justify-between items-center">
        <h4 class="text-sm font-semibold">Option Premium History</h4>
        <UButton
          icon="i-lucide-eye"
          label="Show Greeks"
          size="xs"
          variant="outline"
          @click="showGreeks = !showGreeks"
        />
      </div>
    </template>

    <div v-if="loading" class="flex justify-center py-8">
      <UIcon name="i-lucide-loader-2" class="animate-spin w-6 h-6" />
    </div>

    <div v-else-if="error" class="p-4 bg-red-50 dark:bg-red-900/20 rounded-lg">
      <p class="text-sm text-red-600">{{ error }}</p>
    </div>

    <div v-else>
      <!-- ApexCharts for option premium -->
      <apexchart
        type="line"
        height="300"
        :options="chartOptions"
        :series="chartSeries"
      />

      <!-- Greeks overlay (optional) -->
      <div v-if="showGreeks" class="mt-4 grid grid-cols-4 gap-4">
        <div v-for="greek in greeksData" :key="greek.name" class="text-center">
          <p class="text-xs text-muted">{{ greek.name }}</p>
          <p class="text-sm font-semibold">{{ greek.current }}</p>
          <p class="text-xs" :class="greek.change > 0 ? 'text-green-600' : 'text-red-600'">
            {{ greek.change > 0 ? '+' : '' }}{{ greek.change.toFixed(3) }}
          </p>
        </div>
      </div>
    </div>
  </UCard>
</template>

<script setup lang="ts">
import type { PortfolioTrade, OptionPricePoint } from '~/types'

const props = defineProps<{
  trade: PortfolioTrade
}>()

const loading = ref(true)
const error = ref<string | null>(null)
const showGreeks = ref(false)
const priceData = ref<OptionPricePoint[]>([])

const chartOptions = computed(() => ({
  chart: {
    type: 'line',
    toolbar: { show: true }
  },
  xaxis: {
    type: 'datetime',
    title: { text: 'Date' }
  },
  yaxis: {
    title: { text: 'Option Premium ($)' }
  },
  stroke: {
    curve: 'smooth',
    width: 2
  },
  markers: {
    size: 4
  }
}))

const chartSeries = computed(() => [{
  name: 'Option Premium',
  data: priceData.value.map(p => ({
    x: new Date(p.date).getTime(),
    y: p.price
  }))
}])

const greeksData = computed(() => {
  if (priceData.value.length === 0) return []

  const first = priceData.value[0]
  const last = priceData.value[priceData.value.length - 1]

  return [
    {
      name: 'Delta',
      current: last.delta?.toFixed(3) || 'N/A',
      change: (last.delta || 0) - (first.delta || 0)
    },
    {
      name: 'Gamma',
      current: last.gamma?.toFixed(3) || 'N/A',
      change: (last.gamma || 0) - (first.gamma || 0)
    },
    {
      name: 'Theta',
      current: last.theta?.toFixed(3) || 'N/A',
      change: (last.theta || 0) - (first.theta || 0)
    },
    {
      name: 'Vega',
      current: last.vega?.toFixed(3) || 'N/A',
      change: (last.vega || 0) - (first.vega || 0)
    }
  ]
})

async function loadOptionPrices() {
  loading.value = true
  error.value = null

  try {
    const response = await $fetch<OptionPricePoint[]>(
      `/udgaard/api/options/historical-prices?` +
      `symbol=${props.trade.symbol}&` +
      `strike=${props.trade.strikePrice}&` +
      `expiration=${props.trade.expirationDate}&` +
      `type=${props.trade.optionType}&` +
      `startDate=${props.trade.entryDate}&` +
      `endDate=${props.trade.exitDate || new Date().toISOString().split('T')[0]}`
    )

    priceData.value = response
  } catch (e) {
    error.value = 'Failed to load option price history'
    console.error('Failed to fetch option prices:', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (props.trade.instrumentType === 'OPTION') {
    loadOptionPrices()
  }
})
</script>
```

---

## Phase 5: Frontend - Trade Verification

### 5.1 Add "Verify Prices" Button to Portfolio Page

Update the portfolio page to show verification for closed option trades:

**File:** `asgaard_nuxt/app/pages/portfolio.vue`

```vue
<template>
  <!-- In closed trades table -->
  <UTable :rows="closedTrades">
    <template #actions="{ row }">
      <UButton
        v-if="row.instrumentType === 'OPTION'"
        icon="i-lucide-check-circle"
        label="Verify"
        size="xs"
        variant="outline"
        @click="verifyTrade(row)"
      />
    </template>
  </UTable>

  <!-- Verification Modal -->
  <UModal v-model:open="showVerification" title="Trade Verification">
    <template #body>
      <div v-if="verificationResult" class="space-y-4">
        <!-- Entry Comparison -->
        <UCard>
          <template #header>
            <h5 class="text-sm font-semibold">Entry Price Verification</h5>
          </template>

          <div class="space-y-2">
            <div class="flex justify-between">
              <span class="text-sm text-muted">Your Entry:</span>
              <span class="font-medium">{{ formatCurrency(verificationResult.entryComparison.userEntered) }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-sm text-muted">Market Price:</span>
              <span class="font-medium">{{ formatCurrency(verificationResult.entryComparison.marketPrice) }}</span>
            </div>
            <div class="flex justify-between items-center pt-2 border-t">
              <span class="text-sm text-muted">Difference:</span>
              <div class="flex items-center gap-2">
                <span :class="verificationResult.entryComparison.isAccurate ? 'text-green-600' : 'text-orange-600'">
                  {{ formatCurrency(Math.abs(verificationResult.entryComparison.difference)) }}
                  ({{ verificationResult.entryComparison.percentageDiff.toFixed(1) }}%)
                </span>
                <UIcon
                  :name="verificationResult.entryComparison.isAccurate ? 'i-lucide-check' : 'i-lucide-alert-triangle'"
                  :class="verificationResult.entryComparison.isAccurate ? 'text-green-600' : 'text-orange-600'"
                />
              </div>
            </div>
          </div>
        </UCard>

        <!-- Exit Comparison (if closed) -->
        <UCard v-if="verificationResult.exitComparison">
          <template #header>
            <h5 class="text-sm font-semibold">Exit Price Verification</h5>
          </template>

          <div class="space-y-2">
            <div class="flex justify-between">
              <span class="text-sm text-muted">Your Exit:</span>
              <span class="font-medium">{{ formatCurrency(verificationResult.exitComparison.userEntered) }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-sm text-muted">Market Price:</span>
              <span class="font-medium">{{ formatCurrency(verificationResult.exitComparison.marketPrice) }}</span>
            </div>
            <div class="flex justify-between items-center pt-2 border-t">
              <span class="text-sm text-muted">Difference:</span>
              <div class="flex items-center gap-2">
                <span :class="verificationResult.exitComparison.isAccurate ? 'text-green-600' : 'text-orange-600'">
                  {{ formatCurrency(Math.abs(verificationResult.exitComparison.difference)) }}
                  ({{ verificationResult.exitComparison.percentageDiff.toFixed(1) }}%)
                </span>
                <UIcon
                  :name="verificationResult.exitComparison.isAccurate ? 'i-lucide-check' : 'i-lucide-alert-triangle'"
                  :class="verificationResult.exitComparison.isAccurate ? 'text-green-600' : 'text-orange-600'"
                />
              </div>
            </div>
          </div>
        </UCard>

        <!-- Greeks at Entry/Exit -->
        <UCard>
          <template #header>
            <h5 class="text-sm font-semibold">Greeks Analysis</h5>
          </template>

          <div class="grid grid-cols-2 gap-4">
            <div>
              <p class="text-xs text-muted mb-2">Entry Greeks</p>
              <div class="space-y-1 text-sm">
                <div class="flex justify-between">
                  <span>Delta:</span>
                  <span class="font-medium">{{ verificationResult.entryGreeks.delta?.toFixed(3) }}</span>
                </div>
                <div class="flex justify-between">
                  <span>Theta:</span>
                  <span class="font-medium">{{ verificationResult.entryGreeks.theta?.toFixed(3) }}</span>
                </div>
                <div class="flex justify-between">
                  <span>IV:</span>
                  <span class="font-medium">{{ (verificationResult.entryGreeks.impliedVolatility * 100).toFixed(1) }}%</span>
                </div>
              </div>
            </div>

            <div v-if="verificationResult.exitGreeks">
              <p class="text-xs text-muted mb-2">Exit Greeks</p>
              <div class="space-y-1 text-sm">
                <div class="flex justify-between">
                  <span>Delta:</span>
                  <span class="font-medium">{{ verificationResult.exitGreeks.delta?.toFixed(3) }}</span>
                </div>
                <div class="flex justify-between">
                  <span>Theta:</span>
                  <span class="font-medium">{{ verificationResult.exitGreeks.theta?.toFixed(3) }}</span>
                </div>
                <div class="flex justify-between">
                  <span>IV:</span>
                  <span class="font-medium">{{ (verificationResult.exitGreeks.impliedVolatility * 100).toFixed(1) }}%</span>
                </div>
              </div>
            </div>
          </div>
        </UCard>

        <!-- Profit Attribution (if available) -->
        <UCard v-if="verificationResult.profitAttribution">
          <template #header>
            <h5 class="text-sm font-semibold">Profit Attribution</h5>
          </template>

          <div class="space-y-2 text-sm">
            <div class="flex justify-between">
              <span>Total P/L:</span>
              <span class="font-semibold" :class="verificationResult.profitAttribution.totalProfit >= 0 ? 'text-green-600' : 'text-red-600'">
                {{ formatCurrency(verificationResult.profitAttribution.totalProfit) }}
              </span>
            </div>
            <div class="flex justify-between">
              <span>From Intrinsic Value:</span>
              <span class="font-medium">{{ formatCurrency(verificationResult.profitAttribution.intrinsicValueChange) }}</span>
            </div>
            <div class="flex justify-between">
              <span>From Extrinsic Value:</span>
              <span class="font-medium">{{ formatCurrency(verificationResult.profitAttribution.extrinsicValueChange) }}</span>
            </div>
          </div>
        </UCard>
      </div>
    </template>
  </UModal>
</template>

<script setup lang="ts">
const showVerification = ref(false)
const verificationResult = ref<TradeVerificationResult | null>(null)
const verifying = ref(false)

async function verifyTrade(trade: PortfolioTrade) {
  verifying.value = true
  try {
    const result = await $fetch<TradeVerificationResult>(
      `/udgaard/api/options/verify-trade/${trade.id}`
    )
    verificationResult.value = result
    showVerification.value = true
  } catch (error) {
    toast.add({
      title: 'Error',
      description: 'Failed to verify trade prices',
      color: 'error'
    })
  } finally {
    verifying.value = false
  }
}
</script>
```

---

## Phase 6: Testing & Validation

### 6.1 Unit Tests

```kotlin
class OptionPriceServiceTest {
    @Test
    fun `test getHistoricalOptionPrice returns correct value`() {
        // Mock AlphaVantage response
        // Assert correct price returned for specific date
    }

    @Test
    fun `test findOptionContract filters correctly by strike and expiration`() {
        // Test contract matching logic
    }
}
```

### 6.2 Integration Tests

- Test full flow: Open option trade manually → View chart → Verify prices
- Test option premium chart display with Greeks overlay
- Test price verification against historical market data
- Test API error handling (invalid symbol, missing data, weekends/holidays)
- Test Greeks data storage and retrieval

---

## Implementation Timeline

### Phase 1: Backend API Client (Day 1-2)
- [ ] Create AlphaVantageOptionsClient with historical options fetching
- [ ] Create OptionContract DTOs
- [ ] Create OptionPriceService
- [ ] Add OptionController with `/historical-prices` and `/verify-trade` endpoints
- [ ] Add unit tests

### Phase 2: Database Schema (Day 3)
- [ ] Add Greeks fields to PortfolioTrade entity (entry/exit delta, gamma, theta, vega, IV)
- [ ] Test database migration
- [ ] Update DTOs to include Greeks

### Phase 3: Frontend - Option Trade Chart (Day 4-5)
- [ ] Create OptionTradeChart component
- [ ] Fetch historical option prices for date range (entry to exit or current)
- [ ] Display option premium movement chart (instead of stock price)
- [ ] Add Greeks overlay (optional toggle)
- [ ] Handle loading states and errors
- [ ] Integrate into OpenTradeChart.vue for option trades

### Phase 4: Frontend - Trade Verification & Greeks Display (Day 6)
- [ ] Add "Verify Prices" button to closed option trades
- [ ] Fetch historical data for entry/exit dates
- [ ] Show comparison: user-entered vs. market prices
- [ ] Display Greeks data at entry and exit
- [ ] Show profit attribution breakdown (intrinsic vs. extrinsic)
- [ ] Update Greeks fields in database if missing

### Phase 5: Testing & Polish (Day 7)
- [ ] Integration testing (view chart, verify prices, Greeks display)
- [ ] API error handling (rate limits, missing historical data)
- [ ] Loading states and user feedback
- [ ] Test with various option types (ITM, OTM, different expirations)
- [ ] Documentation updates

---

## Data Flow Diagram

### Viewing Option Trade Chart

```
User clicks "View Chart" on option trade
    ↓
Frontend: GET /api/options/historical-prices?symbol=SPY&strike=600&expiration=2025-12-19&type=CALL&startDate=2025-11-01&endDate=2025-12-04
    ↓
Backend: OptionPriceService.getHistoricalOptionPrices()
    ↓
Backend: Loop through dates, fetch historical data from AlphaVantage
    ↓
Backend: Build price series with Greeks for each date
    ↓
Backend: Return List<OptionPricePoint> (date, price, delta, gamma, theta, vega, IV)
    ↓
Frontend: Render chart with option premium on Y-axis, dates on X-axis
    ↓
Frontend: Optional Greeks overlay (toggle to show delta/theta/IV trends)
```

### Verifying Trade Prices

```
User clicks "Verify Prices" on closed option trade
    ↓
Frontend: GET /api/options/verify-trade/{tradeId}
    ↓
Backend: Load trade from database
    ↓
Backend: Fetch historical option data for entry date
    ↓
Backend: Fetch historical option data for exit date
    ↓
Backend: Compare user-entered vs. market prices
    ↓
Backend: Return verification result with Greeks data
    ↓
Frontend: Display comparison card:
  - Entry: You entered $5.50, Market was $5.48 (✓ Accurate)
  - Exit: You entered $0.10, Market was $0.12 (⚠️ $0.02 difference)
  - Greeks at entry/exit
  - Profit attribution (intrinsic vs. extrinsic)
```

---

## Edge Cases & Considerations

### 1. Historical Data Not Available
- **Issue:** AlphaVantage may not have data for specific dates (weekends, holidays, old data)
- **Solution:**
  - Show error message: "No option data available for this date"
  - User can manually enter price
  - Suggest closest available trading day

### 2. API Rate Limiting
- **Issue:** AlphaVantage free tier limits (5/min, 500/day)
- **Solution:**
  - Show error message: "Rate limit reached, try again in 1 minute"
  - No background jobs or auto-refresh (historical-only approach avoids this)
  - User-initiated only

### 3. API Failures
- **Issue:** AlphaVantage API down or network error
- **Solution:**
  - Show error toast with retry button
  - User can always manually enter price
  - Log error for debugging

### 4. Invalid Option Parameters
- **Issue:** User enters strike/expiration that doesn't exist
- **Solution:**
  - Backend returns 404 Not Found
  - Frontend shows: "No option found matching these parameters"
  - User verifies and corrects inputs

### 5. Symbol Format Matching
- **Issue:** Option contract symbol format varies by broker
- **Solution:**
  - Use components (symbol + expiration + type + strike) to search
  - Filter API response by matching these components
  - Don't rely on exact contract ID matching

---

## Success Metrics

✅ **Option Premium Charts:** Trade charts display actual option prices instead of stock prices
✅ **Price Verification:** "Verify Prices" accurately compares user entries against market data
✅ **Greeks Data Storage:** Entry and exit Greeks stored and displayed for trade analysis
✅ **User Experience:** Clear display of historical option data, Greeks, and profit attribution
✅ **Error Handling:** Graceful handling of API failures, rate limits, missing data
✅ **P/L Analysis:** Profit attribution breakdown (intrinsic vs. extrinsic value changes)
✅ **Performance:** API responses under 3 seconds, no UI blocking during chart load

---

## Future Enhancements

1. **Option Chain Viewer:** Display full option chain for underlying
2. **Implied Volatility Charts:** Track IV changes over time
3. **Greeks Tracking:** Monitor delta, theta decay over position lifetime
4. **Profit/Loss Charts:** Visualize P/L vs. stock price
5. **What-if Analysis:** "What if stock moves to X, what's my P/L?"
6. **Alerts:** Notify when option reaches target profit/loss

---

## References

- AlphaVantage Historical Options API: https://www.alphavantage.co/documentation/#historical-options
- Current Implementation: `PortfolioTrade.kt` - profit and profitPercentage properties
- Related Issue: Option premium vs stock price confusion in P/L calculation

---

_Plan created: 2025-12-05_
_Updated: 2025-12-05 (Simplified for historical data only)_
_Ready for implementation approval_
