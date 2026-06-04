# BTC + Tyr — Breadth-Thrust Continuation strategy development

_Created: 2026-06-04 · Status: **SCOPING (design basis only — not yet specced/screened).** Next candidate after Gjallarhorn SHELVED (`STRATEGY_LEDGER.md` §C.2). Formal spec to be routed to `quant-analyst` before any build/screen._

This doc collects the **design inputs** for BTC+Tyr so the eventual quant spec is grounded in data, not guesses. It is NOT yet a runnable spec.

## Premise

**Breadth-thrust CONTINUATION** — deploy long when a market **breadth thrust** signals the start of a *fresh, broad* recovery, then **ride** the expansion (deploy-and-hold), rather than buying individual fresh-high breakouts. Combine with **Tyr** (institutional-breakout trigger + breadth-recovery gate). Anchor: Zweig Breadth Thrust (continuation half) + the leadership-concentration regime.

**Why it's the strongest next pick (quant, 2026-06-04):**
- **Adjacent to proven alpha:** Gjallarhorn established that breadth-*state* signals carry real, null-beating timing information (+22σ). BTC+Tyr is the *continuation* sibling — a deploy-and-ride signal, not a buy-the-exact-bottom timer.
- **Fixes Gjallarhorn's two standalone killers:** it can be **active far more than ~10% of the calendar** (rides the post-thrust expansion) → clears the cash-drag / sub-30%-CAGR wall that capped Gjallarhorn; and thrust-continuation tapes (2003, 2009-H2, 2020-H2) are where the largest broad-based gains live → a real shot at the 30% CAGR floor.
- **Distinctness PASS:** gates on a *market breadth thrust + participation*, NOT individual-stock fresh-high breakouts → structurally avoids the deprecated breakout-in-uptrend's narrow-leadership death tape. Not return-change momentum, not long-pullback MR, not leveraged-ETF timing, not RS-momentum rotation.

## Design basis from the breakout trade-anatomy (`TRADE_ANATOMY_ANALYSIS.md`, 946 trades, quant-reviewed)

The breakout's own winner/loser dissection tells us what to build and what to avoid:

1. **Breadth as a THRUST/TRANSITION, not a LEVEL gate.** Entry-day breadth *level* does NOT predict win rate (flat ~33-35% from breadth <30 to >60) — it only scales payoff magnitude. So a `marketBreadth > 50%` level gate (and `sectorBreadthGreaterThanMarket`) only *thins*, never *selects* — empirically why Track-2 / Track-2b were rejected. The signal that DOES separate winner years (2003/09/13/20, post-washout broad recoveries) from loser years (2011, 2021-23 narrow chop) is the **regime transition** — a breadth *thrust* — which a daily breadth reading can't see but a multi-day thrust can. **BTC+Tyr's core signal must be the thrust/transition, not a level filter.**

2. **ADX is the strongest UNUSED discriminator → include it.** In the breakout anatomy, win rate climbs **31% → 48%** as ADX goes <20 → >40 (mean +8% P/L at ADX 30-40). The breakout never used ADX. **Add an ADX trend-strength condition to BTC+Tyr** (e.g. `adxRange(minADX≈25-30, maxADX=100)` on the names deployed into post-thrust) — the decisive design question at screen time is whether ADX **selects** (lifts win%/payoff, retains right-tail winners) or merely **thins**. Sweep the threshold for ARS at the condition-screen / Step-0 stage; do not hard-code the value that happens to pass.

3. **Avoid the dilutive loose tails.** The breakout's `%52wHigh(25)`, `%52wLow(30)`, Donchian-"near" tails were dilutive — if BTC+Tyr uses any trend-template conditions, tighten them as a *fresh* hypothesis (validate OOS), don't copy the breakout's loose defaults.

## Parked design input — RSP/SPY leadership-concentration signal (blocked on ingestion [#99](https://github.com/skrymer/trading/issues/99))

The **RSP/SPY relative-strength ratio** (equal-weight vs cap-weight S&P) is the cleanest *direct* broad-vs-narrow detector: rising = broad (average stock participating), falling = narrow (mega-cap concentration, e.g. 2021-23 / current). It measures the **leadership-concentration gap** (`CONTEXT.md`) more directly than the internal `breadthPercent` oscillator.
- **At screen time, compare RSP/SPY-divergence vs the internal breadth-thrust** as BTC+Tyr's regime signal — whichever discriminates the good-vs-bad years better in the anatomy wins.
- **Blocked on [#99](https://github.com/skrymer/trading/issues/99)** (RSP not yet ingested).
- **Data-span caveat:** RSP launched **2003** → covers the full 2005-2015 screen window + GFC/COVID/2022 + Blocks B/C, but **truncates Block A to 2003-2014 (misses the 2000-2002 dot-com bear)**. Partial gap, disclose; not a full disqualifier (per `feedback_signal_must_span_firewall_window`).

## Carry-forward discipline (Gjallarhorn's + George's lessons — front-load these)

- **Cadence / lottery-window gate FIRST.** Breadth-thrust events are rare-ish; confirm at a Step-0 firing-rate check that BTC+Tyr deploys **≥1-2 windows/yr** (not Gjallarhorn's 0.5/yr) BEFORE building anything bespoke — else it inherits Gjallarhorn's standalone-unvalidatability (can't populate walk-forward OOS folds). At screen: ≥3 positive participating windows, no single window ≥60% of compounded return.
- **Random-ranker baseline (G-RANDOM).** If BTC+Tyr selects *what to buy* after the thrust gate fires, it must beat a byte-identical `Random`-ranker baseline on blended CAGR AND per-trade edge (George's lesson) — else the "edge" is entry-universe beta.
- **IS-fitting guardrail.** The anatomy findings (ADX, thrust-not-level, RSP/SPY) are **hypotheses derived from the breakout's data** — they must be validated on BTC+Tyr **out-of-sample** (the firewall), never tuned on within-sample slices.
- **30% CAGR floor** is the final standalone tradability bar.
- **Re-run `diagnostics/trade_anatomy.py` on BTC+Tyr's own trades** to keep the design data-driven through iteration.

## Status / next steps
1. Resolve [#99](https://github.com/skrymer/trading/issues/99) (RSP ingestion) — enables the RSP/SPY signal (optional; internal breadth-thrust is usable now).
2. Route the formal BTC+Tyr spec to `quant-analyst` (entry thrust signal + Tyr trigger + ADX + ranker + sizer + exits + PASS/KILL), built on the design basis above.
3. `/condition-screen` any novel thrust/RSP condition → `/strategy-screen` (2005-2015) with the cadence + G-RANDOM gates → `/validate-candidate` → `/monte-carlo`.

## Reference
- Anatomy: `TRADE_ANATOMY_ANALYSIS.md` (breadth-level-doesn't-discriminate; ADX 31→48%; dilutive tails). Specs: `ALTERNATIVE_STRATEGY_PROPOSALS.md` (BTC + Tyr). Ledger: `STRATEGY_LEDGER.md` §C.2.
- Lessons: `feedback_crisis_timer_cadence_ceiling`, `feedback_random_ranker_baseline_mandatory`, `feedback_signal_must_span_firewall_window`, `feedback_conditional_within_regime_null`.
