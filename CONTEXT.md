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

### Scanner operations

**Scan run**:
A user-triggered invocation of the entry-candidate scanner — `POST /api/scanner/scan` → `ScannerService.scan()`. Evaluates the configured entry strategy against every stock's latest bar and returns the matched-symbol cohort. Distinct from `check-exits` (which evaluates exit conditions on open positions) and `validate-entries` (which re-confirms a small set of candidates against live quotes pre-execution). The operating cadence is one scan run per trading day, executed after US market close.

**Live book**:
The set of currently-open scanner trades (`scanner_trades.status = 'OPEN'`). The trader's actual exposure at a point in time.

**Signal flow**:
The chronological stream of matched-symbol cohorts emitted by scan runs over a window. Distinct from the live book — signal flow is what the scanner *offered*; live book is what the trader *took*.

### External signals

**Ovtlyr signal**:
A third-party buy/sell call from ovtlyr.com for a given symbol on a given date — the `final_calls` field of ovtlyr's payload, valued `BUY`, `SELL`, or absent. An *event*: a stored row exists only on a day a call fired (sparse). Always written *with* the `Ovtlyr` qualifier to keep it distinct from the scanner `Signal *` family above: a *Signal date* / *Signal snapshot* / *Signal flow* concern the platform's own strategy-entry signals, whereas an Ovtlyr signal is an external vendor's directional opinion ingested as reference data. Stored in Midgaard.

**Current Ovtlyr signal**:
The derived *state* — the most recent Ovtlyr signal at or before a given date. Whereas an Ovtlyr signal is an event on its own date, the current Ovtlyr signal is BUY (or SELL) on *every* bar between a BUY call and the next SELL, including bars with no stored row. Null before a symbol's first ever call. This is what strategy conditions ask ("does this stock currently have a buy signal?"), not the raw event.

## Flagged ambiguities

- "edge" vs "provenEdge" — the same concept under two names. Resolved: **Edge** is the canonical term; `provenEdge` is retained only as the existing field name for the portfolio-aggregate instance.
