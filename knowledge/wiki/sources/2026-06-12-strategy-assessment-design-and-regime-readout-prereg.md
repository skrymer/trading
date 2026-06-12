---
type: source
title: 2026-06-12 — Strategy-assessment funnel design + regime read-out pre-registration signed
summary: Design session → ADR 0022 (assessment funnel) + ADR 0023 (read-out revival); two quant consults — battery design, then pre-reg v0→v1 CHANGES-REQUIRED→SIGNED-OFF. No run fired.
status: active
tags: [design-session, quant-consult, assessment, regime, pre-registration]
sources: ["docs/adr/0022-strategy-assessment-is-a-separate-non-adjudicating-funnel.md", "docs/adr/0023-regime-read-out-revived-as-pre-registered-gate-able-series.md", "CONTEXT.md"]
related: ["[[strategy-assessment]]", "[[regime-read-out]]", "[[regime-conditional-portfolio]]", "[[component-firewall]]"]
updated: 2026-06-12
---

# 2026-06-12 — Assessment-funnel design + regime read-out pre-registration

## What was run

No backtest. A `/grill-with-docs` design session (operator pain: the validation funnel short-circuits
at the first failing binding layer, so a Block-A death reveals nothing about the rest of the tape) plus
**two quant consults**, producing ADR 0022, ADR 0023, five new CONTEXT.md terms, and a **quant-signed
regime read-out pre-registration v1** (full spec: [[regime-read-out]]).

## Decisions (recorded in ADR 0022 / 0023; see [[strategy-assessment]] for the methodology)

- A **separate, non-adjudicating assessment funnel** (`/assess-strategy`, to-build) — runs everything,
  reports everything, decides nothing; accepts dead and settled configs alike.
- **Full C-span (2021–25) disclosure** in assessments — the operator's costed overrule of the quant's
  C-coarse recommendation; floor = contamination stamps + permanent operator-eyeballed-C lineage
  annotation surfaced by the deflated-Sharpe readout.
- The shelved 3-axis regime read-out is **revived** as a pre-registered, gate-able series under a
  rescue-forbidden boundary (the revival clause in [[regime-conditional-portfolio]] is now met).

## Quant consult 1 — assessment battery design

- **Spine = a single 25y walk-forward**, not the 4-block battery: block-range slices of its windows are
  *proxies* for the firewall's block verdicts (each firewall block is its own walk-forward with its own
  IS-anchoring and window phasing; per-window stats G3/G4/G5 and per-block Calmars genuinely differ),
  but the additive monthly buckets (ADR 0006) fully recover the GFC/COVID mandate slices.
- Complements: continuous 25y backtest (the only artifact showing the real un-stitched drawdown path —
  the WF stitch omits IS-window drawdowns per ADR 0005), Monte Carlo on stitched trades, deflated-Sharpe
  flag; Random-ranker baseline **only** for permissive-entry + ranker-selects candidates.
- **C-exposure cost framing** (recommendation overruled, preserved verbatim for the record): *"Full
  per-window C numbers buy ~10% more situational awareness; the price is the permanent, uncounted
  contamination of the only true out-of-sample block in the system, for the whole premise family. That
  is the single most expensive trade in the platform."*
- **Honest vs theater regime decomposition**: descriptive per-regime edge ± date-clustered SE + N with a
  hard insufficient-N floor (~30 trades) = legitimate deployment-timing/sizing context; per-regime
  backtests, regime-gate suggestions, or point forecasts = regime-overfitting theater (ARS — see
  [[aliased-regime-sensitivity]]).

## Quant consult 2 — pre-registration review (v0 → v1, SIGNED-OFF)

Two **blocking defects** caught in v0:
1. The gap axis must read the **stateless three-way `gapSmoothed` cut**, not the latched `schmittOn`
   boolean — the latch would collapse NEUTRAL and make NARROW/GRIND unreachable.
2. A **0% direction dead-band** makes UP/DOWN flip on noise and starves FLAT — fixed at ±2%.

Plus: CRISIS = washout-only (reuse the frozen veto verbatim; a new vol/direction OR-leg = three fresh
ARS surfaces); exactly **one hysteresis stage** (input EMA10 + 5-day label dwell, CRISIS enters
immediately/exits with dwell — never double-latch); σ bands calibrated on **Block A only** (2000–2014,
the design-safe window); validation anchors must be market-consensus dates, never reverse-engineered
from where the classifier fires. v1 with all amendments: **SIGNED-OFF**. Constants → frozen in
`RegimeReadoutParams.FROZEN` at build time; methodology → [[regime-read-out]].

## Scope additions (label consumers, no re-freeze)

Market-scoped **regime×sector return matrix** endpoint (spell-clustered SEs, spell-count caveat) +
**sector×regime drill-down** in the assessment's regime decomposition (insufficient-N floor per cell).

## What it taught (durable)

- A 25y WF can serve as a **high-fidelity proxy** for the block battery in a *reporting* context, but
  never reproduces the block verdicts (IS-anchoring differs) — label slices as proxies.
- The C-leak is about the **tape, not the block label**: the spine's 2021–25 windows are the same leak
  surface as "Block C". Classifier self-validation on 2021–25 **market** data is outside the leak
  surface (no strategy P&L involved).
- Regime gates are not forbidden — **rescue is**: ex-ante regime premises on fresh lineages are legal;
  "exclude the regime the report showed losing" is a disguised re-run, refused at the lineage DISTINCT
  gate. ^[inferred — synthesis of the lottery-screen and mean-reversion precedents, quant-endorsed this session]

## Pages updated

Created [[strategy-assessment]] + [[regime-read-out]]; re-statused the read-out section of
[[regime-conditional-portfolio]] (revival clause met; the Component Firewall section stays shelved).
