-- Persistence backing for BacktestResultStore. Replaces the in-memory Caffeine cache
-- (maximumSize=1, expireAfterAccess=1h) so multiple backtestIds can coexist and survive
-- backend restarts. Stated retention intent is "day or two max" — cleanup is manual via
-- the BacktestReportController batch-delete endpoint, not a scheduled job.
--
-- The `report` JSONB column holds the full BacktestReport serialized via Jackson.
-- Scalar metadata + summary columns are extracted at write time so the listing endpoint
-- can render without parsing the 2-3 MB blob per row.
CREATE TABLE backtest_reports (
  backtest_id           UUID PRIMARY KEY,
  entry_strategy_name   VARCHAR(255) NOT NULL,
  exit_strategy_name    VARCHAR(255) NOT NULL,
  start_date            DATE NOT NULL,
  end_date              DATE NOT NULL,
  total_trades          INT NOT NULL,
  edge                  DOUBLE PRECISION NOT NULL,
  cagr                  DOUBLE PRECISION,
  max_drawdown_pct      DOUBLE PRECISION,
  sharpe_ratio          DOUBLE PRECISION,
  report                JSONB NOT NULL,
  created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_backtest_reports_created_at ON backtest_reports(created_at DESC);
