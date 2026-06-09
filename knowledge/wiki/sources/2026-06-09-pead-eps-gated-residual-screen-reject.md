---
type: source
title: PEAD EPS-surprise-gated residual — design-time /condition-screen REJECT (2026-06-09)
summary: PEAD EPS-surprise-gated residual REJECTED at /condition-screen — flat-tape sign-flip persisted after a price-independent EPS sign gate; pre-registered KILL fired, surprise-proxy axis EXHAUSTED.
status: stable
tags: [candidate, event-driven, earnings, condition-screen, failure-mode, beta-delivery]
sources: ["strategy_exploration/dossier/condition-earningsepsgatedresidual.jsonl", "docs/adr/0007-condition-screen-blockc-cap.md"]
related: ["[[pead]]", "[[beta-delivery]]", "[[aliased-regime-sensitivity]]", "[[2026-06-09-pead-market-neutral-residual-screen-reject]]", "[[2026-06-09-pead-earnings-gap-screen-reject]]", "[[thinning-not-selecting]]", "[[lottery-vs-signature]]"]
updated: 2026-06-09
---

# PEAD EPS-surprise-gated residual — design-time /condition-screen REJECT

The **third and pre-registered LAST** price-independent surprise proxy, succeeding the
[[2026-06-09-pead-market-neutral-residual-screen-reject|rejected market-neutral residual]]. Its single
job was to test whether a **fundamental** EPS-sign confirmation — a signal that owes nothing to the
price gap — could finally strip the SPY-direction beta that condemned the two price-based proxies. It
failed the decisive regime test the same way, and the pre-registered KILL clause fired.

## What ran

The [[2026-06-09-pead-market-neutral-residual-screen-reject|market-neutral residual]] script reused
**verbatim** (quant-signed-off for run-#1: no lookahead, unit-beta ATR residual, fail-safe SPY
anchoring), with **one** addition — a fundamental EPS sign gate ANDed onto the final boolean:

```
residualAtr = (open[g] − close[g−1]) / atr[g−1]
            − (spyOpen[g] − spyClose[g−1]) / spyAtr[g−1]
fire long when  residualAtr ≥ θ  AND  e.beatEstimates()     // surprise > 0, sign-only
```

