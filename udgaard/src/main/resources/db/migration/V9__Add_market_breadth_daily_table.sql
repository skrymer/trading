-- Index to speed up market breadth aggregate query
CREATE INDEX idx_stock_quotes_date_trend ON stock_quotes(quote_date, trend);
