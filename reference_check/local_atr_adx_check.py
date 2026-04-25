#!/usr/bin/env python3
"""
Quick validator: locally-computed ATR + ADX vs AlphaVantage's published values.

Pulls OHLCV from EODHD (split/dividend-adjusted via adjusted_close / close
factor — same logic the production EodhdProvider uses), computes Wilder-smoothed
ATR(14) and ADX(14) in pure Python, then diffs against AlphaVantage's ATR/ADX
endpoints. Confirms whether we can throw away EODHD's broken indicator API and
recompute locally without losing parity to the existing AV-sourced values.

Tolerance: 0.5% relative on a converged sample (skip first 60 bars). Runs on a
small symbol set that includes split-affected names (NFLX, AVGO) to verify the
local calculation doesn't have the same split bug as EODHD's `/api/technical/`.

Stdlib-only — no pandas, no numpy. Requires `requests`.
"""
from __future__ import annotations

import argparse
import sys
from datetime import date, timedelta

import requests

PERIOD = 14
TOLERANCE_PCT = 0.5
WARMUP_SKIP_BARS = 60  # discard early bars where seed has not converged


def fetch_eodhd_bars(symbol: str, api_key: str, start: date, end: date) -> list[dict]:
    """Fetch raw EOD bars from EODHD and apply the split/dividend factor to OHL."""
    url = f"https://eodhd.com/api/eod/{symbol}.US"
    params = {"api_token": api_key, "fmt": "json", "from": start.isoformat(), "to": end.isoformat()}
    resp = requests.get(url, params=params, timeout=30)
    resp.raise_for_status()
    bars = []
    for r in resp.json():
        raw_close = r.get("close")
        adj_close = r.get("adjusted_close")
        if raw_close in (None, 0) or adj_close is None:
            continue
        factor = adj_close / raw_close
        bars.append(
            {
                "date": r["date"],
                "high": float(r["high"]) * factor,
                "low": float(r["low"]) * factor,
                "close": adj_close,
            }
        )
    bars.sort(key=lambda b: b["date"])
    return bars


def wilder_seed_then_smooth(values: list[float], period: int) -> list[float | None]:
    """Wilder smoothing: SMA seed across the first `period` values, then RMA forward.

    Returns a list aligned with `values`. Indices [0, period-1] are None
    because the seed isn't ready yet.
    """
    out: list[float | None] = [None] * len(values)
    if len(values) < period:
        return out
    seed = sum(values[:period]) / period
    out[period - 1] = seed
    prev = seed
    for i in range(period, len(values)):
        prev = (prev * (period - 1) + values[i]) / period
        out[i] = prev
    return out


def compute_atr(bars: list[dict], period: int = PERIOD) -> dict[str, float]:
    """Wilder-smoothed ATR. Returns {date: atr} for bars where ATR is defined."""
    trs: list[float] = []
    for i, b in enumerate(bars):
        if i == 0:
            trs.append(b["high"] - b["low"])
            continue
        prev_close = bars[i - 1]["close"]
        tr = max(b["high"] - b["low"], abs(b["high"] - prev_close), abs(b["low"] - prev_close))
        trs.append(tr)
    atr = wilder_seed_then_smooth(trs, period)
    return {bars[i]["date"]: a for i, a in enumerate(atr) if a is not None}


