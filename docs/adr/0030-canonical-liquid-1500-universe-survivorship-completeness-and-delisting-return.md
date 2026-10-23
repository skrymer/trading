# The backtest and live universe is one canonical rule-based "Liquid 1500", with survivorship completeness reported on every result and a pre-registered delisting return

**Amends ADR 0026.** The operator asked to backtest on the S&P Composite 1500 with delisted names included. With the Sharadar rebuild (ADR 0029) the data is survivorship-free, but the *universe definition* must be too, and it must be the same in the backtest and the live scanner (ADR 0026's backtest ≡ live invariant). The quant evaluated five options: the true S&P 1500, the S&P 500 from Sharadar `SP500`, the tradable gate alone over the full candidate set, a rule-based top-N, and multiple selectable universes. The decision is one canonical rule-based universe matching skrymerBT's (skrymerBT ADR 0002).

## Decision

1. **Liquid 1500 is the single verdict universe.**
   - **Selection:** at each month-end, take every *candidate-set* common stock with raw close > $5, point-in-time market cap > $300M and at least 252 bars, ranked by trailing-21-day average dollar volume. The **top 1500** are members from the next session.
   - **Where it applies:** every screen, firewall and walk-forward run, and the live scanner.
   - **It doesn't replace the per-bar tradable gate.** The ADR 0026 gate still applies on top: a member that falls below $5 in the middle of a month can't be entered.
   - **Membership gates entries only.** Falling out of the 1500 never forces an exit, which would be fake rebalance turnover.
2. **Why a rank cut rather than absolute floors.** A fixed count of 1500 keeps the universe the same size in every era. The nominal-dollar floors admit more names every year, so Block A's population would be smaller and differently composed than Block C's, which is the cross-block non-stationarity ADR 0026 warns about. The 1500th name's dollar volume is far above the $1M floor, so a 10 bps cost and a fill at the close are believable for every member. It also excludes the thin tail. The 2026-06-16 filter A/B found most of one strategy's apparent edge in the sub-$5 / sub-$1M names the ADR 0026 gate removes, and the rank cut sits well above that.
3. **No index constituents as the verdict universe.**
   - **The S&P 500 / 1500 are rejected.** They are committee-chosen and large-cap-tilted, and their admission rules contaminate tests: S&P's profitability requirement builds a quality tilt into membership, and names tend to be added after a run-up, which builds in momentum.
   - **The S&P 400/600 historical constituents would need a new paid vendor.** They add nothing over Liquid 1500.
   - **Diagnostics only:** Sharadar `SP500`, if subscribed, is allowed as a diagnostic or benchmark. A today's-constituents list is never used.
4. **Exactly one canonical universe.** Choosing a universe per candidate is the forking-paths knob ADR 0026 forbids. Frozen named variants (the full-gated universe, the `STRESS_5M` floor, an S&P 500 membership) exist **only** as diagnostics in the non-adjudicating strategy assessment (ADR 0022) and never as a verdict basis. If a second verdict universe is ever introduced:
   - the universe id is part of `config_hash` and is pre-registered before Block A;
   - any other universe counts as a single-step G13 neighbour, so a death on one is a death on all;
   - each universe tried adds +1 to `N_eff`.
5. **Survivorship completeness is reported, not assumed.**
   - **On every backtest and walk-forward response:** a `survivorship` block with members resolved / present / dropped, the delisted-dropout share, the delisted names' share of trades, and the count and P&L of `DELISTED` exits.
   - **Acceptance-gate validators (ADR 0029):**
     - *Ingest completeness:* the `TICKERS` candidate set matches what was ingested, year by year, with no unexplained gaps.
     - *Delisting coherence:* each delisted name's last bar matches its `ACTIONS` delisting date. A missing stretch of more than ~5 sessions, inside the series or before the delisting date, is a data gap, not a delisting.
6. **Delisting return, pre-registered.** A position still open when its stock stops trading exits as follows:
   - **Performance-related delisting** (bankruptcy, liquidation, regulatory): the last close × (1 − 0.30), Shumway's NYSE/AMEX estimate.
   - **Merger / acquisition:** the last close.
   - **Unknown reason with a last close under $5, or a last close ≤ 50% of its trailing 252-bar high:** treated as performance-related.
   - **This is one global constant**, never tuned per strategy.
   - **Every result is also reported without the haircut and at −100%.** A verdict that flips between them is a red flag.
   - **Open check:** the mapping is frozen only after `ACTIONS` delisting reason codes are verified against the subscription.
7. **The cap floor's fail-open rule is re-decided on SF1.** Fail-open (ADR 0026) was justified by EODHD's era-correlated shares-coverage gap. Before carrying it forward, measure the null-cap rate among liquid names in 2000–2005 on SF1 `ARQ` shares. If it is under 1.5%, the cap leg becomes fail-closed; otherwise fail-open is retained. The threshold is fixed before measuring.
8. **The measurement universe is unchanged.** Breadth, the leadership gap, the regime read-out, and the RS and quality percentiles stay on the full candidate set (CONTEXT.md *Measurement universe*), never on the Liquid 1500.

## Consequences

- **Memory is solved as a side effect.** A backtest loads only names that were members during its window plus warmup, an estimated 4–6k names over 26 years (to verify) versus about 18k, so no streaming refactor is needed.
- **The rebuild is a vendor epoch and a universe epoch at once.** Every prior verdict is superseded, and the dead-config brake is not reset (ADR 0026 §3).
- **Expect sparser signals.** Breakout-type signals may fire roughly 40% less often (an estimate, to verify) than on the full gated set. Check firing rate per block at the first screen. The universe is never widened to chase power.
- **An honest universe may push strategies below the 25% CAGR floor.** That is information, not a reason to loosen it.
- **Cross-platform check.** The same trade on the same universe should appear in skrymerBT, which gives a free consistency check.
