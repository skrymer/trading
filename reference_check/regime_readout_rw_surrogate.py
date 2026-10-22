#!/usr/bin/env python3
"""Regime read-out v3 trend-efficiency — random-walk surrogate test (ADR 0028, acceptance check 3).

Operator-run, once, after the v3 pre-registration freeze. Answers: does the Choppiness axis carry
information a calibrated random walk does NOT? The literature flags that CHOP may be indistinguishable
from a random walk, so before the GRIND/CHOP separation is called real it must beat an RW surrogate.

  Null      : stationary (Politis-Romano) block bootstrap of SPY daily log returns, mean block length
              L=5 (one week << the 30-day Choppiness window — preserves the return/intraday-range
              marginals and up-to-weekly autocorrelation, DESTROYS the multi-week trend the axis claims).
              Each bar's (close log-return, log(high/close), log(low/close)) tuple is resampled together,
              so synthetic intraday ranges keep their real shape. GBM is deliberately NOT used (i.i.d.
              Gaussian has no autocorrelation -> the axis beats it trivially and meaninglessly).
  Statistic : delta = mean Choppiness(30) over the CHOP-anchor dates - mean over the GRIND-anchor dates,
              on the real adjusted SPY series and on each surrogate, over the IDENTICAL anchor calendars.
  Decision  : real delta > surrogate p95, one-sided -> p = (1 + #{delta_surrogate >= delta_real}) / (K+1) < 0.05.

This is a descriptive evidence test for the trader's report; it does NOT promote any label to gateable.

Usage: python3 regime_readout_rw_surrogate.py --base-url http://localhost:8080/udgaard [--api-key KEY]
"""

import argparse
import json
import math
import random
import statistics
import urllib.request

# Frozen pre-registration constants (ADR 0028) — never tuned to a run.
WINDOW = 30
MEAN_BLOCK = 5
NUM_SURROGATES = 1000
SEED = 20260616  # the freeze date, recorded so the run is reproducible

# Frozen anchor spans (must match regime_readout_anchor_check.py — never edited to make a run pass).
GRIND_SPANS = [("2013-01-01", "2013-12-31"), ("2017-01-01", "2017-12-31"), ("2019-01-01", "2019-06-30")]
CHOP_SPANS = [("2011-01-01", "2011-06-30"), ("2015-01-01", "2015-06-30"), ("2015-07-01", "2016-03-31")]


def fetch(base_url, path, api_key):
    request = urllib.request.Request(f"{base_url}{path}")
    if api_key:
        request.add_header("X-API-Key", api_key)
    with urllib.request.urlopen(request, timeout=600) as response:
        return json.loads(response.read())


def load_spy_bars(base_url, api_key):
    """Real SPY OHLC, date-ascending: list of (date, high, low, close) on the adjusted basis."""
    spy = fetch(base_url, "/api/stocks/SPY", api_key)
    bars = [
        (q["date"], q["high"], q["low"], q["closePrice"])
        for q in spy.get("quotes", [])
        if q.get("high", 0) > 0 and q.get("low", 0) > 0 and q.get("closePrice", 0) > 0
    ]
    bars.sort(key=lambda b: b[0])
    return bars


def anchor_positions(bars, spans):
    """Indices into [bars] whose date falls in any of the given spans."""
    index_by_date = {bar[0]: i for i, bar in enumerate(bars)}
    positions = []
    for start, end in spans:
        positions += [index_by_date[d] for d in index_by_date if start <= d <= end]
    return sorted(positions)


def choppiness_at(high, low, close, position):
    """The frozen Choppiness Index (ADR 0028) at one position, or None if undefined / bad print."""
    if position < WINDOW:
        return None
    true_range_sum = 0.0
    max_high, min_low = float("-inf"), float("inf")
    for j in range(position - WINDOW + 1, position + 1):
        bar_high, bar_low = high[j], low[j]
        if bar_high <= 0 or bar_low <= 0:
            return None
        prior_close = close[j - 1]
        true_range_sum += max(bar_high - bar_low, abs(bar_high - prior_close), abs(bar_low - prior_close))
        max_high, min_low = max(max_high, bar_high), min(min_low, bar_low)
    span = max_high - min_low
    if span <= 0:
        return None
    return 100.0 * math.log10(true_range_sum / span) / math.log10(WINDOW)


