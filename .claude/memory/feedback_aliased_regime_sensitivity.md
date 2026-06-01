---
name: aliased-regime-sensitivity
description: "Aliased Regime Sensitivity (ARS) — a parameter-fragility pattern where aggregate edge sits in a noise band but per-window edges flip sign at specific regime gates under ±1 parameter shifts, and pass/fail across the parameter neighborhood is non-monotone. Worse than simple brittleness because the parameter dimension is structurally the wrong abstraction for the alpha hypothesis."
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 7571f7f6-71bf-476b-8e88-d8d2a44467a6
---

**Rule**: When a strategy's per-window OOS edge sign-flips at specific regime windows (e.g., G6 2020 / G7 2018-Q4) under a single-step shift in any parameter, AND pass/fail across the parameter neighborhood is non-monotone (e.g., {N-1: pass, N: fail, N+1: pass, N+2: pass}), declare **Aliased Regime Sensitivity (ARS)** and reject the strategy. The parameter dimension is structurally inappropriate for the alpha hypothesis; no operating point is robust.

**Why**:

- Discovered 2026-05-29 on Idunn sweep (lookback ∈ {8, 9, 10, 11} × Block A + B). All 4 lookbacks passed Block A. Block B aggregate edges sat in 0.12-0.48% noise band. But verdicts came out **{8: PASS, 9: FAIL G6, 10: FAIL G1+G5+G7, 11: PASS}** — both edges of the neighborhood passed cleanly; both middle values failed on different regime gates.
- Per quant 11th consultation (2026-05-29): this is the **conjunction of discrete parameter aliasing** (signal-processing concept — discrete sampling of an underlying low-frequency oscillation creates spurious alias verdicts) **and regime-conditional alpha** (Ang & Bekaert 2002; Guidolin & Timmermann 2007). The lookback dimension is aliasing against regime-specific microstructure cadences (e.g., 2020 COVID has a ~9-10 day reversal pattern; lb=10 catches the bottom of false rallies, lb=8/11 sample off-phase and miss them).
- This is **strictly worse than simple brittleness** (Pattern A — only one value passes) because Pattern A is at least interpretable as "right answer exists, narrow window." ARS is interpretable as "**there is no right answer; the parameter dimension is the wrong abstraction for this regime.**"

**How to apply**:

- **Detection signature** (all 4 must hold):
  1. Aggregate per-trade edges across the parameter neighborhood sit in a noise band (~3σ of sample mean)
  2. Per-window edges flip SIGN, not just magnitude, on regime-gate windows (G6 / G7 specifically)
  3. Pass/fail across the neighborhood is non-monotone
  4. Trade counts are stable across the neighborhood (rules out population effects)
- **Framework implication**: when ARS is detected, the conclusion is **not** "tune the parameter better" or "find a wider band." It is **"the parameter is structurally inappropriate; redesign the condition or abandon the premise class."**
- **Don't ship the passing values.** Even if {N-1, N+1} pass standalone, they sit one step from failing neighbors. Per quant: "you don't have alpha; you have parameter-luck."
- **Detection at condition-design time** (when `/condition-screen` is built): run candidate at P-1, P, P+1 against the universe and check firing rate, mean forward return, and regime-conditional firing rate. ARS shows up as a sign-flip on mean forward return between P and P±1 even when firing rate is stable.

**Concrete example (Idunn, 2026-05-29)**:

| Lookback | Aggregate Block B edge | 2018-Q4 chop edge | 2020 COVID edge | Verdict |
|---|---:|---:|---:|---|
| 8 | +0.45% | +0.15% | +0.25% | **PASS 10/10** |
| 9 | +0.36% | +0.31% | **−0.07%** | FAIL G6 |
| 10 | +0.12% | **−0.45%** | +0.09% | FAIL G1+G5+G7 |
| 11 | +0.48% | +0.10% | +0.71% | **PASS 10/10** |

Aggregate edge spread: 0.36pp (within 3.6σ on 1000-trade sample). Per-window signs at 2018-Q4 and 2020 COVID flip multiple times across the ±3 neighborhood with no monotone structure. Classic ARS.

Related: [[parameter-fragility-must-be-verified]], [[mean-reversion-pullback-known-weakness]], [[regime-conditional-portfolio-framework]].
