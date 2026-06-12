#!/usr/bin/env python3
"""Regime read-out validation-anchor acceptance check (ADR 0023, pre-registration §5).

Operator-run, once, after the first real compute — NOT a CI test. Verifies the frozen classifier
against the pre-registered, market-consensus anchor spans:

  1. Expected-label coverage >= 60% of days in each anchor span (published labels).
  2. Stability: median published-spell length >= 15 trading days; <= 12 label changes / year.
  3. CHOP D-FLAT diagnostic: >= 70% of CHOP days have |SPY 20-bar return| < 2%.

If any check fails, the SPEC is revised and re-frozen BEFORE any strategy is ever scored against
it (legal: no strategy has seen it). The anchor dates below are frozen from the signed
pre-registration (knowledge/wiki/concepts/regime-read-out.md) — never edit them to make a run pass.

Usage:
  python3 regime_readout_anchor_check.py --base-url http://localhost:8080/udgaard [--api-key KEY]
"""

import argparse
import json
import statistics
import sys
import urllib.request
from datetime import date

SPAN_START = "2000-01-01"
SPAN_END = "2025-12-31"

# Pre-registered anchor spans (label, start, end) — market-structure consensus, frozen at sign-off.
ANCHORS = {
    "CRISIS": [
        ("2000-09-01", "2001-03-31"),
        ("2002-06-01", "2002-10-31"),
        ("2008-09-01", "2009-03-31"),
        ("2011-08-01", "2011-08-31"),
        ("2018-12-01", "2018-12-31"),
        ("2020-02-20", "2020-04-15"),
        # Span amended at v2 (quant-adjudicated ground-truth fix, the sole anchor amendment): the
        # consensus bear ran from the -20% close on 2022-06-13 to the 2022-10-12 cycle low;
        # Jan-Apr 2022 was a -10..-14% correction, not a bear. Never amend a span for coverage.
        ("2022-06-01", "2022-10-31"),
    ],
    "THRUST": [
        ("2003-04-01", "2003-12-31"),
        ("2009-04-01", "2009-09-30"),
        ("2020-04-01", "2020-08-31"),
    ],
    "GRIND": [
        ("2013-01-01", "2013-12-31"),
        ("2017-01-01", "2017-12-31"),
        ("2019-01-01", "2019-06-30"),
    ],
    "NARROW": [
        ("2021-07-01", "2021-12-31"),
        ("2023-01-01", "2023-12-31"),
        ("2024-01-01", "2024-12-31"),
    ],
    "CHOP": [
        ("2011-01-01", "2011-06-30"),
        ("2015-01-01", "2015-06-30"),
        ("2015-07-01", "2016-03-31"),
    ],
}

MIN_COVERAGE = 0.60
MIN_MEDIAN_SPELL_DAYS = 15
MAX_FLIPS_PER_YEAR = 12.0
MIN_CHOP_D_FLAT_FRACTION = 0.70
DIRECTION_DEAD_BAND = 0.02  # frozen A4 band
DIRECTION_LOOKBACK_BARS = 20  # frozen shared horizon


def fetch(base_url, path, api_key):
    request = urllib.request.Request(f"{base_url}{path}")
    if api_key:
        request.add_header("X-API-Key", api_key)
    with urllib.request.urlopen(request, timeout=600) as response:
        return json.loads(response.read())


def check_anchor_coverage(series):
    label_by_date = {row["quoteDate"]: row["publishedLabel"] for row in series}
    failures, lines = [], []
    for label, spans in ANCHORS.items():
        for start, end in spans:
            days = [d for d in label_by_date if start <= d <= end]
            if not days:
                failures.append(f"{label} {start}->{end}: NO DATA")
                continue
            covered = sum(1 for d in days if label_by_date[d] == label)
            coverage = covered / len(days)
            status = "PASS" if coverage >= MIN_COVERAGE else "FAIL"
            if status == "FAIL":
                failures.append(f"{label} {start}->{end}: {coverage:.0%}")
            lines.append(f"  [{status}] {label:7s} {start} -> {end}  coverage {coverage:5.0%}  ({covered}/{len(days)} days)")
    return failures, lines


