---
name: Run backtests against PRD
description: When running backtests, use PRD (port 9080) with X-API-Key auth header, not DEV (port 8080)
type: feedback
originSessionId: c2c817e6-8f7f-4cfb-9e7c-c27ec54cc8f2
---
Run backtests against PRD (port 9080), not DEV (port 8080). PRD has the full dataset and more memory.

**Why:** DEV may not have all stocks ingested, and backtests are resource-intensive.

**How to apply:** Use `http://localhost:9080/udgaard/api/...` with `-H "X-API-Key: changeme"` header for all backtest API calls.
