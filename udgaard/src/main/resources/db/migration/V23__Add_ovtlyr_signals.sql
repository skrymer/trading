-- Ovtlyr signal storage in Udgaard.
--
-- Ovtlyr buy/sell calls are sourced from Midgaard (reference data). They are mirrored
-- into Udgaard so the backtest path — which loads Stock aggregates from this database —
-- can evaluate the Ovtlyr signal entry/exit conditions without a per-stock remote call.
--
-- Identity PK, FK to stocks with ON DELETE CASCADE. Ovtlyr emits at most one call per
-- symbol per day; the UNIQUE(stock_symbol, signal_date) invariant guards the replace-all
-- write semantics against a partial-failure or retry bug that would otherwise silently
-- produce duplicate rows. Its composite btree also serves the symbol-prefix lookups every
-- read does (and the FK integrity check), so no separate index is needed. The CHECK keeps
-- the column honest: the domain has exactly two signal values, so a bad write fails loud
-- at the boundary rather than as a deferred crash when the row is read back.

CREATE TABLE ovtlyr_signals (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  stock_symbol VARCHAR(50) NOT NULL,
  signal_date DATE NOT NULL,
  signal VARCHAR(10) NOT NULL,
  FOREIGN KEY (stock_symbol) REFERENCES stocks(symbol) ON DELETE CASCADE,
  CONSTRAINT uq_ovtlyr_signals_symbol_date UNIQUE (stock_symbol, signal_date),
  CONSTRAINT chk_ovtlyr_signals_signal CHECK (signal IN ('BUY', 'SELL'))
);
