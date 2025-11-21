# Remove /udgaard Prefix from API URLs

## Date
2025-11-18

## Issue
All API URLs in the frontend incorrectly included `/udgaard/` prefix (e.g., `/udgaard/api/portfolio`). This prefix should be removed so URLs are simply `/api/...`.

## Root Cause
The `/udgaard/` prefix was an unnecessary routing layer that complicated the proxy configuration. The frontend should directly use `/api/*` paths which get proxied to the backend.

## Changes Made

### 1. Updated All Vue Files
Replaced all occurrences of `/udgaard/api/` with `/api/` in all Vue components and pages.

**Files Modified**:
- `app/components/backtesting/ConfigModal.vue`
- `app/components/portfolio/OpenTradeChart.client.vue`
- `app/components/portfolio/EquityCurve.client.vue`
- `app/pages/backtesting.vue`
- `app/pages/market-breadth.vue`
- `app/pages/portfolio.vue`
- `app/pages/etf-stats.vue`

**Example Changes**:

**Before**:
```typescript
const allPortfolios = await $fetch<Portfolio[]>('/udgaard/api/portfolio')
const { data: stockSymbols } = useFetch<string[]>('/udgaard/api/stocks')
const report = await $fetch('/udgaard/api/backtest', { ... })
```

**After**:
```typescript
const allPortfolios = await $fetch<Portfolio[]>('/api/portfolio')
const { data: stockSymbols } = useFetch<string[]>('/api/stocks')
const report = await $fetch('/api/backtest', { ... })
```

### 2. Updated nuxt.config.ts
Updated proxy configuration to route `/api/**` directly to backend.

**Before**:
```typescript
routeRules: {
  '/udgaard/api/**': {
    proxy: { to: 'http://localhost:8080/api/**' }
  }
},
nitro: {
  devProxy: {
    '/udgaard/api': {
      target: 'http://localhost:8080/api',
      changeOrigin: true,
      prependPath: true
    }
  }
},
vite: {
  server: {
    proxy: {
      '/udgaard/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/udgaard\/api/, '/api')
      }
    }
  }
}
```

**After**:
```typescript
routeRules: {
  '/api/**': {
    proxy: { to: 'http://localhost:8080/api/**' }
  }
},
nitro: {
  devProxy: {
    '/api': {
      target: 'http://localhost:8080/api',
      changeOrigin: true,
      prependPath: true
    }
  }
},
vite: {
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
}
```

## URL Mapping

### Before (Incorrect)
```
Frontend Request: /udgaard/api/portfolio
Proxy Rewrites:   /api/portfolio
Backend Receives: /api/portfolio
```

### After (Correct)
```
Frontend Request: /api/portfolio
Proxy Forwards:   /api/portfolio
Backend Receives: /api/portfolio
```

## Complete List of Updated Endpoints

All endpoints now use clean `/api/*` paths:

| Endpoint | Controller |
|----------|-----------|
| `/api/stocks` | StockController |
| `/api/stocks/{symbol}` | StockController |
| `/api/stocks/refresh` | StockController |
| `/api/backtest` | BacktestController |
| `/api/backtest/strategies` | BacktestController |
| `/api/backtest/rankers` | BacktestController |
| `/api/backtest/conditions` | BacktestController |
| `/api/market-breadth` | MarketBreadthController |
| `/api/market-breadth/refresh-all` | MarketBreadthController |
| `/api/etf/{symbol}/stats` | EtfController |
| `/api/portfolio` | PortfolioController |
| `/api/portfolio/{id}/stats` | PortfolioController |
| `/api/portfolio/{id}/trades` | PortfolioController |
| `/api/portfolio/{id}/equity-curve` | PortfolioController |

## Benefits

1. **Cleaner URLs**: Removed unnecessary `/udgaard/` prefix
2. **Simpler Proxy Config**: Direct `/api` to `/api` mapping
3. **Consistency**: All endpoints follow same `/api/*` pattern
4. **Easier Debugging**: URL in browser matches backend endpoint
5. **Standards Compliance**: Standard REST API path pattern

## Verification

After changes:
```bash
# Frontend uses:
/api/portfolio
/api/stocks
/api/backtest

# Backend receives:
/api/portfolio
/api/stocks
/api/backtest
```

No more `/udgaard/` prefix anywhere in the codebase.

## Testing Required

After these changes, you should:
1. Restart the Nuxt dev server
2. Test all pages:
   - Portfolio page
   - Backtesting page
   - Market Breadth page
   - ETF Stats page
3. Verify all API calls work correctly

## Related Documentation

- `UI_ENDPOINTS_AUDIT_AND_FIX.md` - Previous endpoint fixes
- `CONTROLLER_REFACTORING_SUMMARY.md` - Controller refactoring details

## Status

✅ **Complete** - All `/udgaard/` prefixes removed
✅ Proxy configuration updated
✅ All Vue files updated
✅ Simpler, cleaner URL structure

---

*Fix applied: 2025-11-18*
*Status: ✅ Complete - Restart frontend to apply changes*
