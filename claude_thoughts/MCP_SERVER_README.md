# Stock Backtesting MCP Server

A Model Context Protocol (MCP) server built with Spring AI that provides strategy and configuration metadata for Claude to help configure backtesting strategies via the REST API.

## Overview

This MCP server exposes **metadata only** through four focused tools:
1. **getStockSymbols** - List all available stock symbols
2. **getAvailableStrategies** - List all entry and exit strategies
3. **getAvailableRankers** - List all available stock rankers
4. **getAvailableConditions** - Get metadata about conditions for custom strategy building

**Important**: This MCP server provides discovery/metadata only. To execute backtests or retrieve stock data, use the REST API endpoints at `http://localhost:8080/api/`.

## Prerequisites

- Java 21+
- MongoDB running on localhost:27017
- Stock data populated in MongoDB database

## Setup

### 1. Build the Project

```bash
./gradlew build
```

### 2. Configure Claude Code MCP Settings

Add the following to your Claude Code MCP configuration file (`~/.claude/mcp_settings.json` or equivalent):

```json
{
  "mcpServers": {
    "stock-backtesting": {
      "command": "java",
      "args": [
        "-jar",
        "/home/sonni/development/git/trading/udgaard/build/libs/udgaard-0.0.1-SNAPSHOT.jar"
      ]
    }
  }
}
```

### 3. Start the MCP Server

The Spring AI MCP server will automatically start when you run the Spring Boot application. It uses stdio transport to communicate with Claude.

```bash
./gradlew bootRun
```

Or using the JAR:

```bash
java -jar build/libs/udgaard-0.0.1-SNAPSHOT.jar
```

## Available Tools

### getStockSymbols

Lists all available stock symbols in the system.

**Parameters:** None

**Returns:**
```json
{
  "symbols": ["AAPL", "MSFT", "GOOGL", ...],
  "count": 1286
}
```

**Example Usage:**
```
Show me all available stock symbols
```

### getAvailableStrategies

Lists all registered entry and exit strategies.

**Parameters:** None

**Returns:**
```json
{
  "entryStrategies": ["PlanAlpha", "PlanBeta", "PlanEtf", "SimpleBuySignal"],
  "exitStrategies": ["PlanMoney", "PlanAlpha", "PlanEtf"]
}
```

**Example Usage:**
```
What entry and exit strategies are available?
```

**Use Case:** Use this to discover what predefined strategies exist before configuring a backtest.

### getAvailableRankers

Lists all available stock rankers for position-limited backtests.

**Parameters:** None

**Returns:**
```json
{
  "rankers": [
    "Heatmap",
    "RelativeStrength",
    "Volatility",
    "DistanceFrom10Ema",
    "Composite",
    "SectorStrength",
    "Random",
    "Adaptive"
  ],
  "count": 8
}
```

**Ranker Descriptions:**
- **Heatmap**: Ranks stocks by sentiment/heatmap values (higher = more greedy/bullish)
- **RelativeStrength**: Ranks by relative strength indicators
- **Volatility**: Ranks by ATR-based volatility
- **DistanceFrom10Ema**: Ranks by distance from 10-period EMA
- **Composite**: Combined ranking using multiple factors
- **SectorStrength**: Ranks by sector performance
- **Random**: Random selection (for baseline comparison)
- **Adaptive**: Dynamically adapts ranking based on market conditions

**Example Usage:**
```
What rankers can I use for position-limited backtests?
```

**Use Case:** When setting `maxPositions` in a backtest, you need a ranker to prioritize which stocks to enter when multiple stocks trigger on the same day.

### getAvailableConditions

Gets detailed metadata about all available trading conditions for custom strategy building.

**Parameters:** None

**Returns:**
```json
{
  "entryConditions": [
    {
      "type": "buySignal",
      "displayName": "Buy Signal",
      "description": "Stock has a buy signal",
      "parameters": [],
      "category": "Stock"
    },
    {
      "type": "priceAboveEma",
      "displayName": "Price Above EMA",
      "description": "Stock price is above specified EMA period",
      "parameters": [
        {
          "name": "period",
          "displayName": "EMA Period",
          "type": "number",
          "defaultValue": 20,
          "min": 1,
          "max": 200
        }
      ],
      "category": "Stock"
    }
    // ... more conditions
  ],
  "exitConditions": [
    {
      "type": "stopLoss",
      "displayName": "Stop Loss",
      "description": "Exit when price drops by specified ATR multiple from entry",
      "parameters": [
        {
          "name": "atrMultiple",
          "displayName": "ATR Multiple",
          "type": "number",
          "defaultValue": 0.5,
          "min": 0.1,
          "max": 5.0
        }
      ],
      "category": "Risk Management"
    }
    // ... more conditions
  ]
}
```

**Example Usage:**
```
What conditions can I use to build a custom entry strategy?
```

