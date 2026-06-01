-- V26: Simple moving averages (50/150/200) and the 52-week high/low channel,
-- ingested from Midgaard alongside the existing EMA/Donchian indicators.
-- Nullable: undefined (no value) on bars without the required trailing history
-- (200 bars for sma_200, 252 for the 52-week channel) — never stored as 0, so a
-- condition can treat "insufficient history" as a fail rather than a spurious 0.0.
ALTER TABLE stock_quotes ADD COLUMN sma_50       DECIMAL(19, 4);
ALTER TABLE stock_quotes ADD COLUMN sma_150      DECIMAL(19, 4);
ALTER TABLE stock_quotes ADD COLUMN sma_200      DECIMAL(19, 4);
ALTER TABLE stock_quotes ADD COLUMN high_52_week DECIMAL(19, 4);
ALTER TABLE stock_quotes ADD COLUMN low_52_week  DECIMAL(19, 4);
