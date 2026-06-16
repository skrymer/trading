---
type: query
title: THRUST-specialist design priming (#83) — rejection cause, within-regime null, ARS boundary, THRUST sparsity
summary: Priming for the #83 THRUST-gated-breakout design — why the breakout was rejected, the within-regime conditional null + primary metric, the premise-derived-vs-P&L-rescue boundary (sharpened by the cross-sectional-loss tension), and THRUST gateability/sparsity.
status: active
tags: [query, regime-specialist, breakout, thrust, methodology]
sources: ["knowledge/wiki/entities/minervini-vcp-breakout.md", "knowledge/wiki/concepts/regime-read-out.md", "knowledge/wiki/sources/2026-06-04-gjallarhorn-null.md", "knowledge/wiki/concepts/aliased-regime-sensitivity.md", "knowledge/wiki/concepts/participate-and-lose.md", "knowledge/wiki/concepts/lottery-vs-signature.md", "knowledge/wiki/concepts/crisis-timer-cadence-ceiling.md"]
related: ["[[minervini-vcp-breakout]]", "[[regime-read-out]]", "[[participate-and-lose]]", "[[aliased-regime-sensitivity]]", "[[lottery-vs-signature]]", "[[crisis-timer-cadence-ceiling]]", "[[r1-leadership-gap-breakout]]", "[[gjallarhorn]]"]
updated: 2026-06-15
---

# THRUST-specialist design priming (#83)

The four linked questions for designing a THRUST-regime-gated breakout (roadmap item 4), answered from
the compiled wiki, plus the one cross-page tension the quant consult must resolve.

## Q1 — Why the Minervini VCP breakout was REJECTED

REJECTED at the Component Firewall **2026-06-03** (quant-confirmed, decisive — not a near-miss),
[[minervini-vcp-breakout]].

**Death cause: [[participate-and-lose]] in narrow-leadership chop** — the breakout cousin of the
mean-reversion-on-pullback weakness. The `spyTrendUp` gate only sits the book aside in outright crisis
(2008: 1 trade); through narrow-breadth-but-index-up tape (2015-16, 2021-23) it stays **fully deployed
(30-47 trades/window) and bleeds** (2023 −19.4%, 2015 −14.7%, 2021 −10.3% CAGR). The loss is **not in
detection** (the stack correctly finds trend/RS/tight-base/breakout names) — it is in **breakout
follow-through failing at a fresh high**, which a per-bar entry signal structurally cannot foresee. The
loss therefore lives in the **cross-section**, not the calendar.

**Binding 25y gate fails (EX-ATR20×SSM):** G1 in-market geom CAGR 9.6–20.8% ≪ 25–30% floor · C2 maxDD
42.3% (>25%) · C3 worst-window DD 22.6% (>20%) · C5 in-market edge CoV 1.86 (>1.5) · C1c in-mkt Calmar
0.42 (<0.5) · C7 **8 negative participating windows** (>1).

**Three pre-registered gate-fixes all failed → STRIKE 3 → premise class DEPRECATED:** (1) `spyTrendUp`
alone — too coarse; (2) `+ breadthEma10Above50` (scalar) — *worse*, halved trades, destroyed Block B's
0-neg-window proof ([[thinning-not-selecting]]); (3) `+ sectorBreadthGreaterThanMarket` (per-name) —
kill rule failed (deepened 2011/2023). Explicitly **NOT lottery** (Block B is a genuine broad edge:
0 neg windows, 20.8% in-mkt CAGR, real 2020 +56.5% recovery) and **NOT ARS** (stable trade counts, no
parameter sign-flip). The 10y screen passed it only because 2005–2015 straddled the broad 2009–13
recovery and never saw a sustained narrow-leadership cluster.

## Q2 — The within-regime conditional null (Gjallarhorn precedent)

A *timing/regime* rule is judged against a **conditional within-regime NULL**, NOT the standard
Random-ranker baseline ([[lottery-vs-signature]], [[2026-06-04-gjallarhorn-null]]).

- **Null population:** random entries drawn **only from the comparably-stressed population** (Gjallarhorn:
  `breadthPercent ≤ 25`), at the candidate's **matched firing rate** (P_FIRE = 0.1486 = 81/545),
  everything else byte-identical, **20 seeds → a distribution**.
- **Why conditional, not uniform:** a uniform-random null lands mostly in calm/bull tape, so beating it
  proves only "stressed days ≠ average days" = **regime beta** (a false GO). The within-regime null makes
  *both* arms operate in the same regime; beating it proves the rule's *specific* selection adds value =
  genuine **timing/selection alpha** (a true signature).
- **Primary metric: per-trade edge > null p95** (≥~2σ); confirm with blended CAGR > null median.
  **Not** win-rate, **not** WFE.
- **Precedent:** Gjallarhorn passed at **+22σ** (edge +2.19% vs null mean −0.17%, all 20 seeds negative).
- **For the THRUST specialist:** the null population = **THRUST-regime days** (the comparable-stress
  population is the favourable regime, not a stress one). Whether the null draws random entry *days* (a
  pure timer) or random *names on THRUST days* (a selection-within-regime rule) is a quant-design call —
  the breakout selects *which* names, so it is closer to selection-within-regime than to a pure timer.
- **Reproducibility:** the null must use a **deliberately-seeded, repo-persisted mechanism** (ADR 0017) —
  Gjallarhorn's original seeding was unverifiable from the repo.

The quant designs the null population + RW-surrogate + the day-vs-name draw; do not self-spec it.

## Q3 — The ARS-rescue boundary (premise-derived vs P&L-derived) — **sharpened**

**The forbidden move (P&L rescue):** gating the *dead config* to THRUST after seeing the regime
decomposition = the rescue-forbidden / [[aliased-regime-sensitivity]] trap. *"Don't ship the passing
values"* — a post-hoc regime patch on a rejected config is IS-fitting to the single OOS window. The
ADR-0008 dead-config brake + the `/strategy-exploration` DISTINCT lineage gate will (correctly) refuse to
advance the dead `config_hash` or a G13 neighbour of it.

**The legitimate path:** a brand-new, **structurally DISTINCT** candidate — a THRUST-aware breakout built
from scratch — confirmed via a **regime-conditional firewall validation with a within-THRUST conditional
null**, *never* by gating the dead config. [[minervini-vcp-breakout]] states this directly: the +7.45
THRUST edge is **descriptive-only**; confirming a THRUST specialist needs the regime-conditional firewall,
not after-the-fact gating.

**⚠ The tension the quant MUST reconcile — this is sharper than "premise-derived vs P&L-rescue" alone.**
The premise-derived story is coherent on its face: THRUST = broad-participation thrust (see Q4), and the
documented death cause is narrow-leadership chop — so "breakouts structurally need broad thrust" maps onto
the regime axis. **But** two stable pages say a *market-level* regime gate is **mechanistically dead** for
*this* breakout, not merely untried:

- [[participate-and-lose]]: the loss lives in the **cross-section** (a breakout fails at a fresh high in
  thin tape *even on a day a perfect classifier calls "broad"*), while any market-level gate acts on the
  **calendar** — one aggregation level *above* where the alpha decays. "It can only remove days; it can't
  fix the entries on the days it keeps. **The real fix is a structurally different ENTRY premise.**"
- [[r1-leadership-gap-breakout]] (ABANDONED 2026-06-09) is the *mechanistic proof*: a clean,
  pre-registered market-level gate deployed **orthogonally** to edge (`corr≈0`), left in-market Calmar
  0.32 ≈ the ungated 0.42. The minervini page's own 2026-06-09 update: the shelved edge survives "only as
  a risk-on leg for a host whose own alpha is **market-timing-level** (e.g. pairing with [[gjallarhorn]])
  — **not** as a breakout fed by a market-state ON/OFF gate."

So the live question is **not just** "is THRUST-gating premise-derived or a P&L rescue" — it is also
**"is a THRUST gate not simply another instance of the market-level gate already proven mechanistically
dead for this breakout (R1)?"** ^[inferred — the synthesis that the THRUST-specialist hypothesis (minervini
2026-06-13) sits in tension with the cross-sectional-loss mechanism (participate-and-lose / R1) is Claude's
cross-page reading; each individual claim is sourced, the tension is not flagged on either page]

