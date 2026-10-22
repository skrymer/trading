#!/usr/bin/env python3
"""Regime read-out v3 trend-efficiency — frozen-parameter OOS stationarity (ADR 0028, acceptance check 4).

Operator-run, once, after the v3 freeze. The efficiency axis has ZERO fitted parameters (Choppiness
N=30 and the 38.2/61.8 bands are rule-fixed, never tuned), so a label-stability OOS test is trivially
inherited from v2 and uninformative. The informative question is the axis's OWN stationarity: does a
fixed Choppiness threshold mean the same thing — demote the same proportion of GRIND, separate
survivors from demoted by the same efficiency gap — in 2015-2025 as in 2000-2014? A regime shift in the
market's typical path-efficiency would betray a fixed threshold that does not transfer forward.

Split at the frozen cutoff 2014-12-31 (bisects no anchor span). For each era report, on the v3 vs v2
series:
  - GRIND-survival-rate  : fraction of v2-GRIND spells the overlay keeps GRIND (vs demotes to CHOP).
  - surviving vs demoted : mean within-spell Choppiness of surviving-GRIND vs demoted-CHOP spells
                           (the efficiency gap the axis is reading).
  - Choppiness median/IQR: the era's path-efficiency distribution.

BINDING auto-check: only the ALREADY-FROZEN stability floors (median published-spell >= 15 trading
days, <= 12 label changes/yr) on the OOS v3 stream. The IS-vs-OOS stationarity reads are REPORTED for
the operator/quant to judge — deliberately NOT auto-gated on a freshly-invented tolerance (that would be
the Aliased-Regime-Sensitivity hazard this whole pre-registration exists to avoid). A generous advisory
sanity band is printed, clearly labelled advisory.

Usage: python3 regime_readout_frozen_oos.py --base-url http://localhost:8080/udgaard [--api-key KEY]
"""

import argparse
import json
import statistics
import urllib.request

CUTOFF = "2014-12-31"  # frozen IS/OOS split — bisects no anchor span
CHOPPINESS_EFFICIENT_BAND = 38.2  # ADR 0028
MIN_MEDIAN_SPELL_DAYS = 15  # already-frozen stability floor (ADR 0023)
MAX_FLIPS_PER_YEAR = 12.0
ADVISORY_SURVIVAL_BAND = 0.25  # advisory only — a generous sanity band, NOT a frozen pass/fail gate


def fetch(base_url, path, api_key):
    request = urllib.request.Request(f"{base_url}{path}")
    if api_key:
        request.add_header("X-API-Key", api_key)
    with urllib.request.urlopen(request, timeout=600) as response:
        return json.loads(response.read())


def published_labels(series):
    return {row["quoteDate"]: row["publishedLabel"] for row in series}


def choppiness_by_date(series):
    return {
        row["quoteDate"]: (row.get("axes") or {}).get("choppinessIndex30")
        for row in series
        if (row.get("axes") or {}).get("choppinessIndex30") is not None
    }


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


def era_stats(v2_spells, v3_labels, choppiness, in_era):
    """Survival rate + surviving/demoted mean Choppiness for the v2-GRIND spells whose START is in the era."""
    spells = [s for s in v2_spells if in_era(s[0])]
    survived = [s for s in spells if not all(v3_labels.get(d) == "CHOP" for d in s)]
    demoted = [s for s in spells if all(v3_labels.get(d) == "CHOP" for d in s)]

    def spell_mean_choppiness(spell_list):
        values = [choppiness[d] for s in spell_list for d in s if d in choppiness]
        return statistics.mean(values) if values else None

    return {
        "spells": len(spells),
        "survival_rate": len(survived) / len(spells) if spells else None,
        "surviving_mean_choppiness": spell_mean_choppiness(survived),
        "demoted_mean_choppiness": spell_mean_choppiness(demoted),
    }


