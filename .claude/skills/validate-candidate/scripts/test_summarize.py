#!/usr/bin/env python3
"""Tests for summarize.py verdict logic (refined framework 2026-05-28).

Covers the verdict precedence:
- TRADABLE iff all binding pass + G11 (A→B) ok + Block C non-catastrophic
- PROVISIONAL iff all binding pass + (G11 fail OR Block C catastrophic)
- INCONCLUSIVE_G11 iff all binding pass + G11 inapplicable
- NEAR_MISS iff binding-fail + all fails tight + ≤2 fails
- REJECTED otherwise (including Block C failures alone — never bind)

Run: python3 -m pytest test_summarize.py  (or `python3 test_summarize.py` for verbose run)
"""
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).parent / "summarize.py"


def make_eval(block: str, overall: str, **kwargs) -> dict:
    """Minimal eval-block JSON shape. Provide overall, aggregate metrics, and gate list."""
    defaults = {
        "label": "test",
        "block": block,
        "block_name": f"Block {block} test",
        "binding": block in {"A", "B", "25y"},
        "overall": overall,
        "first_failure": None,
        "aggregate_edge": 0.5,
        "aggregate_cagr": 35.0,
        "aggregate_max_dd": 12.0,
        "aggregate_sharpe": 2.0,
        "aggregate_calmar": 2.5,
        "windows": 11 if block == "A" else (4 if block == "B" else (22 if block == "25y" else 1)),
        "block_trades": 1000,
        "non_catastrophic": None,
        "gates": [],
    }
    defaults.update(kwargs)
    return defaults


def run_summarize(*eval_paths, script_conditions=0, g13=None, g14=None) -> dict:
    """Invoke summarize.py with the given eval paths; return parsed JSON output."""
    cmd = [sys.executable, str(SCRIPT), "test-candidate"]
    cmd.extend(str(p) for p in eval_paths)
    if script_conditions:
        cmd.extend(["--script-conditions", str(script_conditions)])
    if g13:
        cmd.extend(["--g13", str(g13)])
    if g14:
        cmd.extend(["--g14", str(g14)])
    result = subprocess.run(cmd, capture_output=True, text=True, check=False)
    assert result.returncode == 0, f"summarize.py failed: {result.stderr}"
    return json.loads(result.stdout)