def check_stability(series):
    ordered = sorted(series, key=lambda row: row["quoteDate"])
    spells, flips_by_year = [], {}
    current_label, current_length = None, 0
    for row in ordered:
        label = row["publishedLabel"]
        if label == current_label:
            current_length += 1
        else:
            if current_label is not None and current_length > 0:
                spells.append(current_length)
            if current_label is not None:
                year = int(row["quoteDate"][:4])
                flips_by_year[year] = flips_by_year.get(year, 0) + 1
            current_label, current_length = label, 1
    if current_length > 0:
        spells.append(current_length)

    median_spell = statistics.median(spells) if spells else 0
    years = len({int(row["quoteDate"][:4]) for row in ordered})
    flips_per_year = sum(flips_by_year.values()) / years if years else 0.0

    failures = []
    if median_spell < MIN_MEDIAN_SPELL_DAYS:
        failures.append(f"median spell {median_spell} < {MIN_MEDIAN_SPELL_DAYS} trading days")
    if flips_per_year > MAX_FLIPS_PER_YEAR:
        failures.append(f"{flips_per_year:.1f} flips/year > {MAX_FLIPS_PER_YEAR}")
    lines = [
        f"  median published-spell length: {median_spell} trading days (floor {MIN_MEDIAN_SPELL_DAYS})",
        f"  label changes per year:        {flips_per_year:.1f} (cap {MAX_FLIPS_PER_YEAR})",
        f"  spell count:                   {len(spells)}",
    ]
    return failures, lines


def check_chop_d_flat(series, spy_closes_by_date):
    sorted_dates = sorted(spy_closes_by_date)
    returns_20 = {}
    for index in range(DIRECTION_LOOKBACK_BARS, len(sorted_dates)):
        prior = spy_closes_by_date[sorted_dates[index - DIRECTION_LOOKBACK_BARS]]
        if prior > 0:
            returns_20[sorted_dates[index]] = spy_closes_by_date[sorted_dates[index]] / prior - 1.0

    chop_days = [row["quoteDate"] for row in series if row["publishedLabel"] == "CHOP"]
    measurable = [d for d in chop_days if d in returns_20]
    if not measurable:
        return ["no measurable CHOP days"], ["  no measurable CHOP days"]
    flat = sum(1 for d in measurable if abs(returns_20[d]) < DIRECTION_DEAD_BAND)
    fraction = flat / len(measurable)
    failures = [] if fraction >= MIN_CHOP_D_FLAT_FRACTION else [
        f"CHOP D-FLAT {fraction:.0%} < {MIN_CHOP_D_FLAT_FRACTION:.0%} — the residual is absorbing directional days; revise the SPEC"
    ]
    lines = [f"  CHOP days direction-FLAT: {fraction:.0%} ({flat}/{len(measurable)}; floor {MIN_CHOP_D_FLAT_FRACTION:.0%})"]
    return failures, lines


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8080/udgaard")
    parser.add_argument("--api-key", default=None)
    args = parser.parse_args()

    print(f"Loading read-out series {SPAN_START} -> {SPAN_END} ...")
    series = fetch(args.base_url, f"/api/regime/readout?after={SPAN_START}&before={SPAN_END}", args.api_key)
    print(f"  {len(series)} days loaded")
    print("Loading SPY closes for the D-FLAT diagnostic ...")
    spy = fetch(args.base_url, "/api/stocks/SPY", args.api_key)
    spy_closes = {q["date"]: q["closePrice"] for q in spy.get("quotes", [])}
    print(f"  {len(spy_closes)} SPY closes loaded\n")

    all_failures = []
    print("== 1. Anchor coverage (>= 60% per span) ==")
    failures, lines = check_anchor_coverage(series)
    print("\n".join(lines))
    all_failures += failures

    print("\n== 2. Stability targets ==")
    failures, lines = check_stability(series)
    print("\n".join(lines))
    all_failures += failures

    print("\n== 3. CHOP D-FLAT diagnostic ==")
    failures, lines = check_chop_d_flat(series, spy_closes)
    print("\n".join(lines))
    all_failures += failures

    print("\n" + "=" * 60)
    if all_failures:
        print(f"VERDICT: FAIL ({len(all_failures)} check(s) failed)")
        for failure in all_failures:
            print(f"  - {failure}")
        print("The SPEC must be revised and re-frozen before any strategy is scored against it.")
        sys.exit(1)
    print("VERDICT: PASS — the frozen classifier reproduces the pre-registered anchors.")
    sys.exit(0)


if __name__ == "__main__":
    main()