def compute_adx(bars: list[dict], period: int = PERIOD) -> dict[str, float]:
    """Wilder-smoothed ADX (Welles Wilder's classic formulation)."""
    plus_dm: list[float] = [0.0]
    minus_dm: list[float] = [0.0]
    trs: list[float] = [bars[0]["high"] - bars[0]["low"]]
    for i in range(1, len(bars)):
        up = bars[i]["high"] - bars[i - 1]["high"]
        down = bars[i - 1]["low"] - bars[i]["low"]
        plus_dm.append(up if (up > down and up > 0) else 0.0)
        minus_dm.append(down if (down > up and down > 0) else 0.0)
        prev_close = bars[i - 1]["close"]
        tr = max(
            bars[i]["high"] - bars[i]["low"],
            abs(bars[i]["high"] - prev_close),
            abs(bars[i]["low"] - prev_close),
        )
        trs.append(tr)

    smoothed_tr = wilder_seed_then_smooth(trs, period)
    smoothed_plus = wilder_seed_then_smooth(plus_dm, period)
    smoothed_minus = wilder_seed_then_smooth(minus_dm, period)

    dx_values: list[float] = []
    dx_dates: list[str] = []
    for i, b in enumerate(bars):
        st, sp, sm = smoothed_tr[i], smoothed_plus[i], smoothed_minus[i]
        if st is None or sp is None or sm is None or st == 0:
            continue
        plus_di = 100.0 * sp / st
        minus_di = 100.0 * sm / st
        denom = plus_di + minus_di
        if denom == 0:
            continue
        dx_values.append(100.0 * abs(plus_di - minus_di) / denom)
        dx_dates.append(b["date"])

    adx_seq = wilder_seed_then_smooth(dx_values, period)
    return {dx_dates[i]: a for i, a in enumerate(adx_seq) if a is not None}


def fetch_av_indicator(symbol: str, function: str, api_key: str) -> dict[str, float]:
    url = "https://www.alphavantage.co/query"
    params = {"function": function, "symbol": symbol, "interval": "daily", "time_period": PERIOD, "apikey": api_key}
    resp = requests.get(url, params=params, timeout=30)
    resp.raise_for_status()
    series = resp.json().get(f"Technical Analysis: {function}", {})
    return {d: float(r[function]) for d, r in series.items()}


def diff(label: str, symbol: str, local: dict[str, float], av: dict[str, float], skip_first: int) -> tuple[int, int, float]:
    common = sorted(set(local) & set(av))
    if len(common) <= skip_first:
        print(f"  {label} {symbol}: only {len(common)} overlapping dates; need at least {skip_first + 1}")
        return 0, 0, 0.0
    common = common[skip_first:]
    fails = 0
    max_pct = 0.0
    worst_date = ""
    for d in common:
        a, b = local[d], av[d]
        denom = max(abs(a), abs(b))
        if denom == 0:
            continue
        pct = abs(a - b) / denom * 100.0
        if pct > max_pct:
            max_pct, worst_date = pct, d
        if pct > TOLERANCE_PCT:
            fails += 1
    print(f"  {symbol} {label}: {len(common)} dates, {fails} fails, max diff {max_pct:.4f}% (on {worst_date})")
    return len(common), fails, max_pct


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--eodhd-key", required=True)
    parser.add_argument("--av-key", required=True)
    parser.add_argument("--symbols", nargs="*", default=["AAPL", "MSFT", "NVDA", "TSLA", "NFLX", "AVGO"])
    parser.add_argument("--days", type=int, default=400)
    args = parser.parse_args()

    end = date.today()
    start = end - timedelta(days=args.days)
    print(f"Local ATR/ADX vs AV from {start} to {end}, period={PERIOD}, tolerance={TOLERANCE_PCT}%, skip first {WARMUP_SKIP_BARS} bars\n")

    overall_fails = 0
    for symbol in args.symbols:
        try:
            bars = fetch_eodhd_bars(symbol, args.eodhd_key, start, end)
            if len(bars) < WARMUP_SKIP_BARS + PERIOD:
                print(f"  {symbol}: only {len(bars)} bars, need {WARMUP_SKIP_BARS + PERIOD}+")
                continue
            local_atr = compute_atr(bars)
            local_adx = compute_adx(bars)
            av_atr = fetch_av_indicator(symbol, "ATR", args.av_key)
            av_adx = fetch_av_indicator(symbol, "ADX", args.av_key)
            _, f, _ = diff("ATR", symbol, local_atr, av_atr, WARMUP_SKIP_BARS)
            overall_fails += f
            _, f, _ = diff("ADX", symbol, local_adx, av_adx, WARMUP_SKIP_BARS)
            overall_fails += f
        except requests.RequestException as e:
            print(f"  {symbol}: HTTP error — {e}")
            overall_fails += 1

    print(f"\nTotal mismatches above {TOLERANCE_PCT}%: {overall_fails}")
    return 0 if overall_fails == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
