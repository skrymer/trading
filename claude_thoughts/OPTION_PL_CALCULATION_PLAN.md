# Option P/L Calculation Implementation Plan

**Date:** 2025-12-05
**Status:** Planning Phase
**Scope:** Historical data only (no real-time)

## Problem Statement

Currently, option trades are entered manually with premium prices, but:

**Issues:**
1. **No historical option price data** - Can't visualize option premium movement over time
2. **Trade charts show stock prices** - Not helpful for option trades, should show option premium movement

**Approach:**
Since users enter trades manually (current date has no historical data yet):
- Use historical data **only for visualization**
- Display trade charts with actual option prices (not stock prices)
- No Greeks storage (too complex with scheduled jobs - can add later)
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

---

## Implementation Plan

---

## Phase 1: Backend - Options Data Provider Integration

### 1.1 Create OptionsDataClient Interface

**File:** `udgaard/src/main/kotlin/com/skrymer/udgaard/integration/options/OptionsDataClient.kt`

```kotlin
/**
 * Interface for option data providers
 * Allows switching between different providers (AlphaVantage, Polygon, etc.)
 */
interface OptionsDataClient {
    /**
     * Fetch historical option data for a specific date
     * @param symbol Underlying stock symbol (e.g., "SPY")
     * @param date Date to fetch (e.g., "2025-12-04")
     * @return List of option contracts with pricing data
     */
    fun getHistoricalOptions(symbol: String, date: String): List<OptionContract>?

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
    ): OptionContract?
}
```

### 1.2 Create AlphaVantage Implementation

**File:** `udgaard/src/main/kotlin/com/skrymer/udgaard/integration/alphavantage/AlphaVantageOptionsClient.kt`

```kotlin
@Service
@Primary  // Use this as default implementation
class AlphaVantageOptionsClient(
    @Value("\${alphavantage.api.key}") private val apiKey: String,
    @Value("\${alphavantage.api.baseUrl}") private val baseUrl: String,
    private val restTemplate: RestTemplate
) : OptionsDataClient {

    companion object {
        private val logger = LoggerFactory.getLogger(AlphaVantageOptionsClient::class.java)
    }

    override fun getHistoricalOptions(symbol: String, date: String): List<OptionContract>? {
        try {
            val url = "$baseUrl?function=HISTORICAL_OPTIONS&symbol=$symbol&date=$date&apikey=$apiKey"
            logger.info("Fetching historical options for $symbol on $date")

            val response = restTemplate.getForObject(url, HistoricalOptionsResponse::class.java)
            return response?.data
        } catch (e: Exception) {
            logger.error("Failed to fetch historical options for $symbol on $date", e)
            return null
        }
    }

    override fun findOptionContract(
        symbol: String,
        strike: Double,
        expiration: String,
        optionType: OptionType,
        date: String
    ): OptionContract? {
        val options = getHistoricalOptions(symbol, date) ?: return null

        return options.firstOrNull { contract ->
            contract.strike == strike &&
            contract.expiration == expiration &&
            contract.type.equals(optionType.name, ignoreCase = true)
        }
    }
}
```

**Why use an interface?**
- **Flexibility:** Switch between providers (AlphaVantage, Polygon, Interactive Brokers, etc.)
- **Testing:** Easy to mock for unit tests
- **Cost optimization:** Use different providers for different data needs
- **Fallback:** If one provider is down, switch to another

**Example: Adding a second provider**

To add Polygon.io as an alternative provider:

```kotlin
@Service
@Conditional(...)  // Enable based on configuration
class PolygonOptionsClient(
    @Value("\${polygon.api.key}") private val apiKey: String,
    private val restTemplate: RestTemplate
) : OptionsDataClient {

    override fun getHistoricalOptions(symbol: String, date: String): List<OptionContract>? {
        // Polygon API implementation
    }

    override fun findOptionContract(...): OptionContract? {
        // Polygon-specific filtering
    }
}
```

Then in `application.properties`:
```properties
# Switch providers via configuration
options.provider=alphavantage  # or "polygon"
```

### 1.3 Create OptionContract DTOs

**File:** `udgaard/src/main/kotlin/com/skrymer/udgaard/integration/options/dto/OptionContract.kt`

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

### 1.4 Add Option Price Service

**File:** `udgaard/src/main/kotlin/com/skrymer/udgaard/service/OptionPriceService.kt`

```kotlin
@Service
class OptionPriceService(
    private val optionsDataClient: OptionsDataClient  // Inject interface, not concrete class
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
        return optionsDataClient.findOptionContract(  // Use interface
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

## Phase 2: Backend - API Endpoints

### 2.1 Fetch Historical Option Prices (Date Range)

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

}

data class OptionPricePoint(
    val date: LocalDate,
    val price: Double
    // Greeks data available in API response but not stored/returned for now
    // Can add later: delta, gamma, theta, vega, impliedVolatility
)
```