def choppiness_distribution(choppiness, in_era):
    values = sorted(v for d, v in choppiness.items() if in_era(d))
    if not values:
        return None
    median = statistics.median(values)
    q1 = values[len(values) // 4]
    q3 = values[(3 * len(values)) // 4]
    return median, q3 - q1


def stability(series, in_era):
    """Median published-spell length + flips/year on the era's v3 stream (the inherited frozen floors)."""
    ordered = [row for row in sorted(series, key=lambda r: r["quoteDate"]) if in_era(row["quoteDate"])]
    spells, flips_by_year = [], {}
    current_label, length = None, 0
    for row in ordered:
        label = row["publishedLabel"]
        if label == current_label:
            length += 1
        else:
            if current_label is not None and length > 0:
                spells.append(length)
            if current_label is not None:
                year = int(row["quoteDate"][:4])
                flips_by_year[year] = flips_by_year.get(year, 0) + 1
            current_label, length = label, 1
    if length > 0:
        spells.append(length)
    years = len({int(row["quoteDate"][:4]) for row in ordered})
    median_spell = statistics.median(spells) if spells else 0
    flips_per_year = sum(flips_by_year.values()) / years if years else 0.0
    return median_spell, flips_per_year


def fmt(value, pct=False):
    if value is None:
        return "n/a"
    return f"{value:.0%}" if pct else f"{value:.2f}"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8080/udgaard")
    parser.add_argument("--api-key", default=None)
    args = parser.parse_args()

    span = "?after=2000-01-01&before=2025-12-31"
    print("Loading V3 and V2 read-out series 2000-2025 ...")
    v3_series = fetch(args.base_url, f"/api/regime/readout{span}&version=V3", args.api_key)
    v2_series = fetch(args.base_url, f"/api/regime/readout{span}&version=V2", args.api_key)
    print(f"  v3 {len(v3_series)} days, v2 {len(v2_series)} days\n")

    v2_spells = grind_spells(published_labels(v2_series))
    v3_labels = published_labels(v3_series)
    choppiness = choppiness_by_date(v3_series)

    eras = {
        "IS (<= 2014-12-31)": lambda d: d <= CUTOFF,
        "OOS (>= 2015-01-01)": lambda d: d > CUTOFF,
    }

    print("== Efficiency-axis stationarity across the frozen cutoff (report) ==")
    survival = {}
    for name, in_era in eras.items():
        stats = era_stats(v2_spells, v3_labels, choppiness, in_era)
        dist = choppiness_distribution(choppiness, in_era)
        survival[name] = stats["survival_rate"]
        print(f"  {name}")
        print(f"    v2-GRIND spells        : {stats['spells']}")
        print(f"    GRIND-survival-rate    : {fmt(stats['survival_rate'], pct=True)}")
        print(f"    surviving mean Choppiness: {fmt(stats['surviving_mean_choppiness'])}   demoted mean Choppiness: {fmt(stats['demoted_mean_choppiness'])}")
        if dist:
            print(f"    Choppiness median/IQR  : {dist[0]:.2f} / {dist[1]:.2f}")
        # advisory: are survivors genuinely more efficient than the demoted? (sign read, no free param)
        surviving, demoted = stats["surviving_mean_choppiness"], stats["demoted_mean_choppiness"]
        if surviving is not None and demoted is not None:
            ordering = "OK (survivors more efficient)" if surviving < demoted else "INVERTED — review"
            print(f"    efficiency ordering     : {ordering}")

    is_rate, oos_rate = survival.get("IS (<= 2014-12-31)"), survival.get("OOS (>= 2015-01-01)")
    if is_rate is not None and oos_rate is not None:
        gap = abs(oos_rate - is_rate)
        flag = "within" if gap <= ADVISORY_SURVIVAL_BAND else "OUTSIDE — review for non-stationarity"
        print(f"\n  ADVISORY survival-rate divergence: |OOS - IS| = {gap:.0%} ({flag} the {ADVISORY_SURVIVAL_BAND:.0%} advisory band)")

    print("\n== Binding: inherited stability floors on the OOS v3 stream ==")
    median_spell, flips = stability(v3_series, eras["OOS (>= 2015-01-01)"])
    failures = []
    if median_spell < MIN_MEDIAN_SPELL_DAYS:
        failures.append(f"OOS median spell {median_spell} < {MIN_MEDIAN_SPELL_DAYS}")
    if flips > MAX_FLIPS_PER_YEAR:
        failures.append(f"OOS {flips:.1f} flips/yr > {MAX_FLIPS_PER_YEAR}")
    print(f"  OOS median published-spell: {median_spell} trading days (floor {MIN_MEDIAN_SPELL_DAYS})")
    print(f"  OOS label changes per year: {flips:.1f} (cap {MAX_FLIPS_PER_YEAR})")

    print("\n" + "=" * 60)
    if failures:
        print(f"VERDICT: FAIL ({len(failures)} binding stability check(s) failed OOS)")
        for failure in failures:
            print(f"  - {failure}")
        return 1
    print("VERDICT: PASS — the OOS v3 stream holds the frozen stability floors. Read the stationarity")
    print("report above for the efficiency-axis transfer judgment (descriptive, operator-adjudicated).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
