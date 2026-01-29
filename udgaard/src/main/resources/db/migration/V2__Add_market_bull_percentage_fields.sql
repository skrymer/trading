-- Add market bull percentage fields to stock_quotes table
-- These fields track the market breadth bull percentage and its 10-day EMA

ALTER TABLE stock_quotes
ADD COLUMN market_bull_percentage DECIMAL(19, 4);

ALTER TABLE stock_quotes
ADD COLUMN market_bull_percentage_10ema DECIMAL(19, 4);
