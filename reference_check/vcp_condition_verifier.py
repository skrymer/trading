#!/usr/bin/env python3
"""
Per-trade re-verifier for VCP entry conditions.

For every trade that the engine took in a VCP backtest, independently re-evaluates
the entry conditions on the trade's entry date using Midgaard's raw quote data and
Udgaard's breadth endpoints. Any FAIL means the engine took a trade whose entry
conditions, as independently computed, were NOT all satisfied — a condition-logic
bug (off-by-one, wrong operator, stale reference, etc.).

Conditions verified (all 8 VCP entry conditions):

  1. minimumPrice(10)            close >= 10
  2. uptrend()                   ema5 > ema10 > ema20 AND close > ema50
  3. priceNearDonchianHigh(3)    (donchian - close) / close * 100 <= 3
  4. volumeAboveAverage(1.2, 20) volume >= 1.2 * avg(last 20 vols STRICTLY before entryDate)
  5. volatilityContracted(10, 3.5)
                                 (max(high[-10..0]) - min(low[-10..0])) / atr <= 3.5
                                 Window INCLUDES entry bar (Kotlin uses `date <= currentDate`).
  6. marketUptrend()             breadthPercent > ema10 on entryDate
  7. sectorUptrend()             sector bullPercentage > ema10 on entryDate
  8. aboveBearishOrderBlock(1, 0)
                                 On signal day, close is not INSIDE and not within 2% below
                                 any bearish OB whose triggerDate <= signal day and whose
                                 endDate is null OR >= signal day. With consecutiveDays=1,
                                 the TradingView-style barsSince walkback doesn't bind:
                                 the predicate reduces to "not blocked today". See
                                 AboveBearishOrderBlockCondition.kt:46-68.

Source-of-truth references:
  udgaard/.../strategy/VcpEntryStrategy.kt (the 8-condition composition)
  udgaard/.../strategy/condition/entry/{Minimum,Uptrend,PriceNearDonchianHigh,
    VolumeAboveAverage,VolatilityContracted,MarketUptrend,SectorUptrend}Condition.kt
"""
from __future__ import annotations

import argparse
import sys
from datetime import date
from typing import Any

import requests

VCP_RISK_PERCENTAGE = 1.25
VCP_N_ATR = 2.0

BACKTEST_REQUEST_TEMPLATE = {
    "assetTypes": ["STOCK"],
    "useUnderlyingAssets": False,
    "entryStrategy": {"type": "predefined", "name": "Vcp"},
    "exitStrategy": {"type": "predefined", "name": "VcpExitStrategy"},
    "maxPositions": 15,
    "entryDelayDays": 1,
    "randomSeed": 42,
    "positionSizing": {
        "startingCapital": 10000,
        "sizer": {"type": "atrRisk", "riskPercentage": VCP_RISK_PERCENTAGE, "nAtr": VCP_N_ATR},
        "leverageRatio": 1.0,
    },
}


def post_backtest(udgaard_url: str, start: str, end: str) -> str:
    body = BACKTEST_REQUEST_TEMPLATE | {"startDate": start, "endDate": end}
    r = requests.post(f"{udgaard_url}/api/backtest", json=body, timeout=1800)
    r.raise_for_status()
    return r.json()["backtestId"]


def fetch_trades(udgaard_url: str, backtest_id: str, start: str, end: str) -> list[dict]:
    r = requests.get(
        f"{udgaard_url}/api/backtest/{backtest_id}/trades",
        params={"startDate": start, "endDate": end},
        timeout=60,
    )
    r.raise_for_status()
    return r.json()


def fetch_bulk_quotes(midgaard_url: str, symbols: list[str]) -> dict[str, list[dict]]:
    """Fetch full history for all symbols. Chunked to avoid URL-length limits."""
    out: dict[str, list[dict]] = {}
    chunk_size = 50
    for i in range(0, len(symbols), chunk_size):
        chunk = symbols[i : i + chunk_size]
        r = requests.get(
            f"{midgaard_url}/api/quotes/bulk",
            params=[("symbols", s) for s in chunk],
            timeout=300,
        )
        r.raise_for_status()
        for sym, rows in r.json().items():
            out[sym] = sorted(rows, key=lambda row: row["date"])
    return out


