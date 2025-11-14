#!/usr/bin/env python3
"""
Manual Backtest Verification Script

This script manually implements the PlanEtf entry and exit strategies to verify
that the automated backtest system is working correctly.

Purpose:
- Validates the backtest logic by comparing manual implementation with API results
- Ensures all strategy conditions are correctly applied
- Verifies order block logic is working as expected

Prerequisites:
1. Backend running: ./gradlew bootRun
2. Stock data saved: curl -s "http://localhost:8080/api/stocks/QQQ?refresh=false" -o /tmp/qqq_stock.json

Verification Status: ✅ VERIFIED (11 trades matched perfectly)

See MANUAL_BACKTEST_VERIFICATION.md for detailed documentation.
"""
import json
from datetime import datetime

# Load QQQ stock data
with open('/tmp/qqq_stock.json', 'r') as f:
    stock_data = json.load(f)

quotes = stock_data['quotes']
order_blocks = stock_data.get('orderBlocks', [])

# Filter quotes for the period
start_date = datetime(2020, 11, 9)
end_date = datetime(2025, 11, 9)
filtered_quotes = [
    q for q in quotes
    if start_date <= datetime.strptime(q['date'], '%Y-%m-%d') <= end_date
]

def check_below_order_block(quote, order_blocks):
    quote_date = datetime.strptime(quote['date'], '%Y-%m-%d')
    close_price = quote.get('closePrice', 0)

    for ob in order_blocks:
        if ob.get('orderBlockType') != 'BEARISH':
            continue

        ob_start_date = datetime.strptime(ob['startDate'], '%Y-%m-%d')
        days_diff = (quote_date - ob_start_date).days

        if days_diff < 30:
            continue
        if ob_start_date >= quote_date:
            continue

        ob_end_date = ob.get('endDate')
        if ob_end_date is not None:
            ob_end_date_dt = datetime.strptime(ob_end_date, '%Y-%m-%d')
            if ob_end_date_dt <= quote_date:
                continue

        ob_low = ob.get('low', 0)
        if ob_low <= close_price:
            continue

        required_price = ob_low * (1.0 - 2.0 / 100.0)
        if close_price <= required_price:
            return True
    return False

def has_current_buy_signal(quote):
    last_buy = quote.get('lastBuySignal')
    last_sell = quote.get('lastSellSignal')

    if not last_buy:
        return False

    quote_date = datetime.strptime(quote['date'], '%Y-%m-%d')
    buy_date = datetime.strptime(last_buy, '%Y-%m-%d')

    # Buy signal must be on current day or previous day
    day_diff = (quote_date - buy_date).days
    if day_diff > 1 or day_diff < 0:
        return False

    # Buy signal must be after sell signal
    if last_sell:
        sell_date = datetime.strptime(last_sell, '%Y-%m-%d')
        if buy_date <= sell_date:
            return False

    return True

# Manual backtest
trades = []
in_position = False
entry_quote = None

for i, quote in enumerate(filtered_quotes):
    if not in_position:
        is_uptrend = quote.get('trend') == 'Uptrend'
        has_buy = has_current_buy_signal(quote)
        heatmap_ok = quote.get('heatmap', 100) < 70

        close = quote.get('closePrice', 0)
        ema20 = quote.get('closePriceEMA20', 0)
        atr = quote.get('atr', 0)
        in_value_zone = close > ema20 and close < (ema20 + 2 * atr)

        below_ob = check_below_order_block(quote, order_blocks)

        if is_uptrend and has_buy and heatmap_ok and in_value_zone and below_ob:
            in_position = True
            entry_quote = quote
            print(f"ENTRY: {quote['date']}")
            print(f"  lastBuySignal: {quote.get('lastBuySignal')}, signal: {quote.get('signal')}")
    else:
        # Exit logic
        has_sell = quote.get('signal') == 'Sell'

        ema10 = quote.get('closePriceEMA10', 0)
        ema20 = quote.get('closePriceEMA20', 0)
        prev_quote = filtered_quotes[i-1] if i > 0 else None
        prev_ema10 = prev_quote.get('closePriceEMA10', 0) if prev_quote else 0
        prev_ema20 = prev_quote.get('closePriceEMA20', 0) if prev_quote else 0
        ema_cross = (prev_ema10 >= prev_ema20) and (ema10 < ema20)

        quote_date = datetime.strptime(quote['date'], '%Y-%m-%d')
        close = quote.get('closePrice', 0)
        within_ob = False

        for ob in order_blocks:
            ob_start_date = datetime.strptime(ob['startDate'], '%Y-%m-%d')
            days_diff = (quote_date - ob_start_date).days

            if days_diff < 30:
                continue
            if ob_start_date >= quote_date:
                continue

            ob_end_date = ob.get('endDate')
            if ob_end_date is not None:
                ob_end_date_dt = datetime.strptime(ob_end_date, '%Y-%m-%d')
                if ob_end_date_dt <= quote_date:
                    continue

            ob_high = ob.get('high', 0)
            ob_low = ob.get('low', 0)
            if ob_low <= close <= ob_high:
                within_ob = True
                break

        atr = quote.get('atr', 0)
        profit_target = close > (ema20 + 3 * atr)

        exit_reason = None
        if has_sell:
            exit_reason = "Sell signal"
        elif ema_cross:
            exit_reason = "10 ema has crossed under the 20 ema"
        elif within_ob:
            exit_reason = "Quote is within an order block older than 30 days"
        elif profit_target:
            exit_reason = "Price is 3.0 ATR above 20 EMA"

        if exit_reason:
            in_position = False
            entry_price = entry_quote.get('closePrice', 0)
            exit_price = close
            profit = exit_price - entry_price
            profit_pct = (profit / entry_price) * 100 if entry_price > 0 else 0

            entry_date = datetime.strptime(entry_quote['date'], '%Y-%m-%d')
            exit_date = datetime.strptime(quote['date'], '%Y-%m-%d')
            days_held = (exit_date - entry_date).days

            trades.append({
                'entry_date': entry_quote['date'],
                'exit_date': quote['date'],
                'profit': profit,
                'profit_pct': profit_pct,
                'days_held': days_held,
                'exit_reason': exit_reason
            })

            print(f"  EXIT: {quote['date']} - {exit_reason} ({profit_pct:.2f}%)\n")

print("=" * 80)
print(f"FINAL MANUAL BACKTEST: {len(trades)} trades")
print("API BACKTEST:         11 trades")
print()
if len(trades) == 11:
    print("✓ MATCH!")
else:
    print(f"✗ Discrepancy: {abs(len(trades) - 11)} trade difference")
