---
type: entity
title: George
summary: 52-week-high anchoring RANKER (long top-N); deprecated when a byte-identical Random baseline matched its edge and beat its CAGR — the canonical beta-delivery case.
status: stable
tags: [candidate, ranker, deprecated, beta-delivery]
sources: ["strategy_exploration/GEORGE_STRATEGY_DEVELOPMENT.md", "strategy_exploration/BACKTESTING_FUNNEL.md", "strategy_exploration/dossier/"]
related: ["[[beta-delivery]]", "[[the-funnel]]", "[[component-firewall]]", "[[participate-and-lose]]", "[[long-premise-in-narrow-leadership]]", "[[gjallarhorn]]", "[[2026-06-08-random-baseline-reproducibility-fix]]"]
updated: 2026-06-08
---

# George

A long-only candidate built around a **52-week-high anchoring ranker**: score each name by its
nearness to its own 52-week high (`min(close / high52Week, 1.0)`) and hold the long top-N. The entry
stack is **tradability-only** (history / dollar-volume / minimum-price filters, no signal view) — the
**ranker does all the selecting**, so the entire claimed alpha lives in it. Anchor: George & Hwang
(2004, *J. Finance*) — nearness to the 52-week high is a standalone cross-sectional return predictor
(winners *approaching* their high keep winning), a price-**level** signal, not a return-change one.

George was the **first candidate of the research-widened search** (the first premise drawn from the
published cross-sectional-anomaly literature rather than the project's own deprecated families). The
strategy is named; the ranker stays strategy-neutral (it names the 52-week-high mechanic).

## Status

**DEPRECATED — capped premise (2026-06-04).** The anchoring *class* is deprecated *in the tradable
long-only liquid universe* — not "the paper is wrong" (see the weakest-habitat caveat below). Settled;
no successor.

> **Re-validation pending (2026-06-08, #135).** The capped-premise *reclassification* rested on the
> lost-to-Random comparison — but the engine's `RandomRanker` was **unseeded** until #130, so that
> "byte-identical seed-42 Random" baseline was not actually reproducible
> ([[2026-06-08-random-baseline-reproducibility-fix]]). The comparison is being **re-run with the now-seeded
> baseline** (#135). This does **not** reopen George as tradable: its independent `/strategy-screen` FAIL
> (Sharpe 0.14, GFC drawdown 44.7%) is unaffected and final. Only the *class-deprecation* read — and the
> lowered prior on the proximity condition — is under re-validation.

## Funnel history

| Stage | State |
|---|---|
| Spec (quant-signed) | ✅ |
| Build — ranker + 2 first-class exits, TDD (PR #90) | ✅ (first-class → no [[component-firewall]] G14 step) |
| `/strategy-screen` 2005–2015 | ❌ **FAIL** — G2 Sharpe 0.14 + G4 GFC drawdown 44.7% |
| **G-RANDOM baseline** (byte-identical, `ranker: Random`) | ❌ **George LOST to Random** → capped premise |
| `/validate-candidate` → `/monte-carlo` | ⛔ not reached — deprecated at screen |

The initial single-candidate read mistook George's failure for **[[participate-and-lose]]-in-crisis**
(distributed positive edge, WFE 1.21, one undefended 2008 window cratering Sharpe + DD → "premise alive,
build a defended successor"). The **Random baseline corrected that to capped premise** — it was crediting
entry-universe beta to the ranker.

## Verdicts — George vs the Random baseline

Screen window 2005–2015, 7 OOS windows, identical entry / exit / sizer / `maxPositions` skeleton (only
`ranker` swapped):

| Metric | George (52wk-high ranker) | Random baseline |
|---|---|---|
| Per-trade edge | +1.01% | **+1.08%** (matched) |
| Blended OOS CAGR | +1.1% | **+6.6%** (Random wins) |
| Positive windows | 5/7 | **6/7** (Random wins) |
| 2008 GFC window edge | **−14.3%** | **−2.1%** (Random survives) |
| Aggregate maxDD | 51.3% | **46.1%** (Random wins) |
| Win rate | **53.4%** | 50.0% |
| WFE | **1.21** | 0.86 |
| Trades | 556 | 1695 |

George wins **only** on win-rate and WFE — both payoff-shape artifacts, not signal. Random **matches**
the per-trade edge and **beats** George on every bottom-line metric that matters (CAGR, positive
windows, drawdown, crisis survival).

## Why it died

The ranker **carries no information**: a Random ranker on the same long-biased basket harvests the same
~1% per trade for free, so George's ~1% edge is **[[beta-delivery]]** — entry-universe beta, not ranker
alpha. Worse, the anchoring tilt is a **worse-than-noise GFC liability**: it concentrates into the
most-extended momentum names, so it underperforms even Random in the 2008 window (−14.3% vs −2.1%). A no-
information selector has **no defended successor** to build — defending it would defend nothing.

**Weakest-habitat caveat** ^[inferred] — the anchoring class was tested where it is structurally weakest,
so the deprecation is scoped to *this engine's tradable universe*, not a refutation of George & Hwang:
- **Long-only engine** — can't express the paper's *short* leg, where much of the published spread lives
  (the original 0.65%/mo is a long-short decile spread; George trades only the long top-tranche).
- **Liquidity pre-filter** — stripped the down-cap tier where the anchoring effect survives strongest
  (McLean–Pontiff: edges live down-cap).

The only honest re-test — a long-short decile on a down-cap universe — is **not buildable in a long-only
engine**, so the class is deprecated in the tradable universe rather than strictly killed.

## Failure modes hit

- **[[beta-delivery]]** — George is the canonical instance: a long book whose risk-adjusted return is
  just the index's, exposed by tying the candidate's per-trade edge / CAGR against a same-skeleton Random
  ranker. The Random match on edge + loss on CAGR *is* the beta-delivery signature.
- Initially mis-diagnosed as **[[participate-and-lose]]**-in-crisis; G-RANDOM reclassified it as a capped
  premise (the distributed-edge + one-bad-window pattern is necessary but not sufficient for "premise
  alive" — it survives a Random selector too).

## Reusable findings (durable)

- **The Random-baseline rule is now binding** — any permissive-entry / ranker-selects candidate must
  beat a **byte-identical Random-ranker baseline** on blended OOS CAGR **and** per-trade edge **and**
  positive-window count. Beating Random only on win-rate/WFE is a payoff-shape artifact, not signal —
  WFE must not launder a dead bottom line. This is the cheapest one-shot capped-premise check and is now
  a binding `/strategy-screen` gate (**G-RANDOM**). (memory: `feedback_random_ranker_baseline_mandatory`.)
- **Building blocks kept (PR #90)** — the `nearness52WeekHigh` ranker plus the `maxHoldingDays` and
  `belowPercentOf52WeekHigh` conditions are general, tested, first-class artifacts, reusable in other
  assembled strategies.
- **Prior lowered on the proximity *condition*** ^[inferred] — George's result sharply lowered the prior
  on the gate form of the same 52-week-high signal (a nearness-to-high *condition*): since the stronger
  *ranker* form turned out to be beta, the weaker *gate* form is even less likely to carry alpha. Screen
  it (if at all) as a falsification test, not a hopeful candidate.

## Related

[[beta-delivery]] · [[the-funnel]] · [[component-firewall]] · [[participate-and-lose]] · [[long-premise-in-narrow-leadership]] · [[gjallarhorn]] · [[minervini-vcp-breakout]] · [[purpose]]
