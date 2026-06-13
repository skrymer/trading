---
type: query
title: Regime classification v3 — deep-research brief (how to determine THRUST/CRISIS/GRIND/NARROW/CHOP)
summary: /deep-research brief for a pre-registrable classifier resolving all five regimes — esp. the GRIND/NARROW/CHOP trichotomy v2's daily axes couldn't separate (ADR 0024). Answered 2026-06-14.
status: active
tags: [methodology, regime, pre-registration, classifier, research-brief, deep-research]
sources: ["docs/adr/0024-regime-read-out-v2-accepted-with-limitations.md", "docs/adr/0025-strategy-assessment-emits-applicability-ratings-and-the-decision-names-its-dimension.md"]
related: ["[[2026-06-14-regime-classification-v3-research]]", "[[regime-read-out]]", "[[strategy-assessment]]", "[[minervini-vcp-breakout]]", "[[aliased-regime-sensitivity]]"]
updated: 2026-06-14
---

# Regime classification v3 — deep-research brief

> **ANSWERED 2026-06-14 →** [[2026-06-14-regime-classification-v3-research]] (20 adversarially-verified
> claims; 5 refuted; 20 sources). Build tracked as **issue #168**. Headline: a return+vol classifier
> *structurally cannot* resolve GRIND/NARROW/CHOP (no breadth channel) — resolve them with a
> cross-sectional **concentration** axis (NARROW) + a **multi-week trend-efficiency** axis (GRIND/CHOP)
> layered over the CRISIS/THRUST backbone, validated vs a random-walk surrogate + frozen-parameter OOS.
> The prompt below is retained verbatim as the canonical research question.

**Status: answered 2026-06-14 — see the callout above.** This was the prompt fed to `/deep-research` for the
v3-regime-classifier work (the ADR 0024 "from-scratch new pre-registration on a new axis" path). It is the
open dependency the [[strategy-assessment]] regime/sector **applicability ratings** (ADR 0025) and any
regime-specialist *stable* hang on: today only CRISIS (Grade A) and THRUST (Grade B) are gateable —
GRIND/NARROW/CHOP sit below the v2 axes' resolving power and are `unrateable`. To route a stable of
per-regime specialists we need a classifier that reliably tells those apart.

When run, `/deep-research` should file its findings as a dated `sources/` page and update
[[regime-read-out]]; this query page then gets upgraded off `status: seed` with the synthesized answer.

---

## The prompt (paste into `/deep-research`)

> **How can equity-market regimes be robustly and *pre-registrably* classified into five states — THRUST
> (broad risk-on rally), CRISIS (drawdown/panic), GRIND (low-volatility steady uptrend), NARROW (up-tape
> but narrow mega-cap leadership), and CHOP (directionless/rangebound) — using only daily US-equity
> price/volume/breadth data sourceable back to ~2000?**
>
> **Context / what we've already established (don't re-derive):**
> - We built a daily classifier on three axes — market breadth (level + slope), a leadership-concentration
>   gap (SPY return minus the median stock's return), and SPY realized volatility — plus two crisis legs (a
>   sustained breadth "washout" and a −20% drawdown from the trailing 252-day high). Against ~19
>   consensus-dated historical regime spans it **reliably identifies only CRISIS and THRUST**. It **cannot
>   separate GRIND / NARROW / CHOP** — those three collapse below the resolving power of those cheap daily
>   axes; CHOP degenerated into a residual "unclassified" bucket.
> - So the core unmet need is: **what additional or alternative axis/method reliably distinguishes the
>   non-crisis, non-thrust states from each other** — specifically narrow-leadership-up vs broad-low-vol-grind
>   vs directionless-chop.
>
> **Research questions:**
> 1. **Methods.** Survey how the academic literature and quant practitioners identify equity-market regimes
>    — Markov-switching / Hidden Markov Models, change-point detection, clustering, rule-based state machines,
>    dispersion/correlation regimes, volatility-term-structure regimes, breadth/participation models, factor-
>    and sector-leadership rotation models. For each: what states it resolves, its discriminating power on the
>    *grind/narrow/chop* distinction specifically, and its known failure modes.
> 2. **Discriminating axes.** Which *measurable daily signals* best separate **narrow vs broad leadership**
>    (e.g. cross-sectional return dispersion, equal-weight vs cap-weight spread, % of stocks outperforming
>    the index, sector-participation breadth, top-N concentration) and best separate **trending vs
>    choppy/rangebound** (e.g. efficiency ratio / fractal dimension, ADX-style trend strength,
>    realized-vs-implied vol, autocorrelation of returns, Hurst exponent)? Cite empirical evidence each
>    actually discriminates, not just that it's plausible.
> 3. **Pre-registration & validation.** Since our methodology forbids fitting a regime classifier to outcomes
>    after the fact (data-snooping), how do practitioners *pre-register* a regime model and validate it
>    against **uncontaminated ground truth** — held-out spans, externally-defined regime dates (NBER,
>    published bear-market/correction chronologies, VIX-regime datasets), or out-of-sample anchors? What
>    counts as a defensible ground-truth label set for these five states?
> 4. **Robustness.** Which approaches are *stable* (don't flip-flop, survive small parameter perturbations)
>    vs notoriously overfit / regime-look-ahead-prone? How is regime-classifier stability measured (dwell
>    time, flip rate, transition-matrix stationarity)?
>
> **Hard constraints for any recommendation:**
> - **Data:** must be computable from EODHD-sourceable US-equity history (price, volume, fundamentals, sector
>   membership, market cap, breadth derivable from the universe) spanning **2000–2025**. Flag anything needing
>   data we likely can't source that far back (e.g. clean intraday, options-implied surfaces pre-2010, credit
>   spreads).
> - **Strategy-blind:** the classifier must be computable from market data alone — never from any trading
>   strategy's P&L.
> - **Frequency:** daily or lower; usable as a low-frequency state label, not an intraday signal.
>
> **Deliverable:** a synthesis that (a) ranks the candidate axes/methods by their power to resolve the
> **grind/narrow/chop** trichotomy specifically, (b) names the 2–3 most promising *pre-registrable* designs
> with their data requirements and validation approach, and (c) flags which are overfit-traps to avoid.
> Cited throughout.

---

## Scoping calls (decide before running)

- **Taxonomy:** the prompt fixes the 5-label set but invites the research to critique it (it may argue
  narrow/grind/chop should collapse to 2 states or split to 4). Keep open-to-critique unless we want the 5
  treated as immutable.
- **Academic vs practitioner weighting:** currently asks for both equally.
- **Sequencing:** this is the ADR 0024 v3 path — a *from-scratch* pre-registration, validated on
  uncontaminated ground truth. It does **not** reopen v2 (frozen). Run when we pick up the regime-specialist
  stable; not a prerequisite for shipping the ADR 0025 applicability-rating redesign (Broad + Regime ship
  now; Sector is `unrateable-pending` on issue #167, a separate engine task).
