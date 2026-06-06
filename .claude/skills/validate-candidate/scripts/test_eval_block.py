#!/usr/bin/env python3
"""Tests for eval-block.py — focused on the Block-B G6a/G6b regime-mandate split (issue #51).

G6a (2020-H1 crash survival): trades entered Jan-Apr 2020 OOS edge >= -0.5%
G6b (2020-H2 recovery):       trades entered May-Dec 2020 OOS edge > 0

Both are recomputed from the per-window `outOfSampleStatsByEntryMonth` monthly buckets
(ADR-0006). Other blocks keep the single G6.

Run: python3 -m pytest test_eval_block.py  (or `python3 test_eval_block.py`)
"""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).parent / "eval-block.py"


def month_bucket(trades, winners, sum_win_pct, sum_loss_pct):
    """A TradeStatsSummary as eval-block consumes it — additive fields only."""
    return {
        "trades": trades,
        "winners": winners,
        "sumWinPercent": sum_win_pct,
        "sumLossPercent": sum_loss_pct,
        "grossWinProfit": sum_win_pct,
        "grossLossProfit": sum_loss_pct,
    }


def window(oos_start, edge=1.0, trades=40, dd=5.0, cagr=35.0, buckets=None):
    return {
        "outOfSampleStart": oos_start,
        "outOfSampleEnd": oos_start[:4] + "-12-31",
        "outOfSampleEdge": edge,
        "outOfSampleTrades": trades,
        "outOfSampleMaxDrawdownPct": dd,
        "outOfSampleCagr": cagr,
        "outOfSampleStatsByEntryMonth": buckets or {},
    }


def run_block(windows, block, **agg):
    data = {
        "windows": windows,
        "aggregateOosEdge": agg.get("edge", 1.0),
        "aggregateOosTrades": agg.get("trades", sum(w["outOfSampleTrades"] for w in windows)),
        "aggregateOosCagr": agg.get("cagr", 35.0),
        "aggregateOosMaxDrawdownPct": agg.get("dd", 10.0),
        "aggregateOosRiskMetrics": {
            "sharpeRatio": agg.get("sharpe", 1.2),
            "calmarRatio": agg.get("calmar", 2.0),
        },
    }
    if "spy" in agg:
        data["spyBaselineComparison"] = {
            "verdict": agg["spy"],
            "strategyCalmar": 2.0,
            "benchmarkCalmar": 1.0,
            "benchmarkCagr": 8.0,
            "benchmarkMaxDrawdownPct": 8.0,
            "benchmarkSharpe": 0.9,
            "inconclusiveReason": agg.get("spy_reason"),
        }
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as f:
        json.dump(data, f)
        path = f.name
    result = subprocess.run(
        [sys.executable, str(SCRIPT), path, "--block", block],
        capture_output=True, text=True,
    )
    return json.loads(result.stdout)


def gate_named(summary, name):
    return next((g for g in summary["gates"] if g["name"] == name), None)


class TestG6aCrashSurvival(unittest.TestCase):
    def test_g6a_fails_when_jan_apr_2020_edge_below_minus_half_percent(self):
        # Given a 2020 OOS window whose Jan-Apr trades aggregate to edge = -1.0%
        # (winners=4 avg +5%, losers=6 avg -5% -> 0.4*5 - 0.6*5 = -1.0)
        buckets = {"2020-03": month_bucket(trades=10, winners=4, sum_win_pct=20.0, sum_loss_pct=-30.0)}
        summary = run_block([window("2020-01-01", buckets=buckets)], "B")

        # When Block B is evaluated, G6a exists and fails the -0.5% floor; plain G6 is gone
        g6a = gate_named(summary, "G6a_crash_survival")
        self.assertIsNotNone(g6a)
        self.assertFalse(g6a["passed"])
        self.assertIsNone(gate_named(summary, "G6_regime_mand"))

    def test_g6a_passes_when_jan_apr_2020_edge_at_or_above_floor(self):
        # Given Jan-Apr trades aggregating to edge = -0.4% (just above the -0.5% floor)
        # winners=4 avg +5%, losers=6 avg -4.4%? Use: 0.4*5 - 0.6*4 = 2 - 2.4 = -0.4
        buckets = {"2020-02": month_bucket(trades=10, winners=4, sum_win_pct=20.0, sum_loss_pct=-24.0)}
        summary = run_block([window("2020-01-01", buckets=buckets)], "B")

        # Then G6a passes
        self.assertTrue(gate_named(summary, "G6a_crash_survival")["passed"])


