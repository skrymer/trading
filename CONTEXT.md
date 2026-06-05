# Trading Platform

The domain language of the stock-trading backtesting and portfolio-tracking platform (Udgaard backend, Asgaard frontend).

## Language

### Performance metrics

**Edge**:
The expected return per trade for a set of closed trades — `(winRate · avgWin%) − (lossRate · |avgLoss%|)`, where rates are fractions and avg win/loss are per-trade percentage returns. **Net of transaction cost** (see *Cost boundary*) — a realised per-trade outcome, distinct from the cost-free *Lift*.
_Avoid_: expectancy, expected value
_Note_: the portfolio-aggregate instance of this metric is historically named `provenEdge` in code (`PositionStats.provenEdge`); per-strategy instances are named `edge` (`StrategyClosedStats.edge`, `StrategyBreakdownStats.edge`). Same formula, different scope.

**Win rate**:
The fraction of closed trades with positive realised P&L. *Realised* means **net of transaction cost** (see *Cost boundary*) — a trade whose gross gain is consumed by friction is a loss, not a win.

**Profit factor**:
Gross profit divided by absolute gross loss for a set of closed trades, where each trade's P&L is **net of transaction cost** (see *Cost boundary*). Here "gross" distinguishes winning-trade P&L from losing-trade P&L — it does *not* mean pre-cost. Undefined (null) when there are no losing trades — never reported as zero or infinity.

