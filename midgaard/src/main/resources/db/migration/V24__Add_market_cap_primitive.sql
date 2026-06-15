-- V24: the point-in-time market-cap primitive's stored inputs (ADR 0027). The cap is
-- `(raw_close / k(t)) × shares_outstanding`, so three new stored facts are needed:
--   * quotes.raw_close — the un-adjusted provider close. close_price is EODHD adjusted_close (split AND
--     dividend adjusted); an absolute-level product like market cap must read the raw close instead.
--     Nullable: bars stored before this re-store have none until the operator re-ingests.
--   * fundamentals.shares_outstanding — split-adjusted (current-basis) common shares, the share leg.
--     BIGINT: a share count is a whole number (the EODHD float artifact is rounded on ingest). Nullable.
--   * splits — the corporate actions k(t) is the product of (ratio over ex_date > t). ratio is
--     new-shares-per-old (4.0 for 4:1, 0.125 for 1:8 reverse). Replaced wholesale per symbol on ingest.
ALTER TABLE quotes ADD COLUMN raw_close DECIMAL(19,4);

ALTER TABLE fundamentals ADD COLUMN shares_outstanding BIGINT;

CREATE TABLE splits (
  symbol    VARCHAR(50)      NOT NULL,
  ex_date   DATE             NOT NULL,
  ratio     DOUBLE PRECISION NOT NULL,
  PRIMARY KEY (symbol, ex_date)
);

CREATE INDEX idx_splits_symbol ON splits(symbol);
