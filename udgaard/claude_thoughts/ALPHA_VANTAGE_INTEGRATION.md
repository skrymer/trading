# Alpha Vantage Integration

## Overview

This integration adds support for fetching stock data with volume information from Alpha Vantage API. Volume data is essential for calculating accurate order block volume strength metrics.

## Setup

### 1. Get an API Key

1. Visit [Alpha Vantage Support](https://www.alphavantage.co/support/#api-key)
2. Enter your email and organization
3. Receive your free API key immediately

### 2. Configure the Application

Edit `src/main/resources/application.properties` and replace the placeholder API key:

```properties
alphavantage.api.key=YOUR_ACTUAL_API_KEY_HERE
alphavantage.api.baseUrl=https://www.alphavantage.co/query
```

## API Rate Limits

**Free Tier:**
- 25 API requests per day
- 5 API requests per minute

**Premium Tiers:**
- Available for higher volume usage
- See [Alpha Vantage Premium](https://www.alphavantage.co/premium/) for pricing

## Usage

### Basic Usage

```kotlin
@Autowired
private lateinit var alphaVantageClient: AlphaVantageClient

// Fetch full historical data (20+ years)
val quotes: List<StockQuote>? = alphaVantageClient.getDailyTimeSeriesFull("AAPL")

// Fetch compact data (last 100 data points) - recommended for API limits
val recentQuotes: List<StockQuote>? = alphaVantageClient.getDailyTimeSeriesCompact("AAPL")
```

### Automatic Integration with Stock Fetching

**Volume data is automatically enriched when fetching stocks!**

The `StockService.fetchStock()` method now automatically:
1. Fetches price data from Ovtlyr
2. Fetches volume data from Alpha Vantage (compact mode - last 100 days)
3. Merges volume into stock quotes by matching dates
4. Calculates order blocks with real volume data

**No manual intervention needed!** Just fetch a stock normally:

```kotlin
// Volume is automatically added from Alpha Vantage
val stock = stockService.getStock("AAPL")

// Stock quotes now have volume populated
stock?.quotes?.forEach { quote ->
    println("${quote.date}: Volume = ${quote.volume}")
}
```

**Log Output:**
```
INFO  StockService - Enriching AAPL with volume data from Alpha Vantage (100 quotes)
INFO  AlphaVantageClient - Fetching daily time series for AAPL from Alpha Vantage (outputSize: compact)
INFO  AlphaVantageClient - Successfully fetched 100 quotes for AAPL
```

### Integration with Order Blocks

The volume data from Alpha Vantage is automatically used in order block calculations:

1. **Volume Field**: Each `StockQuote` now includes a `volume` field (Long)
2. **Automatic Enrichment**: Volume is populated when stocks are fetched
3. **Volume Strength**: The `OrderBlockCalculator` calculates relative volume strength
   - Compares current volume to 20-day average volume
   - Formula: `volumeStrength = currentVolume / avgVolume`
   - Values > 1.0 indicate above-average volume
   - Values < 1.0 indicate below-average volume

### Manual Enrichment (Advanced)

If you need to manually enrich data or use full historical volume:

```kotlin
// Get stock data from Ovtlyr (has price but no volume)
val ovtlyrStock = ovtlyrClient.getStockInformation("AAPL")

// Get full historical volume data from Alpha Vantage
val alphaQuotes = alphaVantageClient.getDailyTimeSeriesFull("AAPL")

// Merge the data: Add volume to Ovtlyr quotes
ovtlyrStock?.quotes?.forEach { ovtlyrQuote ->
    val matchingAlphaQuote = alphaQuotes?.find { it.date == ovtlyrQuote.date }
    if (matchingAlphaQuote != null) {
        ovtlyrQuote.volume = matchingAlphaQuote.volume
    }
}

// Calculate order blocks with real volume data
val orderBlocks = orderBlockCalculator.calculateOrderBlocks(ovtlyrStock.quotes)
```

## Data Models

### AlphaVantageTimeSeriesDaily

Represents the API response from Alpha Vantage TIME_SERIES_DAILY endpoint.

```kotlin
data class AlphaVantageTimeSeriesDaily(
    val metaData: MetaData,
    val timeSeriesDaily: Map<String, DailyData>
)
```

### DailyData

Individual day's trading data:

```kotlin
data class DailyData(
    val open: String,      // Opening price
    val high: String,      // High price
    val low: String,       // Low price
    val close: String,     // Closing price
    val volume: String     // Trading volume
)
```

## Caching

### Automatic Caching

The Alpha Vantage client includes built-in caching to optimize API usage:

**Full Historical Data (`alphaVantageQuotes`):**
- Cache TTL: 24 hours
- Max size: 500 symbols
- Use for: Long-term analysis, backtesting

**Compact Data (`alphaVantageCompact`):**
- Cache TTL: 1 hour
- Max size: 500 symbols
- Use for: Recent data, live monitoring

### Cache Behavior

```kotlin
// First call - hits API
val quotes1 = alphaVantageClient.getDailyTimeSeriesFull("AAPL")

// Second call within 24 hours - uses cache
val quotes2 = alphaVantageClient.getDailyTimeSeriesFull("AAPL")

// No API call made, data served from cache
```

### Manual Cache Management

**REST API Endpoints:**

1. **Evict cache for specific symbol:**
```bash
DELETE http://localhost:8080/api/cache/alphavantage/AAPL
```

Response:
```json
{
  "status": "success",
  "message": "Cache evicted for symbol: AAPL",
  "symbol": "AAPL"
}
```

2. **Evict all Alpha Vantage caches:**
```bash
DELETE http://localhost:8080/api/cache/alphavantage
```

Response:
```json
{
  "status": "success",
  "message": "All Alpha Vantage caches evicted",
  "warning": "Next requests will hit the API and count towards rate limits"
}
```

3. **Get cache status:**
```bash
GET http://localhost:8080/api/cache/status
```

Response:
```json
{
  "alphaVantage": {
    "quotesCache": {
      "name": "alphaVantageQuotes",
      "ttl": "24 hours",
      "maxSize": 500,
      "description": "Full historical data (20+ years)"
    },
    "compactCache": {
      "name": "alphaVantageCompact",
      "ttl": "1 hour",
      "maxSize": 500,
      "description": "Recent data (last 100 data points)"
    }
  },
  "rateLimits": {
    "daily": 25,
    "perMinute": 5,
    "tier": "free"
  }
}
```

**Programmatic Cache Control:**

```kotlin
// Evict cache for a specific symbol
alphaVantageClient.evictCache("AAPL")

// Evict all caches
alphaVantageClient.evictAllCaches()
```

## Best Practices

### 1. Leverage Automatic Caching

The client automatically caches responses - no additional code needed:

```kotlin
// Just call the methods normally
val quotes = alphaVantageClient.getDailyTimeSeriesFull("AAPL")
// Subsequent calls within 24 hours use cached data
```

### 2. Use Compact Mode When Possible

For recent analysis, use compact mode to save API calls:

```kotlin
// Good: Only need recent data
val recentQuotes = alphaVantageClient.getDailyTimeSeriesCompact("AAPL")

// Avoid: Don't fetch full history if you only need recent data
val allQuotes = alphaVantageClient.getDailyTimeSeriesFull("AAPL")
```

### 3. Handle Failures Gracefully

The client returns `null` on failure:

```kotlin
val quotes = alphaVantageClient.getDailyTimeSeriesFull("AAPL")
if (quotes == null) {
    logger.warn("Failed to fetch quotes from Alpha Vantage, using cached data")
    // Fallback logic here
}
```

### 4. Monitor API Usage

**With Caching Enabled:**
- Most requests are served from cache
- Check logs for "Fetching daily time series" to see actual API calls
- Only cache misses or evictions trigger API calls

**Example Log Output:**
```
INFO  AlphaVantageClient - Fetching daily time series for AAPL from Alpha Vantage
INFO  AlphaVantageClient - Successfully fetched 5000 quotes for AAPL
```

**Monitor Cache Effectiveness:**
```bash
# Check cache statistics
GET http://localhost:8080/actuator/metrics/cache.gets?tag=name:alphaVantageQuotes
```

## Troubleshooting

### Error: "API key invalid"

- Verify your API key in `application.properties`
- Ensure no extra spaces or quotes around the key

### Error: "API rate limit exceeded"

- Free tier: Wait until next day (resets at midnight UTC)
- Consider upgrading to premium tier
- Implement caching to reduce API calls

### Empty Response

- Check if the stock symbol is valid
- Verify the stock is traded on US exchanges
- Check Alpha Vantage service status

## Frontend Integration

The frontend TypeScript types have been updated to include volume:

```typescript
export interface StockQuote {
    // ... other fields
    volume?: number  // Optional to support legacy data without volume
}
```

Display volume in the UI:

```vue
<template>
  <div>
    <div>Volume: {{ formatVolume(quote.volume) }}</div>
  </div>
</template>

<script setup lang="ts">
const formatVolume = (volume?: number): string => {
  if (!volume) return 'N/A'
  if (volume >= 1000000) {
    return `${(volume / 1000000).toFixed(2)}M`
  }
  if (volume >= 1000) {
    return `${(volume / 1000).toFixed(2)}K`
  }
  return volume.toString()
}
</script>
```

## API Documentation

Full Alpha Vantage API documentation:
- [Documentation Home](https://www.alphavantage.co/documentation/)
- [TIME_SERIES_DAILY](https://www.alphavantage.co/documentation/#daily)
- [Support & FAQ](https://www.alphavantage.co/support/)

## Files Modified

1. **Backend (Kotlin):**
   - `AlphaVantageClient.kt` - API client implementation
   - `AlphaVantageTimeSeriesDaily.kt` - DTO models
   - `StockQuote.kt` - Added `volume` field
   - `OrderBlockCalculator.kt` - Uses real volume for calculations
   - `application.properties` - API configuration

2. **Frontend (TypeScript):**
   - `app/types/index.d.ts` - Added `volume` to StockQuote interface

## Next Steps

1. **Get your API key** and update `application.properties`
2. **Test the integration** with a sample stock
3. **Implement caching** to optimize API usage
4. **Update UI** to display volume data
5. **Consider premium tier** if you need higher limits
