---
type: source
title: PEAD price-gap proxy — design-time /condition-screen REJECT (2026-06-09)
summary: PEAD's first surprise proxy (OHLCV earnings-day price gap) REJECTED at /condition-screen — SPY-regime sign-flip + no post-entry edge + non-monotone gap-size island. Class alive, proxy dead.
status: stable
tags: [candidate, event-driven, earnings, condition-screen, failure-mode, beta-delivery]
sources: ["strategy_exploration/dossier/condition-earningsgap.jsonl", "docs/adr/0007-condition-screen-blockc-cap.md"]
related: ["[[pead]]", "[[beta-delivery]]", "[[aliased-regime-sensitivity]]", "[[thrust-degenerates-to-level]]", "[[lottery-vs-signature]]", "[[btc-tyr]]"]
updated: 2026-06-09
---

# PEAD price-gap proxy — design-time /condition-screen REJECT

## What ran

The first PEAD funnel step: an inline-`script` **EarningsGapCondition** screened in isolation. The
surprise proxy was the **OHLCV post-report price gap**, ATR-normalised
(`(open[gapDay] − close[preReport]) / atr[preReport]`). Screen design — the discriminating part —
fired the signal **on the gap day** with **`entryDelayDays=1`**, so every measured forward return
starts at `close[gapDay+1]`, strictly *after* the gap-day move. The forward-return lift therefore
isolates **post-entry drift** (what a next-session entrant actually captures), excluding the gap we
already missed. BeforeMarket/AfterMarket bar-anchoring resolved the gap day per name (the engine
stores literal `"BeforeMarket"`/`"AfterMarket"`, not `"BMO"`/`"AMC"`; unknown → AfterMarket).

- Universe: 300-sym sanity subset (the full ~3,700-sym run **OOM'd the 20 GB udgaard heap** — the
  universe-baseline forward-return computation loads all symbols' 25y of quotes regardless of how
  rarely the condition fires). Reduced universe **widens date-clustered SEs → anti-conservative**
  (fragile conditions pass *more* easily), so a clean fail here is a **strong** fail.
- Window 2000-01-01 → 2021-01-01 (Block C cap, ADR 0007). `gapAtr` swept 0.5 / 1.0 / 1.5.
  `any-earnings-no-gap` reference arm for the Jaccard/no-surprise null.
- Request: `knowledge/wiki/entities/pead.earnings-gap-screen.sanity.request.json`.
  Artifact: `/tmp/pead-earnings-gap-sanity.json`.

## Headline numbers — three independent binding failures

**(1) SPY-regime sign-flip at every horizon — the decisive kill** (post-entry meanLift):

| Horizon | down | flat | up |
|---|---:|---:|---:|
| 5d  | +0.58% (n=596) | +0.26% (n=929) | −0.15% (n=901) |
| 20d | **+1.73%** | **−0.38%** | **−0.56%** |
| 40d | +2.04% | −0.37% | −1.00% |

At 20d the lift flips sign across the SPY-trailing-return tertiles and — fatally for the
pre-registered rule — **flat is negative**. The entire headline edge comes from down-tape; there is
no event-alpha in normal or rising tape. This is **regime beta in an earnings costume**, the identical
structural death that killed [[btc-tyr]]. PEAD's pre-registered most-likely death ([[beta-delivery]]
via the gap selecting high-momentum names) **materialised**.

**(2) No detectable edge at the pre-registered horizon.** 20d meanLift **+0.114% = 0.44× clustered
SE** (bar was ≥ +1.5% AND ≥ 2× SE) — ~13× below threshold. Horizon shape **decays** (5d +0.20% →
20d +0.11% → 40d −0.02%), the inverse of the genuine slow-diffusion signature (which accumulates).
`signalToFillGap` small (+0.18%, posRate 0.515) — the edge isn't even front-loaded into the first
post-gap bar; it's just absent.

**(3) Non-monotone "island" gap-size sweep** (20d lift): 0.5 ATR −0.18% / 1.0 ATR +0.11% / 1.5 ATR
−0.20%. Only the centre cell is positive; demanding a *stronger* surprise turns lift negative — no
surprise-magnitude monotonicity, which directly contradicts the underreaction mechanism. Firing not
held within ±15% across cells (0.5 = +68%, 1.5 = −36% rel), so the formal 3-clause ARS firing-stability
test is **inconclusive-by-construction**; the non-monotone island + cross-sectional regime-tertile
sign-flip is the real tell. See [[aliased-regime-sensitivity]].

## What passed (did not rescue it)

- **Cadence:** 2,426 signals, 0.228% firing, present in all 21 years, well-distributed — not a
  one-tape lottery ([[lottery-vs-signature]] NOT triggered), the inverse of the [[btc-tyr]] 2009-14
  concentration.
- **Distinctness:** Jaccard vs `any-earnings-no-gap` pooled 0.162 — the gap arm *is* a distinct
  population, not just "every earnings date." Cold comfort: the distinct population it selects has no
  regime-orthogonal edge.

## What it taught

- **The price gap reads market-direction microstructure, not firm-specific surprise content.** On a
  strong-tape day the index itself gaps; a large positive name-gap is partly "the market gapped and
  this name came along" — exactly the common-factor component the SPY-regime tertiles isolate, and
  exactly what flips the sign. This condemns the **OHLCV-gap shortcut**, not the PEAD *mechanism*.
- **The kill is a clean retest harness for any price-based proxy:** `spyRegimeBreakdown` (SPY 20d-return
  tertiles) is computed independently of the entry condition, so a redesigned proxy is tested by the
  *same* down/flat/up machinery — apples-to-apples. ^[inferred]
- **Feasibility finding (recurs):** `BacktestContext.getSpyQuote(date)` **is reachable from an inline
  script** (`ScriptPredicateCompiler` signature `(stock, quote, context)`; `BacktestContext` carries
  the SPY quote map) — so a **market-neutral gap residual** is directly expressible today. Sector-neutral
  is **not** (no per-sector index quote map in `BacktestContext`) without an engine change.

## Disposition + next

**NOT a firewall death** — design-time condition reject, **no `config_hash` burned, the G13 brake is
NOT engaged**. The PEAD *class* is free; only the price-gap proxy is rejected. Do **not** iterate this
config (no tuning `gapAtr`, no bolting a SPY-regime gate on — that IS-fits the very sign-flip that
condemned it).

Quant-recommended successor (separate, screened-from-scratch condition):
1. **Market-neutral gap residual** (PIT-clean, OHLCV-only, attacks the death directly) — run first.
2. **EPS-surprise-gated residual** (hybrid; EPS confirms *sign* only, residual is the PIT-clean
   trigger) — reserved fallback, since EPS reintroduces S4 restatement risk.

## Pages updated

[[pead]] (proxy rejected, class alive, redesign direction), [[beta-delivery]] (first
condition-screen / regime-sign-flip instance), [[aliased-regime-sensitivity]] (non-monotone-island
instance), `index.md`, `overview.md`.
