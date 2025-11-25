# Market/Sector Breadth Refactoring Summary

## Overview

This document summarizes the comprehensive refactoring that separated Market breadth (FULLSTOCK) from Sector breadth for better type safety and clarity.

## Architecture Changes

### Before
- Single `MarketSymbol` enum contained both FULLSTOCK and all 11 sectors
- `MarketBreadth` class used for both market and sector data
- Confusing naming - "market" was used for both concepts

### After (Option 1 Implementation)
- **Clear separation**:
  - `MarketSymbol` enum: FULLSTOCK only (represents entire market)
  - `SectorSymbol` enum: 11 individual sectors (XLE, XLV, XLB, XLC, XLK, XLRE, XLI, XLF, XLY, XLP, XLU)
  - `BreadthSymbol` sealed class: Type-safe union of Market or Sector

- **Unified breadth data**:
  - `Breadth` class works for both market and sectors
  - `BreadthQuote` class for breadth quote data
  - `BreadthRepository` for database access

## Backend Changes

### New Model Classes
- `SectorSymbol.kt` - 11 sector enum
- `MarketSymbol.kt` - FULLSTOCK only
- `BreadthSymbol.kt` - Sealed class with Market/Sector variants
- `Breadth.kt` - Replaces MarketBreadth
- `BreadthQuote.kt` - Replaces MarketBreadthQuote

### Service Layer
- `BreadthService.kt` - Replaces MarketBreadthService with clear methods:
  - `getMarketBreadth()` - For FULLSTOCK
  - `getSectorBreadth(sector: SectorSymbol)` - For individual sectors
  - `refreshAll()` - Refresh all breadth data

### Controller & API
- `BreadthController.kt` - Replaces MarketBreadthController
- **New API Endpoints**:
  - `GET /api/breadth/market` - Get market breadth (FULLSTOCK)
  - `GET /api/breadth/sector/{symbol}` - Get specific sector breadth
  - `POST /api/breadth/refresh-all` - Refresh all breadth data

### Repository
- `BreadthRepository.kt` - Uses String IDs from BreadthSymbol.toIdentifier()

### Data Model Updates
- `StockSymbol.kt` - Changed from `market: MarketSymbol` to `sector: SectorSymbol`
- `OvtlyrBreadth.kt` - Replaces OvtlyrMarketBreadth
- `OvtlyrBreadthQuote.kt` - Replaces OvtlyrMarketBreadthQuote
- All DTOs updated to use new breadth types

### Database Changes
- **New MongoDB collection**: `@Document("breadth")` (was `"marketBreadth"`)
- **ID structure**: Uses `BreadthSymbol.toIdentifier()` (e.g., "FULLSTOCK", "XLK", "XLF")
- **Data migration**: Old data needs refreshing via `/api/breadth/refresh-all`

## Frontend Changes

### TypeScript Types
**`enums.ts`**:
```typescript
// Separate enums for clarity
export enum MarketSymbol {
  FULLSTOCK = 'FULLSTOCK'
}

export enum SectorSymbol {
  XLE = 'XLE',   // Energy
  XLV = 'XLV',   // Health
  // ... (11 total)
}

// Type-safe union
export type BreadthSymbol =
  | { type: 'market', symbol: MarketSymbol }
  | { type: 'sector', symbol: SectorSymbol }
```

**`index.d.ts`**:
```typescript
export interface Breadth {
  id: string
  symbol: BreadthSymbol
  name: string
  quotes: BreadthQuote[]
  inUptrend: boolean
  heatmap: number
  previousHeatmap: number
  donkeyChannelScore: number
}

export interface BreadthQuote {
  symbol: string
  quoteDate: string
  numberOfStocksWithABuySignal: number
  // ... (enhanced with all EMA fields)
}

// Backward compatibility
export type MarketBreadth = Breadth
export type MarketBreadthQuote = BreadthQuote
```

### Pages Updated
- `market-breadth.vue` - Updated to use new API endpoints

### API Endpoint Changes
- Market breadth: `/udgaard/api/market-breadth?marketSymbol=FULLSTOCK` → `/udgaard/api/breadth/market`
- Sector breadth: `/udgaard/api/market-breadth?marketSymbol=XLK` → `/udgaard/api/breadth/sector/XLK`
- Refresh all: `/udgaard/api/market-breadth/refresh-all` → `/udgaard/api/breadth/refresh-all`

