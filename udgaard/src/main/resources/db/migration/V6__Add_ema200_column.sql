-- Add 200 EMA column to stock_quotes table
ALTER TABLE stock_quotes ADD COLUMN close_price_ema200 DECIMAL(19, 4);
