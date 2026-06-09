---
type: concept
title: Beta-Delivery
summary: A long book whose risk-adjusted return is just the index's — profitable in absolute terms but no better than holding SPY; the failure mode G16 catches.
status: seed
tags: [failure-mode, methodology]
sources: ["docs/adr/0013-spy-buy-and-hold-is-a-binding-calmar-only-firewall-baseline.md", "knowledge/wiki/sources/2026-06-05-funnel-deepresearch-findings.md", "knowledge/wiki/sources/2026-06-08-george-random-revalidation-prereg.md"]
related: ["[[component-firewall]]", "[[long-premise-in-narrow-leadership]]", "[[participate-and-lose]]", "[[the-funnel]]", "[[george]]", "[[mrm]]", "[[pead]]", "[[aliased-regime-sensitivity]]", "[[thinning-not-selecting]]", "[[2026-06-08-random-baseline-reproducibility-fix]]", "[[2026-06-08-mrm-screen-reject]]", "[[2026-06-09-pead-earnings-gap-screen-reject]]", "[[2026-06-09-pead-market-neutral-residual-screen-reject]]", "[[2026-06-09-pead-eps-gated-residual-screen-reject]]"]
updated: 2026-06-09
---

# Beta-Delivery

> **status: seed** — the failure-mode anatomy and the detectors are settled. The firewall's **G16** gate
> has not rejected a candidate yet; the cheaper **screen-stage** tells have caught it five times —
> [[george]] and [[mrm]] (random-ranker tell) and [[pead]]'s **three** surprise proxies (price gap,
> market-neutral residual, *and* EPS-sign-gated residual — the SPY-regime-tertile sign-flip tell, thrice).
> So the *Instances* section holds all five screen-stage catches, still awaiting a first G16-firewall
> rejection. The first G16 FAIL promotes this to `active`.

## Definition

A candidate **delivers beta** when its return is just *market exposure in disguise* — it makes money
in absolute terms, but no more than you'd have made (risk-adjusted) by **just holding SPY** over the
same days. The "alpha" is the index's drift, not the strategy's selection or timing. For a long-only
book this is the default null hypothesis, not an exotic failure: any always-long-ish premise inherits
SPY's bull-market return, so an attractive-looking CAGR proves nothing until it is measured *against*
the passive alternative.

## How to detect

The firewall's **G16 SPY buy-and-hold Calmar baseline** is the detector (see [[component-firewall]]):
strategy stitched-OOS **Calmar ≥ SPY's Calmar** over the *identical* OOS-stitched support, binding on
Block A + Block B + the 25y aggregate. A FAIL = the candidate's risk-adjusted return did not beat
holding the index → beta-delivery → REJECTED.

Why **Calmar**, and why **relative** to SPY:

- **Calmar, not Sharpe** — a part-in-cash long-only timer is structurally penalised on Sharpe in a
  low-vol bull block (its cash days drag the per-day mean while always-invested SPY sits ~1+). Calmar
  is neutral to sitting in cash and rewards crash-avoidance, so it isolates real selection/timing skill
  rather than time-in-market.
- **Relative, not absolute** — the absolute Calmar floor (G15) asks "is this tradable *quality*?"; G16
  asks "did it beat *the alternative you already have for free*?". A candidate can clear the absolute
  floor and still fail G16 — high absolute Calmar that is nonetheless ≤ SPY's over the same window is
  still just beta.^[inferred]

Adjacent, cheaper tells before the firewall: a permissive-entry + ranker-selects candidate that loses to
a byte-identical **random-ranker baseline** on blended CAGR *and* per-trade edge is delivering
entry-universe beta (the random-ranker-baseline rule); and a high *correlation*/`beta` to SPY in the
single-backtest `BenchmarkComparison` is a yellow flag that the eventual G16 read may confirm.

