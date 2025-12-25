# Udgaard Backend

Stock trading backtesting platform backend built with Kotlin and Spring Boot.

## Prerequisites

- Java JDK 21+
- Gradle (or use the included wrapper `./gradlew`)

## Database Setup

The application uses H2 database (file-based, no Docker required).

### First-Time Setup

1. **Create secure.properties file:**
   ```bash
   cd udgaard/src/main/resources
   touch secure.properties
   ```

2. **Add Ovtlyr credentials to `secure.properties`:**
   ```properties
   ovtlyr.cookies.token=XXX
   ovtlyr.cookies.userid=XXX
   ```
   (Check network tab on ovtlyr.com website for values)

3. **Initialize the database:**
   ```bash
   ./gradlew initDatabase
   ```
   This creates the H2 database file at `~/.trading-app/database/trading.mv.db` and runs Flyway migrations.

### Database Tasks

The following Gradle tasks are available for database management:

- **`./gradlew startH2Server`** - **REQUIRED:** Starts H2 database in TCP server mode (must run before build/migrations)
- **`./gradlew stopH2Server`** - Stops the H2 TCP server
- **`./gradlew initDatabase`** - Initializes database and runs Flyway migrations (requires H2 server running)
- **`./gradlew flywayMigrate`** - Runs Flyway migrations (requires H2 server running)

### H2 Database Console

When the application is running, access the H2 console at:
- **URL:** http://localhost:8080/udgaard/h2-console
- **JDBC URL:** `jdbc:h2:file:~/.trading-app/database/trading`
- **Username:** `sa`
- **Password:** (leave empty)

## Building and Running

### Build

**IMPORTANT:** The H2 database server must be running before building.

```bash
# Terminal 1: Start H2 database server
./gradlew startH2Server

# Terminal 2: Build the project
./gradlew build
```

This will:
1. Run Flyway migrations (creates/updates database schema)
2. Generate jOOQ code from the database schema
3. Run ktlint code style checks
4. Compile Kotlin code
5. Run unit tests
6. Build the JAR file

### Run

**Option 1: Using Gradle (recommended for development)**
```bash
./gradlew bootRun
```

**Option 2: Using the JAR file**
```bash
./gradlew bootJar
java -jar build/libs/udgaard-0.0.1-SNAPSHOT.jar
```

### Health Check

Verify the application is running:
```bash
curl http://localhost:8080/udgaard/actuator/health
```

## Development Workflow

### First-Time Setup Workflow

```bash
# Terminal 1: Start H2 database server
./gradlew startH2Server

# Terminal 2: Initialize database and build
./gradlew initDatabase  # Creates database schema via Flyway migrations
./gradlew build         # Generates jOOQ code and builds project

# Terminal 2: Start the application
./gradlew bootRun
```

**Why this order?**
1. `startH2Server` - Starts H2 database in TCP server mode so Flyway/jOOQ can connect
2. `initDatabase` - Runs Flyway migrations to create the database schema
3. `build` - jOOQ reads the schema to generate Kotlin code, then compiles everything
4. Without a running database server, Flyway and jOOQ cannot connect

### Regular Development Workflow

Once the database is initialized:

```bash
# Terminal 1: Start H2 database server (if not already running)
./gradlew startH2Server

# Terminal 2: Build and run
./gradlew bootRun

# Or build separately
./gradlew build
./gradlew bootRun
```

**Note:** The H2 server must be running before any Gradle task that accesses the database (build, bootRun, flywayMigrate, etc.)

### Adding New Database Migrations

1. Create a new migration file in `src/main/resources/db/migration/`:
   ```
   V3__description_of_change.sql
   ```

2. Ensure H2 server is running:
   ```bash
   # Terminal 1 (if not already running)
   ./gradlew startH2Server
   ```

3. Run migrations and regenerate jOOQ code:
   ```bash
   # Terminal 2
   ./gradlew flywayMigrate  # Apply new migrations
   ./gradlew build          # Regenerate jOOQ code from updated schema
   ```

4. Restart the application:
   ```bash
   ./gradlew bootRun
   ```

## Code Quality

### Linting (ktlint)

```bash
# Check code style
./gradlew ktlintCheck

# Auto-fix code style issues
./gradlew ktlintFormat
```

### Testing

```bash
# Run all tests
./gradlew test

# Run specific test
./gradlew test --tests "com.skrymer.udgaard.service.StockServiceTest"
```