## Files Modified

### Backend (Kotlin)
**Created**:
- `model/SectorSymbol.kt`
- `model/MarketSymbol.kt` (recreated with FULLSTOCK only)
- `model/BreadthSymbol.kt`
- `model/Breadth.kt`
- `model/BreadthQuote.kt`
- `service/BreadthService.kt`
- `controller/BreadthController.kt`
- `repository/BreadthRepository.kt`
- `integration/ovtlyr/dto/OvtlyrBreadth.kt`
- `integration/ovtlyr/dto/OvtlyrBreadthQuote.kt`

**Modified**:
- `model/StockSymbol.kt` - Changed `market` to `sector`
- `service/StockService.kt` - Updated to use BreadthRepository
- `service/BacktestService.kt` - Updated breadth references
- `integration/ovtlyr/OvtlyrClient.kt` - `getMarketBreadth()` → `getBreadth()`
- `integration/ovtlyr/DataLoader.kt` - Updated to use new symbols
- `integration/ovtlyr/dto/OvtlyrStockInformation.kt` - Updated breadth parameters
- `integration/ovtlyr/dto/OvtlyrStockQuote.kt` - Updated breadth parameters

**Deleted**:
- `model/MarketBreadth.kt` (old version)
- `model/MarketBreadthQuote.kt`
- `service/MarketBreadthService.kt`
- `controller/MarketBreadthController.kt`
- `repository/MarketBreadthRepository.kt`
- `integration/ovtlyr/dto/OvtlyrMarketBreadth.kt`
- `integration/ovtlyr/dto/OvtlyrMarketBreadthQuote.kt`

**Tests Updated**:
- `test/.../UdgaardApplicationTests.kt`
- `test/.../service/StockServiceTest.kt`
- `test/.../integration/ovtlyr/OvtlyrClientTest.kt`

### Frontend (TypeScript/Vue)
**Modified**:
- `app/types/enums.ts` - Split MarketSymbol and SectorSymbol
- `app/types/index.d.ts` - Updated Breadth interfaces
- `app/pages/market-breadth.vue` - Updated API calls

## Testing Results

### Backend
- ✅ **Compilation**: SUCCESS
- ✅ **Tests**: 214/215 passed (1 unrelated failure in AlphaVantageClient)

### Frontend
- ✅ **Type checking**: SUCCESS (1 pre-existing unrelated error in nuxt.config.ts)
- ✅ **API compatibility**: Updated to new endpoints

## Migration Steps

### For Developers
1. **Backend**: Code compiles and tests pass - no action needed
2. **Frontend**: Update imports if using `MarketSymbol` for sectors:
   - Use `SectorSymbol` for XLE, XLV, etc.
   - Use `MarketSymbol.FULLSTOCK` for market data
3. **API calls**: Update to new endpoints (see API Endpoint Changes above)

### For Deployment
1. Deploy updated backend
2. Run `POST /api/breadth/refresh-all` to populate new MongoDB collection
3. Deploy updated frontend
4. Optional: Drop old `marketBreadth` collection after verification

## Benefits

1. **Type Safety**: Can't accidentally use FULLSTOCK as a sector or vice versa
2. **Clarity**: Code clearly distinguishes between market and sector operations
3. **Better APIs**:
   - `getMarketBreadth()` - Obviously for market data
   - `getSectorBreadth(SectorSymbol.XLK)` - Obviously for sector data
4. **Self-Documenting**: Type system enforces correct usage
5. **Future-Proof**: Easy to add market-specific or sector-specific logic

## Breaking Changes

### Backend
- `MarketSymbol` enum no longer contains sectors (only FULLSTOCK)
- `MarketBreadth` class renamed to `Breadth`
- API endpoints changed paths

### Frontend
- Import changes for sector symbols
- API endpoint URLs changed
- Type definitions updated

## Notes

- MongoDB collection name changed: `marketBreadth` → `breadth`
- Old data must be refreshed (breadth data is refreshable, not user data)
- Backward compatibility aliases added in frontend types
- StockSymbol now uses `sector: SectorSymbol` instead of `market: MarketSymbol`

---

**Refactoring Date**: 2025-11-23
**Architecture**: Option 1 (Separated enums + unified Breadth class)
**Status**: ✅ Complete - Backend & Frontend
