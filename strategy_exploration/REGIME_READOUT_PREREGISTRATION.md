# Regime read-out classifier — FROZEN pre-registration v1

> **⛔ SHELVED 2026-06-03 — the regime-conditional PROGRAM was abandoned** (operator
> decision; see `REGIME_CONDITIONAL_BATTLE_PLAN.md` post-mortem). This classifier was never
> implemented. It is **preserved intact** as a clean, quant-signed spec — revive ONLY if a
> market-defined regime read-out is ever wanted as a standalone **research / backtest
> regime-attribution instrument**, decoupled from any portfolio-deployment ambition. Do not
> build it expecting it to resurrect the dead portfolio thesis.

_Frozen 2026-06-03 · quant-signed (APPROVED-WITH-CORRECTIONS, all 8 deltas applied) · **do not tune after reading any attribution/validation run** (`feedback_parameter_fragility_must_be_verified`, anti-snooping rule = `COMPONENT_FIREWALL_PLAN.md` §7)._

A **market-defined** regime classifier (a property of the market, never fitted to a
strategy's P&L). One crisp classifier consumed two ways: a **live operator read-out** and
the **backtest regime-attribution** window classifier (subsumes the binary `COMPONENT_FIREWALL_PLAN.md`
§5 deployed-vs-cash classifier — see §E). All statistics are **causal** (only bars ≤ t);
no full-sample quantity is used anywhere. Builds on the three axes in
`REGIME_CONDITIONAL_BATTLE_PLAN.md` ("The read-out's three axes") and the
*Leadership-concentration gap* term in `CONTEXT.md`.

## A. Axis definitions (per trading day t, causal)

- **breadthEma10(t)** — 10-day EMA of **market** `breadthPercent` (`MarketBreadthService`; not sector). EMA seeded by the SMA of the first 10 bars (washed out — falls inside the discarded burn-in, §H).
- **breadthSlope(t), breadthSlopeSE(t)** — OLS slope of breadthEma10 regressed on the **integer trading-day index 0…19** over the trailing **20 trading days** (slope unit = breadth-% per trading day), and that coefficient's **regression standard error** (a genuine inferential SE — name kept).
- **breadthLevelSD(t)** — **sample standard deviation (n−1)** of `breadthPercent` over the trailing 20 trading days. *Standard deviation, NOT std/√n* — this is the "typical wander" of the level, used only for the hysteresis margin.
- **gap(t)** — `SPY 20-day return − EW 20-day return`. EW return = `∏_{i=t−19..t}(1 + meanᵢ) − 1` (daily-rebalanced equal weight ≈ RSP), where **meanᵢ = cross-sectional mean of the STOCK-universe single-day returns over names with a valid (i−1, i) bar pair and point-in-time universe membership as-of day i** (survivorship-correct — never today's surviving set). SPY and EW must use the **same return type** (price-return for both, or total-return for both — never mismatched). Cross-checked against `SPY − RSP` where RSP exists (2003+).
- **gapBand(t)** — **sample standard deviation (n−1) of the overlapping 20-day gap series** `{gap(t−251 … t)}` over the trailing **252 trading days** (NO ÷√n — same units as gap(t), a signal *scale* not an SE). Renamed from "gapSE" to stop the reflexive ÷√n.
- **rvAnnual(t)** — **sample stdev (n−1)** of SPY daily **log-returns** over the trailing 20 trading days **× √252** (annualized).
- **rvPct(t)** — fraction of `{rvAnnual(t−251 … t)}` that are **≤ rvAnnual(t)** (trailing-252d causal percentile, inclusive of t).

## B. Per-axis states

- **broad(t)** — Schmitt trigger (stateful): **enter** broad when `breadthEma10(t) ≥ 50 + breadthLevelSD(t)`; **leave** broad when `breadthEma10(t) ≤ 50 − breadthLevelSD(t)`; else **hold** broad(t−1). Seeded `false` at the global origin (§H).
- **slope** — rising if `breadthSlope > +breadthSlopeSE`; falling if `breadthSlope < −breadthSlopeSE`; else flat.
- **gap** — broadGap if `gap < −gapBand` (equal-weight leads); narrowGap if `gap > +gapBand` (cap-weight leads); else neutralGap.
- **vol** — lowVol if `rvAnnual < 16%` (annualized); crisisVol if `rvPct ≥ 0.90`.

## C. Label precedence — evaluate top-down, FIRST match wins (every bar gets exactly one)

1. **CRISIS** — crisisVol (`rvPct ≥ 0.90`). The vol tail is the dominant causal axis; overrides everything.
2. **NARROW** — `NOT broad OR falling`. Participation collapse dominates a stale gap sign; **no gap condition required** (by design — requiring narrowGap would mislabel the causal driver of a `NOT broad AND neutralGap` tape).
3. **THRUST** — `broad AND rising AND broadGap`.
4. **GRIND** — `broad AND flat AND neutralGap AND lowVol`.
5. **CHOP** — residual catch-all (e.g. broad+rising+neutralGap; broad+flat with elevated-but-not-crisis vol; broad+flat+lowVol but gap not neutral). "Gap says broad but breadth won't confirm" is *definitionally* chop.

## D. Consistency rule (binds the live read-out to the attribution label)

> The live read-out displays **exactly the label the backtest-attribution classifier assigns** to the current bar from the same frozen cuts and the same causal inputs; the panel augments that single label only with each axis's raw value and its trailing-causal percentile (boundary-proximity transparency, e.g. "GRIND, gap 0.3 band-widths from flipping NARROW") and **never emits an independent, 'suggested', or soft-scored label that can differ from the crisp one.**

Rationale: two label logics = two sources of truth that diverge at transitions; in a single-operator app the human would learn to trust whichever panel agreed with the trades that worked = IS-fitting with a human in the loop.

## E. Gate-binding collapse (ONLY this resolution may flip a binding firewall gate)

- **PARTICIPATE-BROAD** = THRUST ∪ GRIND
- **STAND-ASIDE-NARROW** = NARROW ∪ CHOP
- **CRISIS** = CRISIS

The 5-label resolution is **read-out-only**. Binding gates consume only these 3 hysteretic buckets — the THRUST-vs-GRIND and NARROW-vs-CHOP distinctions are real for the operator's *deployment choice* but too ARS-fragile to gate a pass/fail. This is strictly more powerful than the §5 binary classifier (it adds the broad-participate vs narrow-stand-aside split the dead Track-1/2/2b strategies needed) while staying ARS-defensible.

## F. Frozen constants (with units)

| Quantity | Frozen value | Unit |
|---|---|---|
| breadth level cut | **50** | % of stocks, on breadthEma10 |
| hysteresis margin | **1 × breadthLevelSD** | breadth-% (SD, not SE) |
| slope dead-band | **±1 × breadthSlopeSE** | breadth-% per trading day (trailing 20d) |
| gap dead-band | **±1 × gapBand** | same units as gap (overlapping 20d gaps over 252d) |
| vol GRIND cut | **< 16%** | annualized (×√252) |
| vol CRISIS cut | **rvPct ≥ 0.90** | trailing-252d causal percentile |
| lookbacks | **20** (slope, gap value, rv) · **252** (gapBand, rvPct) | trading days — structural anchors (1 month / 1 year) |

## G. ARS-sweep table (run ONCE on the global series, then slice; frozen BEFORE any attribution run is read)

| Cut | Frozen | −1 step | +1 step |
|---|---|---|---|
| breadth level | 50% | 49% | 51% (absolute pp) |
| hysteresis margin | 1·SD | 0.5·SD | 1.5·SD |
| slope dead-band | ±1·SE | ±0.5·SE | ±1.5·SE |
| gap dead-band | ±1·band | ±0.5·band | ±1.5·band |
| vol GRIND | 16% | 15% | 17% (absolute pp) |
| vol CRISIS | 0.90 | 0.85 | 0.95 |

Lookbacks (20d/252d) are structural anchors — confirmed, not swept. **Rule:** any single step that reclassifies a window **AND** flips a 3-bucket gate ⇒ that axis is ARS-broken ⇒ **redesign/coarsen (more hysteresis, fewer labels), never tune the value.** Mirrors `COMPONENT_FIREWALL_PLAN.md` §5/§7.

## H. Statefulness / burn-in (the Schmitt path-dependence fix)

The classifier is **always evaluated from a single fixed global series origin** — the first date with ≥ 252 valid trailing bars for every axis, never earlier than the **2000-01-01 breadth trust floor**. Attribution windows **slice** the one globally-computed label series; they **never** recompute it from a window-local seed. The first **252 trading days** from the origin are computed but **excluded from all attribution statistics** (incomplete trailing-252 estimates). The Schmitt `broad` state is seeded `false` at the global origin and evolves continuously. The ARS sweep (§G) is likewise run on the global series and then sliced. This makes the label at bar t a deterministic function of all bars ≤ t from one fixed origin — path-dependent on *history* (that is what hysteresis is) but **not** on the arbitrary attribution-window boundary.

## OOS validation (before the labeller is trusted)

Pre-registered here, validated on held-out history: the dot-com bust (narrow) → 2003 recovery (broad) is the cleanest thrust↔narrow stress test in the sample — confirm the gap-sign flip + the labels separate those regimes without the classifier having seen any strategy's results there. Do not coarse-label that span away.

## Status

FROZEN as text, quant-signed. **Not yet implemented.** Next: build the §0 leadership-concentration-gap signal (battle-plan build order), then implement the classifier TDD against this spec, then run the §G ARS sweep, then OOS-validate, then it may serve attribution. Any change to a frozen value after a run is read voids the pre-registration.