## JOOQ Code Generation

JOOQ code is generated automatically during Spring Boot application startup based on the current database schema. The generated code is located at:
```
build/generated-src/jooq/main/com/skrymer/udgaard/jooq/
```

**Important:** Ensure Flyway migrations have been run (`./gradlew initDatabase`) before starting the application, otherwise JOOQ code generation will fail due to missing schema.

## Project Structure

```
udgaard/
├── src/main/kotlin/com/skrymer/udgaard/
│   ├── controller/         # REST API endpoints
│   ├── integration/        # External API integrations (AlphaVantage, Ovtlyr)
│   ├── mcp/               # MCP server for Claude AI integration
│   ├── model/             # Domain models and strategies
│   ├── service/           # Business logic
│   ├── repository/        # Database repositories (JOOQ)
│   └── config/            # Configuration classes
├── src/main/resources/
│   ├── db/migration/      # Flyway migration scripts
│   ├── application.properties
│   └── secure.properties  # Git-ignored, contains API credentials
└── build.gradle           # Build configuration
```

## Configuration

### Main Configuration (`application.properties`)

- **Database:** H2 file-based database
- **Flyway:** Enabled with baseline-on-migrate
- **Cache:** Caffeine cache (30m TTL, 1000 max entries)
- **Timeouts:** 30 minutes for long-running backtests
- **Logging:** DEBUG level for development

### Secure Configuration (`secure.properties`)

Contains sensitive credentials (not committed to git):
- Ovtlyr API credentials
- AlphaVantage API key (optional, can be configured via Settings UI)

## API Documentation

### Backtesting
- `POST /api/backtest` - Run backtest with strategy parameters
- `GET /api/strategies/available` - List available strategies

### Stocks
- `GET /api/stocks` - Get all stocks
- `GET /api/stocks/{symbol}` - Get specific stock with quotes
- `POST /api/stocks/refresh` - Refresh stock data from data providers

### Portfolio
- `GET /api/portfolio` - Get all portfolios
- `POST /api/portfolio` - Create new portfolio
- `GET /api/portfolio/{id}/stats` - Get portfolio statistics
- `POST /api/portfolio/{id}/trades` - Open new trade

### Market Breadth
- `GET /api/breadth` - Get all breadth symbols
- `GET /api/breadth/{symbol}` - Get market breadth for SPY/QQQ/IWM

See the full API documentation at http://localhost:8080/udgaard/actuator after starting the application.

## Troubleshooting

### Database Issues

**Problem:** Database locked or connection errors
```bash
# Solution: Stop any running H2 servers
./gradlew stopH2Server

# Restart H2 server and application
# Terminal 1
./gradlew startH2Server

# Terminal 2
./gradlew bootRun
```

**Problem:** jOOQ code generation fails with "table not found" or "schema not found"
```bash
# Solution: H2 server not running or database not initialized
# Terminal 1
./gradlew startH2Server

# Terminal 2
./gradlew initDatabase
./gradlew build
```

**Problem:** Build fails with "Cannot connect to database" or similar connection errors
```bash
# Solution: H2 server not started
# Terminal 1
./gradlew startH2Server

# Terminal 2
./gradlew build
```

### Build Issues

**Problem:** Gradle daemon issues
```bash
# Solution: Kill Gradle daemons and rebuild
./gradlew --stop
./gradlew clean build
```

**Problem:** ktlint failures
```bash
# Solution: Auto-fix formatting issues
./gradlew ktlintFormat
```

## Libraries

- **Spring Boot 3.5.0** - Application framework
- **Kotlin 2.1.21** - Programming language
- **H2 Database 2.2.224** - Embedded database
- **JOOQ 3.19.23** - Type-safe SQL queries
- **Flyway** - Database migrations
- **Caffeine** - High-performance caching
- **Spring AI MCP Server** - Claude AI integration
- **ktlint** - Kotlin code style checker

## Related Documentation

- [Frontend (Asgaard) README](../asgaard/README.md)
- [Electron Desktop App](../electron/README.md)
- [Main Project README](../README.md)
- [AlphaVantage Integration](../claude_thoughts/ALPHAVANTAGE_REFACTORING_SUMMARY.md)
- [Strategy System](../claude_thoughts/DYNAMIC_STRATEGY_SYSTEM.md)