The candidate-side rebuttal the quant should weigh: R1's gate was the **`SPY − equal-weight` return-gap**
(earned-dead, calendar-orthogonal to edge). THRUST is a **different, concentration/breadth-aware** label —
*possibly* it resolves the bad tape where the return-gap did not. The counter-counter: the narrow-leadership
chop that kills the breakout is precisely the **NARROW/CHOP** regime, which the v2 classifier grades **D
(ungateable, below axis resolving power)** — so a THRUST-only gate may merely *thin* into the precision-only
THRUST cell rather than *select* against the cross-sectional failure. This is exactly the domain adjudication
to route to the quant; do not self-decide it.

## Q4 — THRUST gateability and sparsity

From [[regime-read-out]] (frozen v2, ADR 0023/0024):

- **Gateable: YES.** `GATEABLE_LABELS` = {CRISIS (Grade A), THRUST (Grade B)}. GRIND/NARROW/CHOP are
  Grade D — **rejected at build time** (`RegimeLabelCondition` throws on a D-grade label).
- **THRUST = Grade B, precision-only** ("real where unmasked: 2003 71%"). Gateable, author-beware.
- **Definition:** THRUST = (breadth HIGH ≥50 **OR** slope RISING ≥+3/5 bars) **AND** gap NEG. `gap NEG` =
  SPY 20-bar return < **median** per-name 20-bar return — i.e. the average stock is *beating* SPY =
  **broad participation**. So THRUST genuinely captures broad-thrust tape (premise-consistent with "a
  breakout needs broad participation"); it is **not** "the index is up" — deploy-in-uptrend intent belongs
  to the leadership-gap regime (ADR 0010), not THRUST.
- **The sparsity trap — the drawdown-recovery blind spot:** THRUST is **structurally suppressed ~12 months
  post-crash** (2009-Q2/Q3 published CRISIS at 0% THRUST, even though the recovery was the strongest broad
  thrust on record). So THRUST-labelled days are **sparse and clustered**, and *absent* in the year after
  every crash — exactly the window a recovery-breakout would want.
- **Cadence check is mandatory and up-front** ([[crisis-timer-cadence-ceiling]]): estimate THRUST-gated
  breakout **trade-events/yr before committing**. If <~1/yr it cannot populate per-window OOS folds →
  un-validatable standalone (lottery by construction) → must be embedded in a higher-cadence composite,
  not run alone. This estimate may kill the standalone candidate cheaply — an acceptable outcome. Do it
  first.

## Bottom line for the design step

1. The premise-derived hypothesis is **coherent** (THRUST = broad participation; death cause = narrow
   chop) — but it collides with a **stable, mechanistically-proven** finding that a market-level gate
   can't rescue a cross-sectional loss. **That collision is the gating decision; it is the quant's to
   adjudicate**, framed as "does a concentration-aware THRUST label resolve the cross-sectional failure
   that the R1 return-gap could not, or does it just thin into the precision-only THRUST cell?"
2. If it proceeds, it is a **from-scratch DISTINCT candidate** validated by a **regime-conditional
   firewall + within-THRUST conditional null** (primary metric: per-trade edge > null p95), never the
   gated dead config.
3. **Estimate THRUST cadence first** — it may funnel-disqualify the standalone before any build.

**Pages consulted:** [[minervini-vcp-breakout]], [[regime-read-out]], [[participate-and-lose]],
[[aliased-regime-sensitivity]], [[lottery-vs-signature]], [[crisis-timer-cadence-ceiling]],
[[2026-06-04-gjallarhorn-null]], [[r1-leadership-gap-breakout]] (one-hop).

**Gaps:** (a) No measured THRUST trade-events/yr for a breakout entry stack — the cadence estimate is
unrun. (b) No page yet resolves the Q3 tension (THRUST gate vs the proven-dead market-level gate); that
resolution is the pending quant consult and should be ingested as a new `sources/` page when it lands.
(c) The within-THRUST null's day-vs-name draw is unspecified (quant-design).
