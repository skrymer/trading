# Migration from MongoDB to H2 Database

## Overview

This guide outlines the migration from MongoDB (document database) to H2 (relational database) for the desktop app.

## Changes Made So Far

✅ Updated `build.gradle` - Replaced MongoDB with JPA + H2
✅ Updated `application.properties` - Configured H2 database

## Required Code Changes

### 1. Model Classes - Replace MongoDB annotations with JPA

#### Stock.kt
**Current (MongoDB):**
```kotlin
@Document(collection = "stocks")
class Stock {
  @Id
  var symbol: String? = null
  var quotes: List<StockQuote> = emptyList()  // Embedded documents
  var orderBlocks: List<OrderBlock> = emptyList()  // Embedded documents
}
```

**New (JPA):**
```kotlin
@Entity
@Table(name = "stocks")
class Stock {
  @Id
  var symbol: String? = null

  @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY, mappedBy = "stock")
  var quotes: List<StockQuote> = emptyList()

  @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.LAZY, mappedBy = "stock")
  var orderBlocks: List<OrderBlock> = emptyList()
}
```

#### StockQuote.kt
**Need to add:**
```kotlin
@Entity
@Table(name = "stock_quotes")
class StockQuote {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  var id: Long? = null

  @ManyToOne
  @JoinColumn(name = "stock_symbol")
  var stock: Stock? = null

  // ... existing fields
}
```

#### OrderBlock.kt
Similar changes needed.

#### MarketBreadth.kt
**Current:**
```kotlin
@Document(collection = "marketBreadth")
```

**New:**
```kotlin
@Entity
@Table(name = "market_breadth")
```

#### Portfolio.kt
Similar JPA annotations needed.

### 2. Repository Interfaces

**Current (MongoDB):**
```kotlin
interface StockRepository : MongoRepository<Stock, String>
```

**New (JPA):**
```kotlin
interface StockRepository : JpaRepository<Stock, String>
```

Same for:
- MarketBreadthRepository
- PortfolioRepository
- PortfolioTradeRepository

### 3. Import Statements

Replace:
```kotlin
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.MongoRepository
```

With:
```kotlin
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
```

## Benefits of This Migration

### For Desktop App:
- ✅ **No External Database** - H2 is embedded in the JAR
- ✅ **Single File Storage** - Database is `./data/trading.mv.db`
- ✅ **Smaller Footprint** - No MongoDB process needed
- ✅ **Faster Startup** - No connection to external DB
- ✅ **Easy Backup** - Just copy the `.mv.db` file
- ✅ **Better for Electron** - Self-contained, works offline

### Performance:
- In-memory mode available for ultra-fast backtesting
- Good query performance for your data size
- Efficient indexing

## Database Location

The H2 database will be stored in:
- **Development**: `./data/trading.mv.db` (in project root)
- **Electron App**: User's app data directory (we can configure this)

## H2 Console

For debugging, access the H2 web console at:
- http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:file:./data/trading`
- Username: `sa`
- Password: (empty)

## Alternative: Keep MongoDB for Now

If you want to keep MongoDB temporarily and just test H2:

1. Create a separate Spring profile for H2
2. Keep both dependencies
3. Switch via `--spring.profiles.active=h2`

## Recommended Approach

**Option A: Full Migration (Recommended for Desktop)**
- Migrate all models to JPA
- Test thoroughly
- Best for desktop app distribution

**Option B: Gradual Migration**
- Keep MongoDB dependency
- Add H2 as alternative
- Gradually migrate models
- More work but lower risk

**Option C: MongoDB Embedded (Least Changes)**
- Use Flapdoodle embedded MongoDB
- Keep all existing code
- Larger bundle size

## Next Steps

Would you like me to:
1. **Complete the full JPA migration** (change all models + repositories)?
2. **Set up dual database support** (both MongoDB and H2)?
3. **Use embedded MongoDB instead** (zero code changes)?

Let me know which approach you prefer!
