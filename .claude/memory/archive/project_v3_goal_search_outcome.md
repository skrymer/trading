---
name: project_v3_goal_search_outcome
description: v3 genuinely-different-structures goal search ran 18 candidates and concluded NO-GO — no candidate beats VCP in Block-B Wilcoxon despite higher median Calmar
metadata: 
  node_type: memory
  type: project
  originSessionId: 185506d9-5aab-4adc-bef2-82e39d635563
---

The v3 "genuinely different strategy structures" goal search (concluded 2026-05-24) ended **NO-GO — stay with VCP**, no code change.

Design: same harness as v2 (three-block firewall, per-window median Calmar, Gate 3 Wilcoxon) but using the new scriptable-condition feature (PR #33) to enable structurally-different candidates rather than VCP±1 ablations. 18 candidates across 6 families: MR (mean-reversion), MO (momentum), OB (orderblock), DV (divergence), VZ (value-zone), BR (breadth). Spec `/tmp/goal-spec-v3.md`, full results `/tmp/goal-results-v3.md`.

Outcome:
- **Block A (2000-2014, 12 windows):** 4 strict survivors — BR2 (median Calmar 5.346), VZ3 (5.335), OB2 (3.993), DV1 (3.909) — all clearing Gate 1+2 (median > VCP's 2.471, 0 neg windows). Top 3 carried to Gate 3.
- **Gate 3 (Block B 2015-2020, 7 windows, Bonferroni p < 0.0167):** all three finalists FAIL. BR2 p=0.289, OB2 p=0.531, VZ3 p=0.531. Each has a higher median Calmar than VCP (BR2 8.99 vs VCP 5.31) but VCP wins the majority of windows when paired — Wilcoxon is rank-based and the candidates' wins concentrate in a few large windows.
- **Notable near-misses (Gate 1 PASS, Gate 2 FAIL by exactly one bear-year window):** MR3, MO1, MO3 all fail on W6 = 2008 GFC. MR3 fails on W9 = 2011 EU debt. BR1 fails on worst-of-3 seed. All have median Calmars 3.7-4.8 (well above VCP) but eat one regime-shift bear loss.

**Why:** Calmar-by-median ≠ Calmar-by-window. The candidates' edge concentrates in a few large window-blowouts (e.g. BR2 W7 391 vs VCP 2.98, a COVID-crash window with near-zero drawdown denominator), but Wilcoxon doesn't reward magnitude. VCP's strength is broad-base per-window consistency. Loose-entry strategies with bigger peak wins can't overcome VCP's stable per-window edge. Same statistical lesson as v2 but for a different reason: v2 was overfit single-window edge; v3 is real-but-lumpy edge that loses paired tests.

**How to apply:** treat VCP-replacement-via-direct-substitution as exhausted. v2 ruled out VCP±1 ablations; v3 ruled out alternative-structure replacements. The remaining productive direction is **pair the high-Calmar near-misses (MR3, MO1, MO3) with VCP-style regime filters** (`marketUptrend`, equity-curve shutoff, breadth gate) to eliminate their bear-window losses while keeping their bull-regime edge. The underlying edge in non-bear regimes is real and far exceeds VCP's — the bear-window loss is what kills them in Gate 2. Relates to [[project_v2_goal_search_outcome]].

Side outcome: a real `WalkForwardService` OOM bug was discovered and fixed during the v3 run — windows were processed 2-at-a-time (`MAX_CONCURRENT_WINDOWS=2`), doubling per-window heap; loose-entry candidates blew through 12 GB and even 18 GB. Fixed by setting to 1 (windows sequential) + 15 GB heap. Committed locally on `fix/walk-forward-oom-concurrency` (`39c93b8`), not yet pushed.
