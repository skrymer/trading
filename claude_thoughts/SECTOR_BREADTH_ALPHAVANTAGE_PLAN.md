# Sector Breadth Calculation Using AlphaVantage APIs

**Date**: 2025-12-10
**Status**: Planning Phase
**Goal**: Calculate sector breadth based on percentage of stocks in each S&P sector that meet specific technical criteria

---

## Executive Summary

This document outlines a plan to calculate sector breadth indicators using AlphaVantage APIs. Sector breadth measures the percentage of stocks within each of the 11 S&P sectors that are in technically strong positions, helping identify sector rotation and relative strength.

**Technical Criteria for "In Uptrend":**
1. Stock is in uptrend (existing logic: EMA5 > EMA10 > EMA20 AND Price > EMA50)
2. 10 EMA > 20 EMA (short-term momentum alignment)
3. Close price > 50 EMA (above medium-term support)

---

## Research Findings: AlphaVantage API Limitations

### What AlphaVantage Provides

✅ **Available:**
- `COMPANY_OVERVIEW` - Returns sector classification for individual stocks
- `LISTING_STATUS` - Returns all available stock symbols (no sector info)
- `TIME_SERIES_DAILY_ADJUSTED` - OHLCV data for individual stocks
- Technical indicators (EMA, ATR, ADX, etc.) via API

❌ **Not Available:**
- Direct endpoint for "list all stocks in a sector"
- Sector constituent lists
- Bulk sector classification data
- Pre-calculated sector breadth metrics

### Key Limitation

AlphaVantage does **NOT** provide an API endpoint that returns a list of stocks grouped by sector. We must build and maintain our own sector constituent lists.

---

## Proposed Solution: Two-Phase Approach

### Phase 1: Build Sector Constituent Database (One-Time Setup)

Use AlphaVantage's existing integrations to build a local database of sector classifications:

1. **Get all available stocks** via `LISTING_STATUS` endpoint
2. **Fetch sector for each stock** using existing `getSectorSymbol()` (calls `COMPANY_OVERVIEW`)
3. **Store sector mappings** in database for future use
4. **Update periodically** (quarterly or on-demand)

### Phase 2: Calculate Sector Breadth (Regular Updates)

Once we have sector constituent lists:

1. **For each sector** (XLK, XLF, XLV, etc.)
2. **For each stock in that sector**:
   - Check if already in database with recent data
   - If not, fetch via `TIME_SERIES_DAILY_ADJUSTED`
   - Calculate EMAs (we already do this via `TechnicalIndicatorService`)
   - Check if meets uptrend criteria
3. **Calculate breadth**: `(stocks meeting criteria / total stocks in sector) * 100`
4. **Store in Breadth entity** (similar to market breadth)

---

## Detailed Implementation Plan

### 1. Data Model Updates

#### 1.1 Create SectorConstituent Entity

```kotlin
@Entity
@Table(name = "sector_constituents")
data class SectorConstituent(
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  val id: Long? = null,

  @Column(name = "symbol", length = 20, nullable = false)
  val symbol: String,

  @Enumerated(EnumType.STRING)
  @Column(name = "sector_symbol", nullable = false)
  val sectorSymbol: SectorSymbol,

  @Column(name = "company_name", length = 255)
  val companyName: String? = null,

  @Column(name = "is_active", nullable = false)
  val isActive: Boolean = true,

  @Column(name = "last_updated")
  val lastUpdated: LocalDateTime = LocalDateTime.now(),

  @Column(name = "exchange", length = 50)
  val exchange: String? = null
)
```

#### 1.2 Extend Breadth Model

Already exists - just need to use it for sector breadth:
- `Breadth` entity with `BreadthQuote` for time-series data
- Use `BreadthSymbol.Sector(sectorSymbol)` for identification

---

### 2. New Services and Components

#### 2.1 SectorConstituentService

