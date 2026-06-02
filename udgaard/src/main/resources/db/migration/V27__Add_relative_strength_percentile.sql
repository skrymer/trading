-- V27: Market-relative strength percentile (0-100), ingested from Midgaard.
-- A stock's trailing 252-bar price return ranked cross-sectionally against the whole
-- universe on the same date (100 = stronger than the entire qualifying universe).
-- Computed in Midgaard by a cross-sectional pass (ADR 0009), not per-symbol here.
-- Nullable: undefined below the min-history / min-peer / earliest-date floors — never
-- stored as 0, so a condition can treat "insufficient history" as a fail.
ALTER TABLE stock_quotes ADD COLUMN relative_strength_percentile DECIMAL(19, 4);
