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

### Technical indicators

**Simple moving average (SMA)**:
The arithmetic mean of the last N daily closes. Distinct from the **EMA** family (`closePriceEMA5`…`ema200`), which weights recent bars more heavily — both moving-average families are maintained side by side because traders read each and the Minervini Trend Template is defined specifically on simple averages. Maintained at periods 50/150/200. Undefined (`null`) on any bar with fewer than N prior bars of history — never reported as 0.

**52-week high / 52-week low**:
The highest intraday **high** (resp. lowest intraday **low**) over the trailing **252 trading days** (≈ one trading year). An *intraday* extreme — the printed high/low of the bar, not the closing extreme — consistent with the existing Donchian upper band. Undefined (`null`) until 252 bars of history exist: a stock with fewer than 52 weeks of data has no 52-week high/low, and any condition referencing it fails for that stock. Distinct from the Donchian upper band, which is a short (5-bar) channel.

**Market-relative strength percentile**:
A stock's trailing **252-trading-day price return** (`close / close 252 bars ago − 1`) ranked **cross-sectionally against the whole market on the same date**, expressed as a **0–100 percentile** (100 = stronger than the entire qualifying universe). The peer set on a date is every symbol with a bar that day and ≥ 252 prior bars — **survivorship-free** (delisted names included), which is why it is computed in Midgaard (the only place that holds the full universe). `percentile = 100 · (peers below + ½ · peers equal) / N`; ties share a value. Undefined (`null`) — and any condition referencing it then fails for that stock — when the stock has < 252 bars (it is *excluded from the peer population entirely*, never ranked 0), when the qualifying peer count `N < 100` (too thin for a percentile to mean "vs the market"), or before **2000-01-01** (the survivorship-tilted pre-2000 universe, the same trust floor breadth uses). Price-only (no dividends), so high-yield names rank marginally low — benign for a growth filter. Distinct from three near-neighbours it must never be conflated with: **RSI** (Relative Strength *Index* — a single-symbol momentum oscillator, unrelated); the **TrailingReturn ranker** (which *orders* same-day candidates *within the strategy's subset* and imposes no absolute floor — highly correlated in signal, ρ≈0.9, but a different *mechanism*: an absolute market-wide gate vs an intra-subset ordering); and **breadth** (a market-wide aggregate, not a per-stock measure).
_Avoid_: RS rating, RS rank (the "rank"/"rating" labels imply IBD's 1–99 scaling we deliberately don't use), relative strength index, RSI.

### Strategy attribution

**Unassigned**:
A position with no chosen strategy — its `entryStrategy` is null, blank, or the `"Broker Import"` placeholder that broker-sync stamps on imported positions. Grouped under the literal name `"(Unassigned)"` in per-strategy breakdowns.

### Trade execution dates

**Signal date**:
The date of the OHLCV bar on which a strategy's entry conditions were evaluated as a match (`ScanResult.date` returned by `/api/scanner/scan`, or the `entryDate - entryDelayDays` bar in a backtest). The **decision bar** — distinct from the execution bar. Entry conditions are evaluated **only here**; they are NOT re-evaluated on the entry bar (see `entryDelayDays` below).

**Entry date**:
The date the trade was actually opened — in the broker for scanner trades, simulated as a fill for backtests. Equals `signalDate + entryDelayDays` trading days. Stored as `scanner_trades.entry_date` for scanner trades; recorded on `Trade.entryQuote.date` for backtest trades.

**`entryDelayDays`**:
The number of trading days between a strategy's signal bar and the actual execution. Models a real trader's workflow: a signal is observed at the close of one bar, an order is queued, and execution happens N bars later at that bar's close. Entry conditions are evaluated only at the signal bar — never re-evaluated at the entry bar. This is intentional: a live trader does not re-check the conditions before the order fills.

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

### Condition diagnostics

**Forward return**:
The N-trading-day price return of a *single entry signal*, anchored to the **fill bar** — `close[t+1+N] / close[t+1] − 1`, where `t` is the signal bar and the fill bar is `t + entryDelayDays` (default 1). Reported at N = 5, 10, 20. A per-signal diagnostic measure used only by the condition screen — explicitly **not** a portfolio or per-trade outcome, so it is never called *Edge* or *return* unqualified. Measuring from the signal bar instead of the fill bar would capture the signal→fill gap the strategy never earns — look-ahead that flatters breakout/momentum conditions.