def fetch_market_breadth(udgaard_url: str) -> dict[str, dict]:
    r = requests.get(f"{udgaard_url}/api/breadth/market-daily", timeout=60)
    r.raise_for_status()
    return {row["quoteDate"]: row for row in r.json()}


def fetch_sector_breadth(udgaard_url: str, sector: str) -> dict[str, dict]:
    r = requests.get(f"{udgaard_url}/api/breadth/sector-daily/{sector}", timeout=60)
    r.raise_for_status()
    return {row["quoteDate"]: row for row in r.json()}


def fetch_order_blocks(udgaard_url: str, symbols: list[str]) -> dict[str, list[dict]]:
    """Fetch order blocks per symbol via /api/stocks/{symbol}. No bulk endpoint exists."""
    out: dict[str, list[dict]] = {}
    for sym in symbols:
        r = requests.get(f"{udgaard_url}/api/stocks/{sym}", timeout=60)
        if r.status_code == 404:
            out[sym] = []
            continue
        r.raise_for_status()
        out[sym] = r.json().get("orderBlocks", []) or []
    return out


# ---------- Condition evaluators (mirror Kotlin code exactly) ----------


def check_minimum_price(entry: dict) -> tuple[bool, str]:
    # MinimumPriceCondition.kt:29 — quote.closePrice >= minimumPrice
    passed = entry["close"] >= 10.0
    return passed, f"close={entry['close']:.2f} vs threshold 10.00"


def check_uptrend(entry: dict) -> tuple[bool, str]:
    # UptrendCondition.kt:25-29 — ema5 > ema10 > ema20 AND close > ema50
    passed = (
        entry["ema5"] > entry["ema10"]
        and entry["ema10"] > entry["ema20"]
        and entry["close"] > entry["ema50"]
    )
    return passed, (
        f"ema5={entry['ema5']:.2f} ema10={entry['ema10']:.2f} "
        f"ema20={entry['ema20']:.2f} close={entry['close']:.2f} ema50={entry['ema50']:.2f}"
    )


def check_price_near_donchian_high(entry: dict, max_distance_percent: float = 3.0) -> tuple[bool, str]:
    # PriceNearDonchianHighCondition.kt:28-30
    donchian = entry["donchianUpper5"]
    close = entry["close"]
    if donchian <= 0 or close <= 0:
        return False, f"donchian={donchian} close={close} (non-positive)"
    distance = (donchian - close) / close * 100.0
    passed = distance <= max_distance_percent
    return passed, f"distance={distance:.3f}% vs threshold {max_distance_percent:.1f}%"


def check_volume_above_average(
    rows: list[dict], entry_idx: int, multiplier: float = 1.2, lookback_days: int = 20
) -> tuple[bool, str]:
    # VolumeAboveAverageCondition.kt:36-50 — average of volumes STRICTLY BEFORE entry date,
    # looking back up to `lookbackDays * 1.5` calendar days, taking up to lookback_days most-recent.
    # Historical quotes in the Kotlin code are filtered by calendar-day lookback, which is wider
    # than 20 trading days by design; we mirror that by taking the 20 most-recent rows strictly
    # before entry_idx. (The 1.5x calendar-day widener exists only to survive weekends/holidays;
    # both sides end up with "last 20 trading days".)
    if entry_idx < lookback_days // 2:
        return False, f"only {entry_idx} prior rows, need >= {lookback_days // 2}"
    history = rows[max(0, entry_idx - lookback_days) : entry_idx]
    if len(history) < lookback_days // 2:
        return False, f"only {len(history)} history rows"
    avg_volume = sum(h["volume"] for h in history) / len(history)
    current_volume = rows[entry_idx]["volume"]
    passed = current_volume >= multiplier * avg_volume
    ratio = current_volume / avg_volume if avg_volume > 0 else 0.0
    return passed, f"vol={current_volume} ratio={ratio:.2f}x avg vs threshold {multiplier}x (n_hist={len(history)})"


