# The tradable universe is a frozen-threshold, point-in-time liquidity-gated population; a global universe change opens an epoch that supersedes verdicts without resetting the dead-config brake

The platform ran **one unfiltered ~5,098-name count-equal pool for three different jobs** — the tradable opportunity set, the market-structure measurement population (breadth / the leadership-concentration gap / the regime read-out), and the benchmark-factor series. `BacktestService.resolveSymbols` applied **no price / cap / dollar-volume / listing-age filter**: ~31% of names last-close under $10 and ~48% under $20 — exactly the sub-$10 / thin tail where the modelled round-trip *transaction cost* (10 bps, ADR-0007 boundary) is fiction. The three populations want different definitions (see CONTEXT.md *Trading universe*). This ADR records the **tradable-universe** decision and how a deliberate global change to it interacts with the ADR-0008 dead-config brake. Implemented in issue #173. The **$300M cap floor (Phase 2)** — deferred until the #174 point-in-time market-cap primitive (ADR 0027) shipped — is now **built and active**, opening a second universe epoch (§3).

## Decision

### 1. The tradable universe is a point-in-time, liquidity-gated population

A name is enterable on bar `D` only if, **as of `D`**:

- `asset_type = STOCK` (index/sector ETFs opt-in per config via `assetTypes`; leveraged/inverse ETFs never), **and**
- close ≥ **$5**, **and**
- trailing-20-bar **median** dollar-volume (`close × volume`) ≥ **$1M**, **and**
- ≥ **252 bars** of history, **and**
- **point-in-time market cap ≥ $300M** (the micro/small boundary; `Stock.marketCapAsOf(D)`, ADR 0027) — **fail-open**: a name whose cap is *unmeasurable* (no shares fundamental visible by `filing_date ≤ D`) is **not** excluded by this clause; only a cap *positively below* $300M excludes (Phase 2 — wired in #173, enabled by the #174 cap primitive).

- **Point-in-time per-bar**, not a static symbol-set filter — a name drifts in and out of tradability over its life exactly as it really did. A static snapshot filter would reintroduce the survivorship/lookahead bias the change exists to remove.
- **Default-on** via an overridable `applyLiquidityFilter` config flag (the `costBps` / `creditIdleCash` precedent), scoped to **all tradable-name selection** — backtest, walk-forward, firewall, condition/strategy-screen, **and the scanner** — but **never** the measurement universe.
- **Identical constants in backtest and scanner.** This is the load-bearing invariant: the backtest only predicts live results if `backtest universe ≡ live tradable universe`. A different live floor would validate strategies on a universe the operator cannot trade.
- **Thresholds are frozen pre-registered constants, not per-config knobs.** They are chosen from *real-world* rationale, not fit to any strategy's results: $5 (marginability/penny floor), $1M trailing-median dollar-volume (retail-book fill scale — calibrated against the live book: median position $3.2K, p95 $19K, i.e. <2% of a $1M/day name), 252-bar warmup, $300M (micro/small cap boundary, fail-open). A `$5M` constant exists as a documented **sensitivity-stress / future-growth** variant (used for A/B robustness only, never tuned to make a strategy pass). Revising the constants is a deliberate new *universe epoch* (§3), never a per-backtest tuning.
- **The cap floor fails open, by pre-registered design** (quant-adjudicated). The cap clause excludes a name only on *positive evidence* it is below $300M; an *unmeasurable* cap does not exclude. This is deliberate and candidate-blind — not a knob turned to admit names. Rationale: the share-count coverage gap is **era-correlated** — the null-cap rate among names already clearing the liquidity floor runs ~19% in 2003, decaying to ~0.2% by 2025 — a **vendor fundamentals-coverage artifact, not a micro-cap population**: the 2003 null-cap set is real large/mid-caps (the pre-2016 Alcoa, Allergan, Agrium, AnnTaylor — verified against the vendor EOD + fundamentals API, EOD volumes matching the stored bars exactly). Fail-*closed* would therefore make the tradable population **non-stationary across the firewall blocks** (Block A systematically smaller and differently-composed for a coverage reason unrelated to size), re-injecting the very survivorship/coverage bias this ADR exists to remove — into the *binding* validation layer. Fillability is already carried by the price/volume/age legs, so the cap floor is a secondary refinement that must not override the satisfied primary gate. (Contrast ADR-0019's quality percentile, which fails *closed* because there the null-prone quantity *is* the gate's whole purpose; here it is not.)
- **Ticker-reuse landmine** (the real hazard recorded). A ticker can be reused across distinct entities (e.g. `AA` = pre-2016 Alcoa vs the 2016 Alcoa Corp spin-off; the vendor's earliest `outstandingShares` can belong to the *successor* entity — 2014 for `AA`, not the 2003 bars' issuer). The point-in-time `filing_date ≤ D` gate **correctly** returns null for the pre-reuse era rather than borrowing the successor's shares — and shares must **never** be back-filled across a ticker-reuse boundary.

### 2. Measurement hardening is a no-op now; the $1 bad-print floor is deferred

- **Drop the `OR NULL` default-to-STOCK** in the breadth / gap queries. The OR-NULL audit found **0 null `asset_type`** rows → dropping it is a **provable no-op** → the frozen v2 regime read-out's breadth/gap inputs are byte-identical → **not** a v3 re-basing. A **source guard** (fail-closed on any *future* null) keeps the leak from reopening.
- The **$1 measurement bad-print floor is deferred.** It would drop ~503K stock-days from breadth/gap (a material re-base of frozen v2's inputs), which ADR-0023/0024 forbids as silent v3-by-iteration. It belongs to the future from-scratch regime-v3 re-pre-registration, which re-anchors the hardened universe holistically. v2 already uses the *median* gap leg, robust to those prints in the meantime. No dual-population read is needed.

### 3. A global universe change opens a "universe epoch" that supersedes verdicts without resetting the dead-config brake (extends ADR 0008)

The universe is **not** part of the `config_hash`. The ADR-0008 dead-config refusal exists to stop *optional stopping on the same population* (see a FAIL, turn a knob, roll again). A **one-time, global, pre-registered, candidate-blind** universe change motivated by cost-model fidelity is categorically **not** a knob-turn. Therefore:

- Prior firewall verdicts become legitimately **stale** — they were earned on a tradable population that is no longer the tradable population — but the ledger is **not erased**. Prior dossier lines stay (append-only); the change is a recorded **universe-epoch** event; superseded verdicts are marked `SUPERSEDED-BY-UNIVERSE-EPOCH`, never deleted.
- A dead config may be re-fired on the new universe **only** with a recorded **structural-interaction judgment** (its death cause plausibly interacts with the change) registered via the existing **lineage / `DISTINCT`** machinery — not a silent re-fire of the dead hash. A universe-orthogonal death earns no re-run.
- The brake **re-arms inside the epoch**: once a config has a verdict on the new universe, the tweak-and-re-run refusal applies again.
- **Guardrail against the loophole** ("change the universe to revive my corpse"): the reset is legitimate only because it is one-time / global / recorded / candidate-blind. A **per-candidate** universe knob is forbidden — it is the same data-mine in disguise.
- **`N_eff` accounting:** a re-validation observes a binding stitched-OOS Sharpe on the shared tape, so it **counts as a firewall trial** (+1 to `N_high`); as a within-lineage near-clone it adds ≈0 to the haircut `N_low` and **carries its prior trial cost** (no clean slate). Re-running everything is a *paid* look — so re-validate only configs with a recorded reason to flip.

## Consequences

- Default-on **voids existing firewall verdicts** (different trade population). Practical cost now is ~zero: the funnel is currently empty (no TRADABLE/PROVISIONAL configs). Re-validation is **lazy and documented** — record the epoch, re-validate a candidate only when next reconsidered; no automated "universe-version" stamping (single-user, no machinery).
- **The Phase-2 cap floor opens a *second* universe epoch** (§3) — it removes the provably-sub-$300M tail (~1–6% of liquid names, era-stable) from the tradable population. Same rule applies: prior verdicts on the post-#173 (price/liquidity/age-only) universe become stale-but-not-erased; the funnel is still empty so the practical cost is again ~zero. Recorded, not silently re-based ([[universe-epoch]]).
- The backtest becomes a **faithful predictor of live fills** — the 10 bps cost model is honest on the traded set.
- The cheap-name-driven corpses (DV1, MR3 have on-record `minPrice≥5` re-fires that *collapsed* their CAGR) get **more** dead under the gate — corroborating evidence the change is a *correctness fix*, not a corpse-revival lever.
- Routing dead premises to regimes (the **regime-specialist** program) is a **separate** future effort: only a *premise-derived* regime hypothesis validated with a *within-regime conditional null* is legitimate (reading the regime off a strategy's P&L decomposition is the forbidden Aliased Regime Sensitivity rescue), and any specialist outside CRISIS/THRUST is blocked on #168.

## Alternatives considered

- **Static symbol-set filter** (evaluate the universe once before the run) — rejected: any single snapshot reintroduces survivorship/lookahead bias, the exact thing the change removes.
- **Opt-in-off default** — rejected: leaves every default backtest trading fill-fiction, making the honest-cost goal hollow.
- **Thresholds as free config parameters** — rejected: invites fitting-to-outcomes; frozen constants + a documented stress variant instead.
- **Resetting the ADR-0008 dead-config ledger on a universe change** — rejected: that is the data-mining loophole; the change *supersedes* (records + marks stale) rather than *resets*.
- **Applying the $1 bad-print floor to measurement now** — rejected: it silently re-bases the frozen v2 series (ADR-0023/0024 violation); deferred to a from-scratch regime-v3 pre-registration.
