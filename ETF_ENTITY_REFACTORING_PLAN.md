# ETF Entity Refactoring Plan

## Overview

This plan outlines the refactoring to create a clear separation between stocks and ETFs by introducing a dedicated ETF entity model. The ETF model will be extended with holding weightings, trend data, and other metadata for future enhancements.

## Current State Analysis

### Existing Models

**Stock Model** (`model/Stock.kt`):
- MongoDB collection: `stocks`
- Fields: symbol, sectorSymbol, quotes, orderBlocks, ovtlyrPerformance
- Has rich quote history and strategy testing methods

**Etf Enum** (`model/Etf.kt`):
- Simple enum with symbol and description
- Values: QQQ, SPY, IWM, DIA
- No persistent data or quotes

**EtfMembership** (`model/EtfMembership.kt`):
- Static object with hardcoded stock lists
- Maps ETFs to their constituent stocks
- No weighting information

**EtfStatsService** (`service/EtfStatsService.kt`):
- Calculates stats on-the-fly from constituent stocks
- No persistent ETF data
- Computes bullish percentage, uptrend status

### Issues with Current Approach

1. **No ETF Price Data**: ETFs don't have their own quote history
2. **No Holding Weightings**: Can't distinguish between 5% and 0.1% holdings
3. **Computed Every Time**: Stats are recalculated from scratch on each request
4. **Hardcoded Memberships**: Stock lists are static in code, not database
5. **No Separation**: ETFs and Stocks are conflated in some APIs
6. **Limited Metadata**: No ability to store ETF-specific data (expense ratio, AUM, etc.)

---

## Proposed Architecture

### Option 1: ETF as Separate Entity (Recommended)

**Rationale**: Clear separation of concerns, allows ETF-specific behavior and data

#### New Models

**EtfEntity** (`model/EtfEntity.kt`):
```kotlin
@Document(collection = "etfs")
class EtfEntity {
    @Id
    var symbol: String? = null                    // "SPY", "QQQ", etc.
    var name: String? = null                       // "SPDR S&P 500 ETF Trust"
    var description: String? = null                // "Tracks S&P 500 Index"
    var quotes: List<EtfQuote> = emptyList()      // Historical price data
    var holdings: List<EtfHolding> = emptyList()  // Current holdings with weights
    var metadata: EtfMetadata? = null              // Expense ratio, AUM, etc.

    // Computed fields
    fun getLatestQuote(): EtfQuote?
    fun getQuoteByDate(date: LocalDate): EtfQuote?
    fun getBullishPercentage(date: LocalDate?): Double
    fun getStocksInUptrend(date: LocalDate?): Int
}
```

**EtfQuote** (`model/EtfQuote.kt`):
```kotlin
data class EtfQuote(
    val date: LocalDate,
    val openPrice: Double,
    val closePrice: Double,
    val high: Double,
    val low: Double,
    val volume: Long,

    // Technical indicators (same as StockQuote)
    val closePriceEMA5: Double = 0.0,
    val closePriceEMA10: Double = 0.0,
    val closePriceEMA20: Double = 0.0,
    val closePriceEMA50: Double = 0.0,
    val atr: Double = 0.0,

    // ETF-specific trend metrics
    val bullishPercentage: Double = 0.0,          // % of holdings in uptrend
    val stocksInUptrend: Int = 0,                 // Count of holdings in uptrend
    val stocksInDowntrend: Int = 0,               // Count of holdings in downtrend
    val stocksInNeutral: Int = 0,                 // Count of holdings neutral
    val totalHoldings: Int = 0,                   // Total constituent stocks

    // Buy/sell signals
    val lastBuySignal: LocalDate? = null,
    val lastSellSignal: LocalDate? = null
)
```

**EtfHolding** (`model/EtfHolding.kt`):
```kotlin
data class EtfHolding(
    val stockSymbol: String,                       // "AAPL", "MSFT", etc.
    val weight: Double,                            // Percentage weight (0.0-100.0)
    val shares: Long? = null,                      // Optional: number of shares held
    val marketValue: Double? = null,               // Optional: dollar value of holding
    val asOfDate: LocalDate,                       // When this holding data was captured

    // Computed/cached trend data for this holding
    val inUptrend: Boolean = false,                // Is this holding in uptrend?
    val trend: String? = null                      // "UPTREND", "DOWNTREND", "NEUTRAL"
)
```

