-- V18: Market-relative strength percentile (0-100).
-- A stock's trailing 252-bar price return ranked cross-sectionally against the whole
-- universe on the same date (100 = stronger than the entire qualifying universe).
-- Unlike every other indicator column this is NOT computed per-symbol at ingest: symbol
-- X's percentile on date D depends on every other symbol's return on D, so it is filled by
-- a separate cross-sectional pass after per-symbol ingest completes (ADR 0009).
-- Nullable: null until the cross-sectional pass runs, and for any (symbol, date) below the
-- min-history / min-peer / earliest-date floors.
ALTER TABLE quotes ADD COLUMN relative_strength_percentile DECIMAL(19,4);