```kotlin
@Service
class SectorConstituentService(
  private val sectorConstituentRepository: SectorConstituentRepository,
  private val fundamentalDataProvider: FundamentalDataProvider,
  private val restClient: RestClient
) {
  /**
   * Build sector constituent database from AlphaVantage LISTING_STATUS
   *
   * Process:
   * 1. Fetch all stocks from LISTING_STATUS (CSV response)
   * 2. Filter for active stocks on major US exchanges
   * 3. For each stock, call getSectorSymbol() to get sector
   * 4. Store in database with sector mapping
   *
   * Note: This is RATE LIMITED - will take time with free tier!
   * Free tier: 5 API calls/minute, 500/day
   * Premium: 75 API calls/minute, 1200/day
   */
  fun buildSectorConstituents(): Map<String, Any>

  /**
   * Update sector classification for a specific stock
   */
  fun updateConstituent(symbol: String): SectorConstituent?

  /**
   * Get all stocks in a sector
   */
  fun getConstituentsBySector(sector: SectorSymbol): List<SectorConstituent>

  /**
   * Mark a stock as inactive (delisted)
   */
  fun deactivateConstituent(symbol: String)
}
```

#### 2.2 SectorBreadthCalculator

```kotlin
@Service
class SectorBreadthCalculator(
  private val sectorConstituentService: SectorConstituentService,
  private val stockService: StockService,
  private val breadthRepository: BreadthRepository
) {
  /**
   * Calculate breadth for a specific sector
   *
   * Criteria for "in uptrend":
   * 1. Stock uptrend: EMA5 > EMA10 > EMA20 AND Price > EMA50
   * 2. 10 EMA > 20 EMA
   * 3. Close price > 50 EMA
   *
   * @return Breadth percentage (0-100)
   */
  fun calculateSectorBreadth(sector: SectorSymbol, asOfDate: LocalDate): Double

  /**
   * Calculate breadth for all sectors
   */
  fun calculateAllSectorBreadth(asOfDate: LocalDate): Map<SectorSymbol, Double>

  /**
   * Update sector breadth in database
   */
  fun updateSectorBreadthData(sector: SectorSymbol)
}
```

#### 2.3 AlphaVantageListingClient (New)

```kotlin
@Service
class AlphaVantageListingClient(
  @Value("\${alphavantage.api.key}") private val apiKey: String,
  @Value("\${alphavantage.api.baseUrl}") private val baseUrl: String
) {
  /**
   * Get all listed stocks from LISTING_STATUS endpoint
   *
   * Returns CSV with columns:
   * - symbol
   * - name
   * - exchange
   * - assetType (Stock or ETF)
   * - ipoDate
   * - delistingDate
   * - status
   */
  fun getAllListings(): List<StockListing>

  /**
   * Filter for active US stocks only
   */
  fun getActiveUSStocks(): List<StockListing>
}

data class StockListing(
  val symbol: String,
  val name: String,
  val exchange: String,
  val assetType: String,
  val ipoDate: LocalDate?,
  val delistingDate: LocalDate?,
  val status: String
)
```

---

### 3. API Endpoints

#### 3.1 Sector Constituents Management

```
POST /api/sector-constituents/build
- Build initial sector constituent database
- Returns: { "total": 5000, "processed": 5000, "failed": 0 }

GET /api/sector-constituents/{sector}
- Get all stocks in a sector
- Returns: List<SectorConstituent>

POST /api/sector-constituents/update/{symbol}
- Update sector classification for a stock
- Returns: SectorConstituent

GET /api/sector-constituents/summary
- Get summary of constituents by sector
- Returns: Map<SectorSymbol, Int>
```

#### 3.2 Sector Breadth

```
POST /api/breadth/sector/{sector}/calculate
- Calculate and store sector breadth for specific sector
- Returns: Breadth with latest BreadthQuote

POST /api/breadth/sector/calculate-all
- Calculate breadth for all 11 sectors
- Returns: Map<SectorSymbol, Breadth>

GET /api/breadth/sector/{sector}
- Get sector breadth time-series data
- Returns: Breadth with historical BreadthQuotes
```

