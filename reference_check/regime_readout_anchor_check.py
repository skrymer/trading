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


CHOPPINESS_EFFICIENT_BAND = 38.2  # ADR 0028 — <= efficient, >= 61.8 choppy, between indeterminate
CHOPPINESS_CHOPPY_BAND = 61.8
BACKBONE_LABELS = ("CRISIS", "THRUST", "NARROW")  # v3 leaves these byte-identical to v2 by construction


def grind_spells(label_by_date):
    """Maximal runs of consecutive GRIND days, each a chronological list of dates."""
    spells, current = [], []
    for day in sorted(label_by_date):
        if label_by_date[day] == "GRIND":
            current.append(day)
        elif current:
            spells.append(current)
            current = []
    if current:
        spells.append(current)
    return spells


def choppiness_band(value):
    if value <= CHOPPINESS_EFFICIENT_BAND:
        return f"efficient(<={CHOPPINESS_EFFICIENT_BAND})"
    if value >= CHOPPINESS_CHOPPY_BAND:
        return f"choppy(>={CHOPPINESS_CHOPPY_BAND})"
    return "indeterminate"


def coverage_by_label(series):
    """Per-anchor-span coverage (covered, total) keyed by (label, start, end)."""
    label_by_date = {row["quoteDate"]: row["publishedLabel"] for row in series}
    result = {}
    for label, spans in ANCHORS.items():
        for start, end in spans:
            days = [d for d in label_by_date if start <= d <= end]
            covered = sum(1 for d in days if label_by_date[d] == label)
            result[(label, start, end)] = (covered, len(days))
    return result


def v3_diagnostics(v3_series, v2_series):
    """The v3 trend-efficiency report (ADR 0028). The binding #180 question — did GRIND and CHOP
    each clear 60% WITHOUT regressing the byte-identical CRISIS/THRUST backbone — plus two report-only
    reads: the GRIND-demotion rate (a near-zero rate is a recordable 'axis inert' negative result) and
    the per-spell Choppiness band histogram. The demotion rate and histogram never pass/fail."""
    v2_labels = {row["quoteDate"]: row["publishedLabel"] for row in v2_series}
    v3_labels = {row["quoteDate"]: row["publishedLabel"] for row in v3_series}
    choppiness = {
        row["quoteDate"]: (row.get("axes") or {}).get("choppinessIndex30")
        for row in v3_series
        if (row.get("axes") or {}).get("choppinessIndex30") is not None
    }
    lines, failures = [], []

    # 1. Backbone byte-identical on real data: v3 CRISIS/THRUST/NARROW coverage must equal v2's exactly.
    v2_cov, v3_cov = coverage_by_label(v2_series), coverage_by_label(v3_series)
    regressions = [key for key in v2_cov if key[0] in BACKBONE_LABELS and v2_cov[key] != v3_cov[key]]
    if regressions:
        failures.append(f"backbone regressed on {len(regressions)} span(s) — v3 must leave CRISIS/THRUST/NARROW byte-identical")
    lines.append(f"  backbone byte-identical (CRISIS/THRUST/NARROW): {'OK' if not regressions else 'REGRESSED'}")

    # 2. The binding #180 read: GRIND and CHOP coverage vs v2, did each clear 60%.
    for label in ("GRIND", "CHOP"):
        for start, end in ANCHORS[label]:
            v2c, v2n = v2_cov[(label, start, end)]
            v3c, v3n = v3_cov[(label, start, end)]
            v2pct = v2c / v2n if v2n else 0.0
            v3pct = v3c / v3n if v3n else 0.0
            status = "PASS" if v3pct >= MIN_COVERAGE else "FAIL"
            if status == "FAIL":
                failures.append(f"{label} {start}->{end}: v3 {v3pct:.0%} < {MIN_COVERAGE:.0%}")
            lines.append(f"  [{status}] {label:5s} {start} -> {end}  v2 {v2pct:5.0%} -> v3 {v3pct:5.0%}")

    # 3. Report-only: GRIND-demotion rate.
    v2_spells = grind_spells(v2_labels)
    demoted = [s for s in v2_spells if all(v3_labels.get(d) == "CHOP" for d in s)]
    rate = len(demoted) / len(v2_spells) if v2_spells else 0.0
    lines.append(f"  GRIND-demotion rate (report-only): {len(demoted)}/{len(v2_spells)} spells ({rate:.0%})")
    if v2_spells and not demoted:
        lines.append("    -> axis inert: no v2-GRIND spell was demoted; record as a reasoned negative result")

    # 4. Report-only: Choppiness band histogram over v2-GRIND days, split by v3 outcome.
    for name, keep in (("surviving GRIND", "GRIND"), ("demoted CHOP", "CHOP")):
        days = [d for s in v2_spells for d in s if v3_labels.get(d) == keep and d in choppiness]
        hist = {}
        for d in days:
            band = choppiness_band(choppiness[d])
            hist[band] = hist.get(band, 0) + 1
        lines.append(f"  {name} days by Choppiness band (report-only): {hist or '{}'}")

    return failures, lines


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8080/udgaard")
    parser.add_argument("--api-key", default=None)
    parser.add_argument("--version", default="V2", help="read-out spec: V2 (frozen production) or V3 (trend-efficiency candidate, ADR 0028)")
    args = parser.parse_args()
    version = args.version.upper()

    print(f"Loading {version} read-out series {SPAN_START} -> {SPAN_END} ...")
    series = fetch(args.base_url, f"/api/regime/readout?after={SPAN_START}&before={SPAN_END}&version={version}", args.api_key)
    print(f"  {len(series)} days loaded")
    print("Loading SPY closes for the D-FLAT diagnostic ...")
    spy = fetch(args.base_url, "/api/stocks/SPY", args.api_key)
    spy_closes = {q["date"]: q["closePrice"] for q in spy.get("quotes", [])}
    print(f"  {len(spy_closes)} SPY closes loaded\n")

    all_failures = []
    if version == "V3":
        # The #180 binding read is GRIND/CHOP >= 60% + the byte-identical backbone, NOT the full
        # 19-span tally (CRISIS/THRUST are inherited verbatim from v2, so their v2 misses are not a
        # v3 failure). The full table is printed for context only.
        print("Loading V2 series for the byte-identical + demotion diagnostics ...")
        v2_series = fetch(args.base_url, f"/api/regime/readout?after={SPAN_START}&before={SPAN_END}&version=V2", args.api_key)
        print("\n== 1. v3 trend-efficiency: backbone + GRIND/CHOP coverage + diagnostics ==")
        failures, lines = v3_diagnostics(series, v2_series)
        print("\n".join(lines))
        all_failures += failures
        print("\n-- full anchor coverage (context; CRISIS/THRUST/NARROW inherited from v2) --")
        _, cov_lines = check_anchor_coverage(series)
        print("\n".join(cov_lines))
    else:
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
    scope = "v3 trend-efficiency overlay (GRIND/CHOP + backbone + stability + D-FLAT)" if version == "V3" else "the frozen classifier"
    if all_failures:
        print(f"VERDICT: FAIL ({len(all_failures)} binding check(s) failed) — {scope}")
        for failure in all_failures:
            print(f"  - {failure}")
        if version == "V3":
            print("Per ADR 0028 one-shot/no-sweep: record this as a reasoned negative result; do NOT retry at another N/threshold.")
        else:
            print("The SPEC must be revised and re-frozen before any strategy is scored against it.")
        sys.exit(1)
    print(f"VERDICT: PASS — {scope} reproduces the pre-registered anchors.")
    sys.exit(0)


if __name__ == "__main__":
    main()
