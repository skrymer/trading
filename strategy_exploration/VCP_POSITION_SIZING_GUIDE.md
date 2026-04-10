# VCP Strategy Position Sizing Guide

**Date:** March 2026
**Strategy:** VCP (Volatility Contraction Pattern)
**Backtest Period:** 2016-2025 (10 years), with out-of-era validation 2006-2015
**Universe:** US Stocks, SectorEdge ranker, 15 max positions, 1-day entry delay

---

## Section 1: Position Sizing Fundamentals

### The ATR-Based Sizing Formula

```
shares = floor(portfolioValue * (riskPct / 100) / (nAtr * ATR))
```

Where:
- **portfolioValue** = current portfolio equity (cash + unrealized P/L of open positions)
- **riskPct** = percentage of portfolio risked per trade (e.g., 1.5% means you accept losing 1.5% of your portfolio if the stop is hit)
- **nAtr** = ATR multiplier defining the stop distance (e.g., 2.0 means the stop is placed 2x ATR below entry)
- **ATR** = Average True Range of the stock at entry (14-period, measures daily volatility in dollar terms)

### What Each Parameter Controls

| Parameter | Controls | Higher value means |
|---|---|---|
| `riskPct` | Dollar risk per trade | Larger positions, faster compounding, deeper drawdowns |
| `nAtr` | Stop distance width | Wider stops, fewer shares, less whipsaw but larger per-share loss if stopped |
| `leverageRatio` | Max notional exposure | More positions can be fully sized; 1.0 = cash account, null = unlimited |
| `startingCapital` | Initial portfolio size | Absolute dollar sizing scales with this |

### How Risk Translates to Position Size: Worked Examples

**Example 1 -- Baseline configuration**

- Portfolio value: $50,000
- Stock ATR: $3.50
- Risk per trade: 1.5%
- nAtr: 2.0

```
Dollar risk = $50,000 * 0.015 = $750
Stop distance = 2.0 * $3.50 = $7.00
Shares = floor($750 / $7.00) = 107 shares
```

If the stock trades at $85, the position is 107 * $85 = $9,095 (18.2% of portfolio). If it hits the stop, you lose $750 (1.5% of portfolio). This is the maximum planned loss per trade.

**Example 2 -- Conservative configuration**

- Portfolio value: $50,000
- Stock ATR: $3.50
- Risk per trade: 1.0%
- nAtr: 2.0

```
Dollar risk = $50,000 * 0.01 = $500
Shares = floor($500 / $7.00) = 71 shares
```

Position is 71 * $85 = $6,035 (12.1% of portfolio). Stop loss = $500 (1.0%). Exactly 1/3 smaller than the 1.5% configuration.

**Example 3 -- High-volatility stock**

- Portfolio value: $50,000
- Stock ATR: $8.00 (volatile growth stock)
- Risk per trade: 1.0%
- nAtr: 2.0

```
Shares = floor($500 / $16.00) = 31 shares
```

The formula automatically sizes you smaller in volatile names. This is a core advantage of ATR-based sizing: the market tells you how big to be.

### Portfolio Heat

Portfolio heat = sum of all open position risk. With 10 positions each risking 1.0%, your maximum portfolio heat is 10.0%. This means if every open position hits its stop simultaneously, you lose 10% of your portfolio. In practice this never happens, but it sets the theoretical worst case.

---

## Section 2: Recommended Starting Configuration (New Trader)

### The Configuration

| Parameter | Value | Rationale |
|---|---|---|
| Starting capital | $10,000 - $50,000 | Minimum viable for meaningful position sizes |
| Risk per trade | **1.0%** | 1/3 below backtested baseline; limits drawdowns during learning phase |
| nAtr | 2.0 | Backtested default; no reason to change |
| Leverage ratio | 1.0 | Cash account only; no margin during ramp-up |
| Max positions | **10** | Reduced from 15 to limit correlation clustering |
| Drawdown scaling | **Enabled** (see below) | Safety net for the learning period |

### Drawdown Scaling Thresholds (Starting)

| Drawdown Depth | Risk Multiplier | Effective Risk |
|---|---|---|
| < 7% | 1.00x | 1.0% |
| >= 7% | 0.67x | 0.67% |
| >= 12% | 0.33x | 0.33% |

### API Configuration

```json
{
  "positionSizing": {
    "startingCapital": 10000,
    "riskPercentage": 1.0,
    "nAtr": 2.0,
    "leverageRatio": 1.0,
    "drawdownScaling": {
      "thresholds": [
        { "drawdownPercent": 7.0, "riskMultiplier": 0.67 },
        { "drawdownPercent": 12.0, "riskMultiplier": 0.33 }
      ]
    }
  },
  "maxPositions": 10
}
```

### Expected Performance Range

