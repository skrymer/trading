---
type: synthesis
title: Purpose — the hunt
summary: The goal (one tradable long strategy ≥25% CAGR), the long-only constraint, three earned-dead premise classes (+ RS-momentum downgraded-to-untested), and the open questions driving the search.
status: active
tags: [purpose, thesis]
updated: 2026-06-06
---

# Purpose — what we're hunting and why

The directional intent of the strategy-research wiki, kept deliberately separate from the structural
schema in [`CLAUDE.md`](CLAUDE.md). This is the evolving thesis; revise it as the search moves.

## The goal

Find **one tradable long strategy** for a single operator on US equities, validated through the
3-block firewall ([[the-funnel]]). "Tradable" is the operator's frozen bar:

- **CAGR ≥ 25%** (lowered from 30% on 2026-06-05; operator appetite, not quant-derived — ADR 0015).
  Quant-confirmed **reachable** 2026-06-06 ([[2026-06-06-gate-basis-and-cagr-floor-feasibility]]): it sits
  at ~the 85–90th percentile of the honest survivor distribution, *below* the demonstrated clean ceiling
  (~30–40%; VZ3-s3 30.7%, Idunn Block A 41% both cleared it). Holding it costs *patience*, not an empty set.
- **absolute Calmar ≥ 1.5** (G15) and **Sharpe ≥ 0.5** (G9)
- survives [[the-funnel]] end to end: condition-screen → strategy-screen → validate-candidate →
  promotion/G14 → monte-carlo, with no data-mining shortcuts.

## The hard constraint that shapes everything

The engine is **long-only** (ADR 0010): the only defense is **cash**. There is no short/inverse leg.
A long-only book in a downturn either sits out (cash) or bleeds. This is *why* a single timed long
strategy + cash is the realistic shape, and why the regime-conditional **portfolio** ambition was
abandoned (no viable second long component decorrelated from the first).

## What's been ruled out (deprecated premise classes)

**Three earned-dead + one downgraded-to-untested** (corrected 2026-06-07,
[[2026-06-07-funnel-correctness-consult]]). Do **not** re-open #1-#3 without a structurally new entry
premise — each failed for a documented, reproducible reason, not bad luck:

1. **Long-pullback mean-reversion** (VZ3, MR3, Idunn) — [[participate-and-lose]] + [[aliased-regime-sensitivity]].
2. **Breakout-in-uptrend** (Minervini VCP breakout) — [[participate-and-lose]]; no regime selector fixed it ([[thinning-not-selecting]]).
3. **Leveraged-ETF timing** — data-span disqualified (post-2009) + regime fragility.
4. **Cross-sectional RS-momentum rotation** — ⚠ **NOT a clean death — downgraded to untested-hypothesis.**
   The *instance* that ran ([[george]], a 52-week-high anchoring ranker) is earned-dead: it lost to a Random
   baseline on blended CAGR AND per-trade edge ([[beta-delivery]]). But the *premise class* was deprecated
   **on theory with no run**, and the methodology deep-research ([[2026-06-05-funnel-deepresearch-findings]]
   G1) holds the twin-death analogy is *probably too strong* — momentum splits into factor-momentum (dies
   in narrow leadership) + a durable **stock-specific/idiosyncratic** component that narrow leadership can
   *feed*. A **factor-neutral idiosyncratic-RS** variant is **un-ruled-out** and needs an actual run
   (rank-and-hold `/strategy-screen` vs a mandatory Random baseline, edge measured in narrow-leadership
   windows) before exclusion. Do not re-open the George *flavour* (price-level anchoring ranker).

## Where the search is now (2026-06-05)

- **BTC + Tyr** is the next premise class (ADX + breadth-thrust + RSP/SPY) — fresh, avoids all four
  prior classes. RSP ingested (#99 done) — data block lifted; the RSP/SPY leg is span-caveated on Block A
  (2003 inception, dot-com gap), fully usable on the screen + Blocks B/C. See [[btc-tyr]].
- **[[gjallarhorn]]** (breadth-washout crisis-bottom timer) passed its timing-alpha NULL (+22σ) but is
  **funnel-disqualified standalone** ([[crisis-timer-cadence-ceiling]]) — a shelved overlay component
  awaiting a host, blocked on nested-condition-groups (#93, now resolved) + a regime-transition layer.
- The breakout edge is **shelved** as a real risk-on building block (Block B earned it) pending a
  separately-validated regime layer (#83).

## Open questions (feed the next lint / research)

- Is there a long premise with **genuine cross-sectional resolution** that survives narrow-leadership
  tape, or is every long premise structurally regime-beta and the real answer a *regime-transition
  layer* (#83) rather than a better entry? **This is the binding wall.** The 2026-06-06 feasibility
  consult ([[2026-06-06-gate-basis-and-cagr-floor-feasibility]]) sharpened it: the funnel is empty
  because every premise dies on regime-survival / fragility / beta gates (G4/G6/G7/G11/G13), *not* on the
  return floor — no robust edge was ever rejected merely for sub-25% CAGR. Effort moves the feasible set
  here, not at G1.
- ~~Does crediting idle cash ~3% (#103) and per-trade cost (#101, shipped) move any shelved candidate
  across a gate?~~ **Resolved (2026-06-06):** no — idle-cash is Sharpe-neutral and only modestly eases
  Calmar; 10 bps cost is a sub-half-point drag; the two roughly cancel for a part-in-cash book. Both
  shipped; gates re-confirmed unchanged, run on the realistic basis.
- Can a crisis-bottom timer ever be validated standalone, or only as a composite leg (#93)? **Answered
  for [[gjallarhorn]]:** only as a leg — and even its composite A/B was a NO-GO (long-only crisis-DD
  correlation, [[regime-conditional-portfolio]]).
- Is the "narrow leadership kills cross-sectional RS-momentum" death **too strong**? The methodology
  deep-research ([[2026-06-05-funnel-deepresearch-findings]]) suggests narrow leadership may *feed*
  leader-momentum (factor- vs stock-specific split) — worth an actual run before treating as settled.