class TestG6bRecovery(unittest.TestCase):
    def test_g6b_fails_when_may_dec_2020_edge_not_positive(self):
        # Given May-Dec 2020 trades aggregating to edge = 0.0 (winners=5 avg +4%, losers=5 avg -4%)
        buckets = {"2020-09": month_bucket(trades=10, winners=5, sum_win_pct=20.0, sum_loss_pct=-20.0)}
        summary = run_block([window("2020-01-01", buckets=buckets)], "B")

        # When evaluated, G6b requires strictly > 0 and so fails at exactly 0
        g6b = gate_named(summary, "G6b_recovery")
        self.assertIsNotNone(g6b)
        self.assertFalse(g6b["passed"])

    def test_g6b_passes_when_may_dec_2020_edge_positive(self):
        # Given May-Dec 2020 trades aggregating to a positive edge
        # (winners=6 avg +5%, losers=4 avg -3% -> 0.6*5 - 0.4*3 = 3 - 1.2 = +1.8)
        buckets = {"2020-08": month_bucket(trades=10, winners=6, sum_win_pct=30.0, sum_loss_pct=-12.0)}
        summary = run_block([window("2020-01-01", buckets=buckets)], "B")

        # Then G6b passes
        self.assertTrue(gate_named(summary, "G6b_recovery")["passed"])

    def test_g6a_g6b_isolate_their_halves(self):
        # Given a crash half that bled (-1.0%) but a recovery half that rallied (+1.8%)
        buckets = {
            "2020-03": month_bucket(trades=10, winners=4, sum_win_pct=20.0, sum_loss_pct=-30.0),
            "2020-08": month_bucket(trades=10, winners=6, sum_win_pct=30.0, sum_loss_pct=-12.0),
        }
        summary = run_block([window("2020-01-01", buckets=buckets)], "B")

        # Then the rally cannot rescue the crash gate — each half reads only its own months
        self.assertFalse(gate_named(summary, "G6a_crash_survival")["passed"])
        self.assertTrue(gate_named(summary, "G6b_recovery")["passed"])


class TestG16SpyBaseline(unittest.TestCase):
    def test_g16_fail_on_binding_block_fails_the_block(self):
        # Given a binding block whose engine SPY-baseline verdict is FAIL (strategy Calmar < SPY)
        summary = run_block([window("2008-01-01", edge=2.0)], "A", spy="FAIL")

        # Then G16 is present, failed, and drags the block overall to FAIL
        g16 = gate_named(summary, "G16_spy_baseline")
        self.assertIsNotNone(g16)
        self.assertFalse(g16["passed"])
        self.assertEqual("FAIL", summary["overall"])

    def test_g16_pass_when_verdict_pass(self):
        # Given a binding block whose SPY-baseline verdict is PASS
        summary = run_block([window("2008-01-01", edge=2.0)], "A", spy="PASS")

        # Then G16 passes
        self.assertTrue(gate_named(summary, "G16_spy_baseline")["passed"])

    def test_g16_inconclusive_does_not_bind(self):
        # Given a binding block whose SPY-baseline verdict is INCONCLUSIVE (no-bind guardrail)
        summary = run_block(
            [window("2008-01-01", edge=2.0)], "A",
            spy="INCONCLUSIVE", spy_reason="strategy stitched maxDD < 3.0%",
        )

        # Then G16 passes (does not bind, never auto-fails) but records the reason
        g16 = gate_named(summary, "G16_spy_baseline")
        self.assertTrue(g16["passed"])
        self.assertIn("INCONCLUSIVE", g16["value"])

    def test_g16_absent_benchmark_is_non_fatal(self):
        # Given a binding block with no spyBaselineComparison (e.g. SPY data unavailable)
        summary = run_block([window("2008-01-01", edge=2.0)], "A")

        # Then G16 passes (non-fatal — the gate cannot bind without a benchmark)
        self.assertTrue(gate_named(summary, "G16_spy_baseline")["passed"])

    def test_g16_inconclusive_25y_aggregate_is_flagged_loudly(self):
        # Given an INCONCLUSIVE verdict on the 25-year aggregate (should never happen on 25y of data)
        summary = run_block(
            [window("2008-01-01", edge=2.0)], "25y",
            spy="INCONCLUSIVE", spy_reason="stitched OOS series < 60 trading days",
        )

        # Then it does not fail the block but is surfaced as a loud aggregate flag
        self.assertTrue(gate_named(summary, "G16_spy_baseline")["passed"])
        self.assertTrue(summary["spy_baseline_inconclusive_aggregate"])