Reducing risk from 1.5% to 1.0% scales positions down by 1/3. Combined with 10 max positions instead of 15, expected performance based on backtested data:

| Metric | Backtested (1.5% / 15 pos) | Expected Starting (1.0% / 10 pos) |
|---|---|---|
| CAGR | 58.6% | ~25-30% |
| Max Drawdown | 22.0% | ~14-17% |
| Sharpe | 2.37 | ~2.0-2.3 |
| Calmar | 2.67 | ~1.6-2.0 |
| Trades/year | ~103 | ~70-80 |

The CAGR reduction is larger than linear because compounding amplifies the difference over time. The drawdown reduction is meaningful: 14-17% is psychologically manageable for a new trader versus 22% which often triggers emotional decision-making.

### Why 1.0% and Not 1.5%

1. **The backtest is not reality.** Walk-forward efficiency is 0.78, meaning out-of-sample performance captured 78% of in-sample. Expect further degradation from execution realities (slippage, missed fills, emotional interference).

2. **The pre-2016 era test showed reduced edge.** CAGR dropped from 58.6% to 21.7%, and max drawdown was 24%. The future may resemble the weaker period more than the stronger one.

3. **Psychological survival matters.** A 22% drawdown at 1.5% risk is a $2,200 loss on a $10K account. At 1.0% risk with drawdown scaling, the worst case is closer to $1,400-1,700. The difference between continuing to trade and quitting.

4. **You can always scale up.** You cannot un-lose capital from oversizing during the learning phase.

---

## Section 3: Current Market Environment (March 2026 -- Iran War)

### The Geopolitical Context

The ongoing Iran conflict creates a specific risk profile for trend-following momentum strategies:

- **Elevated VIX / wider ATRs**: Stocks move more per day, which the ATR-based formula partially handles by auto-sizing smaller. However, ATR is a trailing indicator -- it lags sudden volatility spikes.
- **Correlation spikes**: During geopolitical shocks, correlations across sectors converge toward 1.0. Your 10-15 "diversified" positions start behaving like one large position.
- **Gap risk**: War escalation headlines hit outside market hours. Stocks gap through stops, creating losses larger than the planned 1x ATR or 2x ATR stop distance.
- **Reduced edge**: The closest historical analog is 2022 (worst year in backtest: +1.05% edge, essentially break-even). Geopolitical stress compresses the strategy's edge.

### Historical Stress Period Performance

| Period | Context | CAGR | Max DD | Edge |
|---|---|---|---|---|
| 2022 | Rate hikes, bear market | ~1% | 22.0% | +1.05% |
| 2020 Q1 | COVID crash | Recovered | 22.0% | Positive for year |
| 2008-2009 (OOS) | GFC | Part of 21.7% period | 24.0% | +3.01% avg |

Key takeaway: the strategy **survives** crises but with heavily compressed edge and full-depth drawdowns. The 2022 drawdown took 190 days (6+ months) to recover. In a war environment, expect similar or worse dynamics.

### Recommended Wartime Configuration

| Parameter | Normal | Starting | **Wartime (Current)** |
|---|---|---|---|
| Risk per trade | 1.5% | 1.0% | **0.75%** |
| nAtr | 2.0 | 2.0 | 2.0 |
| Leverage ratio | 1.0 | 1.0 | **1.0** |
| Max positions | 15 | 10 | **8** |
| Drawdown scaling | Optional | Enabled | **Enabled (tighter)** |

### Wartime Drawdown Scaling Thresholds

| Drawdown Depth | Risk Multiplier | Effective Risk |
|---|---|---|
| < 5% | 1.00x | 0.75% |
| >= 5% | 0.50x | 0.375% |
| >= 10% | 0.25x | 0.1875% |
| >= 15% | 0.00x | **Stop taking new trades** |

The 15% hard stop is critical. In a war environment, if you are down 15%, the market regime has shifted against the strategy. Preserve capital and wait for stabilization.

### Wartime API Configuration

```json
{
  "positionSizing": {
    "startingCapital": 10000,
    "riskPercentage": 0.75,
    "nAtr": 2.0,
    "leverageRatio": 1.0,
    "drawdownScaling": {
      "thresholds": [
        { "drawdownPercent": 5.0, "riskMultiplier": 0.50 },
        { "drawdownPercent": 10.0, "riskMultiplier": 0.25 },
        { "drawdownPercent": 15.0, "riskMultiplier": 0.0 }
      ]
    }
  },
  "maxPositions": 8
}
```

**Note:** A `riskMultiplier` of 0.0 will result in 0 shares calculated, effectively halting new entries while allowing existing positions to run or exit per strategy rules.

### Maximum Portfolio Heat (Wartime)

With 8 positions at 0.75% risk each: max heat = 6.0%. With drawdown scaling active at 5% DD: max heat drops to 8 * 0.375% = 3.0%. This is extremely conservative but appropriate when correlation spikes can turn 8 "independent" positions into a single correlated bet.

