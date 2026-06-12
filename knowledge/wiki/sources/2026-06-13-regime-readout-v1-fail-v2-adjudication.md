---
type: source
title: 2026-06-13 — Regime read-out v1 anchor FAIL → v2 revision → ACCEPT-WITH-LIMITATIONS
summary: v1 failed 18/19 anchors; diagnostics found the EW mean tail-contaminated; quant v2 (median leg, dd-CRISIS) passed 7/19 — adjudicated a CRISIS detector; GRIND/NARROW/CHOP ungateable.
status: active
tags: [regime, pre-registration, anchor-check, quant-consult, failure-finding]
sources: ["docs/adr/0024-regime-read-out-v2-accepted-with-limitations.md", "strategy_exploration/assessments/anchor-status.json", "reference_check/regime_readout_anchor_check.py", "reference_check/regime_readout_axis_diagnostics.py"]
related: ["[[regime-read-out]]", "[[strategy-assessment]]", "[[aliased-regime-sensitivity]]", "[[regime-conditional-portfolio]]"]
updated: 2026-06-13
---

# Regime read-out: v1 FAIL → v2 → accepted with limitations

## The arc (three computes, two quant adjudications, one binding protocol)

1. **v1 first compute (2026-06-12): FAIL 18/19 anchor spans.** CHOP absorbed 39% of history,
   29% unlabeled, GRIND/NARROW near-unreachable, median spell 5 days.
2. **Axis diagnostics (Block A, read once)** found the root causes:
   - **The EW mean is tail-contaminated** — micro-cap moonshots/bad prints dragged the gap to
     p1 = −99% and *manufactured* the spurious THRUST years (2013, 2023). The trust-breach-by-year
     shape (≈30% of *every* year, collapsing to ~2% in 2019/2023) also exposed **year-over-year
     data-quality drift**: a mean-based gap is non-stationary across the firewall blocks.
   - The fail-closed SE guard was **fail-blind** (it fires exactly in high-dispersion
     crash/recovery tape).
   - The −20% drawdown leg separated all five consensus bears from all corrections perfectly
     (2018-Q4 just under at −19.3% close-basis, as predicted).
   - Sector participation (operator-proposed 4th axis): flat per-year means even after normalizing
     for ETF availability — ruled OUT (collinear with breadth level; an ARS surface buying nothing).
3. **v2** (quant-prescribed, every change mechanism-traced, never coverage-chased): median EW leg;
   tercile gap bands frozen at the clean Block-A p33/p67 (−0.007746/+0.003167, pre-registered
   derivation rule, read once — agreement vs SPY−RSP rose to **80.7%**, validating the leg);
   dd-CRISIS OR-leg (−20%/252-bar, dwell-debounced entry; washout entry stays immediate); trust
   guard demoted to advisory thin-N; NARROW `not-DOWN` (the operator's bear-masquerade amendment);
   warmup 180→400; the sole anchor amendment (2022 span → the consensus bear 2022-06→10).
4. **Cycle 2 (2026-06-13): FAIL 12/19 — and per protocol, no cycle 3.** PASS: CRISIS ×5 (GFC 89%),
   THRUST-2003 (71%), CHOP-2015-H1. FAIL: both remaining CRISIS spans (anchor-vs-definition
   artifacts), THRUST-2009 (**0%** — see the structural finding), GRIND 0–29%, NARROW 29–48%,
   stability near-miss (13d/13 flips vs 15/12).

## The adjudication (ADR 0024)

**ACCEPT-WITH-LIMITATIONS** — per-label trust grades: CRISIS **A** (trustworthy, gateable; a
*confirmation* of a ≥20% drawdown/washout, never an early warning — it lags topping phases);
THRUST **B precision-only** (gateable author-beware); GRIND/NARROW/CHOP **D** (descriptive-only,
**gating rejected at build time** — `RegimeLabelCondition.GATEABLE_LABELS`). The assessment's
regime table banners the D-grade rows; the current-regime line collapses them to "uptrend —
fine-grain label unreliable".

## Durable findings

- **The drawdown-recovery blind spot** (the headline structural finding): a drawdown-from-high
  CRISIS leg with precedence necessarily publishes CRISIS through the entire post-crash recovery
  (~12 months below the trailing high) — so the most violent thrusts in the data (2009-Q2/Q3,
  2020-Q2/Q3) are *structurally* invisible to THRUST. Catching slow bears and seeing post-crash
  thrusts are mutually exclusive on these axes. An accepted trade-off, not a tunable.
- **Full-universe EW means are unusable as market-level signals** — the tail contamination +
  data-quality drift generalize beyond this classifier: any "average stock" series on this platform
  should be a median (or validated against RSP). ^[inferred — the quant ruled it for the gap leg;
  the generalization to other consumers is synthesis]
- **Three cheap daily axes cannot separate grind-from-chop nor narrow-from-chop.** The trichotomy
  is below their resolving power at day granularity. A legitimate v3 is a from-scratch
  pre-registration only: new axis, parameters fixed before any coverage is computed, validated on
  **uncontaminated ground truth** (the 19 anchor spans are burned as a primary gate).
- **The revision-loop protocol held**: every v2 value traces to a mechanism diagnosis or a
  pre-registered distribution read; the iteration ceiling was honored (no cycle 3); the sole anchor
  amendment was a ground-truth fix held regardless of outcome.

## Pages updated

[[regime-read-out]] rewritten to the v2-adjudicated spec; CONTEXT.md term updated; ADR 0024 records
the verdict; `anchor-status.json` → `ACCEPT_WITH_LIMITATIONS` (the assess-strategy pre-flight's
regime-gate prerequisite consumes it).
