# Option P/L Calculation Implementation Plan

**Date:** 2025-12-05
**Status:** Planning Phase
**Scope:** Historical data only (no real-time)

## Problem Statement

Currently, option trades track the option premium at entry (`entryPrice`) and users manually enter exit prices. However:

**Issues:**
1. **No validation** - Users could enter incorrect option prices
2. **No Greeks data** - Can't analyze why a trade won/lost (delta, theta decay, IV changes)
3. **Manual price lookup** - Users must look up historical option prices themselves
4. **No historical analysis** - Can't backfill P/L for past trades or verify accuracy

**Simplified Approach:**
Since this is in development and we only need historical data:
- Focus on **fetching historical option prices** to validate/auto-fill entry/exit prices
- No real-time data needed (users manually close trades anyway)
- No auto-refresh or caching complexity
- Migration is not a concern

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

## Use Cases (Simplified)

### 1. Opening an Option Trade
**Current:** User manually enters option premium
**Enhanced:**
- User enters underlying symbol, strike, expiration, option type, entry date
- Click "Fetch Price" button → API fetches historical option price for that date
- Auto-fills entry price field
- Shows Greeks data for reference

### 2. Closing an Option Trade
**Current:** User manually enters exit price
**Enhanced:**
- Click "Fetch Price" button → API fetches historical option price for exit date
- Auto-fills exit price field
- Shows P/L comparison

### 3. Viewing Trade History
**Current:** Just shows entry/exit prices
**Enhanced:**
- Shows Greeks at entry and exit
- Shows implied volatility changes
- Helps understand why trade won/lost (time decay vs. stock movement)

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
     * Used to fetch price at entry/exit dates
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

### 3.1 Fetch Historical Option Price

**File:** `udgaard/src/main/kotlin/com/skrymer/udgaard/controller/OptionController.kt` (new)

```kotlin
@RestController
@RequestMapping("/api/options")
class OptionController(
    private val optionPriceService: OptionPriceService
) {
    /**
     * Fetch historical option price for a specific date
     *
     * Example: GET /api/options/historical-price?symbol=SPY&strike=600&expiration=2025-12-19&type=CALL&date=2025-12-04
     */
    @GetMapping("/historical-price")
    fun getHistoricalPrice(
        @RequestParam symbol: String,
        @RequestParam strike: Double,
        @RequestParam expiration: String,
        @RequestParam type: String,
        @RequestParam date: String
    ): ResponseEntity<OptionContract> {
        val optionType = OptionType.valueOf(type.uppercase())
        val expirationDate = LocalDate.parse(expiration)
        val priceDate = LocalDate.parse(date)

        val contract = optionPriceService.getHistoricalOptionPrice(
            underlyingSymbol = symbol,
            strike = strike,
            expiration = expirationDate,
            optionType = optionType,
            date = priceDate
        )

        return if (contract != null) {
            ResponseEntity.ok(contract)
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
```

---

## Phase 4: Frontend - "Fetch Price" Button

### 4.1 Update OpenTradeModal

Add "Fetch Price" button next to Entry Price field (for options):