**Use Case:** Use this to understand what building blocks are available for creating custom strategies via the REST API.

## Using the MCP Server with REST API

The MCP server provides **metadata only**. To execute backtests or retrieve data, use the REST API:

### Running a Backtest

Once you know available strategies, symbols, and rankers, run backtests via the REST API:

```bash
curl -X POST http://localhost:8080/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "entryStrategy": {
      "type": "predefined",
      "name": "PlanAlpha"
    },
    "exitStrategy": {
      "type": "predefined",
      "name": "PlanMoney"
    },
    "stockSymbols": ["AAPL", "MSFT", "GOOGL"],
    "startDate": "2024-01-01",
    "endDate": "2024-12-31",
    "maxPositions": 10,
    "ranker": "Adaptive"
  }'
```

### Creating a Custom Strategy

Use conditions from `getAvailableConditions()` to build custom strategies:

```bash
curl -X POST http://localhost:8080/api/backtest \
  -H "Content-Type: application/json" \
  -d '{
    "entryStrategy": {
      "type": "custom",
      "conditions": [
        {"type": "buySignal", "parameters": {}},
        {"type": "priceAboveEma", "parameters": {"period": 20}},
        {"type": "marketInUptrend", "parameters": {}}
      ],
      "operator": "AND"
    },
    "exitStrategy": {
      "type": "custom",
      "conditions": [
        {"type": "stopLoss", "parameters": {"atrMultiple": 0.5}},
        {"type": "priceBelowEma", "parameters": {"period": 10}}
      ],
      "operator": "OR"
    },
    "stockSymbols": ["AAPL"],
    "startDate": "2024-01-01",
    "endDate": "2024-12-31"
  }'
```

## REST API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/backtest` | POST | Execute a backtest with strategies |
| `/api/strategies` | GET | Get available strategies (same as MCP tool) |
| `/api/rankers` | GET | Get available rankers (same as MCP tool) |
| `/api/conditions` | GET | Get available conditions (same as MCP tool) |
| `/api/stocks` | GET | Get stock symbols (same as MCP tool) |

## Workflow Example

1. **Discovery Phase** (via MCP):
   ```
   Claude: What strategies are available?
   Tool: getAvailableStrategies()

   Claude: What stocks can I backtest?
   Tool: getStockSymbols()

   Claude: What rankers exist?
   Tool: getAvailableRankers()
   ```

2. **Configuration Phase** (via REST API):
   ```
   Claude: Run a backtest on AAPL, MSFT with PlanAlpha entry and PlanMoney exit
   Action: POST to /api/backtest with configuration
   ```

3. **Analysis Phase**:
   ```
   Claude: Analyze the results and suggest improvements
   Action: Examine backtest report, compare win rates, suggest parameter tweaks
   ```

## Architecture

### Components

**MCP Tools Service** (`StockMcpTools.kt`):
- Metadata-only tools
- No data retrieval or processing
- Lightweight and fast

**REST API Controller** (`UdgaardController.kt`):
- Backtest execution
- Stock data retrieval
- Heavy computation

**Strategy Registry** (`StrategyRegistry.kt`):
- Dynamic strategy discovery
- Strategy instantiation

**Dynamic Strategy Builder** (`DynamicStrategyBuilder.kt`):
- Builds strategies from condition configurations
- Validates custom strategies

### Benefits of This Design

✅ **Separation of Concerns**: MCP for discovery, REST API for execution
✅ **Lightweight MCP**: Fast metadata queries, no timeouts
✅ **Flexible Integration**: Claude can use both MCP tools and REST API
✅ **Clear Boundaries**: Metadata vs computation cleanly separated

## Troubleshooting

### MCP Server Not Connecting

1. Verify the JAR path in MCP settings is correct
2. Ensure MongoDB is running
3. Check that the application builds successfully
4. Review Claude Code logs for MCP connection errors

### Tools Not Appearing

1. Ensure Spring Boot application is running
2. Check logs for MCP server initialization messages
3. Verify `@Tool` annotations are present in `StockMcpTools.kt`

### Build Issues

```bash
# Clean and rebuild
./gradlew clean build

# Check dependencies
./gradlew dependencies
```

## Development

### Adding New MCP Tools

To add a new metadata tool:

1. Add a method to `StockMcpTools.kt`
2. Annotate it with `@Tool` and provide a description
3. Return JSON string response using `ObjectMapper`
4. Rebuild the application

Example:

```kotlin
@Tool(description = "Get available sectors")
fun getAvailableSectors(): String {
  val sectors = listOf("Technology", "Healthcare", "Finance", ...)
  return objectMapper.writerWithDefaultPrettyPrinter()
    .writeValueAsString(mapOf("sectors" to sectors))
}
```

**Important**: Keep MCP tools focused on metadata only. Heavy computation should go in REST API endpoints.

## License

Part of the Udgaard trading system project.