**SPY buy-and-hold baseline**:
The firewall's passive-alternative benchmark: a candidate must beat *just holding SPY* on **Calmar** (CAGR / |maxDD|), else its return is index beta rather than alpha. **Calmar-only** — Sharpe is reported but deliberately *not* gated, because a part-in-cash long-only timer is structurally penalised on Sharpe in low-vol bull regimes (cash days drag its per-day mean while always-invested SPY sits at Sharpe ~1+), whereas Calmar is neutral to sitting in cash (flat days don't draw down) and rewards the crash-avoidance the strategy exists for. The baseline is computed over the **same out-of-sample-stitched support as the strategy** (SPY held only across the strategy's OOS windows, flat in the IS gaps), through the identical stitch/CAGR/maxDD path — comparing full-span SPY against an OOS-stitched strategy curve would manufacture fake defensive alpha from crashes that merely happen to fall in IS gaps. **Binding on Block A, Block B, and the 25-year aggregate** (the aggregate restitched over the full pooled OOS support — it catches A/B-seam-straddling drawdowns the sub-blocks cannot); **informational on Block C**. A block is **INCONCLUSIVE** (does not bind, does not auto-fail) below 60 OOS trading days or below a ~2–3% stitched maxDD. SPY-*relative*, so complementary to any absolute Calmar floor. See ADR 0013 and ADR 0005.

### Trade execution cost

**Transaction cost (`costBps`)**:
The modelled **round-trip** cost of executing a backtested trade — commission plus slippage — expressed in basis points of traded notional (1 bp = 0.01% = 0.0001). A single backtest-config field (`StrategyConfigDto` → `BacktestContext`), **default 10** (10 bps round-trip, stress at 20). It bundles two real frictions because, for a liquid US large/mid-cap universe, slippage dominates and a fixed/share commission is a rounding error; the bundle *understates* cost for sub-$10 or thin names, which is the trigger to revisit with a per-share commission floor. Charged **split evenly across the two fills** — half on the entry leg, half on the exit leg, each on that leg's own notional: `perLegRate = (costBps / 2) / 10000`; `legCost = shares · legPrice · perLegRate`. Because cost-per-share is independent of share count, it is netted **once** into the per-share `Trade.profit` at trade close in `BacktestService` — `netProfitPerShare = grossProfitPerShare − entryPrice·perLegRate − exitPrice·perLegRate` — from which every downstream metric (CAGR/Sharpe/Calmar/Monte Carlo *and* Edge/Win rate/Profit factor) inherits the net figure. The default is a defensible literature constant; the live portfolio's `Execution` fills and `totalCommissions` can later replace it with a per-book empirical value (commission bps from `totalCommissions / notional`; slippage bps from the *Signal snapshot* close vs the actual fill price).

**Cost boundary**:
The line between metrics that are **net of transaction cost** and those that are **gross / cost-free by construction**, drawn on *realised-trade-outcome vs signal-predictiveness*. **Net:** *Edge*, *Win rate*, *Profit factor* (Performance metrics) and the capital-curve metrics (CAGR/Sharpe/Sortino/Calmar/max drawdown, Monte Carlo) — these are outcomes of *executed* trades that paid two fills. **Gross:** *Forward return*, *Lift*, *Signal→fill gap* (Condition diagnostics) — pre-trade, exit-agnostic, pre-sizing signal measures with no fill to charge (the gross-side contract of ADR 0007). This is the same dividing line the glossary already draws between *Edge* (a realised per-trade outcome) and *Lift* (a pre-trade per-signal measure). The "edge eaten by friction" failure mode — where average per-trade edge is on the order of `costBps` — is caught at the first **net** gate downstream of the condition screen (net Edge / net CAGR), **never** by netting cost into the screen. The per-trade **gross-minus-net Edge spread** is reported as an early-warning scalar: it *is* the average round-trip cost in return terms, so comparing it to gross Edge reveals the danger zone before the net number is read.

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

### Market regimes

**Market regime**:
A label for the prevailing market-structure state, defined as a property of the **market** and **never fitted to any strategy's good/bad years** — doing so is Aliased Regime Sensitivity on a single realisation (the failure mode the firewall exists to catch). Regime is derived independently of any strategy's P&L, pre-registered, and validated out-of-sample; its history is trustworthy only from **2000-01-01** (the breadth trust floor). The regime read-out classifies on **three cheap orthogonal axes** — **breadth** (level + slope), the **leadership-concentration gap** (below), and **realized volatility** (SPY trailing 20-day, low/high band) — *not* on a magnitude-only "dispersion" measure (a single magnitude is sign-blind and cannot separate thrust from narrow-leadership). The expensive full-universe cross-sectional return-dispersion pass is **ruled out** (collinear with these three; changes no stance). Five canonical labels are kept strictly distinct — the phrase "narrow-leadership chop / crisis" is a **conflation to avoid**, because a component native to one is not native to the others.
_Avoid_: a single "dispersion" signal as the discriminator; treating "narrow-leadership", "chop", and "crisis" as one regime.

**Leadership-concentration gap**:
The signed **20-trading-day rolling-return gap between the cap-weighted index and an equal-weighted proxy** — `SPY return(20d) − equal-weight return(20d)`. The equal-weight side is the **mean daily return of the STOCK-type universe**, computed in the *same daily pass* as breadth (cheap; full history to 2000, definition-stable), cross-checked against **SPY − RSP** where RSP exists (2003+). Its **sign is the participation tell** and is the *only* clean thrust-vs-narrow discriminator: **gap < 0** (equal-weight leads) ⇒ broad participation (small/mid-caps surge — a thrust); **gap > 0** (cap-weight leads) ⇒ a few mega-caps carry the index (narrow-leadership). Structural, not coincidental: EW-beats-CW *requires* broad participation, CW-beats-EW *requires* concentration. A single-day gap is noise — the regime tell is the persistent multi-week drift. Distinct from a **per-name concentration ceiling** (a position-level guard inside a specialist — see *Coverage* / the grind specialist build): the gap is a *market-level regime gate* (when a specialist may deploy); a per-name ceiling controls *what* it holds. A scalar market gate cannot see which names a selector holds — conflating the two is the "thinning-not-selecting" error.

**Broad-rally thrust** (a.k.a. **broad risk-on**):
Index up, **breadth high and rising**, leadership-concentration **gap < 0** (equal-weight leads), bases firing off a washout. The native regime of the shelved Minervini breakout component (a recovery-trend specialist).

**Low-dispersion grind**:
Index up, **breadth positive-but-flat**, **gap ≈ 0**, **low realized vol**, no washout (so no bases for the breakout to fire on — it sits flat here). The native regime of the to-build **low-volatility / quality grind specialist** (Track #1). Distinct from broad-rally thrust (which has firing breadth + gap < 0) — the volatility axis is what separates a genuine grind from a quiet-but-deteriorating early-narrow drift.

**Narrow-leadership**:
Index **up** while **breadth is weak or falling** and the leadership-concentration **gap > 0** (cap-weight leads — a few mega-caps carry the index): 2H-2021, 2023, 2024. The tape in which **both** dead premise families (the breakout and the leveraged-long-ETF attempts) *participate-and-lose*. _(The cross-sectional RS-momentum "twin" is **hypothesised** to die here too, but that was deprecated on theory with no run and is unverified — it may even survive, since narrow leadership can feed momentum persistence; see `strategy_exploration/STRATEGY_LEDGER.md` §B Class 4.)_ Distinct from **crisis** (the index is still rising) and from **chop** (which has no sustained index direction).

**Chop**:
Rangebound, trendless whipsaw with no sustained direction. Distinct from narrow-leadership (which has a rising index).

**Crisis** (a.k.a. **correlated risk-off drawdown**):
A broad, correlated decline across names — everything falls together (2008, 2020). Defended by **standing in cash** (the read-out routes every long specialist flat — there is no bear *component*; see *Crisis defense*). Explicitly **not** the same regime as narrow-leadership: a component native to crisis would not be native to an index-up/breadth-down tape, and fusing the two is the trap that lets a "long the narrowing leadership" premise masquerade as a defender when it actually carries the breakout's exposure.

**Regime specialist**:
A strategy component whose edge is real only in its **one** native *market regime* and which is meant to sit flat (in cash) in every other regime. The unit of the regime-conditional portfolio: the book is a blend of specialists, deployed by a transition layer (the regime read-out) that reads the current regime and picks which to run. Distinct from a general-purpose strategy expected to trade across all tapes.
_Status (2026-06-03):_ the **regime-conditional portfolio program is abandoned** (`strategy_exploration/REGIME_CONDITIONAL_BATTLE_PLAN.md` post-mortem) — a long-only engine yields no viable second specialist, so this term and *Coverage* / *Crisis defense* below are **historical**. The *market-regime labels* and the *leadership-concentration gap* above remain live market-structure vocabulary.

**Crisis defense** (the dissolved "defender"):
In a **long-only engine** (no short, no negative quantity, P&L = `exit − entry`), there is **no crisis-specialist component** — defense is an **allocation state, not a strategy with alpha**. Every positive-edge bear instrument is disqualified: inverse 3× ETFs are the already-capped thin-ETF family *plus* daily-rebalance decay drag; bonds/gold have no instrument before ~2002 (no coverage for the 2000–02 crisis window) and re-trigger the thin-palette problem; pure cash has no edge (and is exactly what `C-PARTICIPATE` exists to reject). Therefore crisis defense = the **regime read-out routing every long specialist to cash**. Portfolio-blend G6 (book survives 2008 + 2020) is a *survival* gate satisfied by that shared crisis cash — it rewards *not losing*, never *winning* — **contingent on the read-out's crisis classifier being sharper than `spyTrendUp`** (which stayed deployed-and-bleeding in narrow-down tape). _Avoid_: framing a bear/inverse-ETF "defender component"; treating "≥2 components" as a defense requirement (it is a *coverage* requirement — see below).

**Coverage** (the reason for ≥2 components):
Why the portfolio wants more than one specialist: **diversification across risk-on sub-regimes**, *not* an attack/defend pairing. The expressible components are all **long risk-on specialists**; they each correctly share cash in *crisis* (mandatory, credited by G6) but should be active in **different non-crisis up-tapes** so the book is rarely all-cash when opportunity exists. This is what `C-CASHOVERLAP` measures — coincidence of stand-aside windows **in non-crisis windows only** (crisis cash is exempt). The diversification axis is "which risk-on tape each specialist harvests," and a second component earns its slot only by demonstrably trading *and winning* in tape where the first one's regime is off or marginal — otherwise it collapses into the first one's beta.

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
The recorded ancestry linking a redesigned candidate to the dead candidate it replaces (`lineage_parent`). Registering a successor requires a recorded quant analysis judging the redesign *structurally distinct* from the corpse — this is what separates a legitimate redesign from a disguised re-run of a dead config. One *candidate dossier* file is one lineage — which is also the **cluster boundary** for the effective trial count (below).

**Firewall trial**:
A distinct *config hash* that reached the firewall — i.e. has a `/validate-candidate` `RECORD` event with *any* verdict (`TRADABLE` / `PROVISIONAL` / `NEAR_MISS` / `REJECTED` / `INCONCLUSIVE` — a rejected run still consumed a look at the binding stitched-OOS Sharpe). A `FIRED … PENDING` with no `RECORD` is **not** a trial (no Sharpe was observed); a *condition-screen* or *strategy-screen* survivor is **not** a trial (it competed on a different, cheaper metric, not the firewall Sharpe). The population whose size feeds the *Deflated-Sharpe flag* — counted **globally** across every candidate (shared 2000–2025 data makes the whole search one multiple-testing experiment; a different premise does not start a fresh count).

**Effective trial count (`N_eff`)**:
The number of *independent* firewall trials, used by the *Deflated-Sharpe flag* instead of a flat trial count — flat-counting over-deflates because ±1-neighbour / exit-tweak / sizer-swap variants of one survivor are near-clones, not independent shots. Trials are clustered by *lineage* (one dossier file = one cluster); reported as a band `[N_low, N_high]` where `N_high` = the integer cluster count and `N_low` = a correlation-haircut `1 + (k−1)(1−ρ̄)` collapsing within-lineage near-clones toward independence. The *structurally-distinct* gate on a new lineage thus doubles as the trial-counter: a new lineage = +1; a variant within a lineage ≈ +0.

**Deflated-Sharpe flag**:
A firewall-stage **reported flag** (never an auto-reject) quantifying how much of a survivor's stitched-OOS Sharpe is plausibly *best-of-search* inflation — the Deflated Sharpe Ratio (Bailey-López de Prado) of the candidate's Sharpe against the expected maximum under the null over `N_eff` trials. Computed in the engine (`RiskMetricsService.deflatedSharpe`, a pure search-agnostic function taking `N_eff` and cross-trial Sharpe variance as parameters) and assembled by the state-machine. Reported across the `N_eff` band with the **itemised lineage list always published** (*hidden N is the sin, not uncertain N*): **amber** if the most-deflated (`N_high`) endpoint pushes probabilistic-Sharpe below 0.95, **red** if even `N_low` does. Deliberately a flag, not a binding gate — a human-pruned tree search has no honest scalar N, so the value is *directionally informative but quantitatively soft*. **Augments**, does not replace, the binding screen-stage G5 variant-count gate (different stage, different role). See ADR 0014 and ADR 0008.

## Flagged ambiguities

- "edge" vs "provenEdge" — the same concept under two names. Resolved: **Edge** is the canonical term; `provenEdge` is retained only as the existing field name for the portfolio-aggregate instance.
