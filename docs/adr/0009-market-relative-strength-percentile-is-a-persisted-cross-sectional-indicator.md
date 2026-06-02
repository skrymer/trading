# Market-relative strength percentile is a persisted cross-sectional indicator, not a backtest-context object

The Minervini Trend Template needs a true **relative-strength gate** — "is this stock stronger than ~70% of the market right now?" — evaluated per-stock during signal screening. We decided to implement it as a **persisted indicator** (`StockQuote.relativeStrengthPercentile: Double?`), computed in **Midgaard as a distinct, manually-triggered cross-sectional pass** (decoupled from ingest — see the amendment below), and read by a trivial single-bar condition (`RelativeStrengthPercentileCondition`, `null ⇒ fail`) — exactly mirroring the Phase 1 SMA / 52-week pipeline. We explicitly rejected precomputing per-date rank distributions into `BacktestContext`.

See CONTEXT.md *Market-relative strength percentile* for the term; this ADR records the architectural shape and the quant decisions behind it.

## Why a persisted indicator, not a `BacktestContext` precompute

An `EntryCondition.evaluate(stock, quote, context)` runs in the backtest's **Pass 1 (`collectEntrySignals`), batched and low-memory — the full universe is never co-resident**, and Pass 2 loads only signal-matching stocks. So at the moment the gate fires there is **no same-day peer set in memory** to rank against. The two options were:

- **Precompute per-date RS distributions into `BacktestContext`** (the original framing). Rejected for two reasons: it forces a separate full-universe pass purely to build the distributions — re-introducing exactly the memory cost the two-pass engine exists to avoid (the backend OOMs on concurrent full-universe work) — and it would peg a stock's RS to *the strategy's symbol subset*, which is the **wrong peer set**: a stock's relative strength must not change because you backtested a smaller watchlist.
- **Persist the answer per row** (chosen). The cross-sectional computation happens once, against the correct survivorship-free universe, and each `(symbol, date)` carries its own percentile. The condition is then a single-bar read with no context dependency, surviving the two-pass memory model untouched, and point-in-time correctness is handled once at compute time (and verifiable bit-exact, as the 52-week fields were).

## Why Midgaard, and why a *separate* pass

Midgaard is the reference-data service and the **only** place that holds the complete survivorship-free universe (including delisted symbols) — the correct peer set. But this is **the first cross-symbol computation in either service**: every existing indicator (`calculateAllSMAs`, `calculate52WeekHigh/Low`, `calculateATR`, `calculateADX`) takes a single symbol's `List<RawBar>`, and ingestion is per-symbol, independently parallelized (per-symbol retry, delisted-skip, sector-immutability all assume independence). RS rank cannot live inside that loop: symbol X's percentile on date D depends on every *other* symbol's return on D. It is therefore modelled as a **distinct cross-sectional stage**, separate from the per-symbol loop — adding or correcting one symbol retroactively shifts every peer's percentile on shared dates. This coupling is the price of correctness and is called out here so it is not "fixed" back into the per-symbol loop later.

### Amendment — the recompute is manually triggered, not auto-fired post-ingest

Originally the pass auto-ran at the end of every bulk ingest. That was reverted: the whole-universe sort, run on Postgres's 4 MB default `work_mem`, spilled ~1.4 GB to disk and ran 33 minutes on the full PRD universe (~17.5 M rows) — unacceptable appended to an EODHD re-ingest that itself takes ~10 minutes. The recompute is now **operator-triggered only** (the "Recompute Relative Strength" button / `POST /api/ingestion/recompute-relative-strength`), decoupled from ingestion, and sped up by raising `work_mem` transaction-locally for the sort. Two consequences, both intended: (1) a re-ingest leaves `relative_strength_percentile` **null** for the new rows until the operator runs the recompute — tolerated because the gate condition is **fail-closed on null** (it simply admits no entries on un-ranked bars rather than ranking against a stale cross-section); (2) the daily incremental path was dropped entirely (see issue #81, retiring `/update/all`). This keeps a fast re-ingest fast and puts the multi-minute cross-sectional cost under explicit operator control.

## The quant decisions (signed off by quant-analyst)