---

## Phase 3: Frontend - Option Trade Chart

### 3.1 Create OptionTradeChart Component

**File:** `asgaard_nuxt/app/components/portfolio/OptionTradeChart.vue`

```vue
<template>
  <UCard>
    <template #header>
      <h4 class="text-sm font-semibold">Option Premium History</h4>
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
  },
  tooltip: {
    x: {
      format: 'dd MMM yyyy'
    },
    y: {
      formatter: (value: number) => `$${value.toFixed(2)}`
    }
  }
}))

const chartSeries = computed(() => [{
  name: 'Option Premium',
  data: priceData.value.map(p => ({
    x: new Date(p.date).getTime(),
    y: p.price
  }))
}])

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

**Note:** Greeks data is available in the AlphaVantage API response but not currently used. Can be added later as an overlay feature.


---

## Phase 4: Testing & Validation

### 4.1 Unit Tests

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

### 4.2 Integration Tests

- Test full flow: Open option trade manually → View chart
- Test option premium chart display
- Test API error handling (invalid symbol, missing data, weekends/holidays)
- Test chart rendering with various date ranges
- Test with different option types (ITM, OTM, different expirations)

---

## Implementation Timeline

### Phase 1: Backend API Client (Day 1-2)
- [ ] Create OptionsDataClient interface for provider abstraction
- [ ] Create OptionContract DTOs
- [ ] Implement AlphaVantageOptionsClient (with @Primary annotation)
- [ ] Create OptionPriceService (inject OptionsDataClient interface)
- [ ] Implement service method to fetch historical prices for date range
- [ ] Add OptionController with `/historical-prices` endpoint
- [ ] Add unit tests with mocked OptionsDataClient

### Phase 2: Frontend - Option Trade Chart (Day 3-4)
- [ ] Create OptionTradeChart component
- [ ] Fetch historical option prices for date range (entry to exit or current)
- [ ] Display option premium movement chart (instead of stock price)
- [ ] Handle loading states and errors
- [ ] Integrate into OpenTradeChart.vue or portfolio page for option trades
- [ ] Add chart tooltips and formatting

### Phase 3: Testing & Polish (Day 5)
- [ ] Integration testing (open trade, view chart)
- [ ] API error handling (rate limits, missing historical data, weekends/holidays)
- [ ] Loading states and user feedback
- [ ] Test with various option types (ITM, OTM, different expirations)
- [ ] Test chart with different date ranges
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
Backend: Loop through dates, fetch historical data from provider (AlphaVantage)
    ↓
Backend: Build price series (date, price) for each trading day
    ↓
Backend: Return List<OptionPricePoint> (date, price)
    ↓
Frontend: Render chart with option premium on Y-axis, dates on X-axis
    ↓
Frontend: Display interactive chart with tooltips
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
✅ **User Experience:** Clear display of historical option premium movement
✅ **Error Handling:** Graceful handling of API failures, rate limits, missing data
✅ **Performance:** API responses under 3 seconds, no UI blocking during chart load
✅ **Provider Flexibility:** Easy to switch between data providers via OptionsDataClient interface
✅ **Simplicity:** No complex scheduled jobs or Greeks storage - just simple price visualization

---

## Future Enhancements

1. **Greeks Storage & Display:** Add scheduled job to fetch and store Greeks data (delta, gamma, theta, vega, IV)
2. **Greeks Overlay on Charts:** Toggle to show Greeks trends over time alongside premium
3. **Multiple Data Providers:** Add Polygon.io, Interactive Brokers, or other providers as alternatives
4. **Provider Fallback Strategy:** If AlphaVantage fails, automatically try secondary provider
5. **Cost Optimization:** Route requests to cheapest provider based on quota/pricing
6. **Option Chain Viewer:** Display full option chain for underlying
7. **Implied Volatility Charts:** Track IV changes over time
8. **Profit/Loss Charts:** Visualize P/L vs. stock price
9. **What-if Analysis:** "What if stock moves to X, what's my P/L?"
10. **Alerts:** Notify when option reaches target profit/loss

---

## References

- AlphaVantage Historical Options API: https://www.alphavantage.co/documentation/#historical-options
- Current Implementation: `PortfolioTrade.kt` - profit and profitPercentage properties
- Related Issue: Option premium vs stock price confusion in P/L calculation

---

_Plan created: 2025-12-05_
_Updated: 2025-12-05 (Simplified for historical data only)_
_Ready for implementation approval_
