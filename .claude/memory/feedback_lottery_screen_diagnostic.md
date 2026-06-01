---
name: lottery-screen-diagnostic
description: "Reusable screen-stage rejection rule. A candidate whose edge is concentrated in 1-2 OOS windows with 5+/7 negative-CAGR windows is a \"regime-conditional momentum entry without explicit regime gating = lottery\". Reject at screen stage without further iteration (no sizer sweep, no exit-variant sweep). The 7.43% CAGR is the lottery converging, not an under-deployed engine."
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 7571f7f6-71bf-476b-8e88-d8d2a44467a6
---

**Rule**: Reject at `/strategy-screen` stage without iteration any candidate matching this pattern:
- Headline per-trade edge looks attractive (e.g. > 1.0% per trade)
- BUT edge is concentrated in 1-2 OOS windows out of N (e.g. 2 of 7 windows carry > 8% edge while the other 5 are < 1.5%)
- AND 5+ of N windows are negative by realized CAGR (not edge — CAGR)
- Path quality is lumpy: max DD ≥ 25%, Calmar < 0.5, Sharpe < 0.7

**Why**:
- Quant analysis 2026-05-28 on MJV-s1 (Mjolnir entry + VCP exit at VZ3 baseline sizer): aggregate edge +2.50% but distribution was bimodal — 2009 +49.67% CAGR and 2013 +50.84% CAGR carrying five negative-CAGR years (2008 -7%, 2010 -3%, 2011 -9%, 2014 -18%, and W3 -3%). Geometric compounding on `{+50, -9, -7, +7, +51, -18, -3}` ≈ 7.43%/yr — that IS the strategy's true number, not "leaving CAGR on the table from under-sizing".
- Diagnostic: edge concentrated in 2/N windows is **a regime-detector pattern, not a trade-selector pattern**. The entry only has alpha in ~25-30% of tape regimes; the rest is trade-everything-hope-a-strong-regime-shows-up = regime beta on momentum factor.
- Operationally **unholdable** — 5 of 7 years negative-CAGR means real-money capitulation before the next payday window arrives. Single-operator deployment can't survive that path.

**How to apply**:
- Always check the per-window edge distribution at screen stage, not just the aggregate. Concentrated-edge signature → reject.
- Do NOT attempt to rescue with: sizer sweep (variance-mining trap — bigger size on lumpy engine = ruined account); faster exit (destroys the rare big runners that produce all the CAGR); slower exit (already what produced the lumpiness).
- The fix would be **adding explicit regime gating to the entry** — but at that point you're designing a new candidate from scratch, not iterating this one. File the original as REJECTED and start over with a regime-gated premise.
- This shortcut also applies to candidates we haven't fired yet — if a proposed candidate's premise sounds like "trade momentum hard, let winners run, hope trends materialize" without an explicit regime gate, the screen result will likely fit this pattern.

Related: [[mean-reversion-pullback-known-weakness]], [[regime-conditional-portfolio-framework]], [[backtest-engine-simulates-reality]].
