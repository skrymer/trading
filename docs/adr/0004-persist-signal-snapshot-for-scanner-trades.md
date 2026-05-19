# Persist signal snapshot for scanner trades

Every scanner trade persists an immutable JSONB snapshot of the per-condition evaluation that produced its entry decision. The snapshot is captured server-side at `POST /api/scanner/trades` time and never regenerated on read.

## The problem

Closed trades carry an `entryStrategyName` (e.g. `"Vcp"`) and an `entry_date`, but no record of *what the strategy actually saw* on the bar that triggered the match. Re-running `entryStrategy.test(...)` against historical data later does not reproduce the original evaluation, because the inputs the conditions read drift retroactively over time:

- Sector breadth aggregates change when stocks are re-classified or universe membership changes (commit `97958d3` Normalize sectors, 2026-05-04).
- Stored indicators (Donchian high, EMAs, ATR) shift when bars are removed retroactively (volume=0 filter, 2026-04-28; phantom-holiday bar drop, commit `7b2200b`).
- Provider data revisions can quietly rewrite historical OHLCV.

Concrete case: the `ADSE` trade added 2026-04-02 via a Vcp scanner match shows no Vcp signal anywhere in 2026 when re-evaluated today. Two retroactive recomputes shifted sector-breadth and rolling-window inputs enough to flip the borderline conditions on 2026-04-01 from passing to failing. Audit trail destroyed.

The audit gap also defeats a softer use case: trade review. A user closing a position cannot answer "was the original setup valid by my rules?" by re-running evaluation — the answer they get is current-data truth, not decision-time truth.

## The decision

Persist a snapshot of the full `EntrySignalDetails` (strategy name, list of condition results with thresholds + actual values + pass/fail, overall `allConditionsMet`) on every `scanner_trades` row that was created from a scanner match. Once written, the snapshot is read-only.

Two new columns on `scanner_trades`:

```sql
ALTER TABLE scanner_trades
  ADD COLUMN signal_date     DATE  NULL,
  ADD COLUMN signal_snapshot JSONB NULL;
```

`signal_date` is the OHLCV bar that the scanner evaluated and matched. `signal_snapshot` is the JSONB-serialised `EntrySignalDetails` captured for that bar.

### Where the snapshot is generated

Server-side at add-trade time, via the existing `StrategySignalService.evaluateConditionsForDate(stock, signalDate, entryStrategyName)`. The scan-to-add latency in the typical UX is seconds-to-minutes, so the database state at recompute time matches the state the scanner saw at scan time. The server-recomputed snapshot is the same data the client holds in its `ScanResult.entrySignalDetails`, just round-tripped to avoid a client-trust surface.

The snapshot is **never** regenerated on read. Any re-evaluation after the fact is — by construction — vulnerable to the data-drift bug this ADR exists to fix.

### What survives across mutations

- `closeTrade` and `updateTrade` touch only exit and notes fields; the snapshot is naturally preserved.
- `rollTrade` deletes the existing row and creates a new one. The new row **carries the original `signalSnapshot` and `signalDate` forward**: a roll is position-management mechanics (decaying contract, fresh strike/expiry), not a new strategy decision. Destroying the snapshot on roll would erase the entry-decision audit trail.

### What about legacy rows

Pre-V21 rows store `NULL` on both columns. We do **not** backfill from `/evaluate-date`: today's recompute on legacy `signalDate` values is precisely the drifted truth we are trying not to record. NULL is informative ("we did not capture this") and stays correct under future drift. The UI renders NULL as `"Snapshot unavailable (pre-V21)"`.

### Behaviour under strategy mutation

The snapshot is honest at the moment of capture and immune to later strategy changes:

- **Parameter retune** (e.g. `priceNearDonchianHigh(3.0)` → `(4.0)`): old snapshots keep `threshold: "≤3.0%"`. New snapshots use `≤4.0%`. Each row remains a faithful record of the strategy at decision time. Cohort-level analyses (group by `entryStrategyName`) that span the change get a mixed-definition cohort — by convention we bump the strategy name (`Vcp` → `VcpV2`) on substantive changes, following the `VcpCd3` precedent, so the cohort naturally splits.
- **Condition added/removed/renamed**: the persisted condition list is variable-arity and uses class-simple-name strings, so old snapshots survive class renames and length changes intact. UI iterates with `v-for` — already arity-agnostic.
- **Strategy removed from registry**: `evaluateConditionsForDate` returns null, the trade is added with `signalSnapshot=null`, and `entryStrategyName` is retained for historical attribution.

## Alternatives considered

| Alternative | Why rejected |
|---|---|
| Client sends snapshot blob in POST body | Same outcome as server recompute given the short scan-to-add window, but introduces a client-trust surface and a new shape of API contract for a benefit we don't need given the single-user app context |
| Recompute the snapshot on every read | Defeats the entire purpose — read-time recompute *is* the drift bug |
| Store `signal_date` only, no snapshot | Solves the audit-existence gap ("was this scanner-validated?") but not the replay gap ("show me what conditions passed"). Adding the date alone leaves the ADSE-class question unanswerable |
| Backfill legacy rows from current `/evaluate-date` | Stores today's drifted truth as if it were scan-time truth — exactly the lie that motivated the ADR. Worse than NULL |
| Sidecar table `scanner_trade_signal_snapshots` | Snapshot is always co-loaded with the trade; no query pattern reads conditions independently of their parent row; JSONB on the parent row is simpler and matches the precedent set by `backtest_reports.report_json` |
| Per-condition normalised rows (queryable per-condition) | Hypothetical query patterns ("trades where Donchian was failing") don't exist today; introducing the schema cost ahead of demand is premature |

## Consequences

- The snapshot is the source of truth for "what did the scanner see when this trade was added." `/api/stocks/{symbol}/signals` and `/api/stocks/{symbol}/evaluate-date/{date}` continue to report current-data evaluations and intentionally diverge from old snapshots whenever the inputs have drifted. The divergence is the feature.
- Snapshot column size is bounded (~1–2 KB per row, dominated by ~8 condition results). List responses grow proportionally — negligible at the scanner's trade-count scale.
- The pattern mirrors the backtest engine's `EntryDecisionContext` (capital-allocation snapshot at trade selection): both freeze decision-time state in the row for post-hoc analysis. They capture different facets — capital vs. condition — and are not merged because they serve different aggregates and different read paths.
