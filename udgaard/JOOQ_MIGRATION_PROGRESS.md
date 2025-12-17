# jOOQ Migration Progress

## Summary

Migration from JPA/Hibernate to jOOQ is **in progress**. All domain models, repositories, and mappers have been created. The next phase is to update services and controllers.

---

## ✅ Completed (Phases 1-6)

### Phase 1-3: jOOQ Infrastructure (Completed Earlier)
- ✅ jOOQ dependencies added (version 3.19.23)
- ✅ Code generation configured with KotlinGenerator
- ✅ 44 Kotlin files generated successfully
- ✅ Initial StockJooqRepository created

### Phase 4: Strategy Interfaces Updated
- ✅ `EntryStrategy.kt` - Uses StockDomain/StockQuoteDomain
- ✅ `ExitStrategy.kt` - Uses StockDomain/StockQuoteDomain
- ✅ `DetailedEntryStrategy.kt` - Uses StockDomain/StockQuoteDomain

### Phase 5: Service Layer Updates
- ✅ `BacktestService.kt` - Converts JPA entities to domain models using StockMapper
- ✅ `StrategySignalService.kt` - Converts JPA entities to domain models

### Phase 6: Domain Models Created

#### Stock-Related Domain Models
- ✅ `StockDomain.kt` - Complete with strategy methods
- ✅ `StockQuoteDomain.kt` - All business logic from JPA entity
- ✅ `OrderBlockDomain.kt` - With enum types
- ✅ `EarningDomain.kt` - Business logic methods preserved

#### Breadth-Related Domain Models
- ✅ `BreadthDomain.kt` - Market/sector breadth data
  - Includes `symbolType` and `symbolValue` (database columns)
  - Computed `symbol` property reconstructs BreadthSymbol
- ✅ `BreadthQuoteDomain.kt` - Breadth quote with trend indicators

#### ETF-Related Domain Models
- ✅ `EtfDomain.kt` - ETF with quotes, holdings, and metadata
- ✅ `EtfQuoteDomain.kt` - ETF price data with technical indicators
- ✅ `EtfHoldingDomain.kt` - ETF constituent holdings
- ✅ `EtfMetadataDomain.kt` - ETF metadata (expense ratio, AUM, etc.)

#### Portfolio-Related Domain Models
- ✅ `PortfolioDomain.kt` - User portfolio
- ✅ `PortfolioTradeDomain.kt` - Individual trades with enums:
  - `InstrumentTypeDomain` (STOCK, OPTION, LEVERAGED_ETF)
  - `OptionTypeDomain` (CALL, PUT)
  - `TradeStatusDomain` (OPEN, CLOSED)

**All domain models have:**
- Default constructor values for easy instantiation
- All business logic from JPA entities
- Complete Hibernate independence

### Phase 6: jOOQ Repositories Created

#### Stock Repository
- ✅ `StockJooqRepository.kt` - Complete CRUD operations
  - findBySymbol(), findAll(), findBySymbols()
  - save() with transactional batch inserts
  - delete(), exists()

#### Breadth Repository
- ✅ `BreadthJooqRepository.kt` - Breadth CRUD operations
  - findBySymbol(), findAll(), findAllSymbols()
  - save() with transactional batch inserts
  - delete(), exists()

#### ETF Repository
- ✅ `EtfJooqRepository.kt` - ETF CRUD operations
  - findBySymbol(), findAll(), findAllSymbols()
  - save() with quotes and holdings
  - delete(), exists()

#### Portfolio Repositories
- ✅ `PortfolioJooqRepository.kt` - Portfolio CRUD operations
  - findById(), findAll(), findByUserId()
  - save() with upsert logic
  - delete(), exists()
- ✅ `PortfolioTradeJooqRepository.kt` - Trade CRUD operations
  - findById(), findByPortfolioId(), findByPortfolioIdAndStatus()
  - save() with upsert logic
  - delete(), exists()

**All repositories:**
- Use DSLContext for type-safe SQL
- Support transactional operations
- Handle batch inserts efficiently
- Set foreign key columns directly (no parent references needed)