class TestG1CagrFloor(unittest.TestCase):
    def test_g1_passes_at_25_percent(self):
        # Given a binding block whose aggregate CAGR sits exactly at the recalibrated 25% floor
        summary = run_block([window(f"200{i}-01-01") for i in range(4)], "A", cagr=25.0)

        # Then G1 passes (floor lowered from 30 to 25 per ADR 0015)
        self.assertTrue(gate_named(summary, "G1_cagr")["passed"])

    def test_g1_fails_just_below_25_percent(self):
        # Given aggregate CAGR of 24.9% — a hair under the new floor
        summary = run_block([window(f"200{i}-01-01") for i in range(4)], "A", cagr=24.9)

        # Then G1 fails (it would have passed under the old 30 floor only via... no — still fails)
        self.assertFalse(gate_named(summary, "G1_cagr")["passed"])

    def test_g1_passes_at_26_percent_that_old_floor_rejected(self):
        # Given 26% CAGR — below the retired 30% floor but above the new 25% floor
        summary = run_block([window(f"200{i}-01-01") for i in range(4)], "A", cagr=26.0)

        # Then G1 now passes where the old framework rejected it
        self.assertTrue(gate_named(summary, "G1_cagr")["passed"])


class TestG9SharpeOnly(unittest.TestCase):
    def test_g9_passes_at_sharpe_floor_regardless_of_calmar(self):
        # Given Sharpe exactly 0.5 but a Calmar (1.6) that the old conjunct would still pass —
        # the point is G9 reads ONLY Sharpe now
        summary = run_block([window("2008-01-01", edge=2.0)], "A", sharpe=0.5, calmar=1.6)

        # Then G9 passes on Sharpe alone and the gate is renamed off the compound key
        g9 = gate_named(summary, "G9_sharpe")
        self.assertIsNotNone(g9)
        self.assertTrue(g9["passed"])
        self.assertIsNone(gate_named(summary, "G9_sharpe_calmar"))

    def test_g9_fails_below_sharpe_floor(self):
        # Given Sharpe 0.49 — just under the recalibrated 0.5 floor
        summary = run_block([window("2008-01-01", edge=2.0)], "A", sharpe=0.49, calmar=3.0)

        # Then G9 fails
        self.assertFalse(gate_named(summary, "G9_sharpe")["passed"])

    def test_g9_ignores_low_calmar_when_sharpe_clears(self):
        # Given a high Sharpe but a Calmar (0.4) the old conjunct would have FAILED on
        summary = run_block([window("2008-01-01", edge=2.0)], "A", sharpe=1.5, calmar=0.4)

        # Then G9 itself passes (Calmar quality is now G15's job, not G9's)
        self.assertTrue(gate_named(summary, "G9_sharpe")["passed"])


class TestG15AbsoluteCalmar(unittest.TestCase):
    def test_g15_passes_at_1_5(self):
        # Given Calmar exactly at the 1.5 absolute floor on a binding block
        summary = run_block([window("2008-01-01", edge=2.0)], "A", calmar=1.5)

        # Then G15 passes
        g15 = gate_named(summary, "G15_calmar")
        self.assertIsNotNone(g15)
        self.assertTrue(g15["passed"])

    def test_g15_fails_just_below_1_5(self):
        # Given Calmar 1.49 — a hair under the floor
        summary = run_block([window("2008-01-01", edge=2.0)], "A", calmar=1.49)

        # Then G15 fails and drags the binding block overall to FAIL
        self.assertFalse(gate_named(summary, "G15_calmar")["passed"])
        self.assertEqual("FAIL", summary["overall"])

    def test_g15_binds_on_block_b(self):
        # Given a sub-floor Calmar on binding Block B
        summary = run_block([window("2020-01-01", edge=2.0)], "B", calmar=1.0)

        # Then G15 is present and failed (binding on A/B/25y)
        self.assertFalse(gate_named(summary, "G15_calmar")["passed"])

    def test_g15_binds_on_25y_aggregate(self):
        # Given a sub-floor Calmar on the 25y aggregate
        summary = run_block([window("2008-01-01", edge=2.0)], "25y", calmar=1.2)

        # Then G15 fails the 25y aggregate
        self.assertFalse(gate_named(summary, "G15_calmar")["passed"])

    def test_g15_informational_on_block_c(self):
        # Given a sub-floor Calmar on informational Block C
        summary = run_block([window("2024-01-01", edge=0.1)], "C", calmar=1.0)

        # Then G15 is reported as failed but Block C never binds (overall doesn't gate the run)
        g15 = gate_named(summary, "G15_calmar")
        self.assertIsNotNone(g15)
        self.assertFalse(g15["passed"])
        self.assertFalse(summary["binding"])


class TestOtherBlocksKeepSingleG6(unittest.TestCase):
    def test_block_a_keeps_single_g6_no_split(self):
        # Given a Block A 2008 mandate window with positive edge
        summary = run_block([window("2008-01-01", edge=2.0)], "A")

        # When evaluated, Block A uses the single window-aggregate G6 and has no G6a/G6b
        self.assertIsNotNone(gate_named(summary, "G6_regime_mand"))
        self.assertIsNone(gate_named(summary, "G6a_crash_survival"))
        self.assertIsNone(gate_named(summary, "G6b_recovery"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
