---
name: feedback-min-cagr-tradable
description: "Minimum CAGR for a candidate to be considered tradable is 30%. Apply as a final go/no-go gate after the full v4 validation; not used in screen filtering itself (where lower-CAGR candidates may still be worth deeper validation), but always flagged when reporting screen survivors."
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 7571f7f6-71bf-476b-8e88-d8d2a44467a6
---

A strategy must clear **CAGR >= 30%** to be considered tradable. Below that, even a "passing" screen result is not a candidate to live-deploy.

**Why:** the user has a specific return-rate requirement for committing capital to a strategy. Below ~30% CAGR the platform's existing baselines (e.g. VCP at 51% CAGR) make the new strategy non-additive. Below ~20% CAGR it doesn't beat the time cost of running it.

**How to apply:**

- **Final v4 gate** — add to the strict full-Block-A evaluator as a hard gate (e.g. between G1 CAGR-positive and G6 regime mandate). Don't pass through to Block B if a candidate CAGRs under 30%.
- **/strategy-screen** — does NOT apply as a hard gate (the screen is a fast first-pass filter, not the final verdict; a candidate at 12% screen CAGR might still surface a real edge under different sizing). But **always flag** screen survivors whose CAGR is below 30% so we don't waste deeper-validation time on them by default.
- **Reporting** — when summarising sweep results, always show CAGR vs the 30% bar so the user can see at a glance which candidates meet the tradability threshold.

Linked: [[feedback-use-strategy-screen-for-scanning]] — the screen is the first-pass; this CAGR rule is the final-pass.