### Phase 6: Mappers Created

- ✅ `StockMapper.kt` - Stock, StockQuote, OrderBlock, Earning
  - jOOQ POJO ↔ Domain model conversion
  - JPA entity → Domain model conversion (for migration)
- ✅ `BreadthMapper.kt` - Breadth, BreadthQuote
  - Handles symbolType/symbolValue conversion
- ✅ `EtfMapper.kt` - ETF, EtfQuote, EtfHolding, EtfMetadata
  - Handles embedded metadata
- ✅ `PortfolioMapper.kt` - Portfolio conversion
- ✅ `PortfolioTradeMapper.kt` - Trade conversion with enum mapping

**All mappers:**
- Bidirectional conversion (POJO ↔ Domain)
- Handle nullable fields with defaults
- Preserve all business logic

### Phase 6: Tests Updated
- ✅ All 302 tests passing
- ✅ Tests use domain models via StockMapper conversion

---

## ⚠️ Known Issues to Fix

### Compilation Errors

1. **BreadthMapper** - Field name mismatches:
   - Use `stocksWithBuySignal` not `numberOfStocksWithABuySignal`
   - Use `stocksWithSellSignal` not `numberOfStocksWithASellSignal`
   - Use `stocksInUptrend` not `numberOfStocksInUptrend`
   - Use `stocksInNeutral` not `numberOfStocksInNeutral`
   - Use `stocksInDowntrend` not `numberOfStocksInDowntrend`

2. **BreadthJooqRepository** - Same field name issues as mapper

3. **StockController** - Return type mismatches:
   - Returns `StockDomain` but expects `Stock` (JPA entity)
   - Need to update all controller methods

**Root Cause**: jOOQ generated field names match database columns (camelCase), not JPA entity field names.

---

## 📋 Remaining Work (Phases 7-9)

### Phase 7: Service Layer Migration

**Services to Update:**
1. ✅ `StockService.kt` - COMPLETED
   - Removed EntityManager dependency
   - Already uses StockJooqRepository and BreadthJooqRepository
   - Returns domain models
2. ✅ `BreadthService.kt` - COMPLETED (already used jOOQ)
3. ✅ `EtfService.kt` - COMPLETED
   - Updated to use EtfJooqRepository
   - Returns EtfDomain models
   - Fixed field name mismatches (closePriceEMA5 vs closePriceEma5)
4. ⚠️ `PortfolioService.kt` - IN PROGRESS
   - Updated repository imports to jOOQ
   - Need to update all 20+ methods to use domain models
   - Need to replace `.orElse()` calls (JPA Optional) with null checks
   - Need to replace `deleteById()` and `deleteByPortfolioId()` with jOOQ equivalents
5. ⏳ `OrderBlockCalculator.kt` - TODO
6. ⏳ `StockFactory.kt` - TODO

### Phase 7: Controller Layer Migration

**Controllers to Update:**
1. `StockController.kt` - Work with StockDomain instead of Stock
2. `BacktestController.kt` - Already uses domain models via mapper
3. `BreadthController.kt` - Work with BreadthDomain
4. `EtfController.kt` - Work with EtfDomain
5. `PortfolioController.kt` - Work with PortfolioDomain/PortfolioTradeDomain
6. `MonteCarloController.kt` - Verify domain model usage
7. `CacheController.kt` - Update cache eviction for new structure
8. `DataController.kt` - Update data import/export

### Phase 7: Remove JPA Repositories

**Files to Delete:**
- `src/main/kotlin/com/skrymer/udgaard/repository/StockRepository.kt`
- `src/main/kotlin/com/skrymer/udgaard/repository/BreadthRepository.kt`
- `src/main/kotlin/com/skrymer/udgaard/repository/EtfRepository.kt`
- `src/main/kotlin/com/skrymer/udgaard/repository/PortfolioRepository.kt`
- `src/main/kotlin/com/skrymer/udgaard/repository/PortfolioTradeRepository.kt`

