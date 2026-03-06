-- Add listing/delisting dates to stocks for survivorship bias filtering.
-- Stocks without recent quotes are considered delisted.

ALTER TABLE stocks ADD COLUMN listing_date DATE;
ALTER TABLE stocks ADD COLUMN delisting_date DATE;

-- Backfill listing_date from earliest quote
UPDATE stocks SET listing_date = (
  SELECT MIN(quote_date) FROM stock_quotes WHERE stock_symbol = stocks.symbol
);

-- Backfill delisting_date from latest quote if stale (>90 days old)
UPDATE stocks SET delisting_date = (
  SELECT MAX(quote_date) FROM stock_quotes WHERE stock_symbol = stocks.symbol
) WHERE (
  SELECT MAX(quote_date) FROM stock_quotes WHERE stock_symbol = stocks.symbol
) < CURRENT_DATE - INTERVAL '90 days';