class TestVerdictLogic(unittest.TestCase):
    def setUp(self):
        self.tmpdir = tempfile.mkdtemp()
        self.paths = {}

    def tearDown(self):
        for p in self.paths.values():
            try:
                os.unlink(p)
            except OSError:
                pass

    def write_eval(self, layer: str, **kwargs) -> Path:
        path = Path(self.tmpdir) / f"eval-{layer}.json"
        path.write_text(json.dumps(make_eval(layer, **kwargs)))
        self.paths[layer] = str(path)
        return path

    def test_tradable_when_all_binding_pass_and_g11_ok_and_block_c_non_catastrophic(self):
        # Given: A + B + 25y all PASS with edge_a → edge_b decay < 50%, Block C non-catastrophic
        self.write_eval("A", overall="PASS", aggregate_edge=0.7, aggregate_cagr=35.0)
        self.write_eval("B", overall="PASS", aggregate_edge=0.5, aggregate_cagr=30.0)  # edge decay (0.7-0.5)/0.7 = 0.29 (passes 50% threshold)
        self.write_eval("25y", overall="PASS")
        self.write_eval("C", overall="PASS", non_catastrophic=True)

        # When: summarize runs
        result = run_summarize(self.paths["A"], self.paths["B"], self.paths["25y"], self.paths["C"])

        # Then: verdict is TRADABLE
        self.assertEqual(result["verdict"], "TRADABLE")
        self.assertTrue(result["g11_cross_block_decay"]["passed"])
        self.assertTrue(result["block_c_non_catastrophic"])

    def test_provisional_when_block_c_catastrophic(self):
        # Given: A + B + 25y pass, G11 ok, but Block C has |edge| > 0.5% (catastrophic)
        self.write_eval("A", overall="PASS", aggregate_edge=0.7, aggregate_cagr=35.0)
        self.write_eval("B", overall="PASS", aggregate_edge=0.5, aggregate_cagr=30.0)
        self.write_eval("25y", overall="PASS")
        self.write_eval("C", overall="FAIL", non_catastrophic=False, aggregate_edge=-0.6, aggregate_max_dd=12.0)

        result = run_summarize(self.paths["A"], self.paths["B"], self.paths["25y"], self.paths["C"])

        self.assertEqual(result["verdict"], "PROVISIONAL")
        self.assertFalse(result["block_c_non_catastrophic"])

    def test_provisional_when_g11_fails(self):
        # Given: A + B + 25y pass but edge_a → edge_b decay > 50% (G11 fail)
        self.write_eval("A", overall="PASS", aggregate_edge=1.0, aggregate_cagr=40.0)
        self.write_eval("B", overall="PASS", aggregate_edge=0.3, aggregate_cagr=35.0)  # edge decay 0.7 > 0.5
        self.write_eval("25y", overall="PASS")
        self.write_eval("C", overall="PASS", non_catastrophic=True)

        result = run_summarize(self.paths["A"], self.paths["B"], self.paths["25y"], self.paths["C"])

        self.assertEqual(result["verdict"], "PROVISIONAL")
        self.assertFalse(result["g11_cross_block_decay"]["passed"])

    def test_rejected_when_binding_layer_fails(self):
        # Given: Block A FAILS (binding layer failure)
        self.write_eval(
            "A", overall="FAIL", aggregate_cagr=20.0,
            gates=[{"name": "G1_cagr", "passed": False, "value": 20.0}],
            first_failure="G1_cagr",
        )
        self.write_eval("B", overall="PASS")
        self.write_eval("25y", overall="PASS")

        result = run_summarize(self.paths["A"], self.paths["B"], self.paths["25y"])

        # Then: REJECTED (20% is far from 30% threshold, not a NEAR_MISS)
        # Tight band for G1 is value >= 30 * 0.95 = 28.5, so 20 is well outside
        self.assertEqual(result["verdict"], "REJECTED")

    def test_g6a_crash_survival_failure_rejects_not_near_miss(self):
        # Given: Block B fails only G6a (crash survival) — a regime-mandate sub-gate near its
        # -0.5% floor. Per issue #51 it is "strict", so it must never qualify as a tight-margin
        # NEAR_MISS even when it's the sole failure.
        self.write_eval(
            "B", overall="FAIL",
            gates=[{"name": "G6a_crash_survival", "passed": False, "value": "2020 crash survival OOS edge = -0.6"}],
            first_failure="G6a_crash_survival",
        )
        self.write_eval("A", overall="PASS")
        self.write_eval("25y", overall="PASS")

        result = run_summarize(self.paths["A"], self.paths["B"], self.paths["25y"])

        # Then: REJECTED — a crash-survival failure is structural, not "almost tradable"
        self.assertEqual(result["verdict"], "REJECTED")

    def test_g16_spy_baseline_failure_rejects_not_near_miss(self):
        # Given: Block A fails only G16 (strategy loses to buy-and-hold SPY on Calmar) — a binding,
        # structural failure (delivering index beta), never an "almost tradable" tight margin.
        self.write_eval(
            "A", overall="FAIL",
            gates=[{"name": "G16_spy_baseline", "passed": False,
                    "value": "FAIL (strategy Calmar=0.9 vs SPY Calmar=1.4)"}],
            first_failure="G16_spy_baseline",
        )
        self.write_eval("B", overall="PASS")
        self.write_eval("25y", overall="PASS")

        result = run_summarize(self.paths["A"], self.paths["B"], self.paths["25y"])

        # Then: REJECTED — beating the passive alternative is structural, not a near-miss
        self.assertEqual(result["verdict"], "REJECTED")

    def test_inconclusive_spy_baseline_on_25y_aggregate_is_surfaced_loudly(self):
        # Given: all binding layers PASS but the 25y aggregate's SPY-baseline came back INCONCLUSIVE
        # (should never happen on 25y of data → signals something degenerate upstream).
        self.write_eval("A", overall="PASS", aggregate_edge=0.7, aggregate_cagr=35.0)
        self.write_eval("B", overall="PASS", aggregate_edge=0.5, aggregate_cagr=30.0)
        self.write_eval("25y", overall="PASS", spy_baseline_inconclusive_aggregate=True)
        self.write_eval("C", overall="PASS", non_catastrophic=True)

        result = run_summarize(self.paths["A"], self.paths["B"], self.paths["25y"], self.paths["C"])

        # Then: the loud flag is carried in the summary for the analyst to surface
        self.assertTrue(result["spy_baseline_inconclusive_aggregate"])

    def test_block_c_failure_alone_does_not_trigger_rejected(self):
        # Given: Block A + B + 25y all PASS but Block C FAILS gates (non_catastrophic still True)
        self.write_eval("A", overall="PASS", aggregate_edge=0.7, aggregate_cagr=35.0)
        self.write_eval("B", overall="PASS", aggregate_edge=0.5, aggregate_cagr=30.0)
        self.write_eval("25y", overall="PASS")
        self.write_eval(
            "C", overall="FAIL", non_catastrophic=True,
            aggregate_edge=-0.1, aggregate_max_dd=10.0,
            gates=[{"name": "G1_cagr", "passed": False, "value": 4.0}],
            first_failure="G1_cagr",
        )

        result = run_summarize(self.paths["A"], self.paths["B"], self.paths["25y"], self.paths["C"])

        # Then: TRADABLE (Block C is informational; gate failures don't bind)
        # This was the entire motivation for the framework refinement —
        # exactly VZ3-s3's actual situation that the old framework REJECTED.
        self.assertEqual(result["verdict"], "TRADABLE")

    def test_near_miss_when_binding_failure_within_tight_band_under_cap(self):
        # Given: Block A fails G1 by 4.5% (CAGR 28.65 vs 30 threshold; tight band is value >= 28.5)
        self.write_eval(
            "A", overall="FAIL", aggregate_cagr=28.65,
            gates=[{"name": "G1_cagr", "passed": False, "value": 28.65}],
            first_failure="G1_cagr",
        )
        self.write_eval("B", overall="PASS")
        self.write_eval("25y", overall="PASS")

        result = run_summarize(self.paths["A"], self.paths["B"], self.paths["25y"])

        self.assertEqual(result["verdict"], "NEAR_MISS")
        self.assertEqual(result["tight_margin_failures_binding"], 1)

    def test_block_c_tight_failure_does_not_count_toward_near_miss_cap(self):
        # Given: Block A has 2 tight binding failures; Block C also fails gates
        # (Block C's failures must not count toward the ≤2 binding-tight-failure cap)
        self.write_eval(
            "A", overall="FAIL", aggregate_cagr=28.65, aggregate_max_dd=26.0,
            gates=[
                {"name": "G1_cagr", "passed": False, "value": 28.65},
                {"name": "G2_dd_aggregated", "passed": False, "value": 26.0},
            ],
            first_failure="G1_cagr",
        )
        self.write_eval("B", overall="PASS")
        self.write_eval("25y", overall="PASS")
        self.write_eval(
            "C", overall="FAIL", non_catastrophic=True,
            gates=[
                {"name": "G1_cagr", "passed": False, "value": 4.0},  # not tight in C, but C tights don't count anyway
                {"name": "G9_sharpe_calmar", "passed": False, "value": "sharpe=0.5 calmar=0.4"},
            ],
            first_failure="G1_cagr",
        )

        result = run_summarize(self.paths["A"], self.paths["B"], self.paths["25y"], self.paths["C"])

        # Then: NEAR_MISS — Block A's 2 tight failures qualify; Block C's failures ignored
        self.assertEqual(result["verdict"], "NEAR_MISS")
        self.assertEqual(result["failed_gates_count_binding"], 2)

    def test_inconclusive_g11_when_block_a_edge_non_positive(self):
        # Given: A + B + 25y all pass but Block A's aggregate_edge is 0 (G11 can't divide)
        self.write_eval("A", overall="PASS", aggregate_edge=0.0, aggregate_cagr=35.0)
        self.write_eval("B", overall="PASS", aggregate_edge=0.5, aggregate_cagr=30.0)
        self.write_eval("25y", overall="PASS")
        self.write_eval("C", overall="PASS", non_catastrophic=True)

        result = run_summarize(self.paths["A"], self.paths["B"], self.paths["25y"], self.paths["C"])

        self.assertEqual(result["verdict"], "INCONCLUSIVE_G11")
        self.assertFalse(result["g11_cross_block_decay"]["applicable"])

    def test_tradable_pending_promotion_when_inline_scripts_present(self):
        # Given: all gates pass + inline scripts in the request template
        self.write_eval("A", overall="PASS", aggregate_edge=0.7, aggregate_cagr=35.0)
        self.write_eval("B", overall="PASS", aggregate_edge=0.5, aggregate_cagr=30.0)
        self.write_eval("25y", overall="PASS")
        self.write_eval("C", overall="PASS", non_catastrophic=True)

        result = run_summarize(
            self.paths["A"], self.paths["B"], self.paths["25y"], self.paths["C"],
            script_conditions=2,
        )

        # Then: verdict still TRADABLE but script_conditions surfaced
        self.assertEqual(result["verdict"], "TRADABLE")
        self.assertEqual(result["script_conditions_in_template"], 2)

    def test_g13_advisory_is_surfaced_without_changing_verdict(self):
        # Given: a clean TRADABLE plus an advisory G13 outcome of REJECTED (fragile)
        self.write_eval("A", overall="PASS", aggregate_edge=0.7, aggregate_cagr=35.0)
        self.write_eval("B", overall="PASS", aggregate_edge=0.5, aggregate_cagr=30.0)
        self.write_eval("25y", overall="PASS")
        self.write_eval("C", overall="PASS", non_catastrophic=True)
        g13_path = Path(self.tmpdir) / "g13-outcome.json"
        g13_path.write_text(json.dumps({"outcome": "REJECTED", "reason": "g13_parameter_fragile", "binding": False}))

        # When
        result = run_summarize(
            self.paths["A"], self.paths["B"], self.paths["25y"], self.paths["C"], g13=g13_path)

        # Then: verdict stays TRADABLE (G13 advisory), but the advisory outcome is surfaced
        self.assertEqual(result["verdict"], "TRADABLE")
        self.assertEqual(result["g13_advisory"]["outcome"], "REJECTED")
        self.assertFalse(result["g13_advisory"]["binding"])


    def test_g14_differs_does_not_flip_binding_pass_to_rejected(self):
        # Given: a clean TRADABLE binding result PLUS a G14 DIFFERS outcome.
        # Per quant 2026-05-29 G14 DIFFERS voids the *reusable inline* verdict but does NOT
        # auto-REJECT — this run already validated the promoted config, so the verdict stands.
        self.write_eval("A", overall="PASS", aggregate_edge=0.7, aggregate_cagr=35.0)
        self.write_eval("B", overall="PASS", aggregate_edge=0.5, aggregate_cagr=30.0)
        self.write_eval("25y", overall="PASS")
        self.write_eval("C", overall="PASS", non_catastrophic=True)
        g14_path = Path(self.tmpdir) / "g14.json"
        g14_path.write_text(json.dumps({
            "outcome": "DIFFERS", "jaccard": 0.997, "entry_divergence_count": 2,
            "exit_divergence_count": 0, "pnl_divergence_count": 0,
            "first_divergent_trade": {"bucket": "ENTRY", "symbol": "PENN", "entry_date": "2020-03-20"},
        }))

        # When
        result = run_summarize(
            self.paths["A"], self.paths["B"], self.paths["25y"], self.paths["C"], g14=g14_path)

        # Then: verdict stays TRADABLE (promoted config validated this run); G14 surfaced
        self.assertEqual(result["verdict"], "TRADABLE")
        self.assertEqual(result["g14_implementation_invariance"]["outcome"], "DIFFERS")

    def test_g14_pass_surfaced_alongside_tradable(self):
        # Given: clean TRADABLE plus G14 PASS (promoted config identical to inline)
        self.write_eval("A", overall="PASS", aggregate_edge=0.7, aggregate_cagr=35.0)
        self.write_eval("B", overall="PASS", aggregate_edge=0.5, aggregate_cagr=30.0)
        self.write_eval("25y", overall="PASS")
        self.write_eval("C", overall="PASS", non_catastrophic=True)
        g14_path = Path(self.tmpdir) / "g14.json"
        g14_path.write_text(json.dumps({"outcome": "PASS", "jaccard": 1.0}))

        # When
        result = run_summarize(
            self.paths["A"], self.paths["B"], self.paths["25y"], self.paths["C"], g14=g14_path)

        # Then: TRADABLE and the transfer is recorded
        self.assertEqual(result["verdict"], "TRADABLE")
        self.assertEqual(result["g14_implementation_invariance"]["outcome"], "PASS")


if __name__ == "__main__":
    unittest.main(verbosity=2)