**EtfMetadata** (`model/EtfMetadata.kt`):
```kotlin
data class EtfMetadata(
    val expenseRatio: Double? = null,              // Annual expense ratio (%)
    val aum: Double? = null,                       // Assets under management ($)
    val inceptionDate: LocalDate? = null,          // ETF launch date
    val issuer: String? = null,                    // "State Street", "Vanguard", etc.
    val exchange: String? = null,                  // "NYSE", "NASDAQ", etc.
    val currency: String = "USD",                  // Base currency
    val type: String? = null,                      // "Index", "Sector", "Leveraged", etc.
    val benchmark: String? = null,                 // "S&P 500", "NASDAQ-100", etc.
    val lastRebalanceDate: LocalDate? = null       // When holdings were last rebalanced
)
```

#### Renamed/Deprecated

- **Etf enum** → **EtfSymbol enum** (keep for type safety, similar to SectorSymbol)
- **EtfMembership** → Deprecated (holdings now in database)

**EtfSymbol** (`model/EtfSymbol.kt`):
```kotlin
enum class EtfSymbol(val description: String) {
    SPY("SPDR S&P 500 ETF Trust"),
    QQQ("Invesco QQQ Trust"),
    IWM("iShares Russell 2000 ETF"),
    DIA("SPDR Dow Jones Industrial Average ETF"),
    TQQQ("ProShares UltraPro QQQ"),    // Example: leveraged ETF
    SQQQ("ProShares UltraPro Short QQQ"); // Example: inverse ETF

    companion object {
        fun fromString(value: String?): EtfSymbol? {
            if (value == null) return null
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
    }
}
```

---

## Backend Changes

### Repository Layer

**New Repository**:
```kotlin
// repository/EtfRepository.kt
interface EtfRepository : MongoRepository<EtfEntity, String> {
    fun findBySymbol(symbol: String): EtfEntity?
    fun findBySymbolIn(symbols: List<String>): List<EtfEntity>
}
```

### Service Layer

**New Service**:
```kotlin
// service/EtfService.kt
@Service
class EtfService(
    val etfRepository: EtfRepository,
    val stockService: StockService,
    val ovtlyrClient: OvtlyrClient  // or other data provider
) {
    // Fetch ETF quote data and save
    suspend fun refreshEtf(symbol: String): EtfEntity

    // Get ETF with optional refresh
    fun getEtf(symbol: String, refresh: Boolean = false): EtfEntity?

    // Update holdings for an ETF
    fun updateHoldings(symbol: String, holdings: List<EtfHolding>): EtfEntity

    // Calculate and store trend metrics for a specific date
    fun calculateTrendMetrics(etf: EtfEntity, date: LocalDate): EtfQuote

    // Refresh all ETFs
    suspend fun refreshAll(): Map<String, String>
}
```

**Updated Service**:
```kotlin
// service/EtfStatsService.kt
// Now uses EtfEntity instead of calculating from stocks
@Service
class EtfStatsService(
    val etfService: EtfService,
    val stockService: StockService
) {
    fun getEtfStats(
        symbol: EtfSymbol,
        fromDate: LocalDate,
        toDate: LocalDate
    ): EtfStatsResponse {
        // Get ETF entity from database
        val etf = etfService.getEtf(symbol.name)
            ?: throw IllegalArgumentException("ETF not found: $symbol")

        // Return stats directly from ETF quotes (no recalculation needed)
        return EtfStatsResponse(
            symbol = etf.symbol!!,
            name = etf.name!!,
            currentStats = calculateCurrentStats(etf),
            historicalData = getHistoricalData(etf, fromDate, toDate),
            holdings = etf.holdings.map { /* convert to DTO */ }
        )
    }
}
```

### Controller Layer