### Phase 8: Remove JPA Entities

**Files to Delete:**
- `src/main/kotlin/com/skrymer/udgaard/model/Stock.kt`
- `src/main/kotlin/com/skrymer/udgaard/model/StockQuote.kt`
- `src/main/kotlin/com/skrymer/udgaard/model/OrderBlock.kt`
- `src/main/kotlin/com/skrymer/udgaard/model/Earning.kt`
- `src/main/kotlin/com/skrymer/udgaard/model/Breadth.kt`
- `src/main/kotlin/com/skrymer/udgaard/model/BreadthQuote.kt`
- `src/main/kotlin/com/skrymer/udgaard/model/EtfEntity.kt`
- `src/main/kotlin/com/skrymer/udgaard/model/EtfQuote.kt`
- `src/main/kotlin/com/skrymer/udgaard/model/EtfHolding.kt`
- `src/main/kotlin/com/skrymer/udgaard/model/EtfMetadata.kt`
- `src/main/kotlin/com/skrymer/udgaard/model/Portfolio.kt`
- `src/main/kotlin/com/skrymer/udgaard/model/PortfolioTrade.kt`

### Phase 8: Remove JPA/Hibernate Dependencies

**Edit `build.gradle`:**
```kotlin
// Remove these lines:
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
implementation("org.hibernate:hibernate-core")
// Any other JPA/Hibernate related dependencies
```

**Edit `application.properties`:**
```properties
# Remove JPA/Hibernate properties:
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.*
```

### Phase 9: Test Migration

1. Update remaining tests to use domain models directly
2. Remove StockMapper calls where tests already use domain models
3. Verify all 302+ tests pass
4. Run integration tests
5. Test with real database operations

---

## 🔑 Key Design Decisions

### 1. Domain Models with Default Constructors
All domain models have default values for easy instantiation in tests and service code.

### 2. No Parent References in Domain Models
Unlike JPA entities, domain models don't have parent references (e.g., `earning.stock`).
jOOQ repositories set foreign key columns directly:
```kotlin
.set(EARNINGS.STOCK_SYMBOL, stock.symbol)  // Direct FK
```

### 3. Mapper Pattern
- **jOOQ POJO ↔ Domain**: For persistence layer
- **JPA Entity → Domain**: For gradual migration
- All conversions centralized in mapper classes

### 4. Repository Layer
- jOOQ repositories handle all SQL operations
- Type-safe queries via DSLContext
- Transactional batch inserts for performance
- No lazy loading issues (everything loaded explicitly)

### 5. Service Boundaries
Services work with domain models internally and expose domain models to controllers.
Controllers can convert domain models to DTOs if needed.

---

## 📁 File Structure

