# Trading Platform

The domain language of the stock-trading backtesting and portfolio-tracking platform (Udgaard backend, Asgaard frontend).

## Language

### Performance metrics

**Edge**:
The expected return per trade for a set of closed trades — `(winRate · avgWin%) − (lossRate · |avgLoss%|)`, where rates are fractions and avg win/loss are per-trade percentage returns.
_Avoid_: expectancy, expected value
_Note_: the portfolio-aggregate instance of this metric is historically named `provenEdge` in code (`PositionStats.provenEdge`); per-strategy instances are named `edge` (`StrategyClosedStats.edge`, `StrategyBreakdownStats.edge`). Same formula, different scope.

**Win rate**:
The fraction of closed trades with positive realised P&L.

**Profit factor**:
Gross profit divided by absolute gross loss for a set of closed trades. Undefined (null) when there are no losing trades — never reported as zero or infinity.

### Strategy attribution

**Unassigned**:
A position with no chosen strategy — its `entryStrategy` is null, blank, or the `"Broker Import"` placeholder that broker-sync stamps on imported positions. Grouped under the literal name `"(Unassigned)"` in per-strategy breakdowns.

### Scanner trade dates

**Signal date**:
The date of the OHLCV bar on which a strategy's entry conditions were evaluated as a match (`ScanResult.date` returned by `/api/scanner/scan`). Distinct from the entry date — it's the *decision* bar, not the *execution* bar.

**Entry date**:
The date the trade was actually opened in the broker (i.e. the user filled). Typically `signalDate + N` trading days where `N` reflects the user's entryDelayDays convention (often 1). Stored as `scanner_trades.entry_date`.

**Signal snapshot**:
The immutable record of `EntrySignalDetails` (per-condition pass/fail + actual values) for the signal bar, captured at the moment the trade is added. Persisted verbatim — never recomputed on read, because the underlying inputs (sector breadth, Donchian high, volume averages) can drift retroactively as those tables are recomputed. The snapshot is the only mechanism that survives such drift.

## Flagged ambiguities

- "edge" vs "provenEdge" — the same concept under two names. Resolved: **Edge** is the canonical term; `provenEdge` is retained only as the existing field name for the portfolio-aggregate instance.
