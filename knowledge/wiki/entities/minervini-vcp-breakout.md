---
type: entity
title: Minervini VCP Breakout
summary: Breakout candidate; REJECTED as all-weather (firewall 2026-06-03; PRD-confirmed 2026-06-13, OOS 12.9%). Real in broad-thrust — could be tradable as a THRUST-regime specialist (hypothesis, not a verdict).
status: stable
tags: [candidate, breakout, momentum, rejected, shelved, regime-specialist]
sources: ["project_minervini_vcp_breakout_rejected", "strategy_exploration/dossier/", "strategy_exploration/assessments/minervini-vcp-breakout/"]
related: ["[[component-firewall]]", "[[participate-and-lose]]", "[[thinning-not-selecting]]", "[[long-premise-in-narrow-leadership]]", "[[gjallarhorn]]", "[[parameter-robustness-g13]]", "[[regime-conditional-portfolio]]", "[[r1-leadership-gap-breakout]]", "[[strategy-assessment]]", "[[regime-read-out]]"]
updated: 2026-06-13
---

# Minervini VCP Breakout

A **trend-continuation / breakout** long candidate — Minervini SEPA / Trend-Template + VCP adapted
to a price-only daily engine. Enter leadership names that are (a) in a confirmed Stage-2 uptrend,
(b) high market-relative strength, (c) forming a tight base on drying volume, at the moment price
(d) breaks the pivot on expanding volume. Premise: **breakout in an uptrend.** Variants
`EX-ATR20×SSM` (the validated one) and `EX-VCPOLD×SSM`, ranker `SectorStrengthMomentum`.

