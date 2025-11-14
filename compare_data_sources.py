#!/usr/bin/env python3
"""
Compare stock data from Ovtlyr (via backend API) with Yahoo Finance
"""

import requests
import yfinance as yf
import pandas as pd
from datetime import datetime, timedelta
from typing import Dict, List

# Configuration
BACKEND_URL = "http://localhost:8080"
TEST_SYMBOLS = ["AAPL", "MSFT", "SPY"]
START_DATE = datetime(2020, 1, 1)  # January 1, 2020


def get_ovtlyr_data(symbol: str) -> pd.DataFrame:
    """Fetch data from Ovtlyr via backend API"""
    print(f"Fetching {symbol} from Ovtlyr...")

    url = f"{BACKEND_URL}/api/stocks/{symbol}"
    response = requests.get(url)

    if response.status_code != 200:
        print(f"  Error: HTTP {response.status_code}")
        return pd.DataFrame()

    data = response.json()
    quotes = data.get('quotes', [])

    if not quotes:
        print(f"  No quotes found")
        return pd.DataFrame()

    # Convert to DataFrame
    df = pd.DataFrame(quotes)
    # Ovtlyr data is in Central US time, convert to timezone-naive for comparison
    df['date'] = pd.to_datetime(df['date']).dt.tz_localize(None)
    df = df.sort_values('date')

    print(f"  Found {len(df)} quotes from {df['date'].min()} to {df['date'].max()}")

    return df[['date', 'openPrice', 'high', 'low', 'closePrice', 'atr']]


def get_yahoo_data(symbol: str, start_date: datetime = START_DATE) -> pd.DataFrame:
    """Fetch data from Yahoo Finance"""
    print(f"Fetching {symbol} from Yahoo Finance...")

    end_date = datetime.now()

    ticker = yf.Ticker(symbol)
    df = ticker.history(start=start_date, end=end_date)

    if df.empty:
        print(f"  No data found")
        return pd.DataFrame()

    df.reset_index(inplace=True)
    # Remove timezone info for comparison with Ovtlyr data
    df['Date'] = pd.to_datetime(df['Date']).dt.tz_localize(None)

    print(f"  Found {len(df)} quotes from {df['Date'].min()} to {df['Date'].max()}")

    return df[['Date', 'Open', 'High', 'Low', 'Close', 'Volume']]


def compare_data(ovtlyr_df: pd.DataFrame, yahoo_df: pd.DataFrame, symbol: str):
    """Compare the two data sources"""
    print(f"\n{'='*60}")
    print(f"Comparison for {symbol}")
    print(f"{'='*60}\n")

    if ovtlyr_df.empty or yahoo_df.empty:
        print("  ❌ Cannot compare - one or both sources have no data\n")
        return

    # Rename columns for easier comparison
    ovtlyr_df = ovtlyr_df.rename(columns={
        'date': 'Date',
        'openPrice': 'Open',
        'high': 'High',
        'low': 'Low',
        'closePrice': 'Close'
    })

    # Merge on date
    merged = pd.merge(
        ovtlyr_df,
        yahoo_df,
        on='Date',
        suffixes=('_ovtlyr', '_yahoo'),
        how='inner'
    )

    if merged.empty:
        print("  ❌ No overlapping dates found\n")
        return

    print(f"Overlapping dates: {len(merged)} days")
    print(f"Date range: {merged['Date'].min().date()} to {merged['Date'].max().date()}\n")

    # Calculate differences
    price_fields = ['Open', 'High', 'Low', 'Close']

    print("Average Differences:")
    print(f"{'Field':<15} {'Avg Diff':<15} {'Avg % Diff':<15} {'Max % Diff':<15}")
    print("-" * 60)

    for field in price_fields:
        ovtlyr_col = f"{field}_ovtlyr"
        yahoo_col = f"{field}_yahoo"

        if ovtlyr_col in merged.columns and yahoo_col in merged.columns:
            diff = merged[ovtlyr_col] - merged[yahoo_col]
            pct_diff = (diff / merged[yahoo_col] * 100).abs()

            avg_diff = diff.mean()
            avg_pct_diff = pct_diff.mean()
            max_pct_diff = pct_diff.max()

            print(f"{field:<15} ${avg_diff:>8.4f}      {avg_pct_diff:>8.4f}%      {max_pct_diff:>8.4f}%")

    print("\nSample Data (Last 5 Days):")
    print(merged[['Date', 'Close_ovtlyr', 'Close_yahoo']].tail())

    # Check if differences are acceptable (< 0.5%)
    close_pct_diff = ((merged['Close_ovtlyr'] - merged['Close_yahoo']) / merged['Close_yahoo'] * 100).abs()
    avg_close_diff = close_pct_diff.mean()

    if avg_close_diff < 0.5:
        print(f"\n✅ Data sources are very similar (avg close diff: {avg_close_diff:.4f}%)")
    elif avg_close_diff < 2:
        print(f"\n⚠️  Data sources have minor differences (avg close diff: {avg_close_diff:.4f}%)")
    else:
        print(f"\n❌ Data sources have significant differences (avg close diff: {avg_close_diff:.4f}%)")

    print()


def main():
    print("Stock Data Comparison: Ovtlyr vs Yahoo Finance")
    print("=" * 60)
    print()

    # Check if backend is running
    try:
        response = requests.get(f"{BACKEND_URL}/actuator/health", timeout=2)
        if response.status_code != 200:
            print("❌ Backend is not healthy")
            return
    except Exception as e:
        print(f"❌ Cannot connect to backend at {BACKEND_URL}")
        print(f"   Error: {e}")
        print("\n   Make sure the backend is running with: cd udgaard && ./gradlew bootRun")
        return

    print("✅ Backend is running\n")

    for symbol in TEST_SYMBOLS:
        try:
            ovtlyr_data = get_ovtlyr_data(symbol)
            yahoo_data = get_yahoo_data(symbol, START_DATE)
            compare_data(ovtlyr_data, yahoo_data, symbol)
        except Exception as e:
            print(f"\n❌ Error processing {symbol}: {e}\n")

    print("=" * 60)
    print("Comparison complete!")


if __name__ == "__main__":
    main()
