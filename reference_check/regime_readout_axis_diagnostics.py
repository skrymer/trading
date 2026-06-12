#!/usr/bin/env python3
"""Regime read-out v2 calibration diagnostics (ADR 0023, pre-registration revision loop).

Produces the quant's gating diagnostics — read ONCE from the design-safe window (Block A,
2000-01-01 -> 2014-12-31), after which the v2 values are set and frozen:

  1. gapSmoothed percentile table (sets the NEUTRAL band)
  2. gap-sign agreement vs SPY-RSP, 2003-04-30 -> 2014-12-31 (gap-leg measurement validity)
  3. SPY drawdown-from-trailing-252-high distribution + named-episode minima (sets DD_crisis)
  4. gapTrustworthy breach rate by year (confirms the trust-guard diagnosis)
  5. Confirmatory monthly axis summaries for 2017 / 2022-H1 / 2023 (mechanism verification only)
  6. OPERATOR-PROPOSED axis: sector participation — count of the 11 sectors in breadth uptrend
     per day (distribution + per-year mean; spotlight 2003 vs 2017 vs 2023)

Full daily detail is saved to /tmp/regime-axis-diagnostics.json for the quant.

Usage: python3 regime_readout_axis_diagnostics.py --base-url http://localhost:8080/udgaard
"""

import argparse
import json
import statistics
import urllib.request

BLOCK_A = ("2000-01-01", "2014-12-31")
FULL = ("2000-01-01", "2025-12-31")
PERCENTILES = [1, 5, 10, 20, 25, 30, 40, 50, 60, 70, 75, 80, 90, 95, 99]
SECTORS = ["XLB", "XLC", "XLE", "XLF", "XLI", "XLK", "XLP", "XLRE", "XLU", "XLV", "XLY"]
DRAWDOWN_EPISODES = [
    ("2000-09-01", "2001-03-31"), ("2002-06-01", "2002-10-31"), ("2008-09-01", "2009-03-31"),
    ("2011-01-01", "2011-06-30"), ("2011-08-01", "2011-09-30"), ("2015-08-01", "2016-02-29"),
    ("2018-12-01", "2018-12-31"), ("2020-02-20", "2020-04-15"), ("2022-01-01", "2022-06-30"),
]
CONFIRMATORY_SPANS = [("2017-01-01", "2017-12-31"), ("2022-01-01", "2022-06-30"), ("2023-01-01", "2023-12-31")]
LOOKBACK = 20
EMA_PERIOD = 10


def fetch(base_url, path, api_key=None):
    request = urllib.request.Request(f"{base_url}{path}")
    if api_key:
        request.add_header("X-API-Key", api_key)
    with urllib.request.urlopen(request, timeout=900) as response:
        return json.loads(response.read())


def pct_table(values):
    if not values:
        return {}
    s = sorted(values)
    table = {f"p{p}": s[min(len(s) - 1, int(round(p / 100 * (len(s) - 1))))] for p in PERCENTILES}
    table["mean"] = statistics.mean(s)
    table["stdev"] = statistics.stdev(s) if len(s) > 1 else 0.0
    table["n"] = len(s)
    return table


def n_bar_returns(close_by_date, lookback=LOOKBACK):
    dates = sorted(close_by_date)
    return {
        dates[i]: close_by_date[dates[i]] / close_by_date[dates[i - lookback]] - 1.0
        for i in range(lookback, len(dates))
        if close_by_date[dates[i - lookback]] > 0
    }


def ema(values, period=EMA_PERIOD):
    # Reference-series smoother only (diagnostic #2's SPY-RSP gap). Seeds with the first value,
    # unlike the engine's SMA-seeded EMA — never use this to reconstruct the engine's gapSmoothed.
    out, k = [], 2.0 / (period + 1)
    prev = None
    for i, v in enumerate(values):
        prev = v if prev is None else v * k + prev * (1 - k)
        out.append(prev if i >= period - 1 else None)
    return out


