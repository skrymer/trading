-- V21: Point-in-time quarterly financial-statement line items (ADR 0019 L1), mirroring `earnings`.
-- One row per (symbol, fiscal_date_ending). `filing_date` is the visibility key — a consumer may only
-- see a row on a trading date >= filing_date, never on fiscal_date_ending (CONTEXT *Point-in-time
-- fundamentals*). All line items nullable: the section may be absent (ETFs file no statements) or the
-- provider may omit a field. Curated to the income-statement + balance-sheet items the quality metric
-- needs; sourced from the same EODHD /fundamentals call that already feeds earnings (no extra quota).
CREATE TABLE fundamentals (
  symbol                     VARCHAR(50)   NOT NULL,
  fiscal_date_ending         DATE          NOT NULL,
  filing_date                DATE,
  gross_profit               DECIMAL(19,4),
  cost_of_revenue            DECIMAL(19,4),
  total_revenue              DECIMAL(19,4),
  operating_income           DECIMAL(19,4),
  net_income                 DECIMAL(19,4),
  total_assets               DECIMAL(19,4),
  total_stockholder_equity   DECIMAL(19,4),
  total_current_assets       DECIMAL(19,4),
  total_current_liabilities  DECIMAL(19,4),
  PRIMARY KEY (symbol, fiscal_date_ending)
);

CREATE INDEX idx_fundamentals_symbol ON fundamentals(symbol);