```
udgaard/src/main/kotlin/com/skrymer/udgaard/
├── domain/                           # ✅ All domain models
│   ├── StockDomain.kt
│   ├── StockQuoteDomain.kt
│   ├── OrderBlockDomain.kt
│   ├── EarningDomain.kt
│   ├── BreadthDomain.kt
│   ├── BreadthQuoteDomain.kt
│   ├── EtfDomain.kt
│   ├── EtfQuoteDomain.kt
│   ├── EtfHoldingDomain.kt
│   ├── EtfMetadataDomain.kt
│   ├── PortfolioDomain.kt
│   └── PortfolioTradeDomain.kt
│
├── repository/jooq/                  # ✅ All jOOQ repositories
│   ├── StockJooqRepository.kt
│   ├── BreadthJooqRepository.kt
│   ├── EtfJooqRepository.kt
│   ├── PortfolioJooqRepository.kt
│   └── PortfolioTradeJooqRepository.kt
│
├── mapper/                           # ✅ All mappers
│   ├── StockMapper.kt
│   ├── BreadthMapper.kt
│   ├── EtfMapper.kt
│   ├── PortfolioMapper.kt
│   └── PortfolioTradeMapper.kt
│
├── model/                            # ⚠️ TO BE DELETED (JPA entities)
│   ├── Stock.kt
│   ├── StockQuote.kt
│   ├── OrderBlock.kt
│   ├── Earning.kt
│   ├── Breadth.kt
│   ├── BreadthQuote.kt
│   ├── EtfEntity.kt
│   ├── EtfQuote.kt
│   ├── EtfHolding.kt
│   ├── Portfolio.kt
│   └── PortfolioTrade.kt
│
├── repository/                       # ⚠️ TO BE DELETED (JPA repos)
│   ├── StockRepository.kt
│   ├── BreadthRepository.kt
│   ├── EtfRepository.kt
│   ├── PortfolioRepository.kt
│   └── PortfolioTradeRepository.kt
│
├── service/                          # ⚠️ NEEDS UPDATE
│   ├── StockService.kt               # Uses JPA repository
│   ├── BreadthService.kt             # Uses JPA repository
│   ├── EtfService.kt                 # Uses JPA repository
│   ├── PortfolioService.kt           # Uses JPA repository
│   ├── OrderBlockCalculator.kt       # Uses JPA entities
│   ├── BacktestService.kt            # ✅ Already updated
│   └── StrategySignalService.kt      # ✅ Already updated
│
└── controller/                       # ⚠️ NEEDS UPDATE
    ├── StockController.kt            # Returns JPA entities
    ├── BreadthController.kt          # Returns JPA entities
    ├── EtfController.kt              # Returns JPA entities
    ├── PortfolioController.kt        # Returns JPA entities
    └── BacktestController.kt         # ✅ Already uses domain models
```

---

## 🎯 Next Steps

When resuming work:

1. **Fix compilation errors** (BreadthMapper and BreadthJooqRepository field names) - **IN PROGRESS**
   - Started fixing BreadthMapper line 34-38 to use jOOQ POJO field names
   - Need to complete the fix and update BreadthJooqRepository lines 90-94
2. **Update StockService** to use StockJooqRepository
3. **Update OrderBlockCalculator** to work with domain models
4. **Update StockFactory** to work with domain models
5. **Update controllers** to work with domain models
6. **Remove JPA repositories**
7. **Remove JPA entities**
8. **Remove JPA dependencies**
9. **Run all tests**

---

## 📊 Progress: ~60% Complete

- ✅ Infrastructure (100%)
- ✅ Domain models (100%)
- ✅ Repositories (100%)
- ✅ Mappers (100%)
- ⚠️ Services (20% - only BacktestService/StrategySignalService done)
- ⚠️ Controllers (10% - only BacktestController done)
- ⚠️ Tests (100% passing but using hybrid approach)
- ❌ JPA removal (0%)
- 🔧 **Currently fixing**: BreadthMapper field name mappings (interrupted mid-task)

**Estimated remaining work: 4-6 hours**

---

## 🔧 Session Notes - 2025-12-11 (Continued)

**Work in Progress:**
- Started fixing BreadthMapper.kt field name mismatches
- Identified that `BreadthMapper.toDomain()` (line 30-51) needs to use jOOQ POJO field names:
  - `quote.stocksWithBuySignal` instead of `quote.numberOfStocksWithABuySignal`
  - `quote.stocksWithSellSignal` instead of `quote.numberOfStocksWithASellSignal`
  - `quote.stocksInUptrend` instead of `quote.numberOfStocksInUptrend`
  - `quote.stocksInNeutral` instead of `quote.numberOfStocksInNeutral`
  - `quote.stocksInDowntrend` instead of `quote.numberOfStocksInDowntrend`
- Also identified that non-nullable fields (ema_5, ema_10, ema_20, ema_50, heatmap) don't need `?: 0.0` null checks
- Work was interrupted before completing the Edit operation

**Next Action:**
Complete the BreadthMapper.toDomain() fix, then update BreadthJooqRepository.save() method (lines 90-94) with matching field names.

---

_Last Updated: 2025-12-11 (Session 2)_
_Migration paused mid-fix - will continue fixing BreadthMapper compilation errors_