> **Reproducibility caveat (2026-06-08).** The random-ranker baseline must be *reproducibly seeded* to
> mean anything. Until #130 the engine's `RandomRanker` scored via an **unseeded** RNG (the `randomSeed`
> fed only the 1e-10 tie-break jitter), so every "lost to Random" read — including [[george]]'s — was
> not actually reproducible. Fixed in #130: `RandomRanker(seed)` now scores deterministically per
> `(seed, symbol, date)`, enabling the per-window p95 a multi-seed null needs. See
> [[2026-06-08-random-baseline-reproducibility-fix]]. **George's reclassification was re-run (#135) on a
> seeded 17-draw distribution and HELD — affirmatively re-confirmed:** George's CAGR fell below the entire
> Random cloud and it won 0/7 windows' tails. The seeded null *strengthened* the original single-point
> read rather than overturning it ([[2026-06-08-george-random-revalidation-prereg]]).

## Why it kills

A long-only book that can't beat the index, risk-adjusted, has **no reason to exist** — it adds
operational complexity, execution cost, and single-strategy risk to reproduce something an index fund
delivers passively at lower cost. Worse, beta dressed as alpha is *fragile in exactly the regime you
deploy it for*: when the market that supplied the beta turns (narrow-leadership tape, a drawdown the
timer doesn't dodge), the "edge" evaporates because there was no selection skill underneath it. This is
the structural cousin of [[participate-and-lose]] and the through-line of
[[long-premise-in-narrow-leadership]] — both are about long exposure that looked like skill until the
supporting regime withdrew.

## Why a sizer can't fix it

G16 is a statement about the *shape* of the return stream (Calmar = CAGR / |maxDD|) relative to SPY's
shape over the same days. A position sizer or exit tweak rescales the curve but cannot manufacture
SPY-relative risk-adjusted advantage that the *selection/timing* did not produce. A G16 FAIL therefore
demands a structurally different entry premise, never a sizer/exit iteration (the standard firewall
remediation discipline — see [[the-funnel]]).^[inferred]

## Instances

- **[[george]]** (2026-06-04) — the **screen-stage** instance, caught by the random-ranker tell, not
  G16. A 52-week-high anchoring ranker whose per-trade edge (+1.01%) was *matched* by a byte-identical
  Random-ranker baseline (+1.08%) and whose blended CAGR (+1.1%) was *beaten* by it (Random +6.6%) over
  2005–2015 — i.e. the ~1% "edge" was entry-universe beta, not ranker alpha (and the anchoring tilt was
  a worse-than-noise GFC liability, −14.3% vs Random's −2.1% in 2008). Deprecated at `/strategy-screen`
  via G-RANDOM before ever reaching the firewall, so it has **no G16 read** — it is the cheaper-tell
  confirmation that this failure mode is real, not a G16 rejection.

- **[[mrm]]** (2026-06-08) — ⚠ **VOID as a beta-delivery instance (2026-06-09).** Originally read as the
  second, *stronger* screen-stage instance (single-factor SPY-beta-stripped residual-momentum ranker that
  "lost" to a seeded Random baseline on both legs: edge **+2.80% vs +6.21%**, CAGR **8.95% vs 23.05%**, and
  reproduced George's GFC liability). **That reading is withdrawn:** the walk-forward starved the 504-day
  ranker of its lookback in every OOS window, so MRM was unscoreable in OOS and its "selection" was the
  tie-break RNG — the screen compared *two different random draws*, which alone explains the 499 vs 527
  trade counts and the edge gap with **no anti-selection required**. So this is **not** a confirmed
  beta-delivery instance; the single-factor recipe is re-opened pending a re-run on the fixed engine
  (ADR 0018). See [[2026-06-09-trailing-ranker-warmup-starvation]] and [[2026-06-08-mrm-screen-reject]]
  (now void).

- **[[pead]] price-gap proxy** (2026-06-09) — the **condition-screen / regime-tertile** instance, and the
  first *event-driven* one. Not a ranker (George/MRM) and not G16: an inline earnings-gap entry condition
  whose post-entry forward-return lift, decomposed into SPY 20d-return tertiles, **sign-flipped across the
  regime** (20d: down **+1.73%** / flat **−0.38%** / up **−0.56%**) — positive only in down-tape, the
  entire +0.114% headline a blend that nets to noise. The OHLCV price gap was reading the same-day
  market-direction move (on a strong-tape day the index gaps and drags the name's gap with it), i.e.
  *delivering beta through the surprise measure itself* — "beta-delivery via the back door." **Detection tell
  (new, reusable):** for a per-name event-alpha condition the **SPY-regime `flat` bucket must stay solidly
  positive**; flat-negative with down ≫ up = beta-delivery via event selection, kill at screen. The fix is
  to neutralise the market component *before* entry (market-neutral residual), not a regime gate. See
  [[2026-06-09-pead-earnings-gap-screen-reject]]; cross-ref [[aliased-regime-sensitivity]] (the cross-sectional
  regime-tertile sign-flip variant).

- **[[pead]] market-neutral gap residual** (2026-06-09) — the **second condition-screen** instance and the
  one that hardens the failure mode into a *class*-level claim. The run-#1 successor was built to kill the
  price-gap proxy's beta by explicitly subtracting the same-night SPY gap (in ATR units) before entry:
  `residualAtr = (open[g]−close[g−1])/atr[g−1] − (spyOpen[g]−spyClose[g−1])/spyAtr[g−1]`. **It failed in both
  arms** (no vol gate, and relVol≥1.5): the 20d SPY-regime sign-flip **persisted** (no-vol down +0.90% / flat
  **−0.52%** / up −0.57%; relVol down +1.07% / flat **−0.20%** / up −0.89%), flat-tape still negative, headline
  20d lift negative+sub-SE. **Durable finding:** the beta an earnings gap delivers is *not* the removable
  same-day SPY-index-gap factor — subtracting that exact term left the sign-flip intact (the relVol arm's
  regime spread even *widened*, [[thinning-not-selecting]]). So beta-delivery via an event measure can be
  **irreducible to a single same-day market factor** — an OHLCV market-neutral residual cannot strip it; only
  a price-independent surprise signal (EPS-gated) might. **Detection tell (hardened):** the "flat tertile must
  stay solidly positive" rule survives a same-factor neutralisation attempt → flat-negative is a *class*-level
  kill, not a one-parametrisation artifact. See [[2026-06-09-pead-market-neutral-residual-screen-reject]].

- **[[pead]] EPS-surprise-gated residual** (2026-06-09) — the **third condition-screen** instance, and the
  one that exhausts PEAD's surprise-proxy axis. The residual reused verbatim, with a **price-independent**
  fundamental EPS sign gate added: fire only when the same earning's `surprise > 0` (`beatEstimates()`,
  sign-only — never magnitude). A signal owing *nothing* to the price gap. **It failed the same way:** 20d
  SPY-regime down +0.99% (n=664) / flat **−0.31%** (n=903) / up −0.51% (n=873), flat-tape negative, edge
  only in down-tape; near-equal tertile firing (a genuine cross-sectional flip). Headline 20d meanLift
  **−0.0069% = 0.03× SE** — the condition's absolute 20d return exactly equals the universe baseline (pure
  earnings-day beta, zero lift). The EPS gate thinned the residual population ~21% (3,093→2,443) — the
  intended removal of the positive-gap/negative-surprise cell — without flipping the flat tertile positive
  ([[thinning-not-selecting]] w.r.t. this failure mode). **Durable finding (strictly stronger):** the beta
  an earnings-event long entry delivers is irreducible to **every surprise proxy expressible on current
  data** — not just a single same-day market factor (the residual finding) but also the **fundamental EPS
  surprise sign**. The "flat tertile must stay positive" tell now has **three** confirming instances and
  survives both an OHLCV same-factor neutralisation *and* a price-independent fundamental gate. See
  [[2026-06-09-pead-eps-gated-residual-screen-reject]].

_No G16-firewall instance yet._ G16 was implemented in #102 (ADR 0013); no candidate has been rejected by
the **firewall** gate itself at the time of writing (2026-06-06). When one is, record it here with:
block(s) failed, `strategyCalmar` vs `benchmarkCalmar`, whether it nonetheless cleared the absolute floors
(G1/G2/G9/G15), and the regime in which its beta was sourced.

## Related

[[component-firewall]] · [[long-premise-in-narrow-leadership]] · [[participate-and-lose]] · [[the-funnel]]
