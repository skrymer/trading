---
name: VCP sizer sweep (2026-04-17)
description: Multi-sizer / multi-seed sweep for VCP. AtrRisk(1.25%, 2.0) remains Pareto-dominant. ATR-anchored sizing structurally superior for this strategy.
type: project
originSessionId: a1232822-4edf-4931-a833-5f468c42f49d
---
Ran 9 sizer variants on VCP ($10K, 2016-2025, 15 max positions, entry delay 1, leverage 1.0). Multi-seed validated the two challengers (VolTarget 1.2%, Kelly-half) across seeds 1/7/42/100.

**Result: `AtrRiskSizer(riskPercentage: 1.25, nAtr: 2.0)` is Pareto-dominant** on 4-seed means. CAGR 51.4%, MDD 18.9%, Calmar 2.73, alpha 37.9%.

**Why:** ATR-anchored sizing auto-shrinks positions when realized volatility expands — exactly when drawdown accelerates. VolTarget (equal-vol-contribution) lags because targeted vol is a constant. Kelly-half ties on CAGR but blows out MDD to 23.1% — flat Kelly fractions don't adapt to regime volatility. Pure notional sizers (PercentEquity, Kelly-quarter) produce the deepest drawdowns — the ATR tilt is adding real tail control value.

**How to apply:**
- The VCP production sizing (`AtrRiskSizer` 1.25% / 2.0 ATR) is validated and should not be changed without a materially different hypothesis.
- Seed 42 is a known upside outlier across ALL sizers on this config — any single-seed-42 result must be multi-seed validated before drawing conclusions.
- The pluggable sizer abstraction exists and works; future strategies with different return profiles (mean-reversion, short vol, etc.) may benefit from different sizers — don't assume AtrRisk is universal.
- ~8 effective concurrent positions at 1.25%/2.0; ATR rolls to ~10 at 1.0%, ~13 at 0.75%. Reducing risk to fit more positions sacrifices compounding faster than it improves drawdown.