`e` is the **same** earning that drives the gap day (latest `reportedDate ≤ qd`, `year > 1900`) — the
intended coupling: confirm the *sign* of the surprise that caused the gap. `e.beatEstimates()` =
`(surprise ?: 0.0) > 0.0`. **Sign only — never EPS magnitude** (S4 restatement risk; a restatement
rarely flips a real beat's sign). `entryDelayDays=1` (post-entry drift only), θ swept {0.25, 0.75, 1.25},
horizons 5/20/40, window 2000-01-01 → 2021-01-01 (Block C cap, ADR 0007), `any-earnings-no-gap`
reference arm (UNGATED), close-near-high excluded.

- **Run A only** (no volume gate). Quant concurred run-A-first; the result was not borderline, so the
  relVol arm was unnecessary.
- Universe: 300-sym sanity subset (the full ~3,700-sym run OOMs the 20 GB heap on the universe-baseline
  forward-return load). Reduced universe **widens SEs → anti-conservative**, so a clean fail is a
  **strong** fail.
- **Data-quality gate (PASS, run before authoring):** PRD `earnings` table, 300 sanity symbols,
  2000-2020 — `surprise` **100% non-null every year**; `surprise_percentage` only 79% (2000) → 95%
  (2020) non-null. So the gate uses `surprise > 0` (`beatEstimates()`), not `surprisePercentage > 0`,
  avoiding a ~20% early-window null-drop *orthogonal to the hypothesis* and divide-by-near-zero
  artefacts on penny-EPS names. Sign distribution non-degenerate (pos 43%→64% / neg 16%→28% / **zero
  41%→8%**); the large early zero bucket is genuine EODHD-reported zeros (not null-masquerade),
  correctly excluded by strict `> 0` as "unconfirmed sign." The gate thinned the residual population
  **~21%** (3,093 → 2,443 signals) — the intended modest thin removing the
  positive-gap-but-negative-fundamental-surprise cell.
- Request: `knowledge/wiki/entities/pead.earnings-eps-gated-residual.request.json`. Artifact:
  `/tmp/pead-eps-gated-residual-runA.json`. Script quant-signed-off before firing (2026-06-09).

## Headline numbers — an over-determined FAIL

**(1) THE KILL — flat-tape 20d sign-flip PERSISTED after the EPS sign gate** (20d post-entry meanLift by
SPY-trailing-return tertile):

| down | flat | up |
|---:|---:|---:|
| +0.99% (n=664) | **−0.31%** (n=903) | −0.51% (n=873) |

The lift sign-flips down→flat (+0.99% → −0.31%), the flat bucket is **negative**, and all apparent edge
again lives in down-tape (down−up = +1.50pp). Firing rates are near-equal across tertiles (0.00193 /
0.00251 / 0.00244), so this is a genuine cross-sectional regime sign-flip, not a support artifact. The
bar required NO flip AND flat ≥ +1.0%. This is the **third** consecutive PEAD proxy to print
flat-negative at 20d: raw gap −0.38%, residual −0.52% / −0.20%, **EPS-gated residual −0.31%.** 40d tells
the same story (down +1.70% / flat −0.030% / up −0.44%); 5d already flips (up −0.24%).

**(2) No detectable edge at the pre-registered horizon.** 20d meanLift-over-universe = **−0.0069%** =
**0.03× clustered SE** (SE 0.002694; bar ≥ +1.5% AND ≥ 2× SE). The condition's absolute 20d return
(+1.52%) **exactly equals** the universe baseline — pure earnings-day beta, zero lift. 5d lift +0.14%
(1.08× SE), 40d +0.28% (0.67× SE); `|lift| < SE` at 20d and 40d.

**(3) Horizon non-decay FAILED — non-monotone noise.** Lift 5d +0.14% → 20d −0.007% → 40d +0.28% (20d <
5d) — not the accumulating slow-diffusion signature underreaction requires.

**(4) θ-monotonicity FAILED — all three cells negative at 20d** {0.25: −0.056%, 0.75: −0.0069%, 1.25:
−0.070%}; the 1.25 cell is negative. Demanding a *stronger* residual surprise does not raise drift. See
[[aliased-regime-sensitivity]].

## What passed (did not rescue it)

- **Cadence:** 2,443 signals, present every year (18 → 204, monotone ramp with universe growth),
  `signalCount/dateCount` = 2443/1357 ≈ **1.80** (not lumpy) — [[lottery-vs-signature]] NOT triggered.
  The edge is *absent*, not concentrated.
- **Distinctness:** Jaccard vs `any-earnings-no-gap` pooled **0.163** (max 0.232, 2016) — a genuinely
  distinct sub-population. Cold comfort: distinct, but no regime-orthogonal edge. The EPS sign gate
  thinned ~21% without manufacturing edge — [[thinning-not-selecting]] w.r.t. the regime sign-flip.

## What it taught (the durable, new finding)

- **A price-INDEPENDENT fundamental gate did not strip the beta either.** Three independent surprise
  measures — raw OHLCV gap, OHLCV market-neutral residual, AND a fundamental EPS-sign confirmation — all
  delivered SPY-direction beta with a negative flat tertile. The earnings-event long entry's beta is
  **irreducible to ALL of**: the gap itself, the same-night SPY-index gap, *and* the EPS surprise sign.
  Beta-delivery via an event measure can be irreducible to **every surprise proxy expressible on current
  data**, not merely a single same-day market factor — a strictly stronger statement than the residual
  reject.
- **The "flat tertile must stay solidly positive" tell now has three confirming instances** and survives
  both an OHLCV same-factor neutralisation *and* a price-independent fundamental EPS-sign gate.

## Disposition + next

**NOT a firewall death** — design-time condition reject, **no `config_hash` burned, the G13 brake is NOT
engaged**. The pre-registered escalation condition ("if the flat-tape 20d tertile ALSO sign-flips
negative after EPS-gating") is **met** → **PEAD's surprise-proxy axis is EXHAUSTED**. Do **not** invent a
4th price proxy; do **not** iterate this config (no θ tuning, no bolting a regime gate on — that
IS-fits the sign-flip that condemned it). The slow-diffusion **mechanism** itself remains formally
untouched, but every entry proxy expressible on current data has now failed identically.

Per the pre-registered fork, escalate to operator + `quant-analyst`: **(a)** reconsider the PEAD premise
class entirely, or **(b)** the deferred **sector-neutral residual** — which needs the `BacktestContext`
sector-quote-map engine change (issue #143; sector-ETF OHLCV confirmed to 1998-12-22) and must NOT be
built before that lands, and even then as a **new** candidate from scratch, never bolted onto this dead
config. ^[inferred — the premise-class reconsideration routing is pending quant adjudication; the "axis
exhausted" conclusion is the pre-registered KILL clause firing on observed data.]

## Pages updated

[[pead]] (surprise-proxy axis exhausted; redesign #2 RUN+REJECTED), [[beta-delivery]] (third
condition-screen instance + the price-independent-gate irreducibility finding),
[[aliased-regime-sensitivity]] (third non-monotone-island / regime-tertile sign-flip note), `index.md`,
`wiki/log.md`.
</content>
</invoke>