**Updated Controller**:
```kotlin
// controller/EtfController.kt
@RestController
@RequestMapping("/api/etf")
class EtfController(
    val etfService: EtfService,
    val etfStatsService: EtfStatsService
) {
    // Get ETF data
    @GetMapping("/{symbol}")
    fun getEtf(
        @PathVariable symbol: String,
        @RequestParam(defaultValue = "false") refresh: Boolean
    ): ResponseEntity<EtfEntity>

    // Get ETF stats
    @GetMapping("/{symbol}/stats")
    fun getEtfStats(...): ResponseEntity<EtfStatsResponse>

    // Get ETF holdings
    @GetMapping("/{symbol}/holdings")
    fun getHoldings(@PathVariable symbol: String): ResponseEntity<List<EtfHolding>>

    // Update ETF holdings (admin endpoint)
    @PostMapping("/{symbol}/holdings")
    fun updateHoldings(
        @PathVariable symbol: String,
        @RequestBody holdings: List<EtfHolding>
    ): ResponseEntity<EtfEntity>

    // Refresh ETF data
    @PostMapping("/{symbol}/refresh")
    fun refreshEtf(@PathVariable symbol: String): ResponseEntity<EtfEntity>

    // Refresh all ETFs
    @PostMapping("/refresh-all")
    fun refreshAll(): ResponseEntity<Map<String, String>>
}
```

### Integration Layer

**Data Sources** (needs research):
- Alpha Vantage: ETF quote data
- ETF.com API: Holdings and weights
- Yahoo Finance: Alternative for quotes
- Manual CSV import: Holdings data

**New Integration**:
```kotlin
// integration/alphavantage/AlphaVantageClient.kt (extend existing)
// Add methods for ETF quote data

// integration/etf/EtfDataProvider.kt (new interface)
interface EtfDataProvider {
    fun getEtfQuotes(symbol: String): List<EtfQuote>
    fun getEtfHoldings(symbol: String): List<EtfHolding>
}
```

---

## Frontend Changes

### Type Definitions

**New Types** (`app/types/index.d.ts`):
```typescript
export interface EtfEntity {
  symbol: string
  name: string
  description: string
  quotes: EtfQuote[]
  holdings: EtfHolding[]
  metadata: EtfMetadata | null
}

export interface EtfQuote {
  date: string
  openPrice: number
  closePrice: number
  high: number
  low: number
  volume: number
  closePriceEMA5: number
  closePriceEMA10: number
  closePriceEMA20: number
  closePriceEMA50: number
  atr: number
  bullishPercentage: number
  stocksInUptrend: number
  stocksInDowntrend: number
  stocksInNeutral: number
  totalHoldings: number
  lastBuySignal: string | null
  lastSellSignal: string | null
}

export interface EtfHolding {
  stockSymbol: string
  weight: number
  shares: number | null
  marketValue: number | null
  asOfDate: string
  inUptrend: boolean
  trend: string | null
}

export interface EtfMetadata {
  expenseRatio: number | null
  aum: number | null
  inceptionDate: string | null
  issuer: string | null
  exchange: string | null
  currency: string
  type: string | null
  benchmark: string | null
  lastRebalanceDate: string | null
}

export interface EtfStatsResponse {
  symbol: string
  name: string
  currentStats: EtfCurrentStats
  historicalData: EtfHistoricalDataPoint[]
  holdings: EtfHolding[]
  warning: string | null
}
```

**Updated Enums** (`app/types/enums.ts`):
```typescript
export enum EtfSymbol {
  SPY = 'SPY',
  QQQ = 'QQQ',
  IWM = 'IWM',
  DIA = 'DIA',
  TQQQ = 'TQQQ',
  SQQQ = 'SQQQ'
}

export const EtfSymbolDescriptions: Record<EtfSymbol, string> = {
  [EtfSymbol.SPY]: 'SPDR S&P 500 ETF Trust',
  [EtfSymbol.QQQ]: 'Invesco QQQ Trust',
  [EtfSymbol.IWM]: 'iShares Russell 2000 ETF',
  [EtfSymbol.DIA]: 'SPDR Dow Jones Industrial Average ETF',
  [EtfSymbol.TQQQ]: 'ProShares UltraPro QQQ',
  [EtfSymbol.SQQQ]: 'ProShares UltraPro Short QQQ'
}
```

### Component Updates