def check_volatility_contracted(
    rows: list[dict], entry_idx: int, lookback_days: int = 10, max_atr_multiple: float = 3.5
) -> tuple[bool, str]:
    # VolatilityContractedCondition.kt:32-45 — window is `date <= entryDate` most recent N,
    # which INCLUDES the entry bar itself.
    entry = rows[entry_idx]
    atr = entry["atr"]
    if atr is None or atr <= 0:
        return False, f"atr={atr} (non-positive)"
    # `.take(lookbackDays)` after sortedByDescending → most recent N including entry bar
    window = rows[max(0, entry_idx - lookback_days + 1) : entry_idx + 1]
    if len(window) < lookback_days:
        return False, f"only {len(window)} rows in window, need {lookback_days}"
    max_high = max(w["high"] for w in window)
    min_low = min(w["low"] for w in window)
    range_atr = (max_high - min_low) / atr
    passed = range_atr <= max_atr_multiple
    return passed, f"range/atr={range_atr:.3f} vs threshold {max_atr_multiple:.1f}"


def check_market_uptrend(market_breadth: dict[str, dict], entry_date: str) -> tuple[bool, str]:
    row = market_breadth.get(entry_date)
    if row is None:
        return False, f"no market breadth row for {entry_date}"
    # MarketBreadthDaily.isInUptrend() — breadthPercent > ema10
    passed = row["breadthPercent"] > row["ema10"]
    return passed, f"breadth={row['breadthPercent']:.2f}% vs ema10={row['ema10']:.2f}%"


def check_sector_uptrend(sector_breadth: dict[str, dict], entry_date: str) -> tuple[bool, str]:
    row = sector_breadth.get(entry_date)
    if row is None:
        return False, f"no sector breadth row for {entry_date}"
    # SectorBreadthDaily.isInUptrend() — bullPercentage > ema10
    passed = row["bullPercentage"] > row["ema10"]
    return passed, f"bull={row['bullPercentage']:.2f}% vs ema10={row['ema10']:.2f}%"


def check_above_bearish_order_block(
    order_blocks: list[dict],
    signal_date: str,
    signal_close: float,
    age_in_days: int = 0,
    proximity_percent: float = 2.0,
) -> tuple[bool, str]:
    """VCP uses aboveBearishOrderBlock(consecutiveDays=1, ageInDays=0).

    With consecutiveDays=1 the walkback loop never binds (any hit returns count+1 >= 1,
    and MAX_VALUE also >= 1), so the predicate reduces to:
        "on signal day, close is not inside AND not within proximityPercent% below
         any BEARISH OB that was triggered on/before signal day and whose endDate
         is null OR on/after signal day".

    ageInDays is a TRADING-day filter in Kotlin (Stock.countTradingDaysBetween). With
    ageInDays=0 the trading-day distinction doesn't matter — every OB with
    triggerDate <= signal_date qualifies. For ageInDays > 0 a trading-day computation
    would be required (skipped here; VCP uses 0).
    """
    if age_in_days != 0:
        return False, f"age_in_days={age_in_days} not supported by this verifier (VCP uses 0)"

    relevant = [
        ob for ob in order_blocks
        if ob.get("orderBlockType") == "BEARISH"
        and ob.get("triggerDate", "9999-99-99") <= signal_date
        and (ob.get("endDate") is None or ob["endDate"] >= signal_date)
    ]
    if not relevant:
        return True, "no relevant bearish OBs active on signal day"

    for ob in relevant:
        inside = ob["low"] <= signal_close <= ob["high"]
        near = signal_close < ob["low"] and ((ob["low"] - signal_close) / signal_close * 100.0) <= proximity_percent
        if inside or near:
            status = "inside" if inside else "near"
            return False, (
                f"close={signal_close:.2f} is {status} bearish OB "
                f"[{ob['low']:.2f}-{ob['high']:.2f}] (triggerDate={ob.get('triggerDate')})"
            )

    return True, f"{len(relevant)} relevant OB(s), close={signal_close:.2f} clear of all"


# ---------- Main verification loop ----------


