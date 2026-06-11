---
type: source
title: Quality-tilt /condition-screen flat-tertile kill-test — PASS
summary: Quality gate (≥80) cleared its flat-SPY-tertile /condition-screen kill-test; first long premise to clear the tell that killed PEAD ×3. Not validated — next is the Random-ranker null.
status: stable
tags: [candidate, fundamentals, quality, condition-screen, beta-delivery]
sources: ["docs/adr/0019-fundamentals-are-point-in-time-reference-data-and-quality-is-a-cross-sectional-percentile.md", "docs/adr/0007-condition-screen-enddate-is-block-c-capped.md", "knowledge/wiki/sources/2026-06-10-quality-tilt-build-complete.md"]
related: ["[[quality-profitability-tilt]]", "[[beta-delivery]]", "[[aliased-regime-sensitivity]]", "[[pead]]", "[[the-funnel]]"]
updated: 2026-06-11
---

# Quality-tilt /condition-screen flat-tertile kill-test — PASS

## What was run

`/condition-screen` of the `fundamentalQualityPercentile` gate (`minPercentile=80`) **alone** — the
pre-registered step-1 kill-test for [[quality-profitability-tilt]]. Frozen 300-sym sanity universe
(`condition-screen-sanity-universe-v1`), 2000-01-01 → 2021-01-01 (Block-C-capped, ADR 0007), horizons
[5,10,20], `entryDelayDays=1`, RS70 (`relativeStrengthPercentile minPercentile=70`) as the Jaccard
reference. Interpreted by the `condition-screen-analyst`. This ran only after the full-universe fundamentals
re-ingest + L2 quality-percentile recompute + the Udgaard `refresh/all-stocks` that carried the percentile
into the `trading` DB.

## Headline numbers

| Horizon | meanLift (cond − univ) | × clustered SE | hit-rate lift |
|---|---:|---:|---:|
| 5d | +0.089% | 1.98× | +1.2pp |
| 10d | +0.146% | 2.45× | +1.3pp |
| 20d | **+0.273%** | **3.19×** | +1.4pp |

Overall firing **16.9%** (< 33% amber), stable 12–21%/yr with **no collapse** and no early-2000s coverage
cliff (2000 fires 11.6%, 4,682 signals — the pre-mortem's *secondary* predicted death is also disconfirmed).
`nDates ≈ 5,278`; `signalCount/dateCount ≈ 34` — edge broad across days, not lumpy.

## The decisive gate — flat-SPY-tertile (the pre-registered KILL rule)

The pre-registration: *flat tertile `meanLift` must stay solidly positive at 10d AND 20d; flat ≤ 0 or
positive-only-in-down-tape = beta-delivery-via-selection = KILL.*

| Horizon | down | **flat** | up |
|---|---:|---:|---:|
| 5d | +0.165% | +0.096% | +0.025% |
| 10d | +0.330% | **+0.147%** | −0.008% |
| 20d | +0.627% | **+0.205%** | +0.018% |

**Flat is solidly positive at every horizon; no regime sign-flip; tertile firing balanced** (down 16.35% /
flat 17.20% / up 17.22%). The gradient is `down > flat > up` (quality earns its premium most in weak tape —
flight-to-quality) but **never inverts**. This is the exact opposite of the [[pead]] signature (flat
negative, edge only in down-tape, sign inversion). **KILL NOT triggered.**

## ARS sweep — anti-ARS (clean monotone dose-response)

`minPercentile` auto-swept to {72, 80, 88} (pre-reg nominally {75,80,85}; grid deviation noted, question
unchanged). 20d lift: 72 → **+0.20%**, 80 → **+0.27%**, 88 → **+0.36%** — **strictly positive and
monotone-increasing in the threshold** at every horizon. No sign-flip (ARS clause-1 fails). `swing/|center|`
0.60–0.76 (< 1.0, not even the monotone-steep tell). Firing moves ±39%/−41% rel — a percentile threshold
*is* a support cliff, so ARS clause-3 (stable firing) is unsatisfiable by construction; recorded as **clean
monotone dose-response, not fragility**. More-selective = more-lift is the right-signed behaviour for a real
quality premium.

## Jaccard vs RS70 — a distinct selection axis

Pooled **0.153**, max-year **0.249** (2020) — far below the (uncalibrated) 0.5/0.7 redundancy bands.
Overlap rises modestly in narrow-leadership years (2018 0.233, 2020 0.249) as quality and RS converge onto
mega-caps, but caps at ≤25%. **Quality is not relative-strength in disguise.**

## What it taught

- **First DISCONFIRMING instance of the flat-tertile [[beta-delivery]] detector** (3 prior confirming PEAD
  kills). The detector discriminates — it correctly stayed silent on a genuinely non-beta selector.
- Mechanistic support for the load-bearing claim that a **non-price fundamental selector does not inherit
  SPY through the entry** the way price-state premises ([[george]] / [[mrm]] / [[pead]]) did.^[inferred — screen-stage evidence, not the G16-firewall instance]
- `signalToFillGap` meanGap 0.104% (~38% of the 20d lift) — non-trivial; **carry to net-cost validation**,
  not a screen-stage flag.

## What it does NOT mean

**Not validation.** This is the *anti-conservative* 300-sym sanity universe (tighter clustered SEs than the
full ~3,900-sym population) — it may only **proceed-or-reject, never promote**. The 20d ARS swing (0.163%)
sat just under 2×SE (0.171%) on these tight SEs; a full-universe rescreen formally retires that caveat.

## Next gate

The pre-registered step 2: the **Random-ranker null at `/strategy-screen` on the full universe** —
`FundamentalQualityRanker` must beat a byte-identical Random ranker drawn from the *same*
`qualityPercentile≥80` eligibility set on **per-trade edge AND blended CAGR AND positive-window count**
([[george]] / [[mrm]] discipline). Clearing the flat-tertile test proves the *eligibility universe* isn't
pure beta; it does **not** yet prove the *ranker* adds selection over random-within-quality. That is the real
beta-delivery firewall for this candidate. No `/strategy-screen` or backtest has been fired yet.
