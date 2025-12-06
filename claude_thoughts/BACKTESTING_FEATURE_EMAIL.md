# Backtesting Features - Quick Overview

---

**Subject:** Backtesting Platform Features

---

Hey!

Quick guide on the backtesting platform features.

*[SCREENSHOT PLACEHOLDER: Strategy selection dropdown showing all available strategies]*

---

## The Built-In Strategies

We've got a few pre-built strategies you can mix and match:

**Entry Strategies:**
- **Plan Alpha** - Follows trends using EMAs and checks market breadth
- **Plan Beta** - More complex, looks at multiple factors and sector rotation
- **Plan ETF** - Designed for ETF trading with market regime filtering
- **Simple Buy Signal** - Basic technical indicator approach

**Exit Strategies:**
- **Plan Money** - Uses ATR for stop losses with trailing stops
- **Plan Alpha** - Exits when EMA trends reverse
- **Plan ETF** - Exits based on market breadth changes

Pick one entry, one exit, and you're good to go.

*[SCREENSHOT PLACEHOLDER: Stock selection interface with search and multi-select]*

---

## Building Your Own Strategies

Don't want to use the pre-built strategies? You can create your own using the custom strategy builder.

The builder lets you combine different conditions to create exactly the strategy you want. Just select conditions from dropdowns and configure them - no coding required.

**Entry Strategy Conditions:**
- Buy signal must be active
- Price must be above a specific EMA (choose 5, 10, 20, or 50 day)
- Market must be in uptrend (based on breadth)
- Sector must be showing strength
- Market sentiment must be above a threshold you set
- Sector sentiment must be above a threshold you set
- And more...

**Exit Strategy Conditions:**
- Stop loss triggered (based on ATR multiples you choose)
- Price crosses below a specific EMA
- Market sentiment drops below threshold
- Market breadth deteriorates
- Profit target reached
- Maximum hold time exceeded
- And more...

You can add as many conditions as you want. All conditions must be true for a signal to trigger.

*[SCREENSHOT PLACEHOLDER: Custom strategy builder interface showing condition selection]*

---

## Stock Selection

You can backtest on:
- Individual stocks (AAPL, MSFT, whatever)
- Index ETFs (SPY, QQQ, IWM)
- Sector ETFs (XLK, XLF, etc.)
- Leveraged ETFs (TQQQ, SOXL - more on this below)
- Or just select "All Stocks" to test everything at once

Use the search box to find specific symbols or browse the full list.

---

## The Technical Indicators

Every stock includes these pre-calculated indicators:
- **EMAs** - 5, 10, 20, and 50-day exponential moving averages
- **ATR** - Average True Range for volatility measurement
- **Donchian Channels** - For breakout detection
- **Market Breadth** - SPY, QQQ, and IWM breadth percentages
- **Sector Strength** - How many stocks in each sector are trending up
- **Heatmaps** - Sentiment scores from 0-100 for stock/sector/market

You don't have to calculate anything - it's all ready to use in your strategies.

*[SCREENSHOT PLACEHOLDER: Chart showing stock price with EMAs, ATR bands, and indicators]*

---

## What You Get Back

After running a backtest, you'll see:

**Summary Stats:**
- Total trades made
- How many won vs. lost
- Win rate percentage
- Average win and average loss
- Your "edge" - expected gain per trade

**Financial Performance:**
- Total profit/loss in dollars
- ROI percentage
- Biggest win and biggest loss

**Equity Curve:**
- A chart showing how your account balance would have changed over time
- Helps you see drawdown periods and recovery

**Trade List:**
- Every single trade with entry/exit dates and prices
- Why each trade exited (stop loss, trend reversal, etc.)

*[SCREENSHOT PLACEHOLDER: Performance metrics dashboard showing win rate, edge, total profit]*

---

## The Equity Curve

Line chart showing cumulative balance over time. Useful for spotting:
- When the strategy works well
- When it struggles
- How long drawdowns last
- Whether it recovers consistently

You can zoom in on specific periods for detailed analysis.

*[SCREENSHOT PLACEHOLDER: Equity curve line chart]*

---

## Trade Details

Click on any trade in the list to see full details:
- Entry date and price
- Exit date and price
- How many shares
- Profit or loss
- Exit reason

You can sort by any column, filter by symbol, or search for specific trades.

*[SCREENSHOT PLACEHOLDER: Trade list table with sorting and filtering options]*

---

## Sector Analysis

Want to know which sectors your strategy crushes in? The sector breakdown shows:
- Profit by sector
- Win rate by sector
- Best performing stocks in each sector

Click on a sector to see only trades from that sector. Helps you decide if you want to focus on specific sectors or avoid others.

*[SCREENSHOT PLACEHOLDER: Sector performance breakdown chart]*

---

## Market Breadth

Strategies can filter trades based on market breadth data.

Available breadth indicators:
- SPY (S&P 500)
- QQQ (NASDAQ 100)
- IWM (Russell 2000)

