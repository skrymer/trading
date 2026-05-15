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

## Flagged ambiguities

- "edge" vs "provenEdge" — the same concept under two names. Resolved: **Edge** is the canonical term; `provenEdge` is retained only as the existing field name for the portfolio-aggregate instance.