---

### 4. Implementation Steps

#### Step 1: Data Model (1-2 hours)
- [ ] Create `SectorConstituent` entity
- [ ] Create `SectorConstituentRepository`
- [ ] Add database migration for new table

#### Step 2: AlphaVantage Listing Integration (2-3 hours)
- [ ] Create `AlphaVantageListingClient`
- [ ] Add CSV parsing for `LISTING_STATUS` response
- [ ] Create `StockListing` DTO
- [ ] Test fetching all listings

#### Step 3: Sector Constituent Service (3-4 hours)
- [ ] Create `SectorConstituentService`
- [ ] Implement `buildSectorConstituents()` with rate limiting
- [ ] Add progress tracking for long-running builds
- [ ] Implement constituent management methods
- [ ] Add caching

#### Step 4: Sector Breadth Calculator (4-5 hours)
- [ ] Create `SectorBreadthCalculator`
- [ ] Implement uptrend criteria checking
- [ ] Calculate breadth percentage
- [ ] Store results in `Breadth` entity
- [ ] Add time-series tracking

#### Step 5: Controllers and APIs (2-3 hours)
- [ ] Create `SectorConstituentController`
- [ ] Update `BreadthController` with sector endpoints
- [ ] Add DTOs for requests/responses
- [ ] Document APIs

#### Step 6: Testing (3-4 hours)
- [ ] Unit tests for services
- [ ] Integration tests for API endpoints
- [ ] Test with sample sectors
- [ ] Verify breadth calculations

#### Step 7: Rate Limiting & Optimization (2-3 hours)
- [ ] Implement proper rate limiting for API calls
- [ ] Add batch processing with delays
- [ ] Add resume capability for interrupted builds
- [ ] Optimize database queries

**Total Estimated Time**: 17-24 hours

---

### 5. Rate Limiting Considerations

#### AlphaVantage Rate Limits

**Free Tier:**
- 5 API calls per minute
- 500 API calls per day
- **Implication**: Building full constituent database with 5000 stocks would take ~17 hours

**Premium Tier:**
- 75 API calls per minute
- 1200+ API calls per day
- **Implication**: Building full constituent database would take ~67 minutes

#### Optimization Strategies

1. **Focus on S&P 500 initially** (~500 stocks)
   - Free tier: ~100 minutes
   - Premium: ~7 minutes

2. **Batch processing with delays**
   ```kotlin
   val delayMs = if (isPremium) 800L else 12000L // 75/min vs 5/min
   symbols.chunked(batchSize).forEach { batch ->
     batch.forEach { symbol ->
       getSectorSymbol(symbol)
       delay(delayMs)
     }
   }
   ```

3. **Incremental updates**
   - Build initial database once
   - Update only changed stocks (IPOs, sector changes)
   - Quarterly refresh cycle

4. **Cache sector mappings**
   - Store in database with last_updated timestamp
   - Only re-fetch if > 90 days old

---

### 6. Alternative Data Sources (Future)

If AlphaVantage rate limits are too restrictive:

1. **SPDR Sector ETF Holdings**
   - Each sector ETF (XLK, XLF, etc.) publishes holdings daily
   - Free, updated daily, no API required
   - Source: SPDR website or SEC filings

2. **Yahoo Finance Sector Data**
   - Via yfinance Python library
   - Free, comprehensive sector classifications
   - Could integrate via REST bridge

3. **Manual Curated Lists**
   - Start with S&P 500 sector classifications
   - Publicly available on Wikipedia or S&P website
   - Import as CSV, maintain manually

4. **Polygon.io**
   - Has sector/industry classification
   - Good API with better rate limits
   - Free tier: 5 requests/minute

---

### 7. Breadth Calculation Details

#### Uptrend Criteria (all must be true)

