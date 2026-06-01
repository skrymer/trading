---
name: MCP tools no longer supported
description: The trading platform's MCP server is not a supported integration path. Document and recommend HTTP API endpoints exclusively.
type: feedback
originSessionId: e9db63a6-5658-4517-8fe1-254dedcdebcf
---
The platform exposes only HTTP API endpoints (Udgaard REST on port 9080 PRD / 8080 dev). The MCP server (`mcp/` package, `StockMcpTools.kt`, `spring-ai-starter-mcp-server-webmvc` dependency) is no longer a supported integration path.

**Why:** policy decision; HTTP is the only supported surface going forward.

**How to apply:**
- Skills, CLAUDE.md, and other user-facing docs must not list MCP tools (`runBacktest`, `getStockData`, etc.) as the recommended integration. Reference the HTTP endpoints + the three skills (`/backtest`, `/walk-forward`, `/monte-carlo`) instead.
- Don't suggest using `mcp__*` tools or wire skills/agents to depend on them.
- The MCP code may still compile/run but treat it as legacy. If asked to clean up, that's a separate scoped task — don't bundle MCP removal into unrelated edits.