def verify_trade(
    trade: dict,
    quotes_by_symbol: dict[str, list[dict]],
    market_breadth: dict[str, dict],
    sector_breadth_cache: dict[str, dict[str, dict]],
    order_blocks_by_symbol: dict[str, list[dict]],
    entry_delay_days: int,
) -> dict[str, tuple[bool, str]]:
    """Returns condition_name -> (passed, detail) for one trade.

    The engine evaluates entry conditions on the SIGNAL day, then (if entryDelayDays > 0)
    takes the position on a later trading day. The trade's `entryQuote` refers to the
    post-delay quote, not the signal-day quote — so this verifier must walk back
    `entry_delay_days` trading days from `entryQuote.date` to find the day on which
    the engine actually evaluated the conditions.

    Reference: udgaard/.../service/BacktestService.kt:754-762 — strategyQuote at signal
    date drives the test; tradingEntryQuote is remapped forward by entryDelayDays.
    """
    cond_names = (
        "minimumPrice",
        "uptrend",
        "priceNearDonchianHigh",
        "volumeAboveAverage",
        "volatilityContracted",
        "marketUptrend",
        "sectorUptrend",
        "aboveBearishOrderBlock",
    )
    results: dict[str, tuple[bool, str]] = {}
    symbol = trade["stockSymbol"]
    entry_date = trade["entryQuote"]["date"]
    sector = trade.get("sector") or ""

    sym_rows = quotes_by_symbol.get(symbol)
    if sym_rows is None:
        return {c: (False, f"no Midgaard quotes for {symbol}") for c in cond_names}

    # Find entry row, then walk back entry_delay_days TRADING days to reach the signal row.
    entry_idx = next((i for i, r in enumerate(sym_rows) if r["date"] == entry_date), None)
    if entry_idx is None:
        return {c: (False, f"entry date {entry_date} not found in {symbol} quotes") for c in cond_names}

    signal_idx = entry_idx - entry_delay_days
    if signal_idx < 0:
        return {c: (False, f"cannot walk back {entry_delay_days} trading days from idx {entry_idx}") for c in cond_names}

    signal = sym_rows[signal_idx]
    signal_date = signal["date"]

    results["minimumPrice"] = check_minimum_price(signal)
    results["uptrend"] = check_uptrend(signal)
    results["priceNearDonchianHigh"] = check_price_near_donchian_high(signal)
    results["volumeAboveAverage"] = check_volume_above_average(sym_rows, signal_idx)
    results["volatilityContracted"] = check_volatility_contracted(sym_rows, signal_idx)
    results["marketUptrend"] = check_market_uptrend(market_breadth, signal_date)

    sector_breadth = sector_breadth_cache.get(sector)
    if sector_breadth is None:
        results["sectorUptrend"] = (False, f"no sector breadth cached for '{sector}'")
    else:
        results["sectorUptrend"] = check_sector_uptrend(sector_breadth, signal_date)

    obs = order_blocks_by_symbol.get(symbol, [])
    results["aboveBearishOrderBlock"] = check_above_bearish_order_block(
        obs, signal_date, signal["close"]
    )

    return results


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--udgaard-url", default="http://localhost:8080/udgaard")
    parser.add_argument("--midgaard-url", default="http://localhost:8081")
    parser.add_argument("--start-date", default="2023-01-01")
    parser.add_argument("--end-date", default="2023-12-31")
    parser.add_argument("--backtest-id", default=None, help="Reuse an existing backtest (skip fresh run).")
    parser.add_argument("--entry-delay-days", type=int, default=1, help="Must match the backtest config's entryDelayDays. Default 1 (VCP).")
    parser.add_argument("--verbose", action="store_true", help="Print details for every FAIL, not just summary.")
    parser.add_argument("--max-verbose-fails", type=int, default=20)
    args = parser.parse_args()

    print(f"Udgaard: {args.udgaard_url}")
    print(f"Midgaard: {args.midgaard_url}")

    if args.backtest_id:
        backtest_id = args.backtest_id
        print(f"Reusing backtest {backtest_id}.")
    else:
        print(f"Running VCP backtest [{args.start_date}..{args.end_date}] — this can take several minutes...")
        backtest_id = post_backtest(args.udgaard_url, args.start_date, args.end_date)
        print(f"Backtest completed: {backtest_id}")

    trades = fetch_trades(args.udgaard_url, backtest_id, args.start_date, args.end_date)
    print(f"Fetched {len(trades)} trades.")

    if not trades:
        print("No trades to verify — exiting with success (nothing to check).")
        return 0

    # Bulk-fetch all quote data we'll need.
    unique_symbols = sorted({t["stockSymbol"] for t in trades})
    unique_sectors = sorted({t.get("sector") or "" for t in trades if t.get("sector")})
    print(f"Unique symbols: {len(unique_symbols)}; unique sectors: {len(unique_sectors)}")
    quotes_by_symbol = fetch_bulk_quotes(args.midgaard_url, unique_symbols)
    # IMPORTANT: the engine loads stock.quotes with `quotesAfter = startDate`, so rolling
    # lookbacks (volume average, volatility window) don't see any pre-startDate history.
    # Clip the Python-side data the same way so our re-evaluation matches the engine's
    # actual inputs. See StockJooqRepository.findBySymbols(..., quotesAfter=) (line 160)
    # and BacktestService.backtest(after = startDate) (line 267, 284, 472).
    quotes_by_symbol = {
        sym: [r for r in rows if r["date"] >= args.start_date]
        for sym, rows in quotes_by_symbol.items()
    }
    print(f"Fetched quotes for {len(quotes_by_symbol)} symbols (clipped to >= {args.start_date}).")

    market_breadth = fetch_market_breadth(args.udgaard_url)
    print(f"Fetched market breadth: {len(market_breadth)} rows.")

    sector_breadth_cache: dict[str, dict[str, dict]] = {}
    for s in unique_sectors:
        sector_breadth_cache[s] = fetch_sector_breadth(args.udgaard_url, s)
    print(f"Fetched sector breadth for {len(sector_breadth_cache)} sectors.")

    order_blocks_by_symbol = fetch_order_blocks(args.udgaard_url, unique_symbols)
    print(f"Fetched order blocks for {len(order_blocks_by_symbol)} symbols.")

    # Verify all trades.
    condition_names = [
        "minimumPrice",
        "uptrend",
        "priceNearDonchianHigh",
        "volumeAboveAverage",
        "volatilityContracted",
        "marketUptrend",
        "sectorUptrend",
        "aboveBearishOrderBlock",
    ]
    fail_counts: dict[str, int] = {c: 0 for c in condition_names}
    fail_examples: dict[str, list[str]] = {c: [] for c in condition_names}

    for trade in trades:
        per_trade = verify_trade(
            trade,
            quotes_by_symbol,
            market_breadth,
            sector_breadth_cache,
            order_blocks_by_symbol,
            args.entry_delay_days,
        )
        for c, (passed, detail) in per_trade.items():
            if not passed:
                fail_counts[c] += 1
                if len(fail_examples[c]) < args.max_verbose_fails:
                    fail_examples[c].append(
                        f"{trade['stockSymbol']} {trade['entryQuote']['date']}: {detail}"
                    )

    print()
    print(f"{'condition':<28} {'fails':>7} {'total':>7} {'pass %':>8}")
    print("-" * 52)
    total = len(trades)
    any_fail = False
    for c in condition_names:
        fc = fail_counts[c]
        pct = (total - fc) / total * 100.0 if total else 100.0
        print(f"{c:<28} {fc:>7} {total:>7} {pct:>7.2f}%")
        if fc > 0:
            any_fail = True

    if any_fail and args.verbose:
        print()
        print("=== Failure examples ===")
        for c in condition_names:
            if fail_examples[c]:
                print(f"\n-- {c} --")
                for ex in fail_examples[c]:
                    print(f"  {ex}")

    print()
    if any_fail:
        print(
            "RESULT: FAILED — at least one trade's entry conditions, as independently "
            "re-evaluated, were NOT all satisfied on the entry date. Re-run with --verbose "
            "to inspect the first failures."
        )
        return 1

    print(f"RESULT: PASSED — all {total} trades satisfy all 8 verified VCP entry conditions on their signal date.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