```vue
<template>
  <!-- Entry Price with Fetch button -->
  <div v-if="state.instrumentType === 'OPTION'" class="space-y-2">
    <div class="flex gap-2">
      <UFormField label="Entry Premium" class="flex-1" required>
        <UInput
          v-model.number="state.entryPrice"
          type="number"
          step="0.01"
        />
      </UFormField>

      <UButton
        icon="i-lucide-download"
        label="Fetch Price"
        size="sm"
        :loading="fetchingPrice"
        :disabled="!canFetchPrice"
        class="mt-auto"
        @click="fetchHistoricalPrice"
      />
    </div>

    <!-- Show fetched data -->
    <div v-if="fetchedOptionData" class="p-3 bg-blue-50 dark:bg-blue-900/20 rounded-lg">
      <p class="text-xs font-semibold mb-2">Fetched Option Data ({{ state.entryDate }})</p>
      <div class="grid grid-cols-2 gap-2 text-xs">
        <div>
          <span class="text-muted">Mark Price:</span>
          <span class="font-medium ml-1">{{ formatCurrency(fetchedOptionData.mark) }}</span>
        </div>
        <div>
          <span class="text-muted">Bid/Ask:</span>
          <span class="font-medium ml-1">
            {{ formatCurrency(fetchedOptionData.bid) }} / {{ formatCurrency(fetchedOptionData.ask) }}
          </span>
        </div>
        <div>
          <span class="text-muted">Delta:</span>
          <span class="font-medium ml-1">{{ fetchedOptionData.delta?.toFixed(3) }}</span>
        </div>
        <div>
          <span class="text-muted">IV:</span>
          <span class="font-medium ml-1">{{ (fetchedOptionData.impliedVolatility * 100).toFixed(1) }}%</span>
        </div>
      </div>

      <UButton
        label="Use This Price"
        size="xs"
        color="primary"
        class="mt-2"
        @click="applyFetchedPrice"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
const fetchingPrice = ref(false)
const fetchedOptionData = ref<OptionContract | null>(null)

const canFetchPrice = computed(() => {
  return state.instrumentType === 'OPTION' &&
         state.symbol &&
         state.strikePrice > 0 &&
         state.expirationDate &&
         state.entryDate
})

async function fetchHistoricalPrice() {
  if (!canFetchPrice.value) return

  fetchingPrice.value = true
  try {
    const response = await $fetch<OptionContract>(
      `/udgaard/api/options/historical-price?` +
      `symbol=${state.symbol}&` +
      `strike=${state.strikePrice}&` +
      `expiration=${state.expirationDate}&` +
      `type=${state.optionType}&` +
      `date=${state.entryDate}`
    )

    fetchedOptionData.value = response

    toast.add({
      title: 'Price Fetched',
      description: `Option price: ${formatCurrency(response.mark)}`,
      color: 'success'
    })
  } catch (error) {
    toast.add({
      title: 'Error',
      description: 'Failed to fetch option price',
      color: 'error'
    })
  } finally {
    fetchingPrice.value = false
  }
}

function applyFetchedPrice() {
  if (fetchedOptionData.value) {
    state.entryPrice = fetchedOptionData.value.mark
  }
}
</script>
```

### 4.2 Update CloseTradeModal

Add same "Fetch Price" functionality for exit price:

```vue
<template>
  <div class="flex gap-2">
    <UFormField label="Exit Price" class="flex-1" required>
      <UInput
        v-model.number="exitPrice"
        type="number"
        step="0.01"
      />
    </UFormField>

    <UButton
      v-if="trade.instrumentType === 'OPTION'"
      icon="i-lucide-download"
      label="Fetch"
      size="sm"
      :loading="fetchingPrice"
      @click="fetchExitPrice"
    />
  </div>
</template>
```

---

## Phase 5: UI Enhancements

### 5.1 Option Details Card

Create new component to display option-specific details:

**File:** `asgaard_nuxt/app/components/portfolio/OptionDetailsCard.vue`

