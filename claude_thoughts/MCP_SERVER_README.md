# Stock Backtesting MCP Server

A Model Context Protocol (MCP) server built with Spring AI that provides strategy discovery, stock metadata, and backtesting guidance for Claude Code integration.

## Overview

This MCP server exposes **7 metadata/discovery tools** via SSE transport:

1. **getAvailableSymbols** - List stocks with data (symbol, sector, assetType, quoteCount, lastQuoteDate)
2. **getAvailableStrategies** - List all entry and exit strategy names
3. **getAvailableRankers** - List all available stock rankers
4. **getAvailableConditions** - Get condition metadata for custom strategy building
5. **getStrategyDetails** - Get description for a specific strategy
6. **getSystemStatus** - Health check: database, strategies, cache readiness
7. **explainBacktestMetrics** - Explain backtest metrics (definitions, benchmarks)

**Important**: This MCP server provides discovery/metadata only. To execute backtests or Monte Carlo simulations, use the REST API endpoints.

## Prerequisites

- Java 21+
- H2 database with stock data populated
- Backend running on localhost:8080

## Setup

### 1. Configure Claude Code MCP Settings

The project `.mcp.json` configures the SSE connection:

```json
{
  "mcpServers": {
    "stock-backtesting": {
      "type": "sse",
      "url": "http://localhost:8080/udgaard/sse"
    }
  }
}
```

### 2. Start the Backend

```bash
cd udgaard
./gradlew bootRun
```

The MCP SSE endpoint is automatically available at `http://localhost:8080/udgaard/sse`.

**Note**: The `spring.ai.mcp.server.base-url=/udgaard` property is required because the servlet context path is `/udgaard`. Without it, the SSE transport advertises message endpoints without the context path prefix, causing 404 errors.

## Available Tools

### getAvailableSymbols

Lists all stocks with historical data in the database, including metadata.

**Parameters:** None

**Returns:**
```json
{
  "count": 1438,
  "symbols": [
    {
      "symbol": "AAPL",
      "sector": "XLK",
      "assetType": "STOCK",
      "quoteCount": 2341,
      "lastQuoteDate": "2026-02-10",
      "hasData": true
    }
  ]
}
```

### getAvailableStrategies

Lists all registered entry and exit strategy names.

**Parameters:** None

**Returns:**
```json
{
  "entryStrategies": ["OvtlyrPlanEtf", "PlanAlpha", "PlanQEntryStrategy", "ProjectXEntryStrategy"],
  "exitStrategies": ["OvtlyrPlanEtf", "PlanAlpha", "PlanMoney", "PlanQExitStrategy", "ProjectXExitStrategy"]
}
```

### getAvailableRankers

Lists rankers for position-limited backtests.

**Parameters:** None

**Returns:**
```json
{
  "rankers": ["Heatmap", "RelativeStrength", "Volatility", "DistanceFrom10Ema", "Composite", "SectorStrength", "Random", "Adaptive"],
  "count": 8
}
```

### getAvailableConditions

Gets metadata about all conditions for custom strategy building, including parameter types, defaults, and descriptions.

**Parameters:** None

**Returns:** Entry and exit condition arrays with type, displayName, description, parameters, and category.

### getStrategyDetails

Gets the description for a specific strategy.

**Parameters:**
- `strategyName`: Strategy name (e.g., "PlanAlpha")
- `strategyType`: "entry" or "exit"

**Returns:**
```json
{
  "name": "PlanAlpha",
  "type": "entry",
  "available": true,
  "description": "Entry strategy description from implementation..."
}
```

### getSystemStatus

Health check for backtesting readiness.

**Parameters:** None

**Returns:** Status, database connectivity, stock count, strategy count, cache status, warnings.

### explainBacktestMetrics

Explains backtest metrics with definitions, benchmarks, and interpretation guidance.

**Parameters:**
- `metrics`: Optional comma-separated list (e.g., "edge,winRate"). Omit for all metrics.

## REST API (for execution)

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/backtest` | POST | Execute a backtest |
| `/api/backtest/strategies` | GET | Get strategies (same as MCP) |
| `/api/backtest/rankers` | GET | Get rankers (same as MCP) |
| `/api/backtest/conditions` | GET | Get conditions (same as MCP) |
| `/api/stocks` | GET | Get stocks with metadata (same as MCP) |
| `/api/monte-carlo/simulate` | POST | Run Monte Carlo simulation |

## Architecture

- **Transport**: SSE via `spring-ai-starter-mcp-server-webmvc` (MCP SDK 0.10.0)
- **Configuration**: `McpConfiguration.kt` registers `StockMcpTools` via `MethodToolCallbackProvider`
- **Tools**: `StockMcpTools.kt` - all `@Tool` annotated methods returning JSON strings
- **Properties**: `application.properties` - `spring.ai.mcp.server.*` settings

## Troubleshooting

### MCP Server Not Connecting

1. Ensure backend is running: `curl http://localhost:8080/udgaard/actuator/health`
2. Check `.mcp.json` URL matches: `http://localhost:8080/udgaard/sse`
3. Verify `spring.ai.mcp.server.base-url=/udgaard` is set in `application.properties`
4. Run `/mcp` in Claude Code to reconnect

### Tools Not Returning Data

1. Check H2 database has stock data
2. Verify strategies are registered: `curl http://localhost:8080/udgaard/api/backtest/strategies`
3. Check backend logs for errors
