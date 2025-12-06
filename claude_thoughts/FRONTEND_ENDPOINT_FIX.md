# Frontend Endpoint Fix After Controller Refactoring

## Date
2025-11-18

## Issue
After refactoring the `UdgaardController` into separate focused controllers, the Nuxt frontend's `ConfigModal.vue` was unable to fetch strategies, rankers, and conditions because the API endpoints changed.

## Root Cause
The controller refactoring moved endpoints from:
- `/api/strategies` → `/api/backtest/strategies`
- `/api/rankers` → `/api/backtest/rankers`
- `/api/conditions` → `/api/backtest/conditions`

The frontend was still trying to fetch from the old paths.

## Fix Applied

### File Modified
**Path**: `asgaard/app/components/backtesting/ConfigModal.vue`

**Lines 54-64**: Updated API endpoint paths

### Changes

**Before**:
```typescript
// Fetch available strategies from backend
const { data: availableStrategies } = useFetch<{
  entryStrategies: string[]
  exitStrategies: string[]
}>('/udgaard/api/strategies')

// Fetch available rankers from backend
const { data: availableRankers } = useFetch<string[]>('/udgaard/api/rankers')

// Fetch available conditions for custom strategies
const { data: availableConditions } = useFetch<AvailableConditions>('/udgaard/api/conditions')
```

**After**:
```typescript
// Fetch available strategies from backend
const { data: availableStrategies } = useFetch<{
  entryStrategies: string[]
  exitStrategies: string[]
}>('/udgaard/api/backtest/strategies')

// Fetch available rankers from backend
const { data: availableRankers } = useFetch<string[]>('/udgaard/api/backtest/rankers')

// Fetch available conditions for custom strategies
const { data: availableConditions } = useFetch<AvailableConditions>('/udgaard/api/backtest/conditions')
```

## New Endpoint Structure

All backtest-related endpoints are now under the `/api/backtest` namespace:

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/backtest` | GET | Run backtest with query parameters |
| `/api/backtest` | POST | Run backtest with JSON body (recommended) |
| `/api/backtest/strategies` | GET | Get available entry and exit strategies |
| `/api/backtest/rankers` | GET | Get available stock rankers |
| `/api/backtest/conditions` | GET | Get available conditions for custom strategies |

## Verification

All endpoints verified and working:

```bash
# Strategies endpoint
curl -s http://localhost:8080/api/backtest/strategies
# Returns: { entryStrategies: [...], exitStrategies: [...] }

# Rankers endpoint
curl -s http://localhost:8080/api/backtest/rankers
# Returns: ["Heatmap", "RelativeStrength", "Volatility", ...]

# Conditions endpoint
curl -s http://localhost:8080/api/backtest/conditions
# Returns: { entryConditions: [...], exitConditions: [...] }
```

## Related Documentation

This fix is related to the controller refactoring documented in:
- `CONTROLLER_REFACTORING_SUMMARY.md`

## Status
✅ Fixed - Frontend ConfigModal now correctly fetches from new endpoints

---

*Fix applied: 2025-11-18*
*Status: ✅ Complete*