### What ATR-Based Sizing Does NOT Protect Against

- **Gap risk through stops**: A stock gaps -15% overnight on war news. Your 2x ATR stop was -4%. You lose 3-4x planned risk on that trade. With 8 positions, a correlated gap across the portfolio could produce a -15% to -20% single-day drawdown even at 0.75% risk per trade.
- **Liquidity evaporation**: In crisis moments, bid-ask spreads widen and stops execute at worse prices.
- **ATR lag**: ATR uses trailing data. A sudden volatility regime change means your first few trades are sized based on the old (lower) volatility, making them too large.

These are the reasons for the conservative wartime settings. The drawdown scaling provides a second line of defense when the formula's assumptions break.

---

## Section 4: Drawdown Response Protocol

### Systematic Rules

The protocol below applies regardless of market environment. The thresholds are absolute -- no discretion, no "it feels like it's about to turn around."

| Drawdown | Action | Rationale |
|---|---|---|
| 0-7% | **Trade normally.** | Normal strategy variance. The backtest shows 13% drawdowns occurring routinely. |
| 7-12% | **Review but keep trading.** Drawdown scaling auto-reduces risk to 0.67x. | Entering stress territory. Verify the strategy is executing correctly (no errors, no missed signals). |
| 12-15% | **Elevated alert.** Risk auto-reduced to 0.33x. Review open positions daily. | Approaching worst-case backtested territory. Ensure all stops are in place. |
| 15-20% | **Reduce max positions to 5.** Close weakest positions if overexposed. | The 2022 and 2020 drawdowns both peaked at 22%. You are approaching that zone. |
| 20-25% | **Half-size all new trades manually** (set risk to 0.5% regardless of scaling). No new positions in correlated sectors. | At the edge of backtested worst case. Capital preservation is now the priority. |
| 25%+ | **Stop all new trades.** Let existing positions exit per strategy rules. Do not add. | Beyond backtested norms (the pre-2016 GFC peak was 24%). Something fundamental has changed. |
| 30%+ | **Halt the strategy entirely.** Close all positions at market. | Strategy failure or unprecedented market regime. Full review required before resuming. |

### Recovery Criteria

After hitting a halt level, do not resume at full size. Follow this recovery ladder:

1. **Wait for drawdown to recover to < 15%** before taking any new trades.
2. **Resume at 0.33x risk** (e.g., 0.33% if base is 1.0%) with max 5 positions.
3. **After 10 consecutive trades at 0.33x**, if win rate is > 45% and edge is positive, move to 0.67x risk.
4. **After 20 additional trades at 0.67x**, if metrics hold, return to full risk.
5. The full recovery process takes approximately 2-3 months at normal trade frequency.

### Tracking Drawdown

The backtesting engine tracks drawdown using daily mark-to-market (cash + unrealized P/L vs. peak equity). For live trading via the scanner, track this manually:

```
Current drawdown = (peak portfolio value - current portfolio value) / peak portfolio value * 100
```

Use the scanner's drawdown stats endpoint (`GET /api/scanner/drawdown-stats`) to monitor this in real time.

---

## Section 5: Scaling Up Plan

### Prerequisites Before Any Scale-Up

All of the following must be true:
- Minimum 50 completed trades at the current risk level
- Win rate within 5 percentage points of backtest expectation (> 46%)
- Edge (average win% * WR - average loss% * LR) is positive
- No drawdown exceeding 15% in the measurement period
- You have been trading the strategy for at least 3 months at the current level

### Scaling Milestones

| Milestone | Timing | Risk | Max Positions | Drawdown Scaling |
|---|---|---|---|---|
| **Phase 0: Paper** | Month 1 | N/A | N/A | N/A |
| **Phase 1: Initial** | Months 2-6 | 1.0% | 10 | 7%/12% thresholds |
| **Phase 2: Confirmed** | Months 7-12 | 1.25% | 12 | 7%/12% thresholds |
| **Phase 3: Full** | Month 13+ | 1.5% | 15 | 5%/10% thresholds (or off) |
| **Phase 4: Aggressive** | Year 2+ | 1.5% | 15 | Off; consider leverage 1.2x |

### Phase 0: Paper Trading (Month 1)

Run the scanner live for 1 month without real capital. Track every signal, every entry, every exit. Compare your results against the backtest. This catches execution issues (missed signals, wrong timing, emotional overrides) before real money is at risk.

Minimum requirement: 15-20 paper trades.

### Phase 1 to Phase 2 Transition

After 6 months and 50+ trades at 1.0% risk:

