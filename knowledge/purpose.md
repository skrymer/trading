---
type: synthesis
title: Purpose — the hunt
summary: The goal (one tradable long strategy ≥25% CAGR), the long-only constraint, the earned-dead premise classes (RS-momentum sealed via #137), the empty funnel, and the open questions.
status: active
tags: [purpose, thesis]
updated: 2026-06-09
---

# Purpose — what we're hunting and why

The directional intent of the strategy-research wiki, kept deliberately separate from the structural
schema in [`CLAUDE.md`](CLAUDE.md). This is the evolving thesis; revise it as the search moves.

## The goal

Find **one tradable long strategy** for a single operator on US equities, validated through the
3-block firewall ([[the-funnel]]). "Tradable" is the operator's frozen bar:

- **CAGR ≥ 25%** (lowered from 30% on 2026-06-05; operator appetite, not quant-derived — ADR 0015) — a
  long-run **compounded average**, not a per-year floor (a book that's cash in some years can still average
  ≥25%). Reachable **unlevered**, but leverage is *available* if needed (see the constraint note below):
  the bar is not a leverage-foreclosure.
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

> **Long-only is *direction*, not a leverage ban.** ADR 0010 forbids short/inverse legs; it does **not**
> forbid leverage — the engine supports `leverageRatio` (0.1–100×) + `applyLeverageCap`, and live leverage
> is expressed via **options (calls)**. So a tradable edge may be levered into the CAGR target (≤2× into
> the operator's Calmar-1.5 / 16.7%-DD envelope); leverage cannot, however, rescue a *low-Calmar* edge
> (Calmar is leverage-invariant). Earlier wiki text that read "25% needs forbidden leverage → earned-dead"
> was a misconception — the dead classes died of their own mechanics ([[participate-and-lose]], ARS,
> data-span, [[beta-delivery]]), not a leverage ceiling.

## What's been ruled out (deprecated premise classes)

**Three earned-dead + one downgraded-to-untested** (corrected 2026-06-07,
[[2026-06-07-funnel-correctness-consult]]). Do **not** re-open #1-#3 without a structurally new entry
premise — each failed for a documented, reproducible reason, not bad luck:

1. **Long-pullback mean-reversion** (VZ3, MR3, Idunn) — [[participate-and-lose]] + [[aliased-regime-sensitivity]].
2. **Breakout-in-uptrend** (Minervini VCP breakout) — [[participate-and-lose]]; no regime selector fixed it ([[thinning-not-selecting]]). The #83 breadth-confirmed regime-rescue ([[r1-leadership-gap-breakout]]) was built, run, and **ABANDONED 2026-06-09**: a market-level gate can't rescue this premise (loss is cross-sectional, gate acts on the calendar) — now *mechanistically* proven, not just observed. The `SPY − equal-weight` trailing-return-gap as a regime signal is itself **earned-dead** (horizon-aliases a multi-quarter regime with a multi-week instrument). A *direct, low-frequency concentration* signal stays open — but only on a market-timing premise, never a breakout.
3. **Leveraged-ETF timing** — data-span disqualified (post-2009) + regime fragility.
4. **Cross-sectional RS-momentum rotation** — ✅ **EARNED-DEAD (2026-06-09, resolved).** Three flavours
   now ruled out on real runs: [[george]] (52-week-high anchoring — lost to Random), [[mrm]] (single-factor
   SPY-residual momentum — anti-selective, K=0/10 vs Random), and [[multifactor-residual-momentum]] (#137,
   market+sector-residual — *even more* anti-selective, K=0/10). The deep-research hypothesis that narrow
   leadership *feeds* a durable stock-specific/idiosyncratic momentum component
   ([[2026-06-05-funnel-deepresearch-findings]] G1, [[long-premise-in-narrow-leadership]]) is **refuted**:
   the more factors stripped, the *more* anti-selective the residual — there is no cross-sectional alpha in
   residual relative strength in this universe/period. Per the quant class-killer asymmetry, the
   market+sector FAIL closes the class **without** needing the Fama-French (size/value) step — stripping
   more can only remove signal. See [[2026-06-09-rs-momentum-class-earned-dead]].
   **Caveat that almost hid this:** the first MRM run (#130, 2026-06-08) was a *false* reject — the
   walk-forward starved the 504-day ranker of its lookback (no warmup buffer), measuring RNG-vs-RNG. Fixed
   in ADR 0018 ([[2026-06-09-trailing-ranker-warmup-starvation]]); the fixed-engine re-run confirms the
   death properly. The `MultiFactorResidualMomentumRanker` + the warmup fix are kept as permanent assets.

## Where the search is now (2026-06-09)

- **Funnel EMPTY — five premise families buried, no active candidate.** The 5th class ([[pead]]) is the
  most recent death and the others are unchanged below.
- **[[pead]] (Post-Earnings Announcement Drift) — long-only-drift form ABANDONED (2026-06-09).** The 5th
  class (event-conditioned, per-name) tried three surprise proxies at `/condition-screen` — raw price
  gap, market-neutral gap residual, EPS-sign-gated residual — and **all three died the same way** (20d
  SPY-regime flat tertile negative). Quant verdict **RECONSIDER_CLASS**: the beta enters via the
  holding-window return, not the entry, so no surprise *proxy* fixes it. The mechanism is untouched but
  un-isolable on current data. See [[pead]] + [[2026-06-09-pead-premise-class-adjudication]].
- **6th class — structurally-hedged route SCOPED then DECLINED (2026-06-09).** The PEAD adjudication
  steered toward a beta-hedged premise (net SPY out in the P&L). The quant scoped it (top pick: a
  cross-sectional earnings-surprise dispersion spread, backtestable as a synthetic NAV with zero engine
  change), but the operator **declined on appetite** — 25% CAGR is firm, no market-neutral sleeve. So the
  6th class must be **net-long / directional and ~25%-CAGR-capable.** See
  [[2026-06-09-hedged-class-scoping-declined]].
- **OVTLYR Plan M/ETF evaluated as a directional 6th class (2026-06-09) — did NOT supply one.** A vendor
  long plan; its signals span only ~5y (all in Block C) ⇒ un-validatable, and made honest its
  behavioral-extreme entry collapses into [[gjallarhorn]]. Quant **LIFT_IDEAS_ONLY** (gap-and-crap exit,
  earnings-proximity filter, a likely-dead reconstructed F&G-oscillator diagnostic). See
  [[2026-06-09-ovtlyr-plans-assessment]]. **The directional 6th premise remains unfound — operator/quant
  call.**
- **BTC + Tyr died at design-time screen (2026-06-08)** — emptied the funnel before PEAD was chosen:
  its fresh component, the breadth-thrust GATE, failed in isolation (SPY-regime sign-flip at all 3
  horizons, no 10/20d edge, the "thrust" degenerating into a level gate — [[thrust-degenerates-to-level]];
  62% of firings in the 2009–14 tape, a [[lottery-vs-signature]] artifact). NOT a firewall death; the
  breadth-event class is re-scopable only via a *structurally different, regime-sign-consistent* transition
  predicate, screened from scratch and together with [[gjallarhorn]]. See [[btc-tyr]] +
  [[2026-06-08-btc-breadth-thrust-screen-reject]].
- **#137 is CLOSED (2026-06-09)** — the multi-factor-neutral (market+sector) residual-momentum ranker was
  built and screened vs a seeded Random baseline; it is anti-selective (K=0/10), settling the RS-momentum
  class as **earned-dead** (#4 above). **No filed threads remain.** The next premise is an operator/quant
  call — it must be net-long / directional and ~25%-CAGR-capable (the hedged/market-neutral route was
  declined on appetite). A spin-off: the warmup-starvation fix (ADR 0018) makes any future in-engine
  trailing-history ranker validly screenable, and the seeded-random-sample reduced universe (N=1,500, both
  arms identical list) is the established fast path for ranker-selects screens.
- **[[gjallarhorn]]** (breadth-washout crisis-bottom timer) passed its timing-alpha NULL (+22σ) but is
  **funnel-disqualified standalone** ([[crisis-timer-cadence-ceiling]]) — a shelved overlay component
  awaiting a host, blocked on nested-condition-groups (#93, now resolved) + a regime-transition layer.
- The breakout edge is **shelved** as a real risk-on building block (Block B earned it); the #83
  regime-layer rescue was built + run + **ABANDONED 2026-06-09** ([[r1-leadership-gap-breakout]]) — a
  market-level gate can't rescue a participate-and-lose entry, so the breakout can only host on a
  market-timing-level partner (e.g. [[gjallarhorn]]), never a breakout fed by a market-state gate.

## Open questions (feed the next lint / research)

- Is there a long premise with **genuine cross-sectional resolution** that survives narrow-leadership
  tape, or is every long premise structurally regime-beta and the real answer a *regime-transition
  layer* (#83) rather than a better entry? **This is the binding wall.** The 2026-06-06 feasibility
  consult ([[2026-06-06-gate-basis-and-cagr-floor-feasibility]]) sharpened it: the funnel is empty
  because every premise dies on regime-survival / fragility / beta gates (G4/G6/G7/G11/G13), *not* on the
  return floor — no robust edge was ever rejected merely for sub-25% CAGR. Effort moves the feasible set
  here, not at G1. **Partly answered (2026-06-09, [[r1-leadership-gap-breakout]]):** the *regime-transition
  layer* horn is **refuted for a participate-and-lose entry** — a market-level gate can't rescue it (the
  loss is cross-sectional, the gate is calendar; proven, not observed). So the wall tilts toward the
  *better-entry* horn: the open question narrows to **"is there a long ENTRY premise with genuine
  cross-sectional resolution in thin tape?"** A direct concentration *read-out* may still help a premise
  whose edge is market-timing-level, but it cannot substitute for cross-sectional entry alpha.
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
