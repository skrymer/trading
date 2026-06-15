-- V32: the point-in-time market-cap primitive's stored inputs, mirrored from Midgaard (ADR 0027). The
-- cap is `(raw_close / k(t)) × shares_outstanding`, so three new stored facts are needed:
--   * stock_quotes.raw_close — the un-adjusted provider close. close_price is the adjusted_close (split
--     AND dividend adjusted); an absolute-level product like market cap must read the raw close instead.
--     Nullable: bars stored before this re-store have none until the operator re-ingests.
--   * fundamentals.shares_outstanding — split-adjusted (current-basis) common shares, the share leg.
--     BIGINT: a whole share count (the EODHD float artifact is rounded on ingest in Midgaard). Nullable.
--   * stock_splits — the corporate actions k(t) is the product of (ratio over ex_date > t). ratio is
--     new-shares-per-old (4.0 for 4:1, 0.125 for 1:8 reverse). Replaced wholesale per symbol on ingest.
ALTER TABLE stock_quotes ADD COLUMN raw_close DECIMAL(19, 4);

ALTER TABLE fundamentals ADD COLUMN shares_outstanding BIGINT;

CREATE TABLE stock_splits (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  stock_symbol VARCHAR(50) NOT NULL,
  ex_date DATE NOT NULL,
  ratio DOUBLE PRECISION NOT NULL,
  CONSTRAINT uq_stock_splits_symbol_ex_date UNIQUE (stock_symbol, ex_date),
  FOREIGN KEY (stock_symbol) REFERENCES stocks(symbol) ON DELETE CASCADE
);

CREATE INDEX idx_stock_splits_symbol ON stock_splits(stock_symbol);
