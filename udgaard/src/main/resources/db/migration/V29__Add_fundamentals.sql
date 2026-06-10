-- V29: Point-in-time quarterly fundamentals mirrored from Midgaard (ADR 0019 L1), the raw data the
-- gross-profitability quality signal reads. Mirrors the earnings table: one row per
-- (stock_symbol, fiscal_date_ending), FK to stocks, replaced wholesale on each ingest. filing_date is
-- the point-in-time visibility key — a backtest may only see a row on a trading date >= filing_date.
-- All line items nullable (a section may be absent or a field omitted).
CREATE TABLE fundamentals (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  stock_symbol VARCHAR(50) NOT NULL,
  fiscal_date_ending DATE NOT NULL,
  filing_date DATE,
  gross_profit DECIMAL(19, 4),
  cost_of_revenue DECIMAL(19, 4),
  total_revenue DECIMAL(19, 4),
  operating_income DECIMAL(19, 4),
  net_income DECIMAL(19, 4),
  total_assets DECIMAL(19, 4),
  total_stockholder_equity DECIMAL(19, 4),
  total_current_assets DECIMAL(19, 4),
  total_current_liabilities DECIMAL(19, 4),
  CONSTRAINT uq_fundamentals_symbol_fiscal_date UNIQUE (stock_symbol, fiscal_date_ending),
  FOREIGN KEY (stock_symbol) REFERENCES stocks(symbol) ON DELETE CASCADE
);

CREATE INDEX idx_fundamentals_stock_symbol ON fundamentals(stock_symbol);
