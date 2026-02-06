-- Drop legacy unused EMA columns from stock_quotes table
-- These columns (ema10, ema20, ema50, ema200) are no longer used
-- The application now uses close_price_ema5, close_price_ema10, close_price_ema20,
-- close_price_ema50, close_price_ema100, and close_price_ema200 instead

ALTER TABLE stock_quotes DROP COLUMN IF EXISTS ema10;
ALTER TABLE stock_quotes DROP COLUMN IF EXISTS ema20;
ALTER TABLE stock_quotes DROP COLUMN IF EXISTS ema50;
ALTER TABLE stock_quotes DROP COLUMN IF EXISTS ema200;
