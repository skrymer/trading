#!/usr/bin/env python3
"""Unit tests for the G14 trade-list diff (diff_trades.py) and config_equivalence.py.

Run: python3 -m pytest test_diff_trades.py    (or: python3 test_diff_trades.py)
"""
import diff_trades as dt
import config_equivalence as ce


def trade(symbol, entry, exit_dates=None, profit=100.0, exit_reason="exit"):
    """Build a Trade-shaped dict. exit_dates is the `quotes` date sequence; last = exit."""
    quotes = [{"date": d} for d in (exit_dates or [entry])]
    return {
        "stockSymbol": symbol,
        "entryQuote": {"date": entry, "closePrice": 50.0},
        "quotes": quotes,
        "profit": profit,
        "exitReason": exit_reason,
    }


def run(inline, promoted, pnl_tol=dt.PNL_TOL_DEFAULT, scalars=None):
    return dt.diff(inline, promoted, pnl_tol, ("inline", "promoted"), scalars or {})


# --- PASS: identical trade populations ---

def test_identical_lists_pass():
    # Given two identical trade lists
    a = [trade("AAPL", "2020-03-15", ["2020-03-15", "2020-04-01"]),
         trade("MSFT", "2020-03-16", ["2020-03-16", "2020-04-02"])]
    b = [trade("AAPL", "2020-03-15", ["2020-03-15", "2020-04-01"]),
         trade("MSFT", "2020-03-16", ["2020-03-16", "2020-04-02"])]
    # When diffed
    r = run(a, b)
    # Then PASS with Jaccard 1.0 and no divergences
    assert r["outcome"] == "PASS"
    assert r["jaccard"] == 1.0
    assert r["entry_divergence_count"] == 0
    assert r["exit_divergence_count"] == 0
    assert r["pnl_divergence_count"] == 0


def test_order_independent_pass():
    # Given the same trades in different list order
    a = [trade("AAPL", "2020-03-15"), trade("MSFT", "2020-03-16")]
    b = [trade("MSFT", "2020-03-16"), trade("AAPL", "2020-03-15")]
    # When diffed / Then PASS (key-set, not positional)
    assert run(a, b)["outcome"] == "PASS"


# --- DIFFERS: the Idunn population-shift mode ---

def test_extra_entry_promoted_only_differs():
    # Given the promoted config fires one extra entry (a thin-history symbol)
    a = [trade("AAPL", "2020-03-15")]
    b = [trade("AAPL", "2020-03-15"), trade("PENN", "2020-03-20")]
    # When diffed
    r = run(a, b)
    # Then DIFFERS, the extra entry is promoted_only, Jaccard < 1.0
    assert r["outcome"] == "DIFFERS"
    assert r["jaccard"] == 0.5
    assert r["entry_divergence_count"] == 1
    assert r["promoted_only"] == [{"symbol": "PENN", "entry_date": "2020-03-20"}]
    assert r["first_divergent_trade"]["bucket"] == "ENTRY"
    assert r["first_divergent_trade"]["symbol"] == "PENN"
    assert r["trade_count_delta"] == 1


def test_dropped_entry_inline_only_differs():
    # Given the promoted config drops an entry the inline script fired
    a = [trade("AAPL", "2020-03-15"), trade("GME", "2020-03-18")]
    b = [trade("AAPL", "2020-03-15")]
    # When diffed / Then DIFFERS with the dropped entry as inline_only
    r = run(a, b)
    assert r["outcome"] == "DIFFERS"
    assert r["inline_only"] == [{"symbol": "GME", "entry_date": "2020-03-18"}]
    assert r["first_divergent_trade"]["side"] == "inline_only"


# --- DIFFERS: exit and P&L divergence on matched keys ---

def test_exit_date_divergence_differs():
    # Given a matched (entry,symbol) but a different exit date (held longer)
    a = [trade("AAPL", "2020-03-15", ["2020-03-15", "2020-04-01"])]
    b = [trade("AAPL", "2020-03-15", ["2020-03-15", "2020-04-10"])]
    # When diffed / Then DIFFERS via the EXIT bucket, entry-set still matches
    r = run(a, b)
    assert r["outcome"] == "DIFFERS"
    assert r["jaccard"] == 1.0
    assert r["exit_divergence_count"] == 1
    assert r["pnl_divergence_count"] == 0  # exit divergence takes precedence


def test_pnl_within_tolerance_passes():
    # Given matched trades whose profit differs only by float noise
    a = [trade("AAPL", "2020-03-15", profit=100.0)]
    b = [trade("AAPL", "2020-03-15", profit=100.0 + 1e-7)]
    # When diffed / Then PASS (within 1e-3 relative tolerance)
    assert run(a, b)["outcome"] == "PASS"


