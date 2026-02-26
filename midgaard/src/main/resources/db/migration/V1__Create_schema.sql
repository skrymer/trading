-- V1: Create Midgaard schema for reference data store
--
-- Tables:
--   quotes           - Enriched OHLCV + computed indicators
--   earnings          - Quarterly earnings data
--   symbols           - Symbol reference data
--   ingestion_status  - Tracks ingestion state per symbol

CREATE TABLE quotes (
  symbol              VARCHAR(50)   NOT NULL,
  quote_date          DATE          NOT NULL,
  open_price          DECIMAL(19,4) NOT NULL,
  high_price          DECIMAL(19,4) NOT NULL,
  low_price           DECIMAL(19,4) NOT NULL,
  close_price         DECIMAL(19,4) NOT NULL,
  volume              BIGINT        NOT NULL DEFAULT 0,
  atr                 DECIMAL(19,4),
  adx                 DECIMAL(19,4),
  ema_5               DECIMAL(19,4),
  ema_10              DECIMAL(19,4),
  ema_20              DECIMAL(19,4),
  ema_50              DECIMAL(19,4),
  ema_100             DECIMAL(19,4),
  ema_200             DECIMAL(19,4),
  donchian_upper_5    DECIMAL(19,4),
  indicator_source    VARCHAR(20) DEFAULT 'CALCULATED',
  PRIMARY KEY (symbol, quote_date)
);

CREATE TABLE earnings (
  symbol               VARCHAR(50)   NOT NULL,
  fiscal_date_ending   DATE          NOT NULL,
  reported_date        DATE,
  reported_eps         DECIMAL(19,4),
  estimated_eps        DECIMAL(19,4),
  surprise             DECIMAL(19,4),
  surprise_percentage  DECIMAL(19,4),
  report_time          VARCHAR(50),
  PRIMARY KEY (symbol, fiscal_date_ending)
);

CREATE TABLE symbols (
  symbol        VARCHAR(50) PRIMARY KEY,
  asset_type    VARCHAR(20) NOT NULL,
  sector        VARCHAR(50)
);

CREATE TABLE ingestion_status (
  symbol          VARCHAR(50) PRIMARY KEY,
  bar_count       INT         DEFAULT 0,
  last_bar_date   DATE,
  last_ingested   TIMESTAMP,
  status          VARCHAR(20) DEFAULT 'PENDING'
);

CREATE INDEX idx_quotes_symbol ON quotes(symbol);
CREATE INDEX idx_quotes_date ON quotes(quote_date);
CREATE INDEX idx_quotes_symbol_date ON quotes(symbol, quote_date);
CREATE INDEX idx_earnings_symbol ON earnings(symbol);
