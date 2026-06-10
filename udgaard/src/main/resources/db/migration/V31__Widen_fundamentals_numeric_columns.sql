-- V31: Widen the fundamentals line-item columns from DECIMAL(19,4) (~1e15 ceiling) to DECIMAL(38,4),
-- mirroring Midgaard V23. EODHD occasionally emits bad-print values far above any real figure (e.g.
-- ASND/LYG cost_of_revenue ≈ 1e16); at DECIMAL(19,4) such a value overflowed and aborted the symbol's
-- batch insert in StockJooqRepository, losing even its good fields (gross_profit / total_assets).
-- DECIMAL(38,4) accommodates any real or bad-print value; the quality metric is rank-based so a
-- magnitude outlier occupies one slot and distorts nothing (ADR 0019).
ALTER TABLE fundamentals
  ALTER COLUMN gross_profit              TYPE DECIMAL(38,4),
  ALTER COLUMN cost_of_revenue           TYPE DECIMAL(38,4),
  ALTER COLUMN total_revenue             TYPE DECIMAL(38,4),
  ALTER COLUMN operating_income          TYPE DECIMAL(38,4),
  ALTER COLUMN net_income                TYPE DECIMAL(38,4),
  ALTER COLUMN total_assets              TYPE DECIMAL(38,4),
  ALTER COLUMN total_stockholder_equity  TYPE DECIMAL(38,4),
  ALTER COLUMN total_current_assets      TYPE DECIMAL(38,4),
  ALTER COLUMN total_current_liabilities TYPE DECIMAL(38,4);
