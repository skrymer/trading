---
type: concept
title: Position sizing & risk framework
summary: The ATR-risk sizing framework's design rationale — current-equity (incl. unrealized) basis, ratcheting-peak drawdown scaling, 1.0 leverage cap, annual sector-ranking recalibration.
status: stable
tags: [methodology, risk, sizing]
sources: ["udgaard PositionSizingService.kt", "udgaard sizer/ package"]
related: ["[[component-firewall]]", "[[the-funnel]]"]
updated: 2026-06-07
---

# Position sizing & risk framework

The risk-sizing methodology shared across strategies. The *mechanics* live in code
(`PositionSizingService` + the `sizer/` package — `AtrRiskSizer`, `PercentEquitySizer`, `KellySizer`,
`VolatilityTargetSizer`, `LeverageCap`); this page holds the **design rationale** an analyst needs to
reason about a candidate's sizing, which the KDoc does not carry. Thresholds below are *defaults /
illustrative* — they are config, not law.

## ATR-risk formula

```
shares = floor(currentEquity × (riskPercentage / 100) / (nAtr × ATR))
```

`riskPercentage` = max % of equity risked per trade (default 1.5%); `nAtr` = ATR multiplier setting the
stop distance (default 2.0). Risk dollars are framed as a stop-out loss, so sizing is volatility-normalised
across names.

## Why the equity basis includes unrealized P&L (the load-bearing decision)

`currentEquity = startingCapital + realizedP&L + unrealizedP&L`. Sizing uses **current** equity, not static
starting capital — so risk scales up as the book compounds and **down automatically when positions are
underwater**. Unrealized P&L is included deliberately, because with many concurrent trend positions
**unrealized gains are genuine exposure**:

- Winners run long (≈3:1 W/L), so realized equity rises slowly while true portfolio value climbs far above
  it — realized-only sizing operates on *stale* information.
- When the trend breaks, realized-only equity would still be sizing full positions because the drawdown
  system hasn't triggered yet — it reacts only *after* stops are hit, too late.
- Trend-following drawdowns are **correlated** (positions reverse together); unrealized equity detects the
  regime shift in real time, realized-only detects it after the fact.

**The "ratcheting peak" is a feature, not a bug:** peak equity is the high-water mark of *current* equity
(incl. unrealized), so giving back open profit deepens drawdown faster and trips the protective scaling
sooner. Winners that keep trending rebuild the curve and restore full sizing naturally. ^[inferred — this
is the design rationale; the mechanics are in `PositionSizingService`.]

## Drawdown-responsive scaling

Reduces risk-per-trade in drawdown (entry signals unchanged — only the dollar amount risked). At each
entry, current drawdown vs peak is computed; thresholds are evaluated **deepest-first**, first match wins;
risk steps back up as equity recovers through each threshold. Example threshold ladder (config): `< 3%` →
1.0×, `≥ 3%` → 0.67×, `≥ 8%` → 0.33×. Tuning note: to soften scaling in normal pullbacks, move the
*threshold levels*, never the equity-calculation method.

## Leverage cap — 1.0 mandatory for stock backtests

`maxNotional = currentEquity × leverageRatio`, applied portfolio-level outside the sizer (`LeverageCap`).
**Always set `leverageRatio: 1.0` for stock backtests** — without it, ATR-based sizing on low-ATR stocks
produces 50×+ leverage and unrealistic results. (`5.0` ≈ deep-ITM-options equivalent; `null` = uncapped,
caution.)

## Sector-ranking recalibration policy

When more signals fire than open slots, a sector ranker prioritises which names fill them. The ranking is
derived from a trailing **3-year** unlimited backtest (sectors sorted by average profit-per-trade) and
**recalibrated annually** — sector leadership rotates on multi-year cycles, so more frequent updates add
noise. Sectors with < 30 trades in the window get a neutral mid-pack rank. Walk-forward validation
confirmed IS-derived rankings beat static full-sample rankings (WFE 0.63 → 0.75) — the in-sample-derived
ranking is the right call, recalibrated on the cadence that matches leadership persistence.

## Portfolio heat

Total open risk across positions. At 15 positions × 1.5% the max theoretical heat is 22.5%; drawdown
scaling reduces it under stress. **Guideline: cap total open risk at 10-15%.** Not enforced in the
backtest engine — a manual live-trading discipline.

## Related

[[component-firewall]] · [[the-funnel]]
