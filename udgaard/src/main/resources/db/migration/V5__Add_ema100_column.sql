-- Add 100 EMA column to stock_quotes table
ALTER TABLE stock_quotes ADD COLUMN close_price_ema100 DECIMAL(19, 4);