**Signal→fill gap**:
The single-day return between a condition's signal bar and its fill bar — `close[t+1] / close[t] − 1`. Reported as its own column, never folded into *Forward return*. A condition whose apparent edge lives entirely in this gap is untradeable by construction.

**Lift**:
A condition's forward-return (or hit-rate) statistic **minus the universe all-bars baseline** for the same N, date range, and symbol universe. The headline alpha signal of the condition screen. Absolute forward return is uninformative for a high-firing condition because it converges to the universe base rate; lift is what isolates the condition's contribution. Distinct from *Edge*: lift is a pre-trade, exit-agnostic, per-signal measure; edge is a realised per-trade outcome.

**Firing rate**:
The fraction of evaluated bars on which a condition (or condition stack) matches. A *selectivity* measure, not a performance measure. ≥ 33% marks a condition as low-selectivity (its absolute forward-return stats ≈ the universe, so only *lift* is meaningful); ≥ 60% marks it as effectively a universe filter rather than a signal.

**Aliased Regime Sensitivity (ARS)**:
The failure mode the condition screen exists to catch: a condition whose *firing rate* stays stable across a parameter's immediate neighbourhood (P−1, P, P+1) while its forward-return *lift* changes sign non-monotonically across those neighbours. Signals that the parameter dimension is structurally inappropriate for the alpha hypothesis rather than merely brittle. The screen flags it when lift sign-flips across an adjacent pair, the swing exceeds 2× the date-clustered standard error, and firing rate stays within ±15% relative.

**Condition screen**:
A diagnostic, design-time pre-screen of a single entry condition (or AND/OR stack) run *before* it is wired into a strategy — `POST /api/conditions/screen`. Produces *Forward return* / *Lift* / *Firing rate* / *ARS* / regime stats but **no pass/fail verdict**: a condition that fails the screen is rejected without further work; one that passes is *not* validated and still goes through the full firewall. Restricted to the design-safe window (excludes Block C, the firewall's only true out-of-sample block) so that eyeballing its output cannot leak the final validation block.

### Strategy exploration funnel

**Candidate**:
A single strategy configuration under exploration — its entry/exit/ranker/sizer/maxPositions/entryDelayDays/seed, identified by a stable *config hash*. The unit the exploration funnel tracks from condition-screen through to a tradability verdict. Distinct from a *strategy* (the registered entry/exit class): a candidate is one concrete parameterisation of one being validated at a point in time.

**Config hash**:
The canonical fingerprint of a candidate — a hash over exactly the design-isolation freeze set (`entryStrategy`, `exitStrategy`, `ranker`, `rankerConfig`, `maxPositions`, `entryDelayDays`, `positionSizing`, `randomSeed`), the same fields G10 freezes. Deliberately **excludes** `startDate` / `endDate` (those vary per firewall block by design) so the same candidate keeps one hash across Block A / B / 25y. The spine of the anti-data-mining interlock.

**Candidate dossier**:
The durable, git-tracked, append-only **JSONL** journal of one candidate's passage through the funnel — one self-contained JSON event per line, the last well-formed line being the authoritative current state. The crash-recovery system of record: a mid-write crash truncates at most the final line, leaving all prior events intact. An in-flight backtest is a `FIRED … PENDING` event with no later matching `RECORD`, so a resume after a mid-run crash knows to check for a `backtestId` before re-firing. Written immediately on every transition, never batched.

**Dead config**:
A *config hash* that reached a terminal failing verdict (`REJECTED` or `NEAR_MISS`) in the firewall. Re-running a dead config — or a ±1-parameter neighbour of one (same neighbour classification G13 uses) — is *data-mining*, not validation, and the funnel hard-refuses it (no override). The only legitimate way forward is a redesigned *candidate* on a new *lineage* line. `PROVISIONAL` / `TRADABLE` are *settled* (advance forward, never re-run for a better verdict); `ERROR` is a methodology fault, not a verdict, and may be re-run once fixed.

**Lineage**:
The recorded ancestry linking a redesigned candidate to the dead candidate it replaces (`lineage_parent`). Registering a successor requires a recorded quant analysis judging the redesign *structurally distinct* from the corpse — this is what separates a legitimate redesign from a disguised re-run of a dead config.

## Flagged ambiguities

- "edge" vs "provenEdge" — the same concept under two names. Resolved: **Edge** is the canonical term; `provenEdge` is retained only as the existing field name for the portfolio-aggregate instance.
