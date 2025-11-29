# Alpha Vantage Quick Start Guide

## 1. Get Your API Key (2 minutes)

1. Go to: https://www.alphavantage.co/support/#api-key
2. Enter your email
3. Copy your API key

## 2. Configure Application (30 seconds)

Edit `src/main/resources/application.properties`:

```properties
alphavantage.api.key=YOUR_ACTUAL_API_KEY_HERE
```

## 3. Use the Client (Copy & Paste)

```kotlin
@Autowired
private lateinit var alphaVantageClient: AlphaVantageClient

// Get recent data (cached for 1 hour)
val quotes = alphaVantageClient.getDailyTimeSeriesCompact("AAPL")

// Get full historical data (cached for 24 hours)
val allQuotes = alphaVantageClient.getDailyTimeSeriesFull("AAPL")
```

## 4. That's It! 🎉

**Volume data is now automatically added to all stock fetches!**

When you fetch a stock via `StockService.getStock()`, the system:
- ✅ Fetches price data from Ovtlyr
- ✅ Automatically enriches with volume from Alpha Vantage
- ✅ Calculates order blocks with real volume strength
- ✅ Caches responses to save API calls
- ✅ Handles errors gracefully
- ✅ Logs all operations
- ✅ Stays within rate limits

**Just fetch stocks normally - volume is automatic:**
```kotlin
val stock = stockService.getStock("AAPL")
// Volume already populated in stock.quotes!
```

## Common Tasks

### Force Refresh Data

**Option 1: REST API**
```bash
curl -X DELETE http://localhost:8080/api/cache/alphavantage/AAPL
```

**Option 2: Programmatically**
```kotlin
alphaVantageClient.evictCache("AAPL")
```

### Check Cache Status

```bash
curl http://localhost:8080/api/cache/status
```

### Enrich Ovtlyr Data with Volume

```kotlin
// Get Ovtlyr stock data (has prices but no volume)
val stock = ovtlyrClient.getStockInformation("AAPL")

// Get volume from Alpha Vantage
val alphaQuotes = alphaVantageClient.getDailyTimeSeriesFull("AAPL")

// Merge: Add volume to stock quotes
stock?.quotes?.forEach { quote ->
    val match = alphaQuotes?.find { it.date == quote.date }
    quote.volume = match?.volume ?: 0L
}

// Calculate order blocks with real volume
val blocks = orderBlockCalculator.calculateOrderBlocks(stock.quotes)
```

## Rate Limits (Free Tier)

- **25 requests per day**
- **5 requests per minute**

**With caching:**
- Each stock = 1 API call per 24 hours (full data)
- Each stock = 1 API call per hour (compact data)
- Can monitor 25 stocks easily!

## Troubleshooting

| Issue | Solution |
|-------|----------|
| "API key invalid" | Check `application.properties` for typos |
| "Rate limit exceeded" | Wait until tomorrow or clear cache less often |
| Empty response | Verify stock symbol is valid (US exchanges) |
| Cached old data | Use `DELETE /api/cache/alphavantage/{symbol}` |

## Monitoring

**Check API calls in logs:**
```
INFO  AlphaVantageClient - Fetching daily time series for AAPL...
```

If you see this message, an API call was made (counts towards limit).
If you don't see it, data came from cache (free!).

## Need More?

- Full docs: [ALPHA_VANTAGE_INTEGRATION.md](ALPHA_VANTAGE_INTEGRATION.md)
- Cache details: [CACHING_SUMMARY.md](CACHING_SUMMARY.md)
- API docs: https://www.alphavantage.co/documentation/
