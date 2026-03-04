-- Add option premium and delta to scanner_trades
ALTER TABLE scanner_trades ADD COLUMN option_price DECIMAL(10, 2);
ALTER TABLE scanner_trades ADD COLUMN delta DECIMAL(5, 4);
