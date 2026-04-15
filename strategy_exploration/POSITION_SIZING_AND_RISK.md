# Position Sizing and Risk Management

Reference documentation for the portfolio risk framework used across all strategies.

## Position Sizing Formula

```
shares = floor(currentEquity * (riskPercentage / 100) / (nAtr * ATR))
```

**Parameters:**
- `currentEquity` — live portfolio value (see Equity Calculation below)
- `riskPercentage` — max % of equity risked per trade (default: 1.5%)
- `nAtr` — ATR multiplier defining expected adverse move / stop distance (default: 2.0)
- `ATR` — Average True Range of the stock at entry

**Example:** $50,000 equity, 1.5% risk, 2.0x ATR, stock ATR = $3.00
→ Risk dollars = $50,000 * 0.015 = $750
→ Shares = floor($750 / (2.0 * $3.00)) = floor(125) = 125 shares

## Equity Calculation

```
currentEquity = startingCapital + realizedP&L + unrealizedP&L
```

Position sizing uses **current equity**, not static starting capital. This means:
- Risk per trade scales up as the portfolio grows (compounding)
- Risk per trade scales down when positions are underwater (automatic risk reduction)
- Unrealized P&L is included because it represents real exposure

### Why Include Unrealized P&L

With 15 concurrent positions in a trend-following strategy, unrealized gains represent genuine portfolio risk. If market regime shifts, those open gains can evaporate across multiple positions simultaneously.

**Realized-only equity is dangerous** because:
1. Winners run long (3:1 W/L ratio), so realized equity rises slowly while actual portfolio value climbs far above it
2. When the trend breaks, you'd still be sizing full positions because the drawdown system hasn't triggered — it's operating on stale information
3. Trend-following drawdowns are correlated (multiple positions reverse together). Unrealized equity detects this in real-time; realized-only detects it after stops are hit — too late

**The "ratcheting peak" concern is a feature:** scaling down faster when open profits give back is exactly what you want during regime changes. Winners that keep trending rebuild the curve and restore full sizing naturally.

## Peak Equity and Drawdown

```
peakEquity = max(peakEquity, currentEquity)
drawdownPct = (peakEquity - currentEquity) / peakEquity * 100
```

Peak equity (the high-water mark) is tracked using current equity including unrealized P&L. This means:
- New highs are set when open positions are profitable
- If those gains reverse, drawdown deepens faster (peak was set higher)
- Drawdown scaling triggers sooner, which is the intended protective behavior

Peak equity can be manually reset in the scanner UI when starting fresh or after a strategy change.

## Drawdown-Responsive Scaling

Reduces risk per trade when the portfolio is in drawdown. Does not change entry signals or trade selection — only the dollar amount risked per trade.

**Current thresholds (VCP strategy):**

| Drawdown % | Risk Multiplier | Effective Risk (base 1.5%) |
|:-:|:-:|:-:|
| < 3% | 1.0x | 1.5% |
| >= 3% | 0.67x | 1.0% |
| >= 8% | 0.33x | 0.5% |

**How it works:**
- At each new entry, current drawdown % is computed from peak equity vs current equity
- Thresholds are evaluated deepest-first; the first match applies its multiplier
- When drawdown recovers below all thresholds, full risk is restored
- The scaling is continuous — as equity recovers through each threshold, risk steps back up

**Impact (VCP 2016-2025):** Max DD 21.2% to 13.6% (-36%), CAGR 46.4% to 39.8% (-6.6pp), Calmar 2.18 to 2.90 (+33%). All risk-adjusted ratios improve at the cost of lower absolute returns from compounding drag during recovery phases.

**Tuning:** If the system feels too aggressive in scaling down during normal pullbacks, adjust the threshold levels (e.g., 5%/10% instead of 3%/8%), not the equity calculation method.

## Leverage Ratio

```
maxNotional = currentEquity * leverageRatio
```

Caps total open notional value as a multiple of portfolio equity:
- `1.0` — cash account, no leverage (notional <= portfolio value)
- `5.0` — deep ITM options equivalent
- `null` — no cap (caution: ATR-based sizing can produce extreme leverage on low-ATR stocks)

**Always set `leverageRatio: 1.0` for stock backtests.** Without it, low-ATR stocks can produce 50x+ leverage, leading to unrealistic results.

## Position Limits and Ranking

- `maxPositions` — maximum concurrent open positions (default: 15 for VCP)
- When more signals fire than available slots, the **SectorEdge ranker** prioritizes which stocks fill the slots based on sector ordering
- Within the same sector, ties are broken with random jitter (use `randomSeed` for deterministic backtests)

### Sector Ranking Recalibration

- Derived from a trailing **3-year** unlimited backtest, sorting sectors by average profit per trade
- Recalibrate **annually** — sector leadership rotates on multi-year cycles, so more frequent updates add noise
- Sectors with fewer than 30 trades in the window should get a neutral mid-pack rank
- Walk-forward validation confirmed IS-derived rankings outperform static full-sample rankings (WFE improved 0.63 to 0.75)

**Current ranking (derived 2026-04-13 from 2023-04-13 to 2026-04-13):**
XLC, XLU, XLI, XLK, XLE, XLB, XLV, XLF, XLY, XLP, XLRE

## Portfolio Heat

Total open risk across all positions. With 15 positions at 1.5% risk each, maximum theoretical heat is 22.5%. In practice, drawdown scaling reduces this during stressed periods.

**Guideline:** Cap total open risk at 10-15% of portfolio. Not currently enforced in backtesting — monitor manually in live trading.

## Scanner Workflow (Live Trading)

Position sizing in the scanner uses the same equity-based formula:

1. `currentEquity = startingCapital + realizedP&L + unrealizedP&L` (computed from closed + open scanner trades)
2. `effectiveRisk = baseRisk * drawdownMultiplier` (from DD scaling thresholds)
3. `shares = floor(currentEquity * effectiveRisk / (nAtr * ATR))`

The settings page displays the formula and explains that sizing uses current equity, not static starting capital.

## Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Equity basis | Current (incl. unrealized) | Real-time risk awareness; realized-only creates dangerous lag |
| Peak equity basis | Current (incl. unrealized) | Detects correlated drawdowns in real-time |
| DD scaling approach | Threshold-based multipliers | Simple, transparent, easy to reason about |
| Sector ranking lookback | 3 years | Balances statistical significance with regime responsiveness |
| Ranking recalibration | Annual | Sector leadership persists 1-3 years; more frequent is noise |
| Leverage ratio | 1.0 for stocks | Prevents unrealistic leverage on low-ATR stocks |

---

_Last Updated: 2026-04-15_
