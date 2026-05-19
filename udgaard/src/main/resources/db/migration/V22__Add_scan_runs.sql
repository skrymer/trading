-- Persists daily entry-candidate scan runs for the cohort-divergence diagnostic.
-- A scan run is one user-triggered invocation of ScannerService.scan() against the
-- production entry strategy. The trader runs one scan per trading day after US market
-- close. Multiple scans with the same (signal_date, strategy, ranker) tuple upsert —
-- last write wins — because the trader may re-scan within a session and only the
-- most recent state is operationally interesting.
--
-- The matched_symbols JSONB column holds the lean ScanResult per match
-- (symbol, sectorSymbol, closePrice, atr, rankScore) — enough to compute the
-- diagnostic + future concentration-risk follow-ups without duplicating the
-- per-condition EntrySignalDetails that scanner_trades.signal_snapshot already stores
-- per row.

CREATE TABLE scan_runs (
  id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  signal_date          DATE NOT NULL,
  scan_timestamp       TIMESTAMP NOT NULL,
  entry_strategy_name  VARCHAR(100) NOT NULL,
  exit_strategy_name   VARCHAR(100) NOT NULL,
  ranker_name          VARCHAR(100) NOT NULL,
  total_stocks_scanned INT NOT NULL,
  match_count          INT NOT NULL,
  matched_symbols      JSONB NOT NULL,
  CONSTRAINT uq_scan_runs_signal_date_config
    UNIQUE (signal_date, entry_strategy_name, exit_strategy_name, ranker_name)
);

CREATE INDEX idx_scan_runs_signal_date ON scan_runs(signal_date DESC);
