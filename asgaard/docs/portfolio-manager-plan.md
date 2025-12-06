# Portfolio Manager Implementation Plan

## Overview
A comprehensive portfolio management system allowing users to track their trades, monitor performance, and compare against benchmarks like SPY.

## Features

### Portfolio Management
- Set initial balance and currency
- Track current balance
- Multi-currency support (with future conversion capability)

### Trade Management
**Open Trade:**
- Price of instrument at time of purchase
- Entry and exit strategy used
- Date of trade
- Currency
- Quantity

**Close Trade:**
- Close date
- Close price
- Automatic profit/loss calculation

### Statistics Dashboard
1. Total trades
2. YTD return
3. Annualized returns
4. Average win
5. Average loss
6. Win rate
7. Proven edge

### Visualizations
- Equity curve chart showing total return over time
- SPY comparison chart
- Active trade monitoring with exit condition indicators

---

## Phase 1: Backend - Domain Models & Database

### 1. MongoDB Domain Models

#### Portfolio Model (`Portfolio.kt`)
```kotlin
- id: String (MongoDB ID)
- userId: String (for future multi-user support)
- name: String
- initialBalance: Double
- currentBalance: Double
- currency: String (e.g., "USD", "EUR")
- createdDate: LocalDateTime
- lastUpdated: LocalDateTime
```

#### Trade Model (`Trade.kt`)
```kotlin
- id: String (MongoDB ID)
- portfolioId: String (reference to Portfolio)
- symbol: String (stock symbol)
- entryPrice: Double
- entryDate: LocalDate
- exitPrice: Double? (nullable - null for active trades)
- exitDate: LocalDate? (nullable)
- quantity: Int
- entryStrategy: String (name of strategy used)
- exitStrategy: String (name of strategy used)
- currency: String
- status: TradeStatus (OPEN, CLOSED)
- profit: Double? (calculated on close)
- profitPercentage: Double? (calculated on close)
```

#### PortfolioStats Model (`PortfolioStats.kt`) - Computed DTO
```kotlin
- totalTrades: Int
- openTrades: Int
- closedTrades: Int
- ytdReturn: Double
- annualizedReturn: Double
- avgWin: Double
- avgLoss: Double
- winRate: Double
- provenEdge: Double
- totalProfit: Double
- totalProfitPercentage: Double
```

### 2. Portfolio Controller (`PortfolioController.kt`)

**Endpoints:**
- `POST /api/portfolio` - Create new portfolio
- `GET /api/portfolio/{id}` - Get portfolio details
- `PUT /api/portfolio/{id}` - Update portfolio (e.g., adjust balance)
- `GET /api/portfolio/{id}/stats` - Get portfolio statistics
- `POST /api/portfolio/{id}/trades` - Open new trade
- `PUT /api/portfolio/{id}/trades/{tradeId}/close` - Close trade
- `GET /api/portfolio/{id}/trades` - Get all trades (with filters: open/closed)
- `GET /api/portfolio/{id}/trades/{tradeId}` - Get specific trade details
- `GET /api/portfolio/{id}/equity-curve` - Get equity curve data
- `GET /api/portfolio/{id}/comparison/spy` - Get SPY comparison data

### 3. Service Layer (`PortfolioService.kt`)

**Key Methods:**
- `createPortfolio(initialBalance, currency): Portfolio`
- `openTrade(portfolioId, tradeRequest): Trade`
- `closeTrade(portfolioId, tradeId, closePrice, closeDate): Trade`
- `calculateStats(portfolioId): PortfolioStats`
- `getEquityCurve(portfolioId): EquityCurveData`
- `getSpyComparison(portfolioId, startDate): ComparisonData`
- `checkActiveTradeExits(portfolioId): List<TradeExitAlert>`

**Business Logic:**
- Calculate profit/loss on trade close
- Update portfolio balance on trade close
- Calculate YTD returns based on calendar year
- Calculate annualized returns using CAGR formula
- Compute proven edge: (Win Rate × Avg Win) - (Loss Rate × Avg Loss)
- Generate equity curve from historical trades

### 4. Repository Layer
- `PortfolioRepository` - extends MongoRepository
- `TradeRepository` - extends MongoRepository with custom queries

---

## Phase 2: Frontend - Nuxt UI Components

### 5. Portfolio Manager Page (`/app/pages/portfolio.vue`)