*[SCREENSHOT PLACEHOLDER: Market breadth chart showing SPY/QQQ/IWM over time]*

---

## Leveraged ETFs

Automatic underlying mapping:
- TQQQ → QQQ signals
- SOXL → SOXX signals
- UPRO → SPY signals

Override mappings in the configuration screen if needed.

*[SCREENSHOT PLACEHOLDER: Stock selection showing TQQQ with automatic QQQ mapping]*

---

## How to Run a Backtest

**Step 1: Open the configuration**
Click "Configure Backtest" button

*[SCREENSHOT PLACEHOLDER: Backtest configuration modal - Step 1]*

---

**Step 2: Choose your strategies**
Select an entry strategy from the dropdown (or click "Create Custom" to build your own)
Select an exit strategy from the dropdown (or click "Create Custom")

*[SCREENSHOT PLACEHOLDER: Strategy selection dropdowns]*

---

**Step 3: Select stocks**
Search for specific symbols or select "All Stocks"
You can select multiple individual stocks

*[SCREENSHOT PLACEHOLDER: Stock selection with search]*

---

**Step 4: Set date range**
Pick start and end dates using the date picker
Or choose preset ranges (1 year, 2 years, 5 years)

*[SCREENSHOT PLACEHOLDER: Date range selector]*

---

**Step 5: Run it**
Click "Run Backtest" and wait a few seconds

*[SCREENSHOT PLACEHOLDER: Loading state with progress]*

---

**Step 6: Analyze results**
Review the metrics, charts, and trade list

*[SCREENSHOT PLACEHOLDER: Full results page]*

---

## Example Scenarios

**Conservative:**
Entry: Plan Alpha | Exit: Plan Money | Stocks: SPY, QQQ

**Aggressive:**
Entry: Plan Beta | Exit: Plan Alpha | Stocks: NVDA, AMD, TSLA

**Sector Rotation:**
Entry: Plan ETF | Exit: Plan ETF | Stocks: Sector ETFs

**Custom:**
Entry: Buy signal + price above 50 EMA + market sentiment > 60
Exit: Stop loss 1 ATR + price below 20 EMA

---

## Strategy Development Workflow

**1. Start with a built-in strategy**
Run Plan Alpha to see baseline performance

**2. Review the results**
Check win rate, drawdowns, sector performance
Look at which trades worked and which didn't

**3. Create a variation**
Click "Create Custom Strategy"
Add or remove conditions based on what you learned

**4. Test again**
Run the new strategy and compare results

**5. Iterate**
Keep refining until you find your edge
Save strategies you like with descriptive names

*[SCREENSHOT PLACEHOLDER: Custom strategy builder with saved strategies list]*

---

## Comparing Strategies

Run multiple backtests to compare:
- Win rates across entry strategies
- Loss limiting across exit strategies
- Market breadth filter impact
- EMA period optimization

Results persist in session for side-by-side comparison.

*[SCREENSHOT PLACEHOLDER: Multiple backtest tabs open for comparison]*

---

## Technical Details

**Assumptions:**
- Adjusted prices (splits/dividends)
- Next day's close execution
- No look-ahead bias

**Data:**
- Prices/ATR: AlphaVantage
- Breadth/sentiment: Ovtlyr
- Daily timeframe (EOD)

**Performance:**
- Cached results for instant re-runs
- Handles thousands of trades

---

## Tips

- Test multiple time periods for robustness
- Validate with sufficient trade count
- Use "All Stocks" to find best performers
- Start simple, add complexity only if it improves results
- Save and document your custom strategies

---

## Combining Multiple Conditions

Stack multiple conditions (all must be true):
1. Buy signal active
2. Price above 20-day EMA
3. Market in uptrend
4. Sector in uptrend
5. Market sentiment above 50

Balance trade frequency vs. quality using backtest results.

*[SCREENSHOT PLACEHOLDER: Custom strategy showing multiple stacked conditions]*

---

## Questions?

Click info icons for strategy details. Hover over conditions for descriptions.

---

Cheers!

---

## Screenshot List

1. Strategy selection dropdown showing all available strategies
2. Stock selection interface with search and multi-select
3. Custom strategy builder interface showing condition selection
4. Chart showing stock price with EMAs, ATR bands, and indicators
5. Performance metrics dashboard showing win rate, edge, total profit
6. Equity curve line chart
7. Trade list table with sorting and filtering options
8. Sector performance breakdown chart
9. Market breadth chart showing SPY/QQQ/IWM over time
10. Stock selection showing TQQQ with automatic QQQ mapping
11. Backtest configuration modal - Step 1
12. Strategy selection dropdowns
13. Stock selection with search
14. Date range selector
15. Loading state with progress
16. Full results page
17. Custom strategy builder with saved strategies list
18. Multiple backtest tabs open for comparison
19. Custom strategy showing multiple stacked conditions

---

*Last Updated: December 5, 2024*