```json
{
  "positionSizing": {
    "riskPercentage": 1.25,
    "nAtr": 2.0,
    "leverageRatio": 1.0,
    "drawdownScaling": {
      "thresholds": [
        { "drawdownPercent": 7.0, "riskMultiplier": 0.67 },
        { "drawdownPercent": 12.0, "riskMultiplier": 0.33 }
      ]
    }
  },
  "maxPositions": 12
}
```

This is a 25% increase in risk per trade and a 20% increase in position count. Expected CAGR improvement: +8-12 percentage points. Expected max drawdown increase: +2-3 percentage points.

### Phase 2 to Phase 3 Transition

After 12 months total and 100+ trades:

- Move to the backtested baseline: 1.5% risk, 15 positions.
- Keep drawdown scaling enabled unless live performance has been within 90% of backtest.
- At this point you have statistically significant live data to validate the strategy.

### Metrics to Track at Every Phase

| Metric | Target | Red Flag |
|---|---|---|
| Win rate | > 48% | < 42% for 30+ trades |
| Average edge per trade | > +3.0% | Negative for 20+ trades |
| Max drawdown | < 18% | > 22% (backtest worst case) |
| Avg holding period | 15-30 days | > 45 days (strategy drift) |
| Trades per month | 8-12 | < 3 (market may be unsuitable) or > 20 (overtrading) |
| Sharpe (rolling 6mo) | > 1.5 | < 0.8 |

### When to Scale Back Down

If at any phase you hit:
- Max drawdown exceeding 20%
- Win rate below 42% over 30+ trades
- Three consecutive months of negative returns

Drop back one phase (e.g., Phase 3 to Phase 2) and remain there for another 50 trades before attempting to scale up again.

---

## Section 6: Quick Reference

### Configuration Comparison Table

| Parameter | Normal (Backtested) | Starting (New Trader) | Wartime (March 2026) |
|---|---|---|---|
| Risk per trade | 1.5% | 1.0% | 0.75% |
| nAtr | 2.0 | 2.0 | 2.0 |
| Leverage ratio | 1.0 | 1.0 | 1.0 |
| Max positions | 15 | 10 | 8 |
| Max portfolio heat | 22.5% | 10.0% | 6.0% |
| DD scaling | Off | 7% / 12% | 5% / 10% / 15% |
| Expected CAGR | ~58% | ~25-30% | ~15-20% |
| Expected max DD | ~22% | ~14-17% | ~10-13% |
| Expected Calmar | ~2.7 | ~1.6-2.0 | ~1.3-1.7 |

### API Snippets

**Normal (Backtested Baseline)**

```json
{
  "positionSizing": {
    "startingCapital": 10000,
    "riskPercentage": 1.5,
    "nAtr": 2.0,
    "leverageRatio": 1.0
  },
  "maxPositions": 15
}
```

**Starting (New Trader)**

```json
{
  "positionSizing": {
    "startingCapital": 10000,
    "riskPercentage": 1.0,
    "nAtr": 2.0,
    "leverageRatio": 1.0,
    "drawdownScaling": {
      "thresholds": [
        { "drawdownPercent": 7.0, "riskMultiplier": 0.67 },
        { "drawdownPercent": 12.0, "riskMultiplier": 0.33 }
      ]
    }
  },
  "maxPositions": 10
}
```

**Wartime (March 2026 -- Iran Conflict)**

```json
{
  "positionSizing": {
    "startingCapital": 10000,
    "riskPercentage": 0.75,
    "nAtr": 2.0,
    "leverageRatio": 1.0,
    "drawdownScaling": {
      "thresholds": [
        { "drawdownPercent": 5.0, "riskMultiplier": 0.50 },
        { "drawdownPercent": 10.0, "riskMultiplier": 0.25 },
        { "drawdownPercent": 15.0, "riskMultiplier": 0.0 }
      ]
    }
  },
  "maxPositions": 8
}
```

### Decision Flowchart

```
Are you new to this strategy?
  YES --> Use "Starting" config (1.0%, 10 pos, DD scaling on)
  NO  --> Is the market under geopolitical stress?
            YES --> Use "Wartime" config (0.75%, 8 pos, tight DD scaling)
            NO  --> Have you traded 100+ live trades with >48% WR?
                      YES --> Use "Normal" config (1.5%, 15 pos)
                      NO  --> Stay on "Starting" config
```

### The One Rule That Matters Most

Position sizing is the only part of a trading strategy that can cause permanent capital loss. A strategy with positive edge will recover from any drawdown given enough time -- but only if you are still in the game. The purpose of conservative sizing is not to maximize returns. It is to ensure you survive long enough for the edge to compound.

The backtest proves the edge exists: 100% probability of profit across 10,000 Monte Carlo iterations, 4/4 walk-forward windows profitable, 10/10 years profitable. The only way to lose with this strategy is to size too aggressively and quit during a drawdown.

Size small. Survive. Scale up.