**Layout Structure:**
```
┌─────────────────────────────────────────────────┐
│ Portfolio Header (Balance, Currency Selector)   │
├─────────────────────────────────────────────────┤
│ Stats Cards (6 cards in grid):                  │
│ - Total Trades  - YTD Return  - Annual Return   │
│ - Avg Win       - Avg Loss    - Win Rate/Edge   │
├─────────────────────────────────────────────────┤
│ Equity Curve Chart (70%) │ SPY Comparison (30%) │
├─────────────────────────────────────────────────┤
│ Active Trades Section (if any)                  │
│ - Trade cards with live chart & exit indicators │
├─────────────────────────────────────────────────┤
│ Trade History Table                             │
│ - Sortable, filterable list of all trades       │
│ - Actions: View Details, Close Trade            │
└─────────────────────────────────────────────────┘
```

**Components to Create:**
- `PortfolioHeader.vue` - Balance display and controls
- `PortfolioStats.vue` - Statistics cards
- `OpenTradeModal.vue` - Form to open new trade
- `CloseTradeModal.vue` - Form to close existing trade
- `ActiveTradeCard.vue` - Live trade monitoring
- `TradeHistoryTable.vue` - Historical trades table
- `EquityCurveChart.vue` - Portfolio value over time
- `SpyComparisonChart.vue` - Strategy vs SPY performance

### 6. TypeScript Types (`/app/types/index.d.ts`)

```typescript
export interface Portfolio {
  id: string
  userId?: string
  name: string
  initialBalance: number
  currentBalance: number
  currency: string
  createdDate: string
  lastUpdated: string
}

export interface Trade {
  id: string
  portfolioId: string
  symbol: string
  entryPrice: number
  entryDate: string
  exitPrice?: number
  exitDate?: string
  quantity: number
  entryStrategy: string
  exitStrategy: string
  currency: string
  status: 'OPEN' | 'CLOSED'
  profit?: number
  profitPercentage?: number
}

export interface PortfolioStats {
  totalTrades: number
  openTrades: number
  closedTrades: number
  ytdReturn: number
  annualizedReturn: number
  avgWin: number
  avgLoss: number
  winRate: number
  provenEdge: number
  totalProfit: number
  totalProfitPercentage: number
}
```

### 7. Forms & Validation

**Open Trade Form Fields:**
- Stock Symbol (autocomplete from available stocks)
- Entry Price
- Entry Date (date picker, default: today)
- Quantity
- Entry Strategy (dropdown from available strategies)
- Exit Strategy (dropdown from available strategies)
- Currency (default from portfolio)

**Close Trade Form Fields:**
- Close Price
- Close Date (date picker, default: today)

### 8. Charts & Visualization

**Equity Curve Chart:**
- X-axis: Date
- Y-axis: Portfolio value
- Line showing cumulative returns over time
- Markers for each trade close

**SPY Comparison Chart:**
- Dual Y-axis line chart
- Portfolio return % vs SPY return %
- Same time period
- Different colored lines

**Active Trade Chart:**
- Stock price chart for the active position
- Entry price line (horizontal)
- Current price indicator
- Exit condition indicators (based on strategy)
- P/L indicator

---

## Phase 3: Statistics & Calculations

### Formulas

**YTD Return:**
```
YTD Return = ((Current Balance - Balance at Jan 1) / Balance at Jan 1) × 100
```

**Annualized Return (CAGR):**
```
CAGR = ((Current Balance / Initial Balance)^(365/Days)) - 1) × 100
```

**Win Rate:**
```
Win Rate = (Number of Winning Trades / Total Closed Trades) × 100
```

**Proven Edge:**
```
Edge = (Win Rate × Avg Win %) - ((100 - Win Rate) × Avg Loss %)
```

**Average Win/Loss:**
```
Avg Win = Sum of Winning Trade % / Number of Wins
Avg Loss = Sum of Losing Trade % / Number of Losses
```

---

## Implementation Order

### 1. Backend First
- Create domain models (Portfolio, Trade, PortfolioStats)
- Set up MongoDB repositories
- Implement PortfolioService with business logic
- Create PortfolioController with all endpoints
- Test APIs with curl/Postman

### 2. Frontend Foundation
- Create portfolio.vue page structure
- Add TypeScript types
- Build basic layout with placeholder cards

### 3. Core Features
- Implement Open Trade functionality
- Implement Close Trade functionality
- Add statistics calculation and display

### 4. Visualizations
- Build equity curve chart
- Add SPY comparison
- Create active trade monitoring

### 5. Polish
- Add navigation link
- Improve UX with loading states, error handling
- Add filtering and sorting to trade history

---

## Technical Notes

- **Database**: MongoDB (matching existing architecture)
- **Backend**: Kotlin + Spring Boot
- **Frontend**: Nuxt 4 + TypeScript + NuxtUI 4
- **Charts**: ApexCharts (already in use)
- **Currency**: Store as string, implement conversion logic later
- **Multi-user**: userId field prepared for future multi-tenant support
