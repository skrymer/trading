#!/usr/bin/env python3
"""
Cross-validates Midgaard's stored EMA and Donchian-upper values against an
independent Python recomputation from raw OHLCV.

Mirror of midgaard/.../service/IndicatorCalculator.kt:
- EMA: SMA seed over first `period` closes, then multiplier 2/(period+1).
       Output is 0.0 for indices [0, period-2], real EMA from index period-1.
- Donchian upper 5: max of high over [i-4, i] (inclusive of today). No zero
                    sentinel — the window is always non-empty even at index 0.

Values in Midgaard are stored at 4-decimal precision (BigDecimal HALF_UP),
so we treat abs-diff > 1e-4 as a real mismatch rather than rounding drift.

Stdlib-only — no pandas, no numpy. Requires `requests` (system package).
"""
from __future__ import annotations

import argparse
import sys

import requests

EMA_PERIODS = [5, 10, 20, 50, 100, 200]
DONCHIAN_PERIOD = 5
TOLERANCE = 1e-4

DEFAULT_SYMBOLS = [
    "SPY",
    "XLB", "XLC", "XLE", "XLF", "XLI", "XLK", "XLP", "XLRE", "XLU", "XLV", "XLY",
    "AAPL", "MSFT", "GOOGL", "AMZN", "META", "NVDA", "TSLA", "AVGO", "JPM",
    "V", "UNH", "XOM", "WMT", "JNJ", "PG", "MA", "HD", "CVX", "LLY", "ABBV",
    "KO", "PEP", "MRK", "COST", "BAC", "ORCL", "ADBE", "CRM", "CSCO",
    "MCD", "WFC", "AMD", "TXN", "INTC", "BA", "QCOM", "NKE", "IBM", "CAT",
]


def compute_ema(closes: list[float], period: int) -> list[float]:
    """Mirror of IndicatorCalculator.calculateEMA."""
    if len(closes) < period:
        return [0.0] * len(closes)
    multiplier = 2.0 / (period + 1)
    out = [0.0] * (period - 1)
    ema = sum(closes[:period]) / period  # SMA seed
    out.append(ema)
    for i in range(period, len(closes)):
        ema = (closes[i] - ema) * multiplier + ema
        out.append(ema)
    return out


def compute_donchian_upper(highs: list[float], period: int = DONCHIAN_PERIOD) -> list[float]:
    """Mirror of IndicatorCalculator.calculateDonchianUpper (inclusive window, no zero sentinel)."""
    out: list[float] = []
    for i in range(len(highs)):
        start = max(0, i - period + 1)
        out.append(max(highs[start : i + 1]))
    return out


def fetch_quotes(base_url: str, symbols: list[str], start: str | None, end: str | None) -> dict[str, list[dict]]:
    """Full history is required for correct EMA recomputation: the recurrence depends on the
    SMA seed at index `period - 1`, which only matches Midgaard's stored value when we start
    from the same earliest bar. Truncated fetches will diverge on every row."""
    params: list[tuple[str, str]] = [("symbols", s) for s in symbols]
    if start:
        params.append(("startDate", start))
    if end:
        params.append(("endDate", end))
    r = requests.get(f"{base_url}/api/quotes/bulk", params=params, timeout=300)
    r.raise_for_status()
    raw: dict[str, list[dict]] = r.json()
    return {sym: sorted(rows, key=lambda row: row["date"]) for sym, rows in raw.items() if rows}


def diff_one(expected: list[float], actual: list[float | None], dates: list[str]) -> tuple[float, int, list[tuple[str, float, float, float]]]:
    max_abs = 0.0
    mismatches: list[tuple[str, float, float, float]] = []
    count_over = 0
    for date, exp, act in zip(dates, expected, actual):
        if act is None:
            continue
        d = abs(exp - act)
        if d > max_abs:
            max_abs = d
        if d > TOLERANCE:
            count_over += 1
            if len(mismatches) < 5:
                mismatches.append((date, exp, act, d))
    return max_abs, count_over, mismatches


def check_symbol(sym: str, rows: list[dict], verbose: bool) -> list[tuple[str, str, float, int]]:
    """Returns list of (indicator, status, max_abs, count_over)."""
    closes = [float(r["close"]) for r in rows]
    highs = [float(r["high"]) for r in rows]
    dates = [r["date"] for r in rows]
    results: list[tuple[str, str, float, int]] = []

    for period in EMA_PERIODS:
        col = f"ema{period}"
        if col not in rows[0]:
            continue
        stored = [None if r.get(col) is None else float(r[col]) for r in rows]
        expected = compute_ema(closes, period)
        max_abs, count_over, mismatches = diff_one(expected, stored, dates)
        status = "PASS" if max_abs <= TOLERANCE else "FAIL"
        results.append((col, status, max_abs, count_over))
        if verbose and mismatches:
            for date, exp, act, d in mismatches:
                print(f"    {sym} {col} {date}: expected={exp:.6f} got={act:.6f} diff={d:.6f}")

    stored_donchian = [None if r.get("donchianUpper5") is None else float(r["donchianUpper5"]) for r in rows]
    expected = compute_donchian_upper(highs)
    max_abs, count_over, mismatches = diff_one(expected, stored_donchian, dates)
    status = "PASS" if max_abs <= TOLERANCE else "FAIL"
    results.append(("donchianUpper5", status, max_abs, count_over))
    if verbose and mismatches:
        for date, exp, act, d in mismatches:
            print(f"    {sym} donchianUpper5 {date}: expected={exp:.6f} got={act:.6f} diff={d:.6f}")

    return results


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8081")
    parser.add_argument("--start", default=None, help="Default: omit, fetch from symbol inception (required for correct EMA recomputation).")
    parser.add_argument("--end", default=None, help="Default: omit, fetch to latest.")
    parser.add_argument("--symbols", nargs="+", default=DEFAULT_SYMBOLS)
    parser.add_argument("--verbose", action="store_true", help="Print first 5 mismatched rows per (symbol, indicator)")
    args = parser.parse_args()

    print(f"Fetching {len(args.symbols)} symbols from {args.base_url} [{args.start or '<inception>'} .. {args.end or '<latest>'}]...")
    frames = fetch_quotes(args.base_url, args.symbols, args.start, args.end)
    print(f"Received data for {len(frames)} symbols.")

    any_fail = False
    rows: list[tuple[str, str, str, float, int]] = []
    for sym in args.symbols:
        if sym not in frames:
            print(f"  {sym}: no data returned, skipping")
            continue
        for indicator, status, max_abs, count_over in check_symbol(sym, frames[sym], args.verbose):
            rows.append((sym, indicator, status, max_abs, count_over))
            if status == "FAIL":
                any_fail = True

    # Per-indicator aggregation (across all symbols)
    print()
    print(f"{'indicator':<18} {'worst sym':<10} {'max abs diff':>14} {'rows > tol (all symbols)':>28}")
    print("-" * 74)
    by_ind: dict[str, list[tuple[str, float, int]]] = {}
    for sym, ind, _, m, c in rows:
        by_ind.setdefault(ind, []).append((sym, m, c))
    for ind, entries in by_ind.items():
        worst = max(entries, key=lambda e: e[1])
        total_over = sum(e[2] for e in entries)
        print(f"{ind:<18} {worst[0]:<10} {worst[1]:>14.2e} {total_over:>28}")

    print()
    if any_fail:
        print("RESULT: FAILED — at least one indicator exceeds tolerance. Re-run with --verbose to inspect mismatches.")
        return 1
    print(f"RESULT: PASSED — all indicators within {TOLERANCE:.1e} tolerance across {len(frames)} symbols.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