```vue
<template>
  <UCard>
    <template #header>
      <h4 class="text-sm font-semibold">Option Details</h4>
    </template>

    <div class="space-y-3">
      <!-- Contract Info -->
      <div class="grid grid-cols-2 gap-4">
        <div>
          <p class="text-xs text-muted">Contract</p>
          <p class="font-medium">{{ trade.optionType }} {{ trade.strikePrice }}</p>
        </div>
        <div>
          <p class="text-xs text-muted">Expiration</p>
          <p class="font-medium">{{ trade.expirationDate }}</p>
        </div>
      </div>

      <!-- Pricing -->
      <div class="grid grid-cols-2 gap-4">
        <div>
          <p class="text-xs text-muted">Entry Premium</p>
          <p class="font-medium">{{ formatCurrency(trade.entryPrice) }}</p>
        </div>
        <div>
          <p class="text-xs text-muted">Current Premium</p>
          <p class="font-medium">
            {{ currentPrice ? formatCurrency(currentPrice) : 'Loading...' }}
          </p>
        </div>
      </div>

      <!-- P/L -->
      <div class="pt-3 border-t">
        <div class="flex justify-between">
          <p class="text-sm font-medium">Unrealized P/L</p>
          <p class="text-sm font-bold" :class="unrealizedPL >= 0 ? 'text-green-600' : 'text-red-600'">
            {{ formatCurrency(unrealizedPL) }}
            ({{ formatPercentage(unrealizedPLPercent) }})
          </p>
        </div>
      </div>

      <!-- Greeks (if available) -->
      <div v-if="greeks" class="grid grid-cols-4 gap-2 pt-3 border-t">
        <div>
          <p class="text-xs text-muted">Delta</p>
          <p class="text-sm font-medium">{{ greeks.delta?.toFixed(3) }}</p>
        </div>
        <div>
          <p class="text-xs text-muted">Gamma</p>
          <p class="text-sm font-medium">{{ greeks.gamma?.toFixed(3) }}</p>
        </div>
        <div>
          <p class="text-xs text-muted">Theta</p>
          <p class="text-sm font-medium">{{ greeks.theta?.toFixed(3) }}</p>
        </div>
        <div>
          <p class="text-xs text-muted">Vega</p>
          <p class="text-sm font-medium">{{ greeks.vega?.toFixed(3) }}</p>
        </div>
      </div>
    </div>
  </UCard>
</template>
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

- Test full flow: Open option trade → Click "Fetch Price" → Auto-fill entry price
- Test API error handling (invalid symbol, missing data)
- Test Greeks data storage and retrieval

---

## Implementation Timeline

### Phase 1: Backend API Client (Day 1-2)
- [ ] Create AlphaVantageOptionsClient with historical options fetching
- [ ] Create OptionContract DTOs
- [ ] Create OptionPriceService
- [ ] Add OptionController with `/historical-price` endpoint
- [ ] Add unit tests

### Phase 2: Database Schema (Day 3)
- [ ] Add Greeks fields to PortfolioTrade entity (entry/exit delta, gamma, theta, vega, IV)
- [ ] Test database migration
- [ ] Update DTOs to include Greeks

### Phase 3: Frontend "Fetch Price" Button (Day 4-5)
- [ ] Add "Fetch Price" button to OpenTradeModal
- [ ] Add "Fetch Price" button to CloseTradeModal
- [ ] Display fetched Greeks data in modals
- [ ] Add toast notifications for success/error
- [ ] Handle API errors gracefully

### Phase 4: Display Greeks in Trade History (Day 6)
- [ ] Create OptionDetailsCard component
- [ ] Show entry Greeks in trade details
- [ ] Show exit Greeks for closed trades
- [ ] Add Greeks comparison for closed trades

### Phase 5: Testing & Polish (Day 7)
- [ ] Integration testing (open trade, fetch price, close trade)
- [ ] API error handling (rate limits, invalid data)
- [ ] Loading states and user feedback
- [ ] Documentation updates

---

## Data Flow Diagram

### Opening a Trade with "Fetch Price"

```
User Opens OpenTradeModal
    ↓
User enters: symbol, strike, expiration, option type, entry date
    ↓
User clicks "Fetch Price" button
    ↓
Frontend: GET /api/options/historical-price?symbol=SPY&strike=600&expiration=2025-12-19&type=CALL&date=2025-12-04
    ↓
Backend: OptionPriceService.getHistoricalOptionPrice()
    ↓
Backend: AlphaVantageOptionsClient.getHistoricalOptions(symbol, date)
    ↓
Backend: Filter contracts by strike, expiration, type
    ↓
Backend: Return OptionContract (with price, Greeks, IV)
    ↓
Frontend: Display fetched data in card (mark price, delta, IV, etc.)
    ↓
User clicks "Use This Price"
    ↓
Frontend: Auto-fills entry price field
    ↓
User submits trade → Trade stored with Greeks data
```

### Closing a Trade with "Fetch Price"

```
User Opens CloseTradeModal
    ↓
User enters exit date
    ↓
User clicks "Fetch Price" button
    ↓
Backend: Fetch historical option price for exit date
    ↓
Frontend: Auto-fills exit price field
    ↓
User submits → Trade closed with exit Greeks stored
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

✅ **Accurate Historical Prices:** "Fetch Price" button retrieves correct option prices for specific dates
✅ **Greeks Data Storage:** Entry and exit Greeks stored for trade analysis
✅ **User Experience:** Clear display of fetched option data, easy price auto-fill
✅ **Error Handling:** Graceful handling of API failures, rate limits, missing data
✅ **P/L Accuracy:** Option P/L calculated correctly using historical premium prices
✅ **Performance:** API responses under 3 seconds, no UI blocking

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
