# Alternative Strategy Proposals

## Context and Design Philosophy

The VCP strategy is a post-contraction breakout strategy. Its core thesis: stocks in established uptrends that have squeezed volatility, cleared institutional resistance (bearish order blocks), and are breaking out near Donchian highs on volume tend to continue higher. The ablation study confirmed that `aboveBearishOrderBlock` is the alpha engine, contributing +4.02pp of edge. Everything else is filtering and consistency protection.

The three proposals below are designed to be **structurally different** from VCP in their entry philosophy, while leveraging conditions VCP does not use. Each targets a distinct market microstructure phenomenon.

### What VCP uses and does not use

**VCP uses:** marketUptrend, sectorUptrend, uptrend, volatilityContracted, aboveBearishOrderBlock, priceNearDonchianHigh, volumeAboveAverage, minimumPrice

**VCP does NOT use (available for alternative strategies):**
- ADX range (trend strength quantification)
- ATR expanding (opposite of VCP's contraction)
- EMA spread (momentum magnitude)
- EMA bullish cross (timing via crossover)
- EMA alignment (10 > 20 > 50 stacking)
- Consecutive higher highs in value zone (pullback accumulation)
- Value zone (price near EMA)
- Order block breakout (mitigated OB breakout, different from "above OB")
- Order block rejection (multi-test of resistance)
- Market breadth recovering (contrarian timing)
- Market breadth near Donchian low (mean-reversion breadth signal)
- Sector breadth accelerating (sector rotation detection)
- Sector breadth greater than market (relative sector strength)
- Sector breadth EMA alignment (sector internal health)
- Bullish candle condition
- Days since earnings / no earnings within days
- Price above previous low
- Trailing stop exit
- Bearish order block exit
- Market breadth deteriorating exit
- Sector breadth below exit
- Price below EMA for days exit
- Before earnings exit
- Profit target exit

---

## Strategy 1: Sector Rotation Momentum ("Fenrir")

### Thesis

**Buy stocks in sectors that are gaining participation faster than the broad market, where the stock itself shows emerging trend strength and accelerating EMA separation.** This is a relative-strength sector rotation strategy. The idea is that when capital rotates into a sector, the rising tide lifts individual stocks, and catching this rotation early -- when the sector is accelerating relative to the market -- provides a structural tailwind that VCP does not exploit.

VCP is agnostic to sector momentum (it only requires `sectorUptrend`, a binary filter). Fenrir actively seeks sectors *outperforming* the market with accelerating breadth, then picks the strongest individual stocks within those sectors.

### Entry Conditions

```
entryStrategy {
    // MARKET
    marketUptrend()

    // SECTOR (the differentiator -- active rotation detection)
    sectorBreadthAccelerating(threshold = 3.0)
    sectorBreadthGreaterThanMarket()

    // STOCK
    uptrend()
    emaSpread(fastEmaPeriod = 10, slowEmaPeriod = 20, minSpreadPercent = 1.0)
    adxRange(minADX = 20.0, maxADX = 45.0)
    volumeAboveAverage(multiplier = 1.2, lookbackDays = 20)
    minimumPrice(10.0)
}
```

### Exit Conditions

```
exitStrategy {
    emaCross(10, 20)
    stopLoss(atrMultiplier = 2.5)
    sectorBreadthBelow(maxBreadth = 30.0)
}
```

### Condition Logic Explained

| Condition | Role | Why it matters |
|---|---|---|
| `marketUptrend()` | Macro filter | Same as VCP -- avoid trading in broad downtrends |
| `sectorBreadthAccelerating(3.0)` | **Rotation detection** | Sector EMA5 - EMA20 spread > 3pp means sector participation is growing faster than its trend. Catches the early-to-mid phase of sector rotation before it becomes consensus |
| `sectorBreadthGreaterThanMarket()` | **Relative strength** | The sector must be outperforming the market in breadth. This ensures we are in a leading sector, not a lagging one that happens to be going up |
| `uptrend()` | Stock quality | Minervini template ensures we are buying established leaders, not bottom-fishing |
| `emaSpread(10, 20, 1.0)` | **Momentum magnitude** | The 10/20 EMA separation must be >= 1.0% of price. This filters for stocks with genuine momentum, not flat-liners that technically meet the uptrend template. VCP analysis found winners tend to have 1.30% EMA spread vs 0.99% for never-green losers |
| `adxRange(20.0, 45.0)` | **Trend strength sweet spot** | ADX 20-45 captures emerging-to-strong trends. Below 20 is ranging (no edge). Above 45 risks exhaustion. This is a condition VCP ignores entirely |
| `volumeAboveAverage(1.2, 20)` | Conviction filter | Volume surge confirms institutional participation, same as VCP |
| `minimumPrice(10.0)` | Liquidity filter | Practical tradability |
| `sectorBreadthBelow(30.0)` exit | **Rotation reversal** | When sector breadth drops below 30%, the rotation is over. Exit before the sector drags the stock down. This is a structural exit VCP lacks |

### Expected Characteristics

| Characteristic | Estimate | Rationale |
|---|---|---|
| Trade frequency | Low-moderate (500-2,000 over 10 years) | `sectorBreadthAccelerating` + `sectorBreadthGreaterThanMarket` is a narrow filter. Not every sector accelerates every year |
| Average hold period | 30-60 days | Sector rotation cycles typically last 1-3 months. The emaCross(10,20) + sectorBreadthBelow exits will tend to fire as rotation fades |
| Best regimes | Bull markets with sector rotation (2017, 2019, 2020, 2023) | Requires sectors to take turns leading, which happens in broad advances |
| Worst regimes | 2022-style uniform bear markets, narrow markets (2024 mag-7) | When all sectors decline together, or when leadership is concentrated in a few mega-caps, sector breadth signals are unreliable |
| Expected win rate | 45-52% | Stronger sector tailwind should reduce false entries |
| Expected edge | 4-7% | If sector rotation is a real alpha source, the relative-strength filter should produce higher-quality entries than VCP's broad net |

### Why it Complements VCP

1. **Different entry timing.** VCP enters on post-contraction breakouts (volatility squeeze -> expansion). Fenrir enters when sector momentum is accelerating, regardless of whether the individual stock's volatility is contracted. These two signals can fire on different stocks at different times.

2. **Sector-aware vs sector-agnostic.** VCP treats all sectors equally (sectorUptrend is just a binary gate). Fenrir actively overweights leading sectors. In years where sector rotation is the dominant theme (2017, 2019), Fenrir should generate alpha that VCP misses by being in the right sectors earlier.

3. **Different exit logic.** VCP exits only on technical signals (EMA cross, stop loss). Fenrir adds `sectorBreadthBelow` as a structural exit. When the sector starts losing breadth, Fenrir exits even if the stock's EMA is still aligned -- this should reduce the tail of trades that give back gains as the sector rolls over.

4. **Correlation offset.** VCP's 2022 weakness came from entering trades that technically met the template but were in a broad-market downtrend that dragged sectors down. Fenrir's `sectorBreadthGreaterThanMarket` should produce fewer signals in 2022 because very few sectors outperformed the declining market breadth.

### Risks and Weaknesses

1. **Sector breadth data quality.** The strategy is entirely dependent on accurate, timely sector breadth calculations. If the underlying breadth data has gaps or lags, the accelerating/greater-than-market conditions could produce false positives.

2. **Small trade count risk.** The double sector filter (accelerating AND greater than market) may be too restrictive, producing insufficient trades for statistical confidence. If < 500 trades over 10 years, the edge estimate will have wide confidence intervals.

3. **Sector rotation is not always present.** In narrow markets (e.g., 2024 when a handful of mega-caps drove the index), sector breadth may not differentiate leaders from laggards. The strategy would produce few signals and underperform.

4. **2022 vulnerability persists.** Even though `sectorBreadthGreaterThanMarket` should reduce 2022 signals, `marketUptrend` alone may not be sufficient protection. The strategy could still enter during bear market rallies when a sector briefly outpaces the declining market.

5. **No order block component.** VCP's ablation study showed order blocks provide +4.02pp of edge. By omitting all OB conditions, Fenrir loses that alpha engine. The sector rotation thesis must generate comparable alpha on its own.

---

## Strategy 2: Institutional Breakout ("Tyr")

### Thesis

**Buy stocks that have just broken through a bearish order block (mitigated the resistance), in the early phase of a market breadth recovery, with ADX confirming a strengthening trend.** This is an institutional flow strategy. The order block breakout represents the clearing of a known supply zone where institutions previously distributed shares. When price pushes through that zone and holds, it signals that demand has overwhelmed supply at that level.

The critical difference from VCP: VCP uses `aboveBearishOrderBlock(consecutiveDays=1, ageInDays=0)` which checks whether price is above *any* bearish OB with a 1-day cooldown. Tyr uses `orderBlockBreakout` which specifically requires a *recently mitigated* bearish OB -- meaning price has actively punched through the zone and confirmed above it. This is a more specific, event-driven signal tied to actual supply/demand dynamics, not just being "above" an OB.

The strategy pairs this with market breadth recovering (contrarian timing: buying early in a new upswing) rather than VCP's market uptrend (momentum: buying during an established uptrend).

### Entry Conditions

```
entryStrategy {
    // MARKET (contrarian timing -- buy the early recovery, not the established trend)
    marketBreadthRecovering()

    // SECTOR
    sectorBreadthEmaAlignment()

    // STOCK
    uptrend()
    orderBlockBreakout(consecutiveDays = 2, maxDaysSinceBreakout = 5, ageInDays = 0)
    adxRange(minADX = 25.0, maxADX = 50.0)
    bullishCandle()
    volumeAboveAverage(multiplier = 1.5, lookbackDays = 20)
    minimumPrice(10.0)
}
```

### Exit Conditions

```
exitStrategy {
    trailingStopLoss(atrMultiplier = 2.5)
    bearishOrderBlock(ageInDays = 120)
    stopLoss(atrMultiplier = 3.0)
}
```

### Condition Logic Explained

| Condition | Role | Why it matters |
|---|---|---|
| `marketBreadthRecovering()` | **Contrarian timing** | Breadth just crossed above its EMA10 from below. This fires on the first day of a new breadth upswing -- capturing early entries rather than waiting for an established uptrend. VCP waits for `marketUptrend()` (established). Tyr buys the inflection point |
| `sectorBreadthEmaAlignment()` | Sector health | Sector breadth EMAs must be aligned bullishly. Less strict than sectorUptrend, but confirms the stock's sector has improving internals |
| `uptrend()` | Stock quality | Minervini template. Only buy established leaders |
| `orderBlockBreakout(2, 5, 0)` | **Event-driven entry** | Price has broken above a recently mitigated bearish OB (within 5 trading days) and held above it for 2 days. This is a specific institutional flow event: former resistance becomes support |
| `adxRange(25.0, 50.0)` | **Strong trend confirmation** | ADX 25-50 confirms the stock is in a strong directional trend, not just drifting. Higher floor (25 vs Fenrir's 20) because we want the breakout to occur in context of a strong trend |
| `bullishCandle()` | **Confirmation candle** | The entry day must close above its open. Ensures we are not buying into a reversal candle on the breakout day |
| `volumeAboveAverage(1.5, 20)` | **Strong conviction** | 1.5x average volume (higher than VCP's 1.2x) because OB breakouts require more institutional demand to be significant |
| `trailingStopLoss(2.5)` exit | **Profit protection** | Unlike VCP's fixed stop + EMA cross, Tyr uses a trailing stop that locks in gains. As the post-breakout move extends, the stop moves up. This better suits event-driven trades that either work immediately or fail |
| `bearishOrderBlock(120)` exit | **Structural resistance** | If price enters the next bearish OB overhead (at least 120 days old), exit. This respects the same institutional supply/demand framework on the exit side |
| `stopLoss(3.0)` exit | **Wider initial stop** | 3.0 ATR (vs VCP's 2.5) gives the breakout room to consolidate above the mitigated OB without getting stopped out by normal volatility |

### Expected Characteristics

| Characteristic | Estimate | Rationale |
|---|---|---|
| Trade frequency | Very low (200-800 over 10 years) | `marketBreadthRecovering` fires on a handful of days per year. Combined with OB breakout, this is extremely selective |
| Average hold period | 20-50 days | Trailing stop will lock in gains sooner on strong moves, bearish OB exit will cap runners |
| Best regimes | V-shaped recoveries (Q2 2020, Q4 2022, Q4 2023) | Market breadth recovery + OB breakout fires at inflection points after selloffs |
| Worst regimes | Slow grinding trends, choppy sideways markets | Breadth recovering fires briefly during false bottoms in bear markets. The signal is only useful if the recovery sustains |
| Expected win rate | 42-48% | Contrarian timing catches some false recoveries. But the OB breakout + volume + ADX filters should improve quality |
| Expected edge | 5-9% | If the thesis holds, catching post-recovery OB breakouts should produce outsized winners as the trend reasserts itself |

### Why it Complements VCP

1. **Regime capture.** VCP requires an *established* market uptrend. It does not enter during the transition from downtrend to uptrend. Tyr explicitly targets that transition via `marketBreadthRecovering`. The trades Tyr catches in Q2 2020, Q4 2022, and Q4 2023 are trades VCP would miss because the market had not yet confirmed its uptrend.

2. **Different order block usage.** VCP uses `aboveBearishOrderBlock` (a cooldown filter -- is price above OB for X days?). Tyr uses `orderBlockBreakout` (an event trigger -- did an OB just get mitigated?). These are fundamentally different signals. VCP filters for an existing state; Tyr triggers on a specific event.

3. **Different exit philosophy.** VCP's emaCross(10,20) exit is a lagging indicator that lets winners run far (avg 54-day hold, 58.6% MFE capture). Tyr's trailing stop + bearish OB exit is more active, locking in gains and respecting resistance. This should produce shorter holds with better profit capture efficiency on individual trades, at the cost of missing some extended runners.

4. **Low overlap.** The `marketBreadthRecovering` condition fires on a handful of specific days per year (breadth crossing above EMA10). VCP fires whenever the market is in an uptrend. The signal overlap should be minimal -- most of the time, either VCP's market uptrend fires (but not breadth recovering) or vice versa.

### Risks and Weaknesses

1. **Very low trade count.** The combination of market breadth recovering (rare event) + OB breakout (specific event) + all other filters may produce too few trades for statistical validity. If < 300 trades over 10 years, the backtest is not reliable.

2. **False recovery traps.** Breadth recovering fires on every upswing, including bear market rallies. The 2022 bear market had multiple breadth recoveries that failed. The uptrend() + ADX filters should help, but some whipsaws are inevitable.

3. **Trailing stop drawback.** The trailing stop can exit too early during normal pullbacks in strong trends. VCP's emaCross(10,20) tolerates deeper pullbacks (the EMA cross only fires when the short-term trend reverses). Tyr's trailing stop might exit at a pullback low and miss the subsequent resumption.

4. **No volatility contraction filter.** Without VCP's volatility contracted condition, Tyr may enter stocks with high daily volatility that produce large intra-trade drawdowns. The wider 3.0 ATR stop partially compensates, but the experience may be rougher.

5. **Market breadth recovering is a crossover signal.** It fires on exactly the day breadth crosses above EMA10. If no OB breakout happens to occur on that same narrow window, no trade triggers. This is a feature (selectivity) and a bug (missed opportunities).

---

## Strategy 3: Value Zone Accumulation ("Baldr")

### Thesis

**Buy stocks that are building a base of consecutive higher closes within the value zone (near the 20 EMA), after the market breadth has reached washout levels, in sectors with aligned internals.** This is a mean-reversion-to-trend strategy. Instead of buying breakouts (VCP) or breakout events (Tyr), Baldr buys *accumulation patterns within established trends* -- stocks that have pulled back to the value zone and are being accumulated by patient buyers, as evidenced by consecutive higher closes.

The key insight: VCP waits for the breakout (price near Donchian high + volatility contracted + volume surge). Baldr enters *before* the breakout, when the stock is still in the value zone but showing signs of institutional accumulation. This creates a different risk/reward profile: smaller gains per trade but potentially higher win rate because the entry is closer to the moving average (natural support).

### Entry Conditions

```
entryStrategy {
    // MARKET (contrarian -- buy when breadth is washed out, the rubber band is stretched)
    marketBreadthNearDonchianLow(percentile = 0.15)

    // SECTOR
    sectorBreadthEmaAlignment()

    // STOCK
    uptrend()
    consecutiveHigherHighsInValueZone(consecutiveDays = 3, atrMultiplier = 2.0, emaPeriod = 20)
    emaAlignment(fastEmaPeriod = 10, slowEmaPeriod = 20)
    priceAbovePreviousLow()
    noEarningsWithinDays(days = 7)
    minimumPrice(10.0)
}
```

### Exit Conditions

```
exitStrategy {
    priceBelowEmaForDays(emaPeriod = 20, consecutiveDays = 3)
    stopLoss(atrMultiplier = 2.0)
    exitBeforeEarnings(days = 1)
}
```

### Condition Logic Explained

| Condition | Role | Why it matters |
|---|---|---|
| `marketBreadthNearDonchianLow(0.15)` | **Contrarian breadth signal** | Market breadth is in the bottom 15% of its Donchian channel. The broad market is washed out and due for a mean reversion bounce. This is the opposite of VCP's `marketUptrend()` -- Baldr is buying fear, not confirmation |
| `sectorBreadthEmaAlignment()` | Sector structural health | Even though the market is washed out, the stock's specific sector must still have aligned breadth EMAs. This filters for sectors with latent strength that are being dragged down by the broader market, not sectors in genuine structural decline |
| `uptrend()` | Stock quality | Minervini template. The stock must be in an uptrend even though the market is washed out. This selects for strong stocks holding up in a weak market -- the leaders that will run hardest when the market turns |
| `consecutiveHigherHighsInValueZone(3, 2.0, 20)` | **Accumulation pattern** | 3 consecutive higher closes while price is between 20EMA and 20EMA + 2xATR. This is the signature of patient institutional accumulation during a pullback: buyers are stepping in at progressively higher prices, but not chasing the stock above the value zone |
| `emaAlignment(10, 20)` | Short-term trend confirmation | 10 EMA must be above 20 EMA. Ensures the short-term trend is still intact even though the stock is in a pullback. If 10EMA < 20EMA, the pullback has become a reversal |
| `priceAbovePreviousLow()` | Support holding | Today's price must be above yesterday's low. Ensures the accumulation pattern is still intact and the stock is not breaking down |
| `noEarningsWithinDays(7)` | Binary event avoidance | No earnings within 7 days. Earnings inject unpriced risk that can overwhelm the technical setup |
| `priceBelowEmaForDays(20, 3)` exit | **Trend break confirmation** | Only exit when price stays below the 20 EMA for 3 consecutive days. This is more forgiving than an EMA cross -- it allows temporary dips below the EMA without exiting, only triggering when the trend has genuinely broken |
| `stopLoss(2.0)` exit | Tighter stop | 2.0 ATR (vs VCP's 2.5) because value zone entries are closer to the EMA, meaning there is less room for the trade to work against us before the thesis is invalidated. If the stock drops 2 ATR from a value zone entry, the accumulation thesis has failed |
| `exitBeforeEarnings(1)` exit | Matching entry filter | Since we avoided entering near earnings, we also exit before earnings to avoid binary risk on open positions |

### Expected Characteristics

| Characteristic | Estimate | Rationale |
|---|---|---|
| Trade frequency | Very low (100-500 over 10 years) | `marketBreadthNearDonchianLow(0.15)` fires infrequently. Combined with the value zone accumulation pattern, very selective |
| Average hold period | 15-40 days | Shorter than VCP because entries are closer to support (20EMA) and the exit trigger is a 3-day EMA break, not a cross |
| Best regimes | Market pullbacks within bull trends (Feb 2016, Feb 2018, Dec 2018, Oct 2023, Aug 2024) | The strategy explicitly targets these "buy the dip" moments with a structured accumulation confirmation |
| Worst regimes | 2022-style sustained bear markets, strong trending markets with no pullbacks | When breadth is near Donchian low because the market is in genuine collapse, the uptrend() + emaAlignment filters should prevent entries but some losses are inevitable. In strong trends without pullbacks, no signals fire |
| Expected win rate | 50-58% | Value zone entries are closer to support, so the probability of a bounce is higher. The tighter stop also preserves capital on failures |
| Expected edge | 3-6% | Smaller average wins (enters mid-pullback, not at breakout) but higher win rate. The edge per trade is likely lower than VCP, but with a different distribution: more small/medium winners, fewer large runners |

### Why it Complements VCP

1. **Opposite market timing.** VCP enters when the market is in an established uptrend (breadth is healthy). Baldr enters when breadth is near Donchian lows (market is washed out). These conditions are mutually exclusive on any given day. When VCP is active, Baldr is dormant, and vice versa. This is the strongest diversification benefit of the three proposals.

2. **Buys pullbacks, not breakouts.** VCP buys stocks near Donchian highs after volatility contraction. Baldr buys stocks in the value zone (near the 20 EMA) during accumulation. These are structurally different price levels and patterns. A stock in Baldr's value zone is not near its Donchian high; a stock near its Donchian high is not in the value zone.

3. **Higher win rate, lower payoff profile.** VCP is a low-WR (48.8%), high-payoff (3:1 W/L ratio) strategy. Baldr should be a moderate-WR (50-58%), moderate-payoff (1.5-2.5:1 W/L ratio) strategy. The combined portfolio has a smoother equity curve because Baldr provides steady small wins during VCP's dormant periods (market pullbacks).

4. **Earnings-aware.** VCP does not filter for earnings. Baldr avoids entries within 7 days of earnings and exits before earnings. This eliminates a source of uncontrolled risk that VCP tolerates.

5. **Captures "buy the dip" setups.** When the market pulls back within a bull trend (2016, 2018, 2019, 2023, 2024), VCP stops firing (marketUptrend fails). Baldr starts firing precisely during these pullbacks. A combined VCP + Baldr portfolio maintains signal flow through mini-corrections that would leave VCP-only dormant for weeks.

### Risks and Weaknesses

1. **Extremely low trade count.** The breadth near Donchian low condition fires on a small number of days per year. Combined with the value zone accumulation requirement, the total signal count may be too low for statistical validation. If < 200 trades over 10 years, the strategy cannot be validated reliably.

2. **Bear market false bottoms.** Market breadth can be near its Donchian low during a bear market that has further to fall. The `uptrend()` condition should prevent most of these entries, but a stock in a Minervini uptrend can still decline 20-30% if the market continues lower. The 2.0 ATR stop provides some protection.

3. **Value zone accumulation is fragile.** Three consecutive higher closes can happen randomly. The pattern is not as structurally robust as a volatility contraction or an order block breakout. There is a risk that many of the "accumulation" patterns are just noise that does not lead to a resumption of the trend.

4. **No order block component.** Like Fenrir, Baldr omits order block conditions entirely. This means it lacks VCP's primary alpha engine. The accumulation + contrarian breadth thesis must generate edge on its own.

5. **Tighter stop means more stop-outs.** The 2.0 ATR stop (vs VCP's 2.5 ATR) will stop out more trades that eventually would have recovered. VCP's analysis showed that moving from 2.5 to 2.0 ATR stopped out 233 additional trades, many of which were eventual winners. Baldr accepts this trade-off because the value zone entry is closer to the EMA, meaning a 2.0 ATR breach is a more meaningful failure signal.

6. **No volume condition.** Baldr does not require above-average volume. The rationale is that institutional accumulation can be quiet (they do not want to signal their activity). However, VCP's ablation study showed volume filters add +0.55pp of edge. Omitting volume may let in noise trades. This is a deliberate design choice that should be tested: if adding `volumeAboveAverage(1.0, 20)` (even a low threshold) improves results, it should be included.

---

## Portfolio-Level Considerations

### Signal Overlap Analysis

The three strategies should have minimal signal overlap:

| Market State | VCP Active? | Fenrir Active? | Tyr Active? | Baldr Active? |
|---|---|---|---|---|
| Bull trend, healthy breadth | Yes | Yes (if sectors rotating) | No | No |
| Bull trend, market pullback | No | Maybe | Yes (if recovering) | Yes |
| Bear market | No | No | Occasionally (false recoveries) | Occasionally |
| Narrow/concentrated market | Yes | No | No | Maybe |
| Sector rotation environment | Yes | Yes (primary) | Maybe | Maybe |
| V-shaped recovery | Delayed | Yes | Yes (primary) | Yes (primary) |

### Ranking and Priority

If running multiple strategies simultaneously with position limits, the ranker/allocation decision matters:

1. **VCP** remains the primary strategy (proven edge, validated, highest EC).
2. **Fenrir** activates when strong sector rotation is occurring. Allocate 30-40% of portfolio capacity to Fenrir signals when its conditions are met.
3. **Tyr** activates at market inflection points. Use sparingly (small allocation) because the trade count is very low and the signal is contrarian (higher risk of false recoveries).
4. **Baldr** activates during bull market pullbacks. Allocate 20-30% during breadth washout periods. These trades bridge the gap when VCP goes dormant.

### Combined Portfolio Hypothesis

A VCP + Fenrir + Baldr combination (dropping Tyr due to its very low signal count) should:

- Maintain signal flow across more market regimes than VCP alone
- Reduce the longest dormant period (when VCP has no trades)
- Lower max drawdown through diversification of entry timing
- Preserve 10/10 profitable years by having at least one strategy active in each year

### Implementation Priority

1. **Fenrir (Sector Rotation Momentum)** -- Highest priority. Most trade signals, testable hypothesis, uses well-understood sector breadth data. Likely to produce enough trades for statistical validation.

2. **Baldr (Value Zone Accumulation)** -- Second priority. Strongest diversification argument (opposite market timing from VCP). But low trade count risk means it may not be independently viable. Consider running it as a supplementary signal within the VCP portfolio rather than a standalone strategy.

3. **Tyr (Institutional Breakout)** -- Lowest priority. Theoretically elegant but the intersection of `marketBreadthRecovering` + `orderBlockBreakout` is extremely narrow. Likely too few trades for reliable validation. Would need to relax one of the filters (e.g., replace `marketBreadthRecovering` with `marketUptrend`) to produce enough signals, but that would make it more similar to VCP.

---

## Backtesting Recommendations

When implementing these strategies for backtesting:

1. **Run unlimited first.** Start with no position limits to measure raw edge, then add position sizing.
2. **Require >= 500 trades** for any strategy to be considered statistically valid. Below this, bootstrap confidence intervals are too wide.
3. **Check for VCP overlap.** Run each strategy alongside VCP and measure how many trades overlap (same stock, same day). If overlap > 30%, the strategy is not sufficiently differentiated.
4. **Walk-forward is essential.** Any strategy with < 1,000 trades over 10 years needs walk-forward validation to confirm it is not overfit.
5. **Ablation study each strategy.** Identify which condition is the alpha engine for each (as was done for VCP). If removing one condition drops edge by > 2pp, the strategy is fragile.
6. **Test exit variations.** The proposed exits differ from VCP. Run each strategy with both its proposed exit and VCP's exit (emaCross + stopLoss 2.5) to isolate entry alpha from exit alpha.

---

*Document created: 2026-03-07*
