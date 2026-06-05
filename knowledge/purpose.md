---
type: synthesis
title: Purpose — the hunt
summary: The goal (one tradable long strategy ≥25% CAGR), the long-only constraint, the four deprecated premise classes, and the open questions driving the search.
status: active
tags: [purpose, thesis]
updated: 2026-06-05
---

# Purpose — what we're hunting and why

The directional intent of the strategy-research wiki, kept deliberately separate from the structural
schema in [`CLAUDE.md`](CLAUDE.md). This is the evolving thesis; revise it as the search moves.

## The goal

Find **one tradable long strategy** for a single operator on US equities, validated through the
3-block firewall ([[the-funnel]]). "Tradable" is the operator's frozen bar:

- **CAGR ≥ 25%** (lowered from 30% on 2026-06-05; operator appetite, not quant-derived — ADR 0015)
- **absolute Calmar ≥ 1.5** (G15) and **Sharpe ≥ 0.5** (G9)
- survives [[the-funnel]] end to end: condition-screen → strategy-screen → validate-candidate →
  promotion/G14 → monte-carlo, with no data-mining shortcuts.

## The hard constraint that shapes everything

The engine is **long-only** (ADR 0010): the only defense is **cash**. There is no short/inverse leg.
A long-only book in a downturn either sits out (cash) or bleeds. This is *why* a single timed long
strategy + cash is the realistic shape, and why the regime-conditional **portfolio** ambition was
abandoned (no viable second long component decorrelated from the first).

## What's been ruled out (deprecated premise classes)

Do **not** re-open these without a structurally new entry premise — each failed for a documented,
reproducible reason, not bad luck:

1. **Long-pullback mean-reversion** (VZ3, MR3, Idunn) — [[participate-and-lose]] + [[aliased-regime-sensitivity]].
2. **Breakout-in-uptrend** (Minervini VCP breakout) — [[participate-and-lose]]; no regime selector fixed it ([[thinning-not-selecting]]).
3. **Leveraged-ETF timing** — data-span disqualified (post-2009) + regime fragility.
4. **Cross-sectional RS-momentum rotation** — entry-universe beta, not ranker alpha (George; see [[thinning-not-selecting]]).

## Where the search is now (2026-06-05)

- **BTC + Tyr** is the next premise class (ADX + breadth-thrust + RSP/SPY) — fresh, avoids all four
  deprecated classes. RSP ingest tracked in #99. See `strategy_exploration/BTC_TYR_STRATEGY_DEVELOPMENT.md`.
- **[[gjallarhorn]]** (breadth-washout crisis-bottom timer) passed its timing-alpha NULL (+22σ) but is
  **funnel-disqualified standalone** ([[crisis-timer-cadence-ceiling]]) — a shelved overlay component
  awaiting a host, blocked on nested-condition-groups (#93, now resolved) + a regime-transition layer.
- The breakout edge is **shelved** as a real risk-on building block (Block B earned it) pending a
  separately-validated regime layer (#83).

## Open questions (feed the next lint / research)

- Is there a long premise with **genuine cross-sectional resolution** that survives narrow-leadership
  tape, or is every long premise structurally regime-beta and the real answer a *regime-transition
  layer* (#83) rather than a better entry?
- Does crediting idle cash ~3% (#103) and per-trade cost (#101, shipped) move any shelved candidate
  across a gate?
- Can a crisis-bottom timer ever be validated standalone, or only as a composite leg (#93)?