def drawdown_from_high(close_by_date, window=252):
    dates = sorted(close_by_date)
    closes = [close_by_date[d] for d in dates]
    dd = {}
    for i in range(len(dates)):
        high = max(closes[max(0, i - window + 1): i + 1])
        dd[dates[i]] = closes[i] / high - 1.0
    return dd


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8080/udgaard")
    parser.add_argument("--api-key", default=None)
    args = parser.parse_args()
    out = {}

    print(f"Loading full-span read-out (with axes) {FULL[0]} -> {FULL[1]} ...")
    series = fetch(args.base_url, f"/api/regime/readout?after={FULL[0]}&before={FULL[1]}", args.api_key)
    by_date = {row["quoteDate"]: row for row in series}
    block_a = [r for r in series if BLOCK_A[0] <= r["quoteDate"] <= BLOCK_A[1]]
    print(f"  {len(series)} days ({len(block_a)} in Block A)\n")

    # --- 1. gapSmoothed percentiles (Block A) ---
    gaps = [r["axes"]["gapSmoothed"] for r in block_a if (r.get("axes") or {}).get("gapSmoothed") is not None]
    out["gapSmoothed_blockA"] = pct_table(gaps)
    print("== 1. gapSmoothed distribution (Block A 2000-2014) ==")
    for k, v in out["gapSmoothed_blockA"].items():
        print(f"  {k:6s} {v: .5f}" if isinstance(v, float) else f"  {k:6s} {v}")

    # --- 2. gap-sign agreement vs SPY-RSP (2003-04-30 -> 2014-12-31) ---
    spy = fetch(args.base_url, "/api/stocks/SPY", args.api_key)
    rsp = fetch(args.base_url, "/api/stocks/RSP", args.api_key)
    spy_closes = {q["date"]: q["closePrice"] for q in spy.get("quotes", [])}
    rsp_closes = {q["date"]: q["closePrice"] for q in rsp.get("quotes", [])}
    spy_r, rsp_r = n_bar_returns(spy_closes), n_bar_returns(rsp_closes)
    overlap = sorted(d for d in spy_r if d in rsp_r and "2003-04-30" <= d <= BLOCK_A[1])
    ref_raw = {d: spy_r[d] - rsp_r[d] for d in overlap}
    ref_smoothed = dict(zip(overlap, ema([ref_raw[d] for d in overlap])))
    agree_raw = agree_smooth = total_raw = total_smooth = 0
    for d in overlap:
        ours = by_date.get(d, {}).get("axes", {}) or {}
        g = ours.get("gapSmoothed")
        if g is None:
            continue
        total_raw += 1
        if (g >= 0) == (ref_raw[d] >= 0):
            agree_raw += 1
        if ref_smoothed.get(d) is not None:
            total_smooth += 1
            if (g >= 0) == (ref_smoothed[d] >= 0):
                agree_smooth += 1
    out["gap_sign_agreement_vs_spy_rsp"] = {
        "days": total_raw,
        "vs_raw_spy_rsp": agree_raw / total_raw if total_raw else None,
        "vs_ema10_spy_rsp": agree_smooth / total_smooth if total_smooth else None,
    }
    print("\n== 2. gap-sign agreement vs SPY-RSP (2003-04-30 -> 2014-12-31) ==")
    print(f"  days compared:        {total_raw}")
    print(f"  vs raw SPY-RSP gap:   {agree_raw / total_raw:.1%}" if total_raw else "  no overlap")
    print(f"  vs EMA10 SPY-RSP gap: {agree_smooth / total_smooth:.1%}" if total_smooth else "")

    # --- 3. SPY drawdown distribution + episode minima ---
    dd = drawdown_from_high(spy_closes)
    dd_block_a = [v for d, v in dd.items() if BLOCK_A[0] <= d <= BLOCK_A[1]]
    out["spy_drawdown_blockA"] = pct_table(dd_block_a)
    out["spy_drawdown_episode_minima"] = {
        f"{s}->{e}": min((v for d, v in dd.items() if s <= d <= e), default=None) for s, e in DRAWDOWN_EPISODES
    }
    print("\n== 3. SPY drawdown-from-252d-high ==")
    print(f"  Block A percentiles: p1 {out['spy_drawdown_blockA']['p1']:.1%}, p5 {out['spy_drawdown_blockA']['p5']:.1%}, "
          f"p10 {out['spy_drawdown_blockA']['p10']:.1%}, p25 {out['spy_drawdown_blockA']['p25']:.1%}, "
          f"p50 {out['spy_drawdown_blockA']['p50']:.1%}")
    print("  episode minima (close-basis):")
    for span, v in out["spy_drawdown_episode_minima"].items():
        print(f"    {span}: {v:.1%}" if v is not None else f"    {span}: no data")

    # --- 4. gapTrustworthy breach rate by year ---
    breaches = {}
    for r in series:
        year = r["quoteDate"][:4]
        axes = r.get("axes") or {}
        total, breached = breaches.get(year, (0, 0))
        breaches[year] = (total + 1, breached + (0 if axes.get("gapTrustworthy") else 1))
    out["gap_trust_breach_by_year"] = {y: b / t for y, (t, b) in sorted(breaches.items())}
    print("\n== 4. gapTrustworthy breach rate by year ==")
    for y, rate in out["gap_trust_breach_by_year"].items():
        print(f"  {y}: {rate:5.1%}")

    # --- 5. Confirmatory monthly axis summaries ---
    print("\n== 5. Confirmatory monthly axis means (2017 / 2022-H1 / 2023) ==")
    out["confirmatory_months"] = {}
    for s, e in CONFIRMATORY_SPANS:
        months = {}
        for r in series:
            d = r["quoteDate"]
            if not (s <= d <= e):
                continue
            axes = r.get("axes") or {}
            m = months.setdefault(d[:7], {"L": [], "gap": [], "vol": [], "D": [], "washout": 0, "dd": []})
            for key, field in (("L", "breadthLevel"), ("gap", "gapSmoothed"), ("vol", "realizedVol"), ("D", "direction")):
                if axes.get(field) is not None:
                    m[key].append(axes[field])
            m["washout"] += 1 if axes.get("washoutActive") else 0
            if d in dd:
                m["dd"].append(dd[d])
        summary = {
            month: {
                "L": round(statistics.mean(v["L"]), 1) if v["L"] else None,
                "gap": round(statistics.mean(v["gap"]), 5) if v["gap"] else None,
                "vol": round(statistics.mean(v["vol"]), 3) if v["vol"] else None,
                "D": round(statistics.mean(v["D"]), 4) if v["D"] else None,
                "washoutDays": v["washout"],
                "minDD": round(min(v["dd"]), 3) if v["dd"] else None,
            }
            for month, v in sorted(months.items())
        }
        out["confirmatory_months"][f"{s}->{e}"] = summary
        print(f"  {s} -> {e}:")
        for month, v in summary.items():
            print(f"    {month}: L {v['L']}, gap {v['gap']}, vol {v['vol']}, D {v['D']}, washoutDays {v['washoutDays']}, minDD {v['minDD']}")

    # --- 6. Operator-proposed axis: sector participation ---
    print("\n== 6. Sector participation (count of 11 sectors in breadth uptrend) ==")
    # Normalized by sectors AVAILABLE that day — XLRE (2015+) and XLC (2018+) would otherwise cap
    # earlier years at 9/10 of 11 and confound any cross-era comparison.
    sector_up_by_date, sector_available_by_date = {}, {}
    for sector in SECTORS:
        rows = fetch(args.base_url, f"/api/breadth/sector-daily/{sector}", args.api_key)
        for row in rows:
            d = row["quoteDate"]
            sector_available_by_date[d] = sector_available_by_date.get(d, 0) + 1
            if row.get("isInUptrend") or row.get("bullPercentage", 0) > row.get("ema10", 0):
                sector_up_by_date[d] = sector_up_by_date.get(d, 0) + 1

    def participation(d):
        available = sector_available_by_date.get(d, 0)
        return sector_up_by_date.get(d, 0) / available if available else 0.0

    counts_block_a = [participation(r["quoteDate"]) for r in block_a]
    out["sector_participation_blockA"] = pct_table(counts_block_a)
    by_year = {}
    for r in series:
        y = r["quoteDate"][:4]
        by_year.setdefault(y, []).append(participation(r["quoteDate"]))
    out["sector_participation_by_year"] = {y: round(statistics.mean(v), 3) for y, v in sorted(by_year.items())}
    print(f"  Block A: mean {out['sector_participation_blockA']['mean']:.2f}, "
          f"p25 {out['sector_participation_blockA']['p25']}, p50 {out['sector_participation_blockA']['p50']}, "
          f"p75 {out['sector_participation_blockA']['p75']}")
    spotlight = {y: out["sector_participation_by_year"].get(y) for y in ("2003", "2008", "2017", "2022", "2023", "2024")}
    print(f"  per-year mean spotlight: {spotlight}")

    with open("/tmp/regime-axis-diagnostics.json", "w") as f:
        json.dump(out, f, indent=2)
    print("\nSaved full detail to /tmp/regime-axis-diagnostics.json")


if __name__ == "__main__":
    main()
