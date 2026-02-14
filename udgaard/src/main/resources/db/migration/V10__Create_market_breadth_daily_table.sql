-- Pre-calculated market breadth: % of all stocks in uptrend per trading day
CREATE TABLE market_breadth_daily (
  quote_date DATE PRIMARY KEY,
  breadth_percent DECIMAL(19, 4) NOT NULL
);

-- Populate from existing stock data
INSERT INTO market_breadth_daily (quote_date, breadth_percent)
SELECT quote_date,
       COUNT(CASE WHEN trend = 'Uptrend' THEN 1 END) * 100.0 / COUNT(*)
FROM stock_quotes
GROUP BY quote_date;