```kotlin
fun isInUptrend(quote: StockQuote): Boolean {
  // Criterion 1: General uptrend (existing logic)
  val generalUptrend =
    quote.closePriceEMA5 > quote.closePriceEMA10 &&
    quote.closePriceEMA10 > quote.closePriceEMA20 &&
    quote.closePrice > quote.closePriceEMA50

  // Criterion 2: 10 EMA > 20 EMA
  val shortTermAlignment = quote.closePriceEMA10 > quote.closePriceEMA20

  // Criterion 3: Close > 50 EMA
  val aboveMediumTermEMA = quote.closePrice > quote.closePriceEMA50

  return generalUptrend && shortTermAlignment && aboveMediumTermEMA
}
```

#### Breadth Calculation

```kotlin
fun calculateBreadth(constituents: List<SectorConstituent>): Double {
  val activeStocks = constituents.filter { it.isActive }
  if (activeStocks.isEmpty()) return 0.0

  val stocksInUptrend = activeStocks.count { constituent ->
    val stock = stockService.getStock(constituent.symbol)
    val latestQuote = stock?.quotes?.maxByOrNull { it.date }
    latestQuote?.let { isInUptrend(it) } ?: false
  }

  return (stocksInUptrend.toDouble() / activeStocks.size) * 100.0
}
```

#### Example Output

```
Sector Breadth (as of 2025-12-10):
- XLK (Technology):    72.5% (145/200 stocks in uptrend)
- XLF (Financials):    65.3% (95/145 stocks in uptrend)
- XLV (Health):        58.2% (88/151 stocks in uptrend)
- XLE (Energy):        45.1% (38/84 stocks in uptrend)
- XLI (Industrials):   68.7% (112/163 stocks in uptrend)
- XLY (Discretionary): 71.2% (98/138 stocks in uptrend)
- XLP (Staples):       52.4% (44/84 stocks in uptrend)
- XLU (Utilities):     38.9% (21/54 stocks in uptrend)
- XLB (Materials):     55.6% (45/81 stocks in uptrend)
- XLRE (Real Estate):  41.7% (25/60 stocks in uptrend)
- XLC (Communications): 63.8% (33/52 stocks in uptrend)
```

---

### 8. Database Schema

#### sector_constituents Table

```sql
CREATE TABLE sector_constituents (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  symbol VARCHAR(20) NOT NULL,
  sector_symbol VARCHAR(10) NOT NULL,
  company_name VARCHAR(255),
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  last_updated TIMESTAMP NOT NULL,
  exchange VARCHAR(50),
  UNIQUE KEY unique_symbol (symbol),
  INDEX idx_sector (sector_symbol),
  INDEX idx_active (is_active)
);
```

#### Usage with Existing Breadth Table

The `breadth` and `breadth_quotes` tables already exist and can be used:

```kotlin
// Create sector breadth entry
val breadth = Breadth(
  symbol = BreadthSymbol.Sector(SectorSymbol.XLK).toIdentifier(), // "SECTOR_XLK"
  quotes = mutableListOf()
)

// Add breadth quote
val quote = BreadthQuote(
  date = LocalDate.now(),
  advancingPercent = 72.5, // % of stocks in uptrend
  advancingCount = 145,    // stocks meeting criteria
  decliningCount = 55,     // stocks not meeting criteria
  unchangedCount = 0
)
```

---

### 9. Frontend Integration

#### Display Sector Breadth

Similar to existing market breadth page (`market-breadth.vue`):

```vue
<template>
  <div>
    <h2>Sector Breadth Analysis</h2>

    <!-- Sector Breadth Summary Cards -->
    <div class="grid grid-cols-3 gap-4">
      <div v-for="sector in sectors" :key="sector.symbol">
        <Card>
          <h3>{{ sector.name }}</h3>
          <div class="breadth-value">{{ sector.breadth }}%</div>
          <div class="breadth-counts">
            {{ sector.uptrendCount }} / {{ sector.totalCount }} stocks
          </div>
          <BreadthIndicator :value="sector.breadth" />
        </Card>
      </div>
    </div>

    <!-- Sector Breadth Chart -->
    <SectorBreadthChart :data="breadthHistory" />

    <!-- Top/Bottom Sectors -->
    <TopSectorsTable :sectors="sectors" />
  </div>
</template>
```

