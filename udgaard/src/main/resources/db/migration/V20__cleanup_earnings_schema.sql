-- Drop legacy duplicate columns and add a uniqueness invariant.
--
-- The `earnings` table was originally populated by an AlphaVantage importer that
-- wrote both snake_case (`reported_eps`) and run-together (`reportedeps`) variants
-- of the same data, plus a redundant `symbol` column alongside `stock_symbol`.
-- The table is empty in production (no ingestion path was ever wired) so the
-- drops are safe — no data migration needed.
--
-- The UNIQUE constraint guards the replace-all write semantics: one earnings row
-- per (symbol, fiscal date) is the invariant Midgaard already gives us, and the
-- constraint catches any future partial-failure or retry bug that would
-- otherwise silently produce duplicates.

ALTER TABLE earnings DROP COLUMN reportedeps;
ALTER TABLE earnings DROP COLUMN estimatedeps;
ALTER TABLE earnings DROP COLUMN symbol;

ALTER TABLE earnings
  ADD CONSTRAINT uq_earnings_symbol_fiscal_date
  UNIQUE (stock_symbol, fiscal_date_ending);
