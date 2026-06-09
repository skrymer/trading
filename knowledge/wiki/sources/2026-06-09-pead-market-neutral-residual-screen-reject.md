---
type: source
title: PEAD market-neutral gap residual — design-time /condition-screen REJECT (2026-06-09)
summary: PEAD market-neutral gap residual REJECTED at /condition-screen — SPY-regime sign-flip persisted after neutralisation (both arms); price-proxy class condemned, escalate to EPS-gated residual.
status: stable
tags: [candidate, event-driven, earnings, condition-screen, failure-mode, beta-delivery]
sources: ["strategy_exploration/dossier/condition-earningsgapresidual.jsonl", "docs/adr/0007-condition-screen-blockc-cap.md"]
related: ["[[pead]]", "[[beta-delivery]]", "[[aliased-regime-sensitivity]]", "[[2026-06-09-pead-earnings-gap-screen-reject]]", "[[thinning-not-selecting]]", "[[lottery-vs-signature]]"]
updated: 2026-06-09
---

# PEAD market-neutral gap residual — design-time /condition-screen REJECT

The run-#1 successor to the [[2026-06-09-pead-earnings-gap-screen-reject|rejected price-gap proxy]].
The redesign's single job was to strip the SPY-direction beta out of the surprise measure so the 20d
flat-tape tertile would go solidly positive. It failed — **in both arms**.

## What ran

A new, screened-from-scratch inline-`script` condition: a **market-neutral gap residual**, ATR-normalised
and market-neutralised on the gap day `g`:

```
residualAtr = (open[g] − close[g−1]) / atr[g−1]               // the name's gap, ATR units
            − (spyOpen[g] − spyClose[g−1]) / spyAtr[g−1]      // minus the market's same-night gap
fire long when residualAtr ≥ θ
```

