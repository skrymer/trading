---
name: feedback-absolute-benchmark-when-baseline-collapses
description: "When the relative baseline you've been measuring against collapses (e.g. a lookahead bug fix exposes that the benchmark had no real edge), do NOT just lower the gate by re-running candidates against the corrected baseline. The relative methodology stops being meaningful when the baseline itself is near-zero. Re-anchor to absolute thresholds — min CAGR, max drawdown, per-window edge consistency, named-regime survival."
metadata:
  node_type: memory
  type: feedback
  originSessionId: 7571f7f6-71bf-476b-8e88-d8d2a44467a6
---

When the baseline a strategy search has been measured against collapses (here: corrected VCP went from documented ~+5.29% edge to ~+0.43% ex-outlier after PR #34), do NOT just re-run the existing candidates against the corrected baseline using the old relative gates. **"Beats corrected VCP" becomes a trivially low bar** — many candidates will clear it without actually being tradeable.

**Why:** Relative gates (Wilcoxon vs baseline, "median Calmar > baseline") were calibrated against an inflated baseline. When the baseline collapses, the gate effectively auto-loosens. Survivors of the new gate are not survivors of the original bar — they're just better than a near-zero benchmark, which is much easier and means much less.

**How to apply:**
- Before re-running candidates against a newly-corrected baseline, redefine the gates in **absolute** terms: min CAGR floor, max drawdown cap, per-window edge-consistency thresholds, named-regime survival (must be positive in specific bear windows).
- A reasonable starting set for an equity long-only swing strategy on a US large-cap universe: CAGR >= 12% (above buy-and-hold), max drawdown <= 25% (lower than 2008 SPY -55%), per-window OOS edge stdev capped, every named bear window (2008, 2011, 2015-energy, 2018Q4, 2020, 2022) positive.
- The relative methodology can be kept as a *secondary* filter ("also beats VCP"), but it shouldn't be the primary gate when the baseline has near-zero edge.

Cross-reference: [[project_v3_goal_search_outcome]] (the v3 NO-GO verdict was measured against pre-fix VCP), the OB-lookahead fix (PR #34 in trading repo) that triggered the collapse.
