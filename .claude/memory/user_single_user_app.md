---
name: trading platform is a single-user app
description: This codebase has exactly one user (the developer himself). Don't propose multi-user infrastructure — use the trade-offs that make sense at this scale.
type: user
originSessionId: 399c9a2a-ecaf-4456-befd-f2cc7b6af5cc
---
The trading platform (udgaard + midgaard + asgaard) is operated by exactly one user — the developer. There is no multi-user environment, no shared deployment, no other analysts.

This shapes design trade-offs:
- **Manual operational triggers are fine.** Don't propose dashboards / cron jobs / event-driven cascades to "remind users" or "auto-refresh for everyone." A single line in PR description / claude.md telling the user to run a curl command is the appropriate handoff. The user is in the loop already.
- **Don't build admin UIs.** Operational endpoints called via `curl` + `API_KEY` are the right interface, not a settings page.
- **Documented manual workflows are the contract.** "After deploying X, run Y" is acceptable — no need to engineer self-healing systems.
- **Skip multi-tenant patterns.** No row-level security, no per-user data partitioning, no impersonation flows.
- **Skip drift dashboards / observability tooling beyond logs.** Warn-logs in normal log streams are enough — the user reads logs himself.

Examples where this matters: post-deploy refresh triggers (manual is fine), backfill operations (manual is fine), provider drift detection (warn-log is enough — no dedicated `/drift` endpoint needed).

The only exception: things that are too tedious to do manually at any scale (e.g. the analyst sub-agents under `.claude/agents/`, the `/backtest-reports` UI for managing accumulated runs).