- **Metric:** single trailing **252-bar** price return, **no skip** (`close[D]/close[D−252] − 1`), guarded by `close[D−252] > 0`. 252 matches the `TrailingReturnRanker`'s far anchor exactly. The IBD-style quarter-weighted blend (0.4/0.2/0.2/0.2) is the more faithful Minervini construction but is a *different indicator with its own parameter surface* — deferred as a clean follow-up candidate, not built in v1.
- **Percentile estimator:** the **midpoint / mean-rank** plotting position `100 · (count< + ½·count==) / N` — *not* the ad-hoc `(strictly below)/(N−1)` first drafted, which is biased and endpoint-pinned at the top tail (where the 70 threshold and the strongest names live) and distorts selectivity in thin periods. The midpoint estimator is symmetric, tie-stable, and unbiased at both tails and at small N.
- **Definedness:** a symbol with < 252 prior bars (or `close[D−252] ≤ 0`) is **excluded from the peer population entirely**, never ranked 0 — ranking an undefined metric as 0 would inject phantom weakest-names and make the gate progressively easier to pass during IPO waves. Null indicator ⇒ condition **fails closed**.
- **Peer-count floor `N_min = 100`:** the percentile's sampling error is ≈ `50/√N` points near the median, so N=100 resolves a 70-cut to ≈ ±5 pts — the floor below which "top 30% of *the market*" degrades to "top 30% of *a small club*." Paired with a **hard 2000-01-01 calendar floor** because the pre-2000 universe is survivorship-tilted *even when N is mechanically large* (composition bias N cannot detect — the same breadth-trust-floor reasoning). Both guards are required; neither subsumes the other.
- **Look-ahead profile:** the percentile for date D uses only bars ≤ D (`close[D−252]`, `close[D]`, and peers' metrics computed through D) — **no forward leakage**. It shares the exact `close[D]` decision-bar semantics of every other close-based condition; the same-bar-fill optimism under `entryDelayDays = 0` is the platform's existing, documented perfect-fill idealization, not an RS-specific leak.

## Consequences — when this gate actually earns its place (read before stacking)

The gate is **not distinct from the `TrailingReturn` ranker as a *signal*** — both are ~12-month momentum on the same universe, ρ≈0.9, sharing the D−252 anchor and differing only over the most recent ~21 bars. Do **not** justify it on signal-distinctness; a reviewer will correctly call that redundant. It is distinct as a **mechanism**:

> The ranker is a *relative, intra-subset ordering* with no absolute floor — it always fills top-N of whatever fired that day, even if all candidates are weak *vs the broader market*. The percentile gate is an *absolute, market-wide threshold* applied per-symbol independent of the day's pool. They bind in disjoint situations:
> - **The gate binds (earns its place)** when the day's candidate pool is small relative to `maxPositions`, **or** the ranker is non-momentum (sector / volatility / distance-from-EMA / random), **or** `maxPositions` is loose. There the ranker imposes no strength floor and would fill names strong-within-today's-signals but weak-vs-market; the gate vetoes exactly those (the narrow-breadth / late-cycle failure mode Minervini's RS rule targets).
> - **The gate degenerates to a near-no-op** when `ranker = TrailingReturn(252,21)` **and** `maxPositions` is tight enough that the qualifying pool routinely exceeds the cap — then the ranker's top-N already sits in the high-momentum cross-section and the gate vetoes almost nothing.

Therefore the gate's defensible home is as a **ranker-agnostic absolute RS floor**: it keeps the Stage-2 market-relative-strength guarantee even if the ordering policy is later swapped. The ablation that justifies shipping it is **gate + the strategy's *actual* ranker vs that-ranker-alone** (plus the empirical **veto rate** — fraction of ranker-selected entries the gate rejects), **never** gate+TrailingReturn vs TrailingReturn-alone, which will falsely show ~zero lift. If the veto rate is ≈ 0% across Block A for the chosen config, the gate is genuinely redundant *for that config* and should not ship with it. Run the `/condition-screen` Jaccard-overlap check against `TrailingReturn`-selected sets before stacking ([the ablation-redundancy convention](../../CONTEXT.md)).

## Two verification gates before this ships (not yet checked — data does not exist until computed)

1. **`min(N)` over 2000–2002 must clear 100** on every trading day in the survivorship-free, ≥252-bar universe. If some early dates fall short, those dates produce a null (fail-closed) gate for *all* symbols — acceptable but must be *known*, not discovered mid-backtest. One query settles it once the metric is computed.
2. **PIT/bit-exactness on PRD**, as was done for the SMA / 52-week fields, before any firewall run relies on the field.

## Rejected / deferred alternatives

- **`BacktestContext` per-date precompute** — wrong peer set (strategy subset) + reintroduces the full-universe memory cost; see above.
- **Compute in Udgaard from a lightweight `(symbol, date, close)` table** — keeps Midgaard per-symbol, but duplicates the indicator pipeline outside the reference-data service that owns the universe.
- **IBD quarter-weighted blend** — higher fidelity, deferred to a v2 candidate with its own firewall pass.
- **`(strictly below)/(N−1)` percentile** and **IBD 1–99 scale** — rejected on estimator bias and scale-convention grounds respectively (see quant decisions).