---

### 10. Considerations and Challenges

#### Challenges

1. **API Rate Limits**
   - Building initial database is slow with free tier
   - Solution: Start with S&P 500, upgrade to premium, or use alternative sources

2. **Stale Data**
   - Sector classifications can change (rare but happens)
   - Solution: Periodic refresh (quarterly)

3. **IPOs and Delistings**
   - New stocks added, old stocks removed
   - Solution: Regular syncs with LISTING_STATUS

4. **Data Freshness**
   - Stock data must be recent for accurate breadth
   - Solution: Refresh stock data before calculating breadth

5. **Computation Time**
   - Calculating breadth for all sectors = fetching data for 1000s of stocks
   - Solution: Background jobs, caching, incremental updates

#### Best Practices

1. **Start Small**: S&P 500 only (500 stocks across 11 sectors)
2. **Cache Aggressively**: Store breadth calculations, refresh daily/weekly
3. **Background Jobs**: Calculate breadth overnight, not on-demand
4. **Monitor Usage**: Track API call usage to avoid hitting limits
5. **Graceful Degradation**: Show cached data if calculation fails

---

### 11. Success Metrics

- [ ] Successfully map all S&P 500 stocks to sectors
- [ ] Calculate sector breadth with <1% error rate
- [ ] Breadth updates complete within 1 hour
- [ ] API responds to breadth queries in <500ms
- [ ] Historical breadth data available for charting
- [ ] Integration with existing strategies (conditions can use sector breadth)

---

### 12. Future Enhancements

1. **Industry-Level Breadth**
   - Drill down further than 11 sectors
   - Use AlphaVantage's industry field from COMPANY_OVERVIEW

2. **Market Cap Weighted Breadth**
   - Weight by market cap instead of equal weight
   - Fetch market cap from COMPANY_OVERVIEW

3. **Relative Strength Ranking**
   - Rank sectors by breadth strength
   - Identify sector rotation patterns

4. **Breadth Divergence Alerts**
   - Alert when sector breadth diverges from price
   - Early warning system for trend changes

5. **Historical Breadth Backtesting**
   - Test strategies using historical sector breadth
   - Validate breadth as a timing indicator

---

## Summary

### What We Need to Build

1. **SectorConstituent** entity and repository
2. **AlphaVantageListingClient** for fetching stock listings
3. **SectorConstituentService** for building/managing sector mappings
4. **SectorBreadthCalculator** for computing breadth percentages
5. **Controllers** for sector constituent management and breadth APIs
6. **Frontend components** for displaying sector breadth

### Key Dependencies

- ✅ AlphaVantage `COMPANY_OVERVIEW` API (already integrated)
- ✅ AlphaVantage `LISTING_STATUS` API (need to add)
- ✅ Existing `Breadth` and `BreadthQuote` entities (already exist)
- ✅ Existing `TechnicalIndicatorService` (EMA calculations)
- ✅ Existing `StockService` (stock data management)

### Recommended Approach

**Phase 1 (MVP)**: S&P 500 Sector Breadth
- Use manual S&P 500 sector list (free, instant)
- Calculate breadth for 11 sectors
- Store in existing Breadth tables
- Simple frontend display

**Phase 2**: AlphaVantage Integration
- Build full constituent database
- Automate sector classification updates
- Add more stocks beyond S&P 500

**Phase 3**: Advanced Features
- Industry-level breadth
- Market cap weighting
- Breadth divergence alerts
- Historical backtesting

---

**Next Steps**: Review plan, prioritize phases, begin implementation with Step 1 (Data Model).
