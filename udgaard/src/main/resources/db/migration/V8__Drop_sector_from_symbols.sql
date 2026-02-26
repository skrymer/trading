-- V8: Drop sector_symbol from symbols table.
-- Sector is now stored on stocks.sector (populated from Midgaard during ingestion).

ALTER TABLE symbols DROP COLUMN sector_symbol;