**New Components**:
- `components/etf/EtfChart.vue` - Price chart for ETF
- `components/etf/EtfHoldings.vue` - Holdings table with weights
- `components/etf/EtfTrendIndicator.vue` - Visual trend display
- `components/etf/EtfMetadata.vue` - Display ETF metadata

**Updated Pages**:
- ETF detail page showing quotes, holdings, trends
- Portfolio page to support ETF positions

---

## Data Migration Strategy

### Phase 1: Create Schema (No Breaking Changes)

1. **Add new models** (EtfEntity, EtfQuote, EtfHolding, EtfMetadata)
2. **Add new repository** (EtfRepository)
3. **Add new service** (EtfService)
4. **Keep existing** Etf enum, EtfMembership, EtfStatsService

**Timeline**: Week 1

### Phase 2: Data Population

1. **Create initial ETF entities** in database for SPY, QQQ, IWM, DIA
2. **Fetch historical quotes** from data provider
3. **Import holdings data** (manual CSV or API)
4. **Calculate trend metrics** for historical dates
5. **Add background job** to refresh ETF data nightly

**Timeline**: Week 2

### Phase 3: Controller Updates (Backward Compatible)

1. **Add new endpoints** (`/api/etf/{symbol}`, `/api/etf/{symbol}/holdings`)
2. **Keep existing endpoints** (`/api/etf/{symbol}/stats`)
3. **Update stats endpoint** to use EtfEntity internally
4. **Add deprecation warnings** to old approach

**Timeline**: Week 3

### Phase 4: Frontend Migration

1. **Add new types** and enums
2. **Create new components** for ETF display
3. **Update existing pages** to use new API
4. **Add holdings visualization**

**Timeline**: Week 4

### Phase 5: Cleanup (Breaking Changes)

1. **Remove EtfMembership** object (replaced by database holdings)
2. **Simplify EtfStatsService** (no longer recalculates from stocks)
3. **Remove deprecated code**

**Timeline**: Week 5

---

## Database Schema

### New MongoDB Collections

**Collection**: `etfs`

```javascript
{
  "_id": "SPY",
  "symbol": "SPY",
  "name": "SPDR S&P 500 ETF Trust",
  "description": "Tracks the S&P 500 Index",
  "quotes": [
    {
      "date": "2024-11-22",
      "openPrice": 593.45,
      "closePrice": 595.12,
      "high": 596.23,
      "low": 592.88,
      "volume": 45230100,
      "closePriceEMA5": 592.45,
      "closePriceEMA10": 590.12,
      "closePriceEMA20": 585.34,
      "closePriceEMA50": 575.89,
      "atr": 5.23,
      "bullishPercentage": 67.3,
      "stocksInUptrend": 336,
      "stocksInDowntrend": 123,
      "stocksInNeutral": 44,
      "totalHoldings": 503
    }
  ],
  "holdings": [
    {
      "stockSymbol": "AAPL",
      "weight": 7.12,
      "shares": 150234567,
      "marketValue": 28500000000,
      "asOfDate": "2024-11-15",
      "inUptrend": true,
      "trend": "UPTREND"
    },
    {
      "stockSymbol": "MSFT",
      "weight": 6.85,
      "shares": 145234567,
      "marketValue": 27500000000,
      "asOfDate": "2024-11-15",
      "inUptrend": true,
      "trend": "UPTREND"
    }
  ],
  "metadata": {
    "expenseRatio": 0.0945,
    "aum": 500000000000,
    "inceptionDate": "1993-01-22",
    "issuer": "State Street Global Advisors",
    "exchange": "NYSE",
    "currency": "USD",
    "type": "Index",
    "benchmark": "S&P 500 Index",
    "lastRebalanceDate": "2024-11-15"
  }
}
```

### Indexes

```javascript
// Primary key
db.etfs.createIndex({ "_id": 1 })

// Lookup by symbol (unique)
db.etfs.createIndex({ "symbol": 1 }, { unique: true })

// Query quotes by date
db.etfs.createIndex({ "quotes.date": 1 })

// Query holdings by stock
db.etfs.createIndex({ "holdings.stockSymbol": 1 })
```

