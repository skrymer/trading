-- Store the BacktestReport blob gzip-compressed in a BYTEA column instead of raw JSONB.
--
-- Postgres caps a single jsonb value's internal array size at ~256 MB. A high-candidate
-- strategy (one that records an EntryDecisionContext for every missed entry across a large
-- universe and date range) produces a report that overflows that cap, failing the insert.
-- gzip compresses the report JSON ~10x, so even a multi-hundred-MB report lands well under
-- any limit. The scalar summary columns (edge, cagr, sharpe_ratio, ...) are still extracted
-- for the listing endpoint; the report blob itself is only ever read back whole, so dropping
-- JSONB queryability on it costs nothing.
--
-- Existing rows hold raw-JSON JSONB the new read path cannot decode, and backtest reports are
-- ephemeral (retention "a day or two", manual cleanup) — so the table is cleared as part of
-- the format change rather than migrated.

DELETE FROM backtest_reports;
ALTER TABLE backtest_reports DROP COLUMN report;
ALTER TABLE backtest_reports ADD COLUMN report BYTEA NOT NULL;
