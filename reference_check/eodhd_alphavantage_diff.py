#!/usr/bin/env python3
"""
Cross-validates EODHD-provided OHLCV + ATR + ADX against AlphaVantage for the
same symbol set, before promoting EODHD as the default ingestion source.

Independence rationale: AlphaVantage and EODHD are unrelated upstreams; if both
agree to within a small tolerance, the values are likely correct. Disagreement
beyond tolerance flags either a calculation difference (Wilder vs RMA), a
split-adjustment lag, or one provider's data being wrong.

Tolerances:
- adjusted_close: 1e-3 (price-level rounding)
- ATR / ADX: 1.0% (Wilder smoothing implementation differences ~0.5-1%)

Stdlib-only — no pandas, no numpy. Requires `requests` (system package).
"""
from __future__ import annotations

import argparse
import sys
from datetime import date, timedelta

import requests

DEFAULT_SYMBOLS = [
    "AAPL", "MSFT", "NVDA", "GOOGL", "META",
    "AMZN", "TSLA", "AMD", "AVGO", "NFLX",
    "JPM", "V", "MA", "WMT", "COST",
    "UNH", "JNJ", "PG", "KO", "PEP",
]

PRICE_TOLERANCE = 1e-3
INDICATOR_TOLERANCE_PCT = 1.0
# EODHD computes Wilder-smoothed indicators from the requested `from` date
# forward, so the first ~3×period bars haven't fully converged. AlphaVantage
# always has years of history seeding the same calculation. Pull EODHD with a
# generous warmup buffer and skip the first WARMUP_TRADING_DAYS from the
# comparison so we are diffing converged values, not warmup transients.
INDICATOR_WARMUP_DAYS = 90


def fetch_eodhd_eod(symbol: str, api_key: str, start: date, end: date) -> dict[str, float]:
    """Fetch adjusted-close per date from EODHD `/eod` endpoint."""
    url = f"https://eodhd.com/api/eod/{symbol}.US"
    params = {
        "api_token": api_key,
        "fmt": "json",
        "from": start.isoformat(),
        "to": end.isoformat(),
    }
    resp = requests.get(url, params=params, timeout=30)
    resp.raise_for_status()
    return {row["date"]: float(row["adjusted_close"]) for row in resp.json() if row.get("adjusted_close") is not None}


def fetch_eodhd_indicator(symbol: str, api_key: str, function: str, start: date, end: date) -> dict[str, float]:
    """Fetch a single indicator series (atr|adx) from EODHD `/technical`.

    Pulls from `start - INDICATOR_WARMUP_DAYS` so Wilder smoothing has time
    to converge before the comparison window begins.
    """
    url = f"https://eodhd.com/api/technical/{symbol}.US"
    fetch_start = start - timedelta(days=INDICATOR_WARMUP_DAYS)
    params = {
        "api_token": api_key,
        "fmt": "json",
        "function": function,
        "period": 14,
        "from": fetch_start.isoformat(),
        "to": end.isoformat(),
    }
    resp = requests.get(url, params=params, timeout=30)
    resp.raise_for_status()
    rows = {row["date"]: float(row[function]) for row in resp.json() if row.get(function) is not None}
    return {d: v for d, v in rows.items() if date.fromisoformat(d) >= start}


def fetch_av_ohlcv(symbol: str, api_key: str) -> dict[str, float]:
    """Fetch adjusted-close per date from AlphaVantage TIME_SERIES_DAILY_ADJUSTED."""
    url = "https://www.alphavantage.co/query"
    params = {
        "function": "TIME_SERIES_DAILY_ADJUSTED",
        "symbol": symbol,
        "outputsize": "full",
        "apikey": api_key,
    }
    resp = requests.get(url, params=params, timeout=30)
    resp.raise_for_status()
    series = resp.json().get("Time Series (Daily)", {})
    return {d: float(row["5. adjusted close"]) for d, row in series.items()}


def fetch_av_indicator(symbol: str, api_key: str, function: str) -> dict[str, float]:
    """Fetch a single indicator series (ATR|ADX) from AlphaVantage."""
    url = "https://www.alphavantage.co/query"
    params = {
        "function": function,
        "symbol": symbol,
        "interval": "daily",
        "time_period": 14,
        "apikey": api_key,
    }
    resp = requests.get(url, params=params, timeout=30)
    resp.raise_for_status()
    series = resp.json().get(f"Technical Analysis: {function}", {})
    return {d: float(row[function]) for d, row in series.items()}


def diff_series(
    label: str,
    symbol: str,
    eodhd: dict[str, float],
    av: dict[str, float],
    abs_tol: float | None,
    pct_tol: float | None,
) -> tuple[int, int, float]:
    """Compare two date→value maps. Returns (compared, fails, max_pct_diff)."""
    common = sorted(set(eodhd) & set(av))
    if not common:
        print(f"  {label} {symbol}: no overlapping dates; skipping")
        return 0, 0, 0.0
    fails = 0
    max_pct = 0.0
    for d in common:
        a, b = eodhd[d], av[d]
        if a == 0 == b:
            continue
        abs_diff = abs(a - b)
        pct_diff = abs_diff / max(abs(a), abs(b)) * 100.0
        max_pct = max(max_pct, pct_diff)
        if abs_tol is not None and abs_diff > abs_tol:
            fails += 1
        elif pct_tol is not None and pct_diff > pct_tol:
            fails += 1
    return len(common), fails, max_pct


def run(symbols: list[str], eodhd_key: str, av_key: str, days: int) -> int:
    end = date.today()
    start = end - timedelta(days=days)
    overall_fails = 0
    print(f"Cross-checking EODHD vs AlphaVantage from {start} to {end} ({len(symbols)} symbols)\n")

    for symbol in symbols:
        try:
            eod_close = fetch_eodhd_eod(symbol, eodhd_key, start, end)
            av_close = fetch_av_ohlcv(symbol, av_key)
            n, f, mx = diff_series("EOD", symbol, eod_close, av_close, abs_tol=PRICE_TOLERANCE, pct_tol=None)
            print(f"  {symbol} EOD : {n} dates compared, {f} fails, max diff {mx:.4f}%")
            overall_fails += f

            for fn in ("atr", "adx"):
                eod_ind = fetch_eodhd_indicator(symbol, eodhd_key, fn, start, end)
                av_ind = fetch_av_indicator(symbol, av_key, fn.upper())
                n, f, mx = diff_series(fn.upper(), symbol, eod_ind, av_ind, abs_tol=None, pct_tol=INDICATOR_TOLERANCE_PCT)
                print(f"  {symbol} {fn.upper()} : {n} dates compared, {f} fails, max diff {mx:.4f}%")
                overall_fails += f
        except requests.RequestException as e:
            print(f"  {symbol}: HTTP error — {e}")
            overall_fails += 1

    print(f"\nTotal mismatches above tolerance: {overall_fails}")
    return 0 if overall_fails == 0 else 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--eodhd-key", required=True, help="EODHD API token")
    parser.add_argument("--av-key", required=True, help="AlphaVantage API key")
    parser.add_argument("--symbols", nargs="*", default=DEFAULT_SYMBOLS, help="Symbol list (default: 20 mega-caps)")
    parser.add_argument("--days", type=int, default=200, help="Lookback window in days (default: 200)")
    args = parser.parse_args()
    return run(args.symbols, args.eodhd_key, args.av_key, args.days)


if __name__ == "__main__":
    sys.exit(main())
