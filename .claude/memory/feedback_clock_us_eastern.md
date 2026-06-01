---
name: feedback-clock-us-eastern
description: "JVM Clock bean must be pinned to America/New_York — trading sessions and signal_date anchor to NY, not the JVM's default zone"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 876abf58-fbe1-4ff6-a22b-465a1d0c28a0
---

When wiring a `Clock` bean for services that compare "today" against persisted dates (scan_runs.signal_date, trade entry_date, breadth dates), pin it to `Clock.system(ZoneId.of("America/New_York"))` — NOT `Clock.systemDefaultZone()`.

**Why:** Scan runs and trade entries anchor to NYSE/NASDAQ sessions via `currentMarketDate` (latest breadth date), which advances on NY market sessions. The dev machine runs CET; production may run UTC. At 22:00 NY time (post-close on May 19), CET = 03:00 May 20 — `LocalDate.now()` would report May 20 while the latest scan_run.signal_date is still May 19. A "today's scan" diagnostic would return empty.

**How to apply:** Any time-of-day-sensitive service that reads dates persisted by ScannerService / BacktestService / breadth ingestion must use the NY-pinned `Clock` bean. Read-only date math from market data (e.g., `currentMarketDate.maxOrNull()`) doesn't need a clock at all — prefer that pattern when feasible.