> ⚠ **Distinct candidate.** This is the *new* breakout candidate — NOT the original VCP, which was
> **INVALIDATED** by a look-ahead order-block bug (PR #34) and never re-validated. Different premise
> assembly, different death. Don't conflate the two.

## Status

**REJECTED — Component Firewall, 2026-06-03** (quant-confirmed, decisive not a near-miss). All three
pre-registered market-gate fixes failed → STRIKE 3 → the breakout-in-uptrend entry-time-regime
premise class is **DEPRECATED**. **But the edge is real in broad-thrust tape** (Block B earned it):
the candidate is **SHELVED as a known-real risk-on building block**, contingent on a separately
validated regime-transition layer to host it. "Exhausted" ≠ "edge is fake." The
regime-conditional portfolio program that would have been its host is itself ABANDONED — so it sits
on the shelf with no live host. ^[inferred — that the host program is now abandoned is the broader
project state, not stated in the candidate docs]

> **Update 2026-06-09 — the market-level regime-rescue path is CLOSED.** The separately-validated
> regime-transition layer this candidate was waiting on (#83) was built and run as
> [[r1-leadership-gap-breakout]] — and **ABANDONED**: a market-level gate cannot rescue this breakout
> because its loss lives in the cross-section (false breaks at fresh highs in thin tape) while a gate
> acts on the calendar ([[participate-and-lose]], now mechanistically proven). The shelved edge survives,
> but only as a risk-on leg for a host whose own alpha is **market-timing-level** (e.g. pairing with
> [[gjallarhorn]]) — **not** as a breakout fed by a market-state ON/OFF gate. Any future regime read must
> be a *direct, low-frequency concentration* signal, not a `SPY − equal-weight` return-gap (earned-dead).

## Assessment 2026-06-13 — PRD clean-data re-run · **could be tradable as a THRUST-regime specialist**

First `/assess-strategy` battery (ADR 0022, non-adjudicating; `config_hash 81a1d38ee0a6`). A 2026-06-12
run on the **dev** universe was **invalidated**: dev's DB carried un-caught bad prints (VPI $65k / LAF
$26.4k split-adjustment failures; midgaard flagged ~166 symbols) that inflated the per-trade edge ~24×
(3.7→90.9), lifted CAGR ~25 pts (12.9%→37.7%), and *spuriously passed* the SPY baseline. PRD is clean
(1 irrelevant violation — the ERX leveraged ETF, outside the STOCK universe), so the PRD re-run
reproduces the firewall record: OOS CAGR **12.9%** (≈ the documented 9.6% in-market / 12.7% blended),
edge **3.66/trade**, Sharpe 0.49, Calmar 0.34, maxDD ~44%, **9/22 negative windows, SPY-baseline FAIL**
as an all-weather book.

**As an all-weather strategy it stays REJECTED** — the firewall verdict is unchanged and remains the
only road to TRADABLE. But the clean **regime decomposition** quantifies the long-documented "edge is
real in broad-thrust tape" claim on the same trade population:

| Published regime at entry | edge/trade (PRD clean) | trust grade (ADR 0024) |
|---|---|---|
| **THRUST** | **+7.45** | B — gateable, precision-only |
| CRISIS | **−3.05** | A — gateable (loses in crisis) |
| GRIND / NARROW / CHOP | +4.4 / +2.1 / +3.6 — **uncitable** | D — descriptive-only |

> **Label — could be tradable as a THRUST-regime specialist (operator hypothesis 2026-06-13, NOT a
> verdict).** Fits the operator's regime-specialist thesis: stop hunting an all-weather strategy; build
> a stable of per-regime specialists routed by a regime classifier, with this candidate the **THRUST
> leg**. The +7.45 THRUST edge corroborates the Block-B "real in broad-thrust" finding — but it is
> **descriptive-only**: confirming a THRUST specialist requires a *regime-conditional firewall
> validation* with a within-THRUST conditional null (draws from the comparable THRUST population, per
> the conditional-within-regime-null rule), **never** gating this config to THRUST after the fact —
> that is the rescue-forbidden / [[aliased-regime-sensitivity]] trap. ^[inferred — the specialist
> framing is the operator's direction + Claude's synthesis; the THRUST edge sign/magnitude is measured]
>
> **Open dependency for the stable:** a reliable proxy for the *non*-CRISIS/non-THRUST regimes. ADR 0024
> found the three cheap daily axes cannot separate GRIND/NARROW/CHOP (Grade D), so routing currently
> resolves only CRISIS (A) and THRUST (B, precision-only — the dd-recovery blind spot suppresses THRUST
> ~12 months post-crash). A regime-specialist stable that must *know it is in THRUST* therefore still
> needs a from-scratch v3 classifier axis (cross-sectional dispersion / sector participation / vol
> term-structure) for the remaining regimes.

Path-risk (sized MC, clean): drawdown distribution centers ~33% maxDD, P(>40%)≈15%; the realized ~46%
path sat in the worst ~15% of trade orderings. The MC *return* envelope is unusable (full-reinvestment
compounding). Deflated-Sharpe AMBER, with `nEff` understated (minervini predates the dossier register).

## Funnel history

| Stage | Outcome |
|---|---|
| `/condition-screen` — RS percentile, VCP-A (narrowing range), VCP-B (volume dry-up) | PROCEED / ARS-clean (300-sym sanity universe — full-universe screen OOMs a high-firing gate) |
| `/strategy-screen` (2005–2015, full universe) — exit sweep | EX-ATR20 + EX-VCPOLD advance; Sharpe ~1.25, CAGR 17.5% |
| ranker sweep | `SectorStrengthMomentum` wins; `TrailingReturn` **lost to Random** (ρ≈0.9 redundant with the RS gate) |
| `/verify-promotion` (G14) | **PASS** — promoted base conditions diff identically (946=946 trades, Jaccard 1.000000) |
| Component Firewall (Track 1) | **REJECTED** — 6 binding gates fail |
| Track 2 (scalar breadth gate) | **REJECTED — worse** ([[thinning-not-selecting]]) |
| Track 2b (per-name sector-breadth gate) | **REJECTED** — kill rule failed → STRIKE 3, premise DEPRECATED |

The 10y screen passed it because 2005–2015 straddled the broad 2009–13 recovery and **never saw a
sustained narrow-leadership cluster** — the 25y firewall doing its job ([[the-funnel]]).

## Verdicts — in-market CAGR by block + the three gate-fix attempts

| Variant / fix | Block A | Block B | 25y | Verdict |
|---|---|---|---|---|
| **EX-ATR20×SSM** in-market geom CAGR | 16.9% | **20.8%** | 9.6% | ❌ ≪ 30% floor (below even the 25% symmetric target) on all 3 blocks |
| EX-VCPOLD×SSM | — | — | — | **NO-GO** — an exit change can't un-take failed breakouts (entry-population failure); skipped as wasted compute |
| **Fix 1** — `spyTrendUp` alone (baseline gate) | participates fully, bleeds | broad-edge OK | 8/21 neg windows | FAIL — too coarse, only stands aside in outright crisis |
| **Fix 2** — `+ breadthEma10Above50` (scalar) | flips 2006/18/19 neg | **destroys 0-neg proof (20.8→12.1)** | deepens 2011/2016 | FAIL — **worse**: halved trades, [[thinning-not-selecting]] |
| **Fix 3** — `+ sectorBreadthGreaterThanMarket` (per-name) | — | thinned 152→106 | 2011 −4.6→−13.2, 2023 −19.4→−25.9 | FAIL — kill rule failed → STRIKE 3 |

Block-B detail is the load-bearing nuance: **0 negative windows, 20.8% in-market CAGR, real 2020
+56.5% recovery alpha.** The edge is genuine in its native regime; the firewall verdict was
explicitly **NOT lottery**.

Other 25y binding fails (EX-ATR20×SSM): C2 maxDD 42.3% (>25%), C3 worst-window DD 22.6% (>20%),
C5 in-market edge CoV 1.86 (>1.5), C1c-Calmar(in-mkt) 0.42 (<0.5), C7 8 negative participating
windows (>1). Three operator classes — no gate / scalar / per-name — all **thin-and-deepen the same
way**, which is the proof the failure is the **entry premise, not the selector**: no selector fixes it.

## Why it died

**[[participate-and-lose]] in narrow-leadership chop — the breakout cousin of mean-reversion's known
weakness.** `spyTrendUp` only sits the book aside in outright crisis (2008: 1 trade, 0.8% DD). In
narrow-breadth-but-index-up tape (2015–16, 2021–23) it stays **fully deployed (30–47 trades/window)
and bleeds** (2023 −19.4%, 2015 −14.7%, 2021 −10.3% CAGR). The loss is **not in detection** — the
entry stack correctly identifies trend, RS, tight-base, breakout names. The loss is in **breakout
follow-through failing at a fresh high**: in narrow-leadership chop the breakout *triggers correctly
and then fails*, so a per-bar entry signal structurally cannot see it coming.

**The diagnostic tell — in-market geom CAGR 9.6% < blended 12.7% inversion — is real, not an
artifact.** Geometric compounding of the lumpy active-window sequence is the true alpha number; the
cash/partial-year stitching *smooths* the blend, so the active-only sequence compounding *below* it
means returns are **dispersion-dominated, not alpha-dominated**.

It is **not** ARS (stable high trade counts, clustered consistent losses, no parameter flip) and
**not** lottery (Block B is a genuine broad-regime edge). It is a **regime-discrimination** problem
that a daily entry-time signal can't solve — confirmed three independent ways: §10 in-market-CAGR
inversion, the breadth-at-entry table (breadth predicts payoff *magnitude*, not win *frequency* →
gating only thins), and the leave-one-out ablation (removing almost any filter *raises* blended CAGR
while PF craters toward 1.0/0.7 — the filters concentrate a tail they don't generate).

## Failure modes hit

- [[participate-and-lose]] — the binding cause; breakout cousin of the mean-reversion-on-pullback
  weakness ([[vz3]] / [[mr3]] / [[george]]'s class).
- [[thinning-not-selecting]] — every scalar/per-name market gate halved trades and deepened the bad
  windows instead of removing the bad ones (a scalar market gate can't solve a cross-sectional
  per-name selection problem).
- [[long-premise-in-narrow-leadership]] — the synthesis this candidate is the breakout data point in.
- Crosses [[parameter-robustness-g13]]'s two-tier framework (Minervini-spec constants = Tier-2
  no-retune; VCP/exit tunables = Tier-1 binding) — passed G14 but never reached a clean Tier-1 sweep
  because the premise died upstream.

## Reusable findings (durable capital)

- **Promoted G14-PASS conditions survive as reusable artifacts** (PR #85/#90): `NarrowingRangeCondition`
  (progressive range contraction, `stepWindow=10`) and `VolumeDryUpCondition` (`dryupWindow=10`,
  `baseWindow=50`, `dryupRatio=0.7`). Strategy-neutral (named by mechanic, not by the named strategy
  that used them). G14 `/verify-promotion` PASSed: promoted vs inline-script diffed identically over
  25y (946=946 trades, entry-set Jaccard 1.000000, 0 divergences). These are the **primary
  selectivity** — the ablation showed they can't even be dropped tractably (OOM = they thin
  candidates before the combinatorial fan-out); the other conditions are refinements on their output.
- **The `C-CASHOVERLAP` gate-bug fix** (Component Firewall §10, quant 2026-06-03): the original
  "all stand-aside windows must not coincide" coverage gate was a **bug** — in a long-only engine
  *every* valid component shares crisis cash by construction, so it false-rejected every valid book
  and contradicted the Portfolio-blend G6 survival gate. Corrected to: **crisis cash is EXEMPT**
  (mandatory shared survival, credited by G6, never penalized); the gate measures only the fraction
  of **non-crisis** OOS days where all components are simultaneously in cash. A durable
  [[component-firewall]] methodology fix, independent of this candidate.
- **The shelved edge itself** — a known-real risk-on breakout building block, Block-B-proven
  (0 negative windows, 20.8% in-market CAGR, 2020 +56.5% recovery alpha), awaiting a
  separately-validated regime-transition host. Pairs structurally with a bottom-timer like
  [[gjallarhorn]] (which carries no participate-and-lose surface), not as a standalone strategy.
- **Population-bias audit clean:** the universe is survivorship-free (delisted names included through
  delisting, force-closed as real losses), no pre-liquidity truncation in this stack — so the
  concentration / breadth / discrimination findings are NOT survivorship artifacts.
- **Design principles for any future breakout candidate** (typed hypotheses, re-tested via fresh
  screen, never inherited as fact): a breakout-event trigger + SPY-regime crash filter are
  load-bearing for trade quality; ADX-as-a-gate is contraindicated for tail-edge premises (cuts the
  very tail that IS the edge); the VCP base should be the *primary* thinner with trend/RS/distance as
  secondary refiners. **The loss-cut must reference the breakout *pivot / base-low*, not the entry
  close** — a stop anchored on the entry close fires on the normal post-breakout retest of the pivot,
  cutting winners before they run (a reusable exit-design lesson for any breakout premise).

## Related

[[component-firewall]] · [[participate-and-lose]] · [[thinning-not-selecting]] ·
[[long-premise-in-narrow-leadership]] · [[gjallarhorn]] · [[parameter-robustness-g13]] ·
[[lottery-vs-signature]] · [[beta-delivery]] · [[vz3]] · [[mr3]] · [[george]] ·
[[crisis-timer-cadence-ceiling]] · [[the-funnel]] · [[purpose]]