def test_pnl_beyond_tolerance_differs():
    # Given matched trades whose profit differs materially
    a = [trade("AAPL", "2020-03-15", profit=100.0)]
    b = [trade("AAPL", "2020-03-15", profit=105.0)]
    # When diffed / Then DIFFERS via the PNL bucket
    r = run(a, b)
    assert r["outcome"] == "DIFFERS"
    assert r["pnl_divergence_count"] == 1
    assert r["first_divergent_trade"]["bucket"] == "PNL"


# --- duplicate key anomaly ---

def test_duplicate_key_blocks_pass():
    # Given a duplicate (entry,symbol) key in one list (data anomaly)
    a = [trade("AAPL", "2020-03-15"), trade("AAPL", "2020-03-15")]
    b = [trade("AAPL", "2020-03-15")]
    # When diffed / Then it cannot PASS — the anomaly is surfaced
    r = run(a, b)
    assert r["outcome"] == "DIFFERS"
    assert r["duplicate_keys_inline"]


# --- diagnostic edge delta is reported but never flips the verdict ---

def test_edge_delta_is_diagnostic_only():
    # Given identical trades but differing reported edge scalars
    a = [trade("AAPL", "2020-03-15")]
    b = [trade("AAPL", "2020-03-15")]
    # When diffed with edge scalars
    r = run(a, b, scalars={"inline_edge": 0.48, "promoted_edge": 0.36})
    # Then still PASS (identity holds) and the edge delta is recorded
    assert r["outcome"] == "PASS"
    assert abs(r["aggregate_edge_delta"] - (-0.12)) < 1e-9


# --- config equivalence (ERROR precondition) ---

BASE_CFG = {
    "startDate": "2000-01-01", "endDate": "2025-12-31",
    "stockSymbols": ["AAPL", "MSFT"], "ranker": "composite", "rankerConfig": {},
    "maxPositions": 10, "entryDelayDays": 1,
    # startingCapital lives nested in positionSizing, not at the top level (BacktestRequest DTO).
    "positionSizing": {"startingCapital": 100000, "sizer": {"type": "atrRisk", "riskPercentage": 1.25}},
    "randomSeed": 42,
    "entryStrategy": {"conditions": [{"type": "script", "script": "x"}]},
    "exitStrategy": {"conditions": []},
}


def test_equivalent_configs_differ_only_in_conditions():
    # Given two configs identical except the entry condition representation
    inline = dict(BASE_CFG)
    promoted = dict(BASE_CFG)
    promoted["entryStrategy"] = {"conditions": [{"type": "pullback2of3", "lookbackDays": 9}]}
    # When checked / Then equivalent (diff is meaningful)
    assert ce.check(inline, promoted)["equivalent"]


def test_symbol_order_does_not_break_equivalence():
    # Given the same universe in different order
    inline = dict(BASE_CFG)
    promoted = dict(BASE_CFG, stockSymbols=["MSFT", "AAPL"])
    # When checked / Then still equivalent (order-insensitive)
    assert ce.check(inline, promoted)["equivalent"]


def test_differing_universe_is_error():
    # Given configs with a genuinely different symbol universe
    inline = dict(BASE_CFG)
    promoted = dict(BASE_CFG, stockSymbols=["AAPL", "MSFT", "NVDA"])
    # When checked / Then NOT equivalent — the universe drives the trade population
    r = ce.check(inline, promoted)
    assert not r["equivalent"]
    assert any(m["field"] == "stockSymbols" for m in r["mismatches"])


def test_differing_seed_is_error():
    # Given configs with different random seeds (single-path noise risk)
    inline = dict(BASE_CFG)
    promoted = dict(BASE_CFG, randomSeed=7)
    # When checked / Then NOT equivalent — seed mismatch is an ERROR precondition
    r = ce.check(inline, promoted)
    assert not r["equivalent"]
    assert any(m["field"] == "randomSeed" for m in r["mismatches"])


def test_differing_sizer_is_error():
    # Given configs with different position sizing
    inline = dict(BASE_CFG)
    promoted = dict(BASE_CFG, positionSizing={"sizer": {"type": "percentEquity", "pct": 5}})
    # When checked / Then NOT equivalent
    assert not ce.check(inline, promoted)["equivalent"]


def test_differing_window_is_error():
    # Given configs over different date ranges
    inline = dict(BASE_CFG)
    promoted = dict(BASE_CFG, endDate="2021-06-30")
    # When checked / Then NOT equivalent — the diff window must match
    assert not ce.check(inline, promoted)["equivalent"]


if __name__ == "__main__":
    import sys
    funcs = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    failed = 0
    for f in funcs:
        try:
            f()
            print(f"PASS {f.__name__}")
        except AssertionError as e:
            failed += 1
            print(f"FAIL {f.__name__}: {e}")
    print(f"\n{len(funcs) - failed}/{len(funcs)} passed")
    sys.exit(1 if failed else 0)
