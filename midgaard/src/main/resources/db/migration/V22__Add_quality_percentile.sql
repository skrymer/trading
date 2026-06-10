-- V22: Gross-profitability quality percentile (0-100), the L2 cross-sectional indicator of ADR 0019,
-- mirroring relative_strength_percentile (V18). A stock's gross-profitability GP/TA ranked
-- cross-sectionally against the whole universe on the same date (100 = more profitable-per-asset than
-- the entire qualifying universe). Like RS this is NOT computed per-symbol at ingest: symbol X's
-- percentile on date D depends on every other symbol's latest-known fundamental on D, so it is filled
-- by a separate operator-triggered cross-sectional pass after ingest. Nullable: null until the pass
-- runs, and for any (symbol, date) below the min-filings / min-peer / earliest-date floors (CONTEXT
-- *Gross-profitability quality percentile*). The read condition is fail-closed on null.
ALTER TABLE quotes ADD COLUMN quality_percentile DECIMAL(19,4);
