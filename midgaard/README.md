# Midgaard - Reference Data Service

Midgaard is a standalone reference data service that provides OHLCV stock data with pre-computed technical indicators. It serves as the data backbone for the [Udgaard](../udgaard/) backtesting platform.

## What It Does

- Ingests historical stock data from **AlphaVantage** (initial bulk loads) and **Polygon/Massive API** (daily updates)
- Computes technical indicators: **EMA** (5/10/20/50/100/200), **ATR** (14), **ADX** (14), **Donchian** (5)
- Stores 3,128 symbols (stocks, ETFs, leveraged ETFs, bond ETFs, commodity ETFs)
- Serves enriched quote data via REST API to downstream consumers
- Provides earnings data and historical options pricing
- Includes a Thymeleaf admin UI for monitoring ingestion progress

## Tech Stack

- **Kotlin 2.3.0** / **Spring Boot 3.5.0** / **Java 21**
- **PostgreSQL 17** (Docker Compose)
- **jOOQ 3.19.23** for type-safe SQL
- **Flyway** for database migrations
- **Kotlinx Coroutines 1.9.0** for async ingestion
- **Spring Security** with API key authentication

## Quick Start

### Prerequisites

- Java 21+
- Docker (for PostgreSQL)

### Local Development

```bash
# Start PostgreSQL
docker compose up -d postgres

# Initialize database (Flyway migrations + jOOQ codegen)
./gradlew initDatabase

# Start the application (http://localhost:8081)
./gradlew bootRun
```

### Docker Deployment

```bash
# Build the JAR (requires PostgreSQL running for jOOQ codegen)
./gradlew bootJar

# Generate an API key
API_KEY=$(openssl rand -hex 32)
echo "API Key: $API_KEY"

# Generate the SHA-256 hash
export MIDGAARD_API_KEY_HASH=$(echo -n "$API_KEY" | sha256sum | awk '{print $1}')

# Start everything
docker compose up -d
```

## API Endpoints

All `/api/**` endpoints require the `X-API-Key` header when security is enabled.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/quotes/{symbol}` | Quotes with indicators (optional `startDate`, `endDate`) |
| `GET` | `/api/quotes/bulk` | Bulk quotes (`symbols`, `startDate`, `endDate`) |
| `GET` | `/api/symbols` | All symbols |
| `GET` | `/api/symbols/{symbol}` | Symbol details |
| `GET` | `/api/earnings/{symbol}` | Earnings history |
| `GET` | `/api/options/{symbol}` | Historical options (optional `date`) |
| `GET` | `/api/options/{symbol}/find` | Find specific option contract |
| `GET` | `/api/status` | System status (counts, rate limits) |
| `POST` | `/api/ingestion/initial/{symbol}` | Trigger initial ingest for symbol |
| `POST` | `/api/ingestion/initial/all` | Trigger bulk initial ingest (async) |
| `POST` | `/api/ingestion/update/{symbol}` | Trigger daily update for symbol |
| `POST` | `/api/ingestion/update/all` | Trigger bulk daily update (async) |
| `GET` | `/api/ingestion/status` | Ingestion status for all symbols |
| `GET` | `/api/ingestion/progress` | Bulk operation progress |
| `GET` | `/actuator/health` | Health check (always public) |

## Configuration

### Environment Variables (Production)

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5433/datastore` |
| `SPRING_DATASOURCE_USERNAME` | Database username | `trading` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `trading` |
| `APP_SECURITY_ENABLED` | Enable API key authentication | `false` |
| `APP_SECURITY_API_KEY_HASH` | SHA-256 hash of the API key | _(empty)_ |
| `APP_UI_ENABLED` | Enable Thymeleaf admin UI | `true` |
| `SPRING_FLYWAY_ENABLED` | Run migrations on startup | `true` |
| `ALPHAVANTAGE_API_KEY` | AlphaVantage API key | _(via secure.properties)_ |
| `MASSIVE_API_KEY` | Polygon API key | _(via secure.properties)_ |

### API Key Setup

```bash
# Generate a random API key
API_KEY=$(openssl rand -hex 32)

# Compute the SHA-256 hash to store in configuration
echo -n "$API_KEY" | sha256sum | awk '{print $1}'
```

Set the hash as `APP_SECURITY_API_KEY_HASH`. Clients authenticate with `X-API-Key: <raw-key>`.

## Data Providers

| Provider | Used For | Rate Limits |
|----------|----------|-------------|
| **AlphaVantage** | Initial OHLCV load, ATR, ADX, earnings, company info, options | 5/sec, 75/min, 75K/day |
| **Polygon (Massive)** | Daily OHLCV updates | 80/sec, 1K/min, 100K/day |

API keys are stored in `src/main/resources/secure.properties` (not committed to git).

## Database

### Schema (Flyway V1-V2)

- **quotes** - OHLCV + computed indicators (ATR, ADX, EMAs, Donchian)
- **earnings** - Quarterly earnings data
- **symbols** - Reference data (3,128 symbols across 5 asset types)
- **ingestion_status** - Per-symbol ingestion tracking

### Indicator Computation

Initial load uses AlphaVantage API values for ATR/ADX. Daily updates compute indicators locally using a 250-bar lookback seed from existing data. EMAs bootstrap from SMA, ATR/ADX use Wilder's smoothing.

## Code Quality

```bash
./gradlew ktlintCheck    # Kotlin formatting (ktlint 1.5.0)
./gradlew ktlintFormat   # Auto-fix formatting
./gradlew detekt         # Static analysis (Detekt 2.0.0-alpha.2)
```
