-- V16: Simple moving averages (50/150/200) and the 52-week high/low channel.
-- Computed alongside the existing EMA/Donchian indicators at full-history ingest.
-- Nullable: a bar with fewer than the required trailing window of history (200 bars
-- for sma_200, 252 bars for the 52-week channel) has no value — never stored as 0.
-- The 52-week high/low are intraday extremes (highest high / lowest low) over the
-- trailing 252 trading days, distinct from the short donchian_upper_5 channel.
ALTER TABLE quotes ADD COLUMN sma_50       DECIMAL(19,4);
ALTER TABLE quotes ADD COLUMN sma_150      DECIMAL(19,4);
ALTER TABLE quotes ADD COLUMN sma_200      DECIMAL(19,4);
ALTER TABLE quotes ADD COLUMN high_52_week DECIMAL(19,4);
ALTER TABLE quotes ADD COLUMN low_52_week  DECIMAL(19,4);
