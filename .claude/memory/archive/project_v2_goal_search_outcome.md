---
name: project_v2_goal_search_outcome
description: The v2 overfitting-resistant goal search ran 21 candidates against VCP and concluded NO-GO — VCP unbeaten
metadata: 
  node_type: memory
  type: project
  originSessionId: 876abf58-fbe1-4ff6-a22b-465a1d0c28a0
---

The v2 "beat VCP's Calmar" goal search (concluded 2026-05-22) ended **NO-GO — stay with VCP**, no code change.

Design: three-block data firewall (Search 2000-2014 / Validation 2015-2020 / Lockbox 2021-2025), per-window median Calmar objective, 4 gates. Spec `/tmp/goal-spec-v2.md`, full results `/tmp/goal-results-v2.md`.

Outcome: 21 pre-registered single-variable VCP ablations run on Block A → 7 cleared Gate 1+2 (median per-window Calmar > VCP's 2.471, and 0 negative OOS windows). Top-3 finalists — H2b (volatilityContracted maxAtr 3.5→5.0), H8 (+priceAbovePreviousLow), H3a (volumeAboveAverage mult 1.2→1.0) — ALL failed Gate 3, the Block-B Wilcoxon paired test on data the search never saw. None beats VCP out-of-sample.

**Why:** the Block-A "improvements" were artifacts — apparent edge concentrated in single windows (lumpy, regime-dependent), not a durable broad-based edge. Same failure mode v1's C20 slipped through under weaker gates; v2's firewall + Gate 3 caught it pre-adoption.

**How to apply:** the single-variable-ablation-of-VCP space is exhausted — don't re-run it expecting a winner. VCP's entry gates, exit set, and regime filters are locally efficient. A future search must try genuinely different strategy structure, not VCP±1. The v2 harness (firewall + per-window-median-Calmar + Wilcoxon Gate 3) is the validated methodology to reuse. Relates to [[project_sizer_sweep_2026_04_17]].
