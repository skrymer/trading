-- V28: Make Udgaard's universe a pure projection of Midgaard's catalogue (ADR 0011).
-- Udgaard no longer keeps its own symbol catalogue; the trading universe is the ingested
-- `stocks` table, reconciled from Midgaard at ingestion. asset_type — the one field the
-- stocks-derived universe would otherwise lose — moves onto `stocks`, backfilled in place
-- from the soon-to-be-dropped `symbols` table so existing rows keep their type without a
-- re-ingest. Midgaard is the single source of truth, so the catalogue table is dropped.
ALTER TABLE stocks ADD COLUMN asset_type VARCHAR(20);

UPDATE stocks s
SET asset_type = sym.asset_type
FROM symbols sym
WHERE s.symbol = sym.symbol;

DROP TABLE symbols;
