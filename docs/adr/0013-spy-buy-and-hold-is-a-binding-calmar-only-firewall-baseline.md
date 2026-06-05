# SPY buy-and-hold is a binding Calmar-only firewall baseline

The refined firewall (`/validate-candidate`) adds a binding gate requiring a candidate to beat **buy-and-hold SPY on Calmar**, computed by the engine over the **same out-of-sample-stitched support** as the strategy. A long-only book that cannot beat just holding the index, risk-adjusted, is delivering beta — this gate makes that explicit. This ADR locks in the metric, the baseline construction, and the binding scope. It is the continuation of ADR 0005, which built the OOS-stitched aggregate and deferred benchmark comparison "until a caller needs it" — this gate is that caller.

## The choice

Per block, the engine computes a **stitched-OOS SPY buy-and-hold curve through the identical code path that produces the strategy's stitched-OOS curve** (same per-window OOS spans, same `dailyReturns`-then-concatenate, same wall-clock-span CAGR numerator, same gap-excluded synthetic curve for maxDD — ADR 0005). It then gates:

> **strategy block Calmar ≥ SPY block Calmar**, binding on **Block A, Block B, and the 25-year aggregate**; **informational on Block C**.

Sharpe (strategy and SPY) is **reported for diagnostics but is not gated**. The CAGR floor (25%, G1) and the absolute Calmar floor (1.5, G15 — ADR 0015) remain separate gates.

## Why Calmar-only and not Sharpe (or both)

The original plan gated on *both* Sharpe ≥ SPY and Calmar ≥ SPY. Sharpe was dropped because **`strategy Sharpe ≥ SPY Sharpe` is structurally biased against the exact strategy class we are building** — a part-in-cash long-only timer:

1. **Cash-dilution.** Sharpe is per-unit-of-time. A strategy flat much of the calendar earns 0% (in-engine) on those days, which enter the daily-return series as zeros that drag its mean. In a low-vol bull block (Block B 2014–2021), always-invested SPY sits at Sharpe ~1+; a timer mathematically cannot match a low-vol melt-up's per-day mean unless its in-market days are wildly superior. Gating on Sharpe-≥-SPY there is regime-fitting the gate to the bull tape.
2. **Sharpe punishes upside vol** symmetrically — wrong objective for a convex selection/timing strategy.
3. **The idle-cash bug compounds it.** The engine credits idle cash 0% (reality ≈ 3% SGOV); this understates the strategy's Sharpe *in proportion to time-in-cash*, biting hardest in exactly the bull blocks where the Sharpe gate is already hardest.

Calmar (CAGR / |maxDD|) suffers none of this: cash days are flat (they don't manufacture drawdown), so Calmar rewards the crash-avoidance a timer is *for*, and it is robust to the 0%-cash bug. Calmar is the metric that actually expresses the objective — grow capital while controlling the worst peak-to-trough.

## Why stitched-OOS SPY (b) and not full-span SPY (a)

The strategy's stitched curve structurally **omits IS-period drawdowns** — a crash landing in an IS window never touches the strategy's maxDD because the strategy has no equity path on those dates. Benchmarking that against full-span SPY (whose maxDD *includes* those crashes) **manufactures fake defensive alpha proportional to how many crashes happen to fall in IS gaps** — an artifact of the window schedule, not of strategy timing, and a false-PASS generator. Stitching SPY identically keeps both legs on the same trading-day support: the only difference is *what was held*, which is exactly the "did my selection/timing beat just holding the index on these days" question. **Symmetry of convention, not per-side correctness, is what validates the gate** — so the existing ADR-0005 CAGR-wall-clock / maxDD-gap-excluded asymmetry must be applied identically to SPY, never "fixed" on one side only.

## Why the 25y aggregate binds, and why it must be restitched

Calmar is **non-additive**. maxDD is a path extremum, so the worst peak-to-trough on the pooled 25y curve can **straddle the A/B seam** (peak late in A, trough early in B) — an excursion neither block's maxDD can see; aggregate maxDD ⩾ max(block maxDDs), often strictly. Aggregate PASS/FAIL therefore genuinely diverges from per-block in both directions. This makes the aggregate a **distinct test on a distinct support**, not double-counting — *provided* aggregate Calmar is computed on the genuinely pooled-and-restitched OOS curve (one maxDD over the full concatenated support), **never derived as an average/min of the sub-block Calmars** (which would carry zero new information and make this redundant). Requiring the gate on A *and* B *and* aggregate is a **conjunction**, which lowers the false-positive rate — the opposite of multiple-testing inflation, and the intended behaviour for a hard firewall.

## Why Block C stays informational

Block C (2021–2025) is off the binding path as a data-snooping control — it caps fail-and-retry holdout iterations against recent data. That rationale is a **property of the block, not the gate**, and applies to this gate equally; "beat recent SPY" is if anything *more* susceptible to recent-regime snooping. The C Calmar-vs-SPY number is reported as a directional sanity read, never binding.

## Guardrails (eligibility, not auto-fail)

A block's Calmar gate is **INCONCLUSIVE** (does not bind, does not auto-fail) when:
- the stitched OOS return series has **< 60 trading days** (reuse `RiskMetricsService.MIN_OVERLAP_DAYS_FOR_CORRELATION`), or
- the stitched **maxDD < ~2–3%** — a trivially tiny denominator yields an explosive Calmar that would "beat" SPY as a small-sample artifact (mirrors the lottery / 1–2-window reject heuristic).

An INCONCLUSIVE block never auto-fails the firewall, but an INCONCLUSIVE **aggregate** over 25 years is surfaced loudly — it signals something upstream is degenerate.

## Consequences

- Engine-computed, not skill-computed: the SPY baseline and the per-block Calmar comparison live in `WalkForwardService` / `RiskMetricsService` (testable Kotlin), exposed as response fields; `/validate-candidate` reads the verdict. Consistent with "keep calculations in the backend" and the standing intent to move the funnel pipeline into the engine.
- A regression test must construct a drawdown that straddles the A/B seam and assert `aggregate maxDD > max(per-block maxDD)` — the executable proof that the aggregate is a genuine restitch and not silently reduced to a function of sub-block Calmars by a later refactor.
- This gate is **SPY-relative**; it is complementary to, not a substitute for, any absolute Calmar floor (e.g. Calmar ≥ 1.0 from the B4 risk-adjusted reframe). Relative adapts to regime ("beat the passive alternative"); absolute sets a minimum tradable quality. They catch different failures.

## What this does NOT decide

- **The idle-cash 0%→~3% fix.** Deliberately deferred to a tracked follow-up. Calmar tolerates the bug (cash days don't draw down), so the gate is valid today; the fix only sharpens NEAR_MISS resolution.
- **The absolute Calmar floor / B4 risk-adjusted reframe** (Rec 6) — its own change, grilled separately.
- **Non-SPY benchmarks.** The construction is benchmark-agnostic; SPY is the only baseline wired today.
