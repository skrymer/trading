-- Persist the bar the scanner matched on (signal_date) plus a verbatim record of
-- the per-condition evaluation (signal_snapshot) at add-trade time. Both nullable
-- so legacy rows and any future manual-add path stay representable.
--
-- The snapshot is immutable after creation. Re-deriving it later from /evaluate-date
-- is not safe — retroactive recomputes (sector breadth, indicator backfills, bar
-- deletions) can shift historical evaluation results. See docs/adr/0004.

ALTER TABLE scanner_trades
  ADD COLUMN signal_date DATE NULL,
  ADD COLUMN signal_snapshot JSONB NULL;

CREATE INDEX idx_scanner_trades_signal_date ON scanner_trades(signal_date);
