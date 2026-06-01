---
name: parameter-fragility-must-be-verified
description: "A strategy's verdict must survive ±1 step on every discrete parameter and ±10% on every continuous parameter. If the verdict flips on a single bar shift in a 10-day lookback, the edge is alignment-fitting, not structural. Verify parameter stability BEFORE promoting any strategy to TRADABLE, regardless of how impressive the center-config numbers look."
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 7571f7f6-71bf-476b-8e88-d8d2a44467a6
---

**Rule**: Any strategy with a TRADABLE verdict must survive perturbation of every numeric tunable:
- **Discrete dimensions** (lookback days, position counts, history requirements): ±1 step on the dimension
- **Continuous dimensions** (thresholds, multipliers, percentages): ±10% relative

A TRADABLE verdict that doesn't hold across this neighborhood is **alignment-fitting**, not edge-discovering, and won't survive live trading.

**Why**:

- Discovered 2026-05-29 on Idunn (promoted from VZ3-s3). The original VZ3-s3 had an off-by-one in the higher-low lookback (used `ref[ref.size - 10]` against an inclusive-range result, effectively reading 9 trading days back instead of 10). Under the buggy lookback=9, VZ3-s3 was TRADABLE in the firewall smoke test. Under corrected lookback=10, Idunn was REJECTED at Block B (G1_cagr 29.36% < 30% + G5_cov 2.86 + G7 chop 2018-Q4 −0.45%).
- The 1-day lookback shift moved Block B aggregate edge from +0.48% to +0.12%, flipped the 2018-Q4 chop window sign (+0.21% → −0.45%), and exploded G5 CoV from 0.70 to 2.86 — far larger than 220-trade-sample noise can explain.
- Per quant: the bars at t−9, t−10, t−11 are highly autocorrelated (typical 1-day autocorrelation of lows is 0.85-0.95). A structural mean-reversion edge at the ~2-week horizon should be approximately invariant across that 1-day shift. The fact that the edge collapses means it's tracking *which specific historical bars the index happens to hit*, not the structural feature the variable name claims.
- The framework gap: the v4 firewall validates ONE config. Whatever passes is presumed structural. There is no built-in test for parameter stability across the neighborhood. Going forward we need **G13 — Parameter Robustness** as a hard binding gate on every TRADABLE verdict.

**How to apply**:

- **Before promoting a strategy** (e.g. via `/create-condition` or any path that takes an inline-script research candidate to first-class): fire the firewall at the CENTER config AND at every ±1-step / ±10% neighbor of every numeric tunable. Accept TRADABLE only if all neighbors PASS the binding-layer gates.
- **During code review** for new conditions: if a parameter is hardcoded in a `RegisteredStrategy` (e.g. `pullback2of3(lookbackDays = 10, ...)`), require evidence that the ±1 step (lookback=9 and lookback=11) both produce comparable Block A and Block B numbers. "Comparable" = aggregate edge within ±0.2pp, CoV within ±0.3, no flipped gate verdicts.
- **When reading firewall verdicts**: a TRADABLE smoke test on a candidate that uses any parameterized condition is presumptive at best. The verdict isn't durable until parameter stability is verified.
- **When debugging or fixing condition code**: if you change a numeric constant (off-by-one, threshold tune, etc.), the change INVALIDATES any prior firewall verdict on the strategy. Re-fire the firewall AND verify the new center config is parameter-robust before relying on the new verdict.
- **Selection bias trap**: if you observe that lookback=N fails and lookback=N±1 passes, do NOT promote the N±1 value. Picking the value that passes after seeing the OOS result is data-snooping laundered through "we're matching shipped code" or "we found the right value." If N±1 looks promising, it's a candidate for fresh `/strategy-screen` → `/validate-candidate` as a separate candidate with a pre-registered parameter justification — not a retroactive rescue of the failing N candidate.

**Concrete trigger for G13 framework gate** (to be added to `/validate-candidate` after quant sign-off on step sizes):
- Discrete: ±1 step (quant draft, pending empirical calibration from the brittleness sweep + a known-passer sweep)
- Continuous: ±10% relative (quant draft)
- Placement: after G7, on the center config that already passed G1-G11
- Verdict aggregation: TRADABLE only if all neighbors PASS; PROVISIONAL if 1 neighbor fails on G5/G7 only; REJECTED otherwise
- Skip if no tunable parameters (e.g. zero-config conditions like `marketUptrend`)

Related: [[regime-conditional-portfolio-framework]], [[script-conditions-must-be-promoted]], [[lottery-screen-diagnostic]], [[mean-reversion-pullback-known-weakness]].
