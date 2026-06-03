# Udgaard has no symbol catalogue; the universe is the ingested `stocks` table, reconciled from Midgaard at ingestion

Udgaard previously kept its own `symbols` table, seeded only by Flyway migrations (`V2`, `V18`, `V25`) and never synced, so it drifted from Midgaard's live catalogue (4,220 vs 4,996) and made `refresh/all-stocks` repeatedly fetch invalid tickers Midgaard had purged (`No data from provider` log noise). We **deleted the `symbols` table** and made Midgaard the single source of truth: udgaard no longer stores a catalogue at all. The trading universe is now simply `SELECT symbol, asset_type FROM stocks` — the symbols udgaard actually has data for — and reconciliation happens at ingestion: the full-universe refresh (`POST /refresh/all-stocks`) fetches Midgaard's catalogue, prunes any `stocks` row not in it (cascading to quotes/blocks/earnings), then ingests the live list. `asset_type` moved onto the `stocks` table (backfilled in the drop migration, stamped at ingestion) because it was the one field the stocks-derived universe would otherwise lose.

## Considered Options

- **Bidirectional sync service with soft-delete + protected set** (the original issue #82 proposal): a `SymbolSyncService` reconciling two catalogues, an `active` flag, and an allow-list of "udgaard-intentional" symbols. Rejected: every symbol udgaard needs (SPY, sector ETFs, the leveraged baskets) already exists in Midgaard, so the protected set is empty and the second catalogue is pure redundancy that can only drift.
- **Runtime fetch from Midgaard** (udgaard queries `/api/symbols`, cached, whenever it needs the universe): rejected because it couples every backtest/scanner/screen run to Midgaard being reachable, with no offline fallback.
- **Chosen: no catalogue, universe = ingested `stocks`, prune at ingestion.** Self-cleaning (a ticker that never ingests successfully never enters the universe), no runtime coupling outside ingestion, single source of truth.

## Consequences

- Ingestion is the only place udgaard reads Midgaard's symbol catalogue (`MidgaardClient.getAllSymbols()`). The prune is guarded by a `> 0` check so a Midgaard outage / empty response never wipes the universe; the prune attaches only to the full-universe refresh, never the targeted `refresh/stocks`.
- `positions` / `scanner_trades` reference symbol as a bare string (no FK), so a prune can orphan such a row in principle — left unguarded, since the prune set is invalid drifted-dead tickers that never carry positions/trades.
- New symbols Midgaard adds appear in udgaard only after they ingest; UI symbol search and asset-type-filtered universes reflect only ingested symbols. Acceptable — you can only backtest/scan a symbol you have data for.
- The seed migrations (`V2`/`V18`/`V25`) remain in history but are inert; no future symbol seeding in udgaard.
