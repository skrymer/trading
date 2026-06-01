---
name: feedback-use-strategy-screen-for-scanning
description: "Use the /strategy-screen skill for any first-pass strategy variant scanning. Full Block A walk-forward is for known candidates worth deeper validation, not for exploratory sweeps."
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 7571f7f6-71bf-476b-8e88-d8d2a44467a6
---

When sweeping multiple strategy variants (10-30+) to find candidates worth deeper validation, default to **/strategy-screen** (10-year 2005-2015 Block A, relaxed gates, 3-5 min per candidate). It is the quant-validated tool for first-pass triage.

**Why:** Full Block A (2000-2015, ~10 min/candidate) is for validation of *known* candidates that already showed promise. Using full Block A for exploratory sweeps wastes hours on candidates that the screen would have rejected in minutes. Validated by the quant in this project — 10y of 2005-2015 has enough statistical power to filter obvious losers without sacrificing the 3-block firewall's final-validation integrity.

**How to apply:**
- Exploratory sweep of multiple variants → `/strategy-screen`.
- Survivors then re-run on full Block A via `/walk-forward`.
- A single known-strategy verification (e.g. confirming a fix or testing one variant) → `/walk-forward` directly is fine; no need to pre-screen.

Linked: [[feedback-get-expert-review-before-persisting]] — the screen's gates were quant-reviewed before persisting; the screen itself is the persisted version of "what the quant said is enough".