---

## API Endpoints

### New Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/etf/{symbol}` | Get ETF entity with quotes and holdings |
| GET | `/api/etf` | List all ETFs |
| GET | `/api/etf/{symbol}/holdings` | Get current holdings with weights |
| GET | `/api/etf/{symbol}/quotes` | Get historical quotes |
| POST | `/api/etf/{symbol}/refresh` | Refresh ETF data from source |
| POST | `/api/etf/refresh-all` | Refresh all ETFs |
| POST | `/api/etf/{symbol}/holdings` | Update holdings (admin) |

### Updated Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/etf/{symbol}/stats` | Now uses EtfEntity internally (backward compatible) |

---

## Testing Strategy

### Unit Tests

**Model Tests**:
- `EtfEntityTest` - Test ETF entity methods
- `EtfQuoteTest` - Test quote calculations
- `EtfHoldingTest` - Test holding weightings

**Service Tests**:
- `EtfServiceTest` - Test CRUD operations
- `EtfStatsServiceTest` - Test stats calculations with new model

**Repository Tests**:
- `EtfRepositoryTest` - Test database queries

### Integration Tests

- Test ETF data fetch from external API
- Test holdings update workflow
- Test trend calculation accuracy
- Test backward compatibility of stats endpoint

---

## Benefits

1. **Clear Separation**: ETFs are first-class entities, not computed from stocks
2. **Performance**: Pre-calculated trend metrics, no real-time computation
3. **Richer Data**: Holdings with weights, metadata, ETF-specific quotes
4. **Scalability**: Can add leveraged ETFs (TQQQ), inverse ETFs (SQQQ), sector ETFs
5. **Flexibility**: Can track holdings changes over time
6. **Better UX**: Faster API responses, more detailed information
7. **Future-Proof**: Foundation for portfolio tracking, ETF backtesting

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Holdings data availability | High | Start with manual CSV import, add API later |
| Historical data backfill | Medium | Fetch incrementally, prioritize recent data |
| Breaking existing features | High | Phased migration with backward compatibility |
| Increased storage | Low | MongoDB handles nested documents efficiently |
| Data staleness | Medium | Automated nightly refresh job |

---

## Timeline Summary

| Phase | Duration | Deliverables |
|-------|----------|-------------|
| Phase 1: Schema | 1 week | New models, repository, service |
| Phase 2: Data | 1 week | Populated ETF entities with quotes & holdings |
| Phase 3: Backend | 1 week | New endpoints, updated stats service |
| Phase 4: Frontend | 1 week | New components, updated pages |
| Phase 5: Cleanup | 1 week | Remove deprecated code |
| **Total** | **5 weeks** | Fully migrated ETF entity system |

---

## Open Questions

1. **Holdings Data Source**: Which API/service provides reliable ETF holdings with weights?
   - Options: ETF.com API, Quandl, manual CSV import, Yahoo Finance

2. **Refresh Frequency**: How often should ETF quotes and holdings be refreshed?
   - Quotes: Daily (after market close)
   - Holdings: Monthly (after rebalancing)

3. **Historical Holdings**: Do we need historical holding snapshots?
   - Use case: Track portfolio composition changes over time
   - Storage: Potentially large, may need separate collection

4. **Leveraged/Inverse ETFs**: Should we support TQQQ (3x), SQQQ (-3x)?
   - If yes, need multiplier field and special handling

5. **International ETFs**: Support non-US ETFs (UCITS, etc.)?
   - Currency handling, different exchanges

6. **ETF Backtesting**: Should ETFs be tradeable in backtesting system?
   - Requires extending backtesting logic to handle ETFs

7. **Real-time Data**: Do we need intraday ETF prices?
   - Current system is EOD (end-of-day), real-time requires different data source

---

## Next Steps

1. **Decide on holdings data source** (API vs manual import)
2. **Review and approve this plan**
3. **Create Phase 1 implementation ticket**
4. **Set up development branch**: `feature/etf-entity-refactoring`
5. **Begin implementation**

---

**Status**: 📋 Plan Ready for Review
**Created**: 2025-11-23
**Architecture**: Separate ETF Entity with Holdings & Trend Data