SPY's "previous day" anchored to the **name's** `prev.date` for calendar consistency
(`context.getSpyQuote(quote.date)` / `getSpyQuote(prev.date)`, every lookup null-guarded). Gap-day
timing, `year>1900` and `countTradingDaysBetween ≤ 3` guards reused verbatim from the dead condition.
`entryDelayDays=1` (forward returns start `close[g+1]`, post-entry drift only). Window
2000-01-01 → 2021-01-01 (Block C cap, ADR 0007). θ swept {0.25, 0.75, 1.25} (centre shifted down from
the raw gap's 1.0 because the residual is mechanically smaller). `any-earnings-no-gap` reference arm.
Script **quant-signed-off** before firing (no lookahead; unit-beta ATR residual confirmed the correct
run-#1 form; SPY-calendar-mismatch is fail-safe — a missing SPY bar drops the signal, never substitutes
a stale one).

- **Two arms, pre-registered:** Run A no volume gate; Run B same residual AND same-day
  `volume[g] / avg(volume, 20d) ≥ 1.5`. close-near-high deliberately excluded (re-imports the momentum
  being removed).
- Universe: 300-sym sanity subset (the full ~3,700-sym run OOMs the 20 GB heap — the universe-baseline
  forward-return load, independent of firing rate). Reduced universe **widens SEs → anti-conservative**,
  so a clean fail is a **strong** fail.
- Requests: `knowledge/wiki/entities/pead.earnings-gap-residual.request.json` (A),
  `pead.earnings-gap-residual.volgate.request.json` (B). Artifacts: `/tmp/pead-gap-residual-runA.json`,
  `/tmp/pead-gap-residual-runB.json`.

## Headline numbers — three binding failures, both arms

**(1) THE KILL — SPY-regime sign-flip PERSISTED after neutralisation** (20d post-entry meanLift by
SPY-trailing-return tertile):

| Arm | down | flat | up |
|---|---:|---:|---:|
| **A** (no vol gate) | +0.90% (n=869) | **−0.52%** (n=1139) | −0.57% (n=1080) |
| **B** (relVol ≥ 1.5) | +1.07% (n=707) | **−0.20%** (n=959) | −0.89% (n=919) |

The lift still flips sign across the tertiles and — fatal against the pre-registered rule — the
**flat-tape bucket is negative in both arms** (bar required no flip AND flat ≥ +1.0%). All the apparent
edge still lives in down-tape. Subtracting the same-night SPY gap **did not remove** the common-factor
component the tertiles isolate. Run B's regime spread (down−up = +1.96pp) is in fact *wider* than Run A's
(+1.48pp) — the volume gate is [[thinning-not-selecting]], not a rescue.

**(2) No detectable edge at the pre-registered horizon.** 20d meanLift-over-universe is **negative** in
both arms (A −0.13% = −0.46× SE; B −0.08% = −0.30× SE; bar ≥ +1.5% AND ≥ 2× SE), with `|lift| < SE` at
every horizon → no detectable edge anywhere. Horizon shape is non-monotone noise (5d + → 20d − → 40d +),
not the accumulating slow-diffusion signature. The condition's absolute 20d return is positive (+1.40%)
but the universe baseline returns the same — it is earnings-day beta, zero lift.

**(3) θ-monotonicity fails — the 1.25 cell is negative.** 20d lift by θ: A {−0.09%, −0.13%, −0.12%},
B {+0.05%, −0.08%, −0.06%} — a non-monotone island hugging zero; demanding a *stronger* residual surprise
does not raise drift. Firing not held within ±15% across cells (coarse `relativeStep` 0.67 > 0.5), so the
formal ARS firing-stability test is inconclusive-by-construction — but there is no edge for the parameter
to be fragile about. See [[aliased-regime-sensitivity]].

## What passed (did not rescue it)

- **Cadence:** A 3,093 / B 2,588 signals, present every year, `signalCount/dateCount ≈ 1.9` (not lumpy) —
  [[lottery-vs-signature]] NOT triggered.
- **Distinctness:** Jaccard vs `any-earnings-no-gap` pooled A 0.207 / B 0.173 — a genuinely distinct
  sub-population. Cold comfort again: distinct, but with no regime-orthogonal edge.

## What it taught (the durable, new finding)

- **The beta the earnings gap delivers is NOT the removable same-night SPY-index-gap term.** Explicitly
  subtracting that exact component left the regime sign-flip essentially intact (Run B even widened it).
  This rules out the **entire OHLCV market-neutral-residual repair path**, not just one parametrisation —
  a much stronger statement than the raw-gap reject, which only condemned the un-neutralised gap.
  Beta-delivery via an event measure can be **irreducible to a single same-day market factor**.
- **The "flat tertile must stay positive" tell hardens to a class-level kill.** It now has a second
  confirming instance *and* survives a same-factor neutralisation attempt — flat-negative is not a
  one-parametrisation artifact.
- **The volume confirmation is thinning-not-selecting w.r.t. this failure mode** — relVol ≥ 1.5 removes
  ~16% of trades without improving the keep set's regime-orthogonality.

## Disposition + next

**NOT a firewall death** — design-time condition reject, **no `config_hash` burned, the G13 brake is NOT
engaged**. The PEAD *class* (event-conditioned, per-name drift) survives; the **price-based surprise-proxy
class** (raw gap + market-neutral residual) is now condemned. Do **not** iterate this config (no θ/relVol
tuning, no bolting a regime gate on — that IS-fits the sign-flip that condemned it).

**KILL TRIGGER → ESCALATE** per the authoritative quant spec: the regime sign-flip persisted after
neutralisation, so fall back to the reserved **EPS-surprise-gated residual** — EPS confirms *sign only*
(`residualAtr ≥ θ AND surprisePercentage > 0`; never threshold on EPS magnitude — S4 restatement risk).
EPS fields are 100% populated 2000-2020 but PIT-suspect, which is why it was second. This is the **last
price-independent surprise test** before abandoning PEAD's surprise-proxy axis entirely.

## Pages updated

[[pead]] (residual rejected, price-proxy class condemned, EPS-gated residual promoted to next),
[[beta-delivery]] (second condition-screen / regime-sign-flip instance + the irreducibility finding),
`index.md`, `wiki/log.md`.