def delta_statistic(high, low, close, grind_positions, chop_positions):
    """mean Choppiness over CHOP anchors - mean over GRIND anchors (the axis-power statistic)."""
    grind = [c for p in grind_positions if (c := choppiness_at(high, low, close, p)) is not None]
    chop = [c for p in chop_positions if (c := choppiness_at(high, low, close, p)) is not None]
    if not grind or not chop:
        raise ValueError("an anchor set has no defined Choppiness — check the SPY history depth")
    return statistics.mean(chop) - statistics.mean(grind)


def stationary_bootstrap_indices(length, mean_block, rng):
    """Politis-Romano stationary bootstrap: geometric(1/mean_block) block lengths, uniform random
    starts, wrap-around, until [length] indices are drawn from range(length)."""
    p = 1.0 / mean_block
    indices = []
    while len(indices) < length:
        start = rng.randrange(length)
        offset = 0
        while True:
            indices.append((start + offset) % length)
            if len(indices) >= length:
                break
            offset += 1
            if rng.random() < p:
                break
    return indices[:length]


def make_surrogate(bars, rng):
    """A synthetic SPY OHLC path: block-bootstrap each bar's (log-return, high-ratio, low-ratio) tuple
    and rebuild prices. Index-aligned to the real series, so the anchor positions are identical."""
    n = len(bars)
    # tuples[i-1] carries bar i's (close log-return vs i-1, log(high/close), log(low/close)).
    tuples = []
    for i in range(1, n):
        _, high, low, close = bars[i]
        prior_close = bars[i - 1][3]
        tuples.append((math.log(close / prior_close), math.log(high / close), math.log(low / close)))
    boot = stationary_bootstrap_indices(n - 1, MEAN_BLOCK, rng)
    synth_high, synth_low, synth_close = [0.0] * n, [0.0] * n, [0.0] * n
    synth_close[0], synth_high[0], synth_low[0] = bars[0][3], bars[0][1], bars[0][2]
    for t in range(1, n):
        log_return, high_ratio, low_ratio = tuples[boot[t - 1]]
        synth_close[t] = synth_close[t - 1] * math.exp(log_return)
        synth_high[t] = synth_close[t] * math.exp(high_ratio)
        synth_low[t] = synth_close[t] * math.exp(low_ratio)
    return synth_high, synth_low, synth_close


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8080/udgaard")
    parser.add_argument("--api-key", default=None)
    args = parser.parse_args()

    print("Loading real SPY OHLC ...")
    bars = load_spy_bars(args.base_url, args.api_key)
    print(f"  {len(bars)} bars ({bars[0][0]} -> {bars[-1][0]})")
    grind_positions = anchor_positions(bars, GRIND_SPANS)
    chop_positions = anchor_positions(bars, CHOP_SPANS)
    print(f"  {len(grind_positions)} GRIND-anchor dates, {len(chop_positions)} CHOP-anchor dates")

    real_high = [b[1] for b in bars]
    real_low = [b[2] for b in bars]
    real_close = [b[3] for b in bars]
    delta_real = delta_statistic(real_high, real_low, real_close, grind_positions, chop_positions)
    print(f"\n  real delta (mean CHOP-anchor Choppiness - mean GRIND-anchor Choppiness): {delta_real:.3f}")

    print(f"\nDrawing {NUM_SURROGATES} stationary-block-bootstrap surrogates (L={MEAN_BLOCK}, seed={SEED}) ...")
    rng = random.Random(SEED)
    surrogate_deltas = []
    at_least_as_extreme = 0
    for k in range(NUM_SURROGATES):
        high, low, close = make_surrogate(bars, rng)
        delta_k = delta_statistic(high, low, close, grind_positions, chop_positions)
        surrogate_deltas.append(delta_k)
        if delta_k >= delta_real:
            at_least_as_extreme += 1
        if (k + 1) % 200 == 0:
            print(f"  {k + 1}/{NUM_SURROGATES} ...")

    surrogate_deltas.sort()
    p95 = surrogate_deltas[int(0.95 * (NUM_SURROGATES - 1))]
    p_value = (1 + at_least_as_extreme) / (NUM_SURROGATES + 1)

    print("\n" + "=" * 60)
    print(f"  surrogate delta mean   : {statistics.mean(surrogate_deltas):.3f}")
    print(f"  surrogate delta p95    : {p95:.3f}")
    print(f"  real delta             : {delta_real:.3f}")
    print(f"  one-sided p-value      : {p_value:.4f}  (= (1 + #surrogate >= real) / (K+1))")
    if p_value < 0.05:
        print("VERDICT: PASS — the Choppiness axis separates GRIND from CHOP beyond a calibrated random walk.")
        return 0
    print("VERDICT: FAIL — the GRIND/CHOP separation is within random-walk noise; record as a negative result.")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
