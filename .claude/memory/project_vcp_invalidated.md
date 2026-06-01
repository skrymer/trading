---
name: vcp-invalidated
description: "VCP is NOT a tradable strategy. The order-block condition (`aboveBearishOrderBlock`) had a look-ahead bug — fixed in PR"
metadata: 
  node_type: memory
  type: project
  originSessionId: 7571f7f6-71bf-476b-8e88-d8d2a44467a6
---

**Fact**: VCP is currently **NOT TRADABLE**. The order-block condition (`AboveBearishOrderBlockCondition.getRelevantOrderBlocks`) had a look-ahead bug that inflated all historical backtest results. The bug was fixed in PR #34. Pre-fix backtest claims (CAGR 56.3%, MDD 22.6%, Calmar 2.52, Sharpe 2.17 — all from `VCP_TRADING_PLAN.md` "Validated Performance" section) are all invalidated.

**Post-fix result**: VCP-corrected was re-validated through v4 Block A (2000-2014, 12 windows, sizer 1.25%/2.0nAtr) on 2026-05-26 — documented in `strategy_exploration/v4_block_a_results.md`. Result: **REJECTED** with 6 of 9 v4 gates failing:
- G2 (DD 39.16% > 25%) FAIL
- G3 (worst-window DD 26.35% > 20%) FAIL
- G4 (positive windows 8/12 = 66.7% < 75%) FAIL
- G5 (CoV 3.44 > 1.5 — W1 2003 +117% edge dominates) FAIL
- G6 (2008 GFC OOS edge −3.48%, must be > 0) FAIL
- G9 (Sharpe 0.54 < 0.8) FAIL
- Only G1 (CAGR 40.62%), G7 (2011 EU-debt +1.18%), G8 (min trades 37) pass

CAGR is purely the 2003 W1 outlier (+1029% window); 2008 collapses. Per the doc: "confirmed-fragile baseline".

**The OB fix cost VCP ~16pp of CAGR (56.3% → 40.62%) and roughly doubled max DD (22.6% → 39.16%).** The original "VCP TRADABLE" status was entirely an artefact of the look-ahead. There is no rescue path that keeps the OB design premise.

**Why this matters**:

- All references to "VCP TRADABLE" or "VCP baseline" in older docs/skills/memories are stale.
- The 2026-04-17 sizer sweep result (AtrRisk 1.25%/2.0×ATR Pareto-dominant) is based on pre-fix data — needs re-validation.
- The VCP_TRADING_PLAN.md / VCP_STRATEGY_DEVELOPMENT.md / VCP_POSITION_SIZING_GUIDE.md files reference numbers that may no longer hold.
- The regime-conditional portfolio framework's "currently 1/4 components passing (VCP)" claim is wrong — actually **0/4 components passing**.
- VZ3, MR3, DV1, MR4 candidate analyses that used "beats VCP" as a comparison anchor are using a contaminated baseline.

**How to apply**:

- Do NOT cite VCP as a tradable strategy in any session, document, or recommendation until it has been re-validated through the current 3-block firewall with the fixed OB condition.
- When discussing strategy roster, count: 0 firewall-validated strategies. (Whatever passed the prior methodology pre-PR #34 is reset.)
- Before any portfolio framework work proceeds, VCP needs to be re-validated. If it still passes under the fixed OB condition, it's component #1. If not, the search starts from zero strategies, not from "VCP plus N more".
- The first-class condition design pattern is still valid — the bug was in the OB condition's lookahead logic, not in the type-safe condition system. New first-class conditions (created via `/create-condition` with lookahead audits) are still the right design.

**Operational implication**: 0 firewall-validated strategies. The regime-conditional portfolio framework is at 0/4 transition criteria, not 1/4. The "stay in component-search mode" guidance still holds. Same Block A sweep (2026-05-26/27) rejected ALL 16 candidates tested (VZ3/MR3/MO1/MO3/BR1/BR2/BR3/DV1 and variants), so this is not a VCP-specific problem — it's the design space we've been exploring being insufficient against v4. Either need a fundamentally different premise direction (per quant: narrow-leadership / chop / crisis component, structurally different entry premise) or accept the v4 floor is empirically very hard to clear and reconsider the methodology.

Related: [[regime-conditional-portfolio-framework]], [[script-conditions-must-be-promoted]] (PR #34 future-OB bug class), [[lottery-screen-diagnostic]].
