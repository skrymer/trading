#!/usr/bin/env python3
"""Tests for G13 Parameter Robustness (advisory) — neighbor generation + verdict aggregation.

G13 perturbs each in-scope tunable by one step and re-fires Block A + Block B per neighbor.
See SKILL.md "G13 — Parameter Robustness" and REFERENCE.md for the signed-off design.

Run: python3 -m pytest test_g13.py  (or `python3 test_g13.py` for verbose run)
"""
import unittest

import g13_aggregate
import g13_neighbors


def neighbor_result(name="lookbackDays", direction="+1", step=1, passed=True,
                    classification="DISCRETE", floor_flag=False, failing_gates=None):
    return {
        "name": name,
        "tunable": f"entryStrategy.conditions[0].parameters.{name}",
        "direction": direction,
        "step": step,
        "classification": classification,
        "floor_flag": floor_flag,
        "passed": passed,
        "failing_gates": failing_gates or [],
    }


def request_with(condition_params=None, sizer=None, max_positions=15, entry_delay=1):
    """A minimal walk-forward request with a tunable surface to perturb."""
    return {
        "startDate": "2000-01-01",
        "endDate": "2014-01-01",
        "maxPositions": max_positions,
        "entryDelayDays": entry_delay,
        "positionSizing": {
            "startingCapital": 10000,
            "sizer": sizer or {"type": "atrRisk", "riskPercentage": 1.25, "nAtr": 2.0},
            "leverageRatio": 1.0,
        },
        "entryStrategy": {
            "type": "custom",
            "conditions": [
                {"type": "pullback", "parameters": condition_params or {"lookbackDays": 10}},
                {"type": "uptrend"},
            ],
        },
        "exitStrategy": {"type": "custom", "conditions": [{"type": "marketDowntrend"}]},
        "ranker": "SectorEdge",
    }


class TestNeighborGeneration(unittest.TestCase):
    def test_discrete_condition_param_yields_plus_and_minus_one(self):
        # Given: a request whose only alpha tunable is a discrete lookbackDays = 10
        req = request_with(condition_params={"lookbackDays": 10})

        # When: neighbors are generated
        neighbors = g13_neighbors.generate_neighbors(req)

        # Then: lookbackDays produces exactly the -1 and +1 fired values
        lb = [n for n in neighbors if n["name"] == "lookbackDays"]
        fired = sorted(n["fired"] for n in lb)
        self.assertEqual(fired, [9, 11])
        self.assertTrue(all(n["classification"] == "DISCRETE" for n in lb))

    def test_continuous_sizer_param_steps_by_ten_percent_at_nominal_precision(self):
        # Given: a continuous riskPercentage = 1.25 (2 decimals)
        req = request_with(sizer={"type": "atrRisk", "riskPercentage": 1.25, "nAtr": 2.0})

        # When: neighbors are generated
        neighbors = g13_neighbors.generate_neighbors(req)

        # Then: riskPercentage fires at ×0.9 and ×1.1 rounded to 2 dp
        rp = [n for n in neighbors if n["name"] == "riskPercentage"]
        fired = sorted(n["fired"] for n in rp)
        self.assertEqual(fired, [1.13, 1.38])  # 1.125 half-up → 1.13; 1.375 half-up → 1.38
        self.assertTrue(all(n["classification"] == "CONTINUOUS" for n in rp))

    def test_continuous_step_that_rounds_back_to_center_is_widened_and_flagged(self):
        # Given: a tiny continuous param whose ×0.9/×1.1 rounds back to the nominal at its precision
        #        atrMultiplier = 0.1 (1 dp): ×1.1 = 0.11 → 0.1, ×0.9 = 0.09 → 0.1 — both no-ops
        req = request_with()
        req["exitStrategy"]["conditions"] = [{"type": "trail", "parameters": {"atrMultiplier": 0.1}}]

        # When
        neighbors = g13_neighbors.generate_neighbors(req)

        # Then: both neighbors widened off-center and flagged no_op_widened
        am = [n for n in neighbors if n["name"] == "atrMultiplier"]
        self.assertEqual(len(am), 2)
        self.assertTrue(all(n["no_op_widened"] for n in am))
        self.assertTrue(all(n["fired"] != n["nominal"] for n in am))

    def test_discrete_pinned_at_floor_yields_only_plus_one_and_floor_flag(self):
        # Given: maxPositions = 1 (natural floor; 0 positions is invalid)
        req = request_with(max_positions=1)

        # When
        neighbors = g13_neighbors.generate_neighbors(req)

        # Then: only the +1 neighbor exists and it is floor-flagged
        mp = [n for n in neighbors if n["name"] == "maxPositions"]
        self.assertEqual(len(mp), 1)
        self.assertEqual(mp[0]["fired"], 2)
        self.assertTrue(mp[0]["floor_flag"])

    def test_unrecognized_param_falls_back_to_subtype_and_is_flagged(self):
        # Given: an unknown-named int param on a condition
        req = request_with(condition_params={"wobbleCount": 5})

        # When
        neighbors = g13_neighbors.generate_neighbors(req)

        # Then: classified DISCRETE by int subtype and the fallback is flagged
        wc = [n for n in neighbors if n["name"] == "wobbleCount"]
        self.assertTrue(wc)
        self.assertTrue(all(n["classification"] == "DISCRETE" for n in wc))
        self.assertTrue(all(n["subtype_fallback"] for n in wc))

    def test_categorical_and_structural_fields_are_not_tunables(self):
        # Given: a normal request
        req = request_with()

        # When
        names = {n["name"] for n in g13_neighbors.generate_neighbors(req)}

        # Then: sizer type, startingCapital, ranker, dates are excluded; real tunables included
        self.assertNotIn("type", names)
        self.assertNotIn("startingCapital", names)
        self.assertNotIn("ranker", names)
        self.assertNotIn("startDate", names)
        self.assertIn("nAtr", names)
        self.assertIn("maxPositions", names)

    def test_pm2_neighbor_steps_two_for_discrete_and_continuous(self):
        # Given: a request and a ±2 probe on a discrete and a continuous tunable
        req = request_with(condition_params={"lookbackDays": 10})

        # When: discrete +2 probe
        disc = g13_neighbors.pm2_neighbor(req, {
            "tunable": "entryStrategy.conditions[0].parameters.lookbackDays",
            "name": "lookbackDays", "classification": "DISCRETE", "direction": "+1"})
        # And: continuous x1.2 probe on nAtr = 2.0
        cont = g13_neighbors.pm2_neighbor(req, {
            "tunable": "positionSizing.sizer.nAtr",
            "name": "nAtr", "classification": "CONTINUOUS", "direction": "x1.1"})

        # Then: discrete fires nominal+2, continuous fires nominal×1.2, both marked step 2
        self.assertEqual(disc["fired"], 12)
        self.assertEqual(disc["step"], 2)
        self.assertEqual(cont["fired"], 2.4)
        self.assertEqual(cont["step"], 2)


class TestVerdictAggregation(unittest.TestCase):
    def test_tradable_when_all_neighbors_pass(self):
        # Given: every ±1 neighbor passes Block A + Block B
        results = [neighbor_result(direction="-1"), neighbor_result(direction="+1")]

        # When
        outcome = g13_aggregate.g13_outcome(results)

        # Then: TRADABLE retained, advisory (non-binding)
        self.assertEqual(outcome["outcome"], "TRADABLE")
        self.assertFalse(outcome["binding"])

    def test_rejected_when_neighbor_fails_a_regime_gate(self):
        # Given: one neighbor fails G7 (chop regime — binary, no escape valve)
        results = [
            neighbor_result(direction="-1", passed=True),
            neighbor_result(direction="+1", passed=False,
                            failing_gates=[{"name": "G7_regime_chop", "value": "2018-Q4=-0.45"}]),
        ]

        # When / Then: regime-gate neighbor failure -> REJECTED (the removed {G5,G7} valve stays removed)
        outcome = g13_aggregate.g13_outcome(results)
        self.assertEqual(outcome["outcome"], "REJECTED")
        self.assertEqual(outcome["reason"], "g13_parameter_fragile")

    def test_rejected_when_two_or_more_neighbors_fail(self):
        # Given: two neighbors fail, even on near-miss continuous gates
        results = [
            neighbor_result(name="lookbackDays", direction="+1", passed=False,
                            failing_gates=[{"name": "G5_cov_edge", "value": 1.6}]),
            neighbor_result(name="nAtr", direction="x1.1", passed=False, classification="CONTINUOUS",
                            failing_gates=[{"name": "G1_cagr", "value": 28.0}]),
        ]

        outcome = g13_aggregate.g13_outcome(results)
        self.assertEqual(outcome["outcome"], "REJECTED")

    def test_needs_pm2_probe_when_single_continuous_near_miss_one_directional(self):
        # Given: +1 fails G5 near-miss (1.6 <= 1.65), -1 passes clean, no ±2 supplied yet
        results = [
            neighbor_result(direction="-1", passed=True),
            neighbor_result(direction="+1", passed=False,
                            failing_gates=[{"name": "G5_cov_edge", "value": 1.6}]),
        ]

        outcome = g13_aggregate.g13_outcome(results)
        self.assertEqual(outcome["outcome"], "NEEDS_PM2_PROBE")
        self.assertEqual(outcome["pm2_probe"]["direction"], "+1")

    def test_rejected_when_pm2_neighbor_also_fails_cliff(self):
        # Given: the near-miss boundary plus a ±2 probe that also fails -> edge cliff
        results = [
            neighbor_result(direction="-1", passed=True),
            neighbor_result(direction="+1", passed=False,
                            failing_gates=[{"name": "G5_cov_edge", "value": 1.6}]),
            neighbor_result(direction="+2", step=2, passed=False,
                            failing_gates=[{"name": "G5_cov_edge", "value": 1.9}]),
        ]

        outcome = g13_aggregate.g13_outcome(results)
        self.assertEqual(outcome["outcome"], "REJECTED")
        self.assertEqual(outcome["reason"], "g13_parameter_fragile_pm2_cliff")

    def test_provisional_when_pm2_neighbor_recovers(self):
        # Given: near-miss boundary, ±2 probe passes -> the ±1 miss was noise
        results = [
            neighbor_result(direction="-1", passed=True),
            neighbor_result(direction="+1", passed=False,
                            failing_gates=[{"name": "G5_cov_edge", "value": 1.6}]),
            neighbor_result(direction="+2", step=2, passed=True),
        ]

        outcome = g13_aggregate.g13_outcome(results)
        self.assertEqual(outcome["outcome"], "PROVISIONAL")
        self.assertEqual(outcome["reason"], "g13_regime_sensitive_neighbor")

    def test_rejected_when_failure_not_one_directional(self):
        # Given: both ±1 neighbors of the same tunable fail (even near-miss) -> not one-directional
        results = [
            neighbor_result(direction="-1", passed=False,
                            failing_gates=[{"name": "G5_cov_edge", "value": 1.6}]),
            neighbor_result(direction="+1", passed=False,
                            failing_gates=[{"name": "G5_cov_edge", "value": 1.6}]),
        ]

        outcome = g13_aggregate.g13_outcome(results)
        self.assertEqual(outcome["outcome"], "REJECTED")

    def test_rejected_when_g5_failure_outside_near_miss_band(self):
        # Given: G5 fails at 1.8 (> 1.65 ceiling+band) -> wide-margin, not near-miss
        results = [
            neighbor_result(direction="-1", passed=True),
            neighbor_result(direction="+1", passed=False,
                            failing_gates=[{"name": "G5_cov_edge", "value": 1.8}]),
        ]

        outcome = g13_aggregate.g13_outcome(results)
        self.assertEqual(outcome["outcome"], "REJECTED")

    def test_build_neighbor_result_marks_pass_only_when_both_blocks_pass(self):
        # Given: Block A passes, Block B fails one gate
        meta = {"tunable": "t.nAtr", "name": "nAtr", "direction": "x1.1", "step": 1,
                "classification": "CONTINUOUS", "floor_flag": False}
        eval_a = {"overall": "PASS", "gates": [{"name": "G1_cagr", "passed": True, "value": 35.0}]}
        eval_b = {"overall": "FAIL", "gates": [
            {"name": "G5_cov_edge", "passed": False, "value": 1.6},
            {"name": "G1_cagr", "passed": True, "value": 31.0}]}

        # When
        rec = g13_aggregate.neighbor_result_from_evals(meta, eval_a, eval_b)

        # Then: not passed; only the failing gate is collected
        self.assertFalse(rec["passed"])
        self.assertEqual([g["name"] for g in rec["failing_gates"]], ["G5_cov_edge"])

    def test_g9_sharpe_only_near_miss_reads_the_numeric_value(self):
        # Given: a failing G9 gate whose value is the bare Sharpe number eval-block now emits
        # (Sharpe-only since ADR 0015 — no more "sharpe=.. calmar=.." compound string)
        meta = {"tunable": "t.nAtr", "name": "nAtr", "direction": "x1.1", "step": 1,
                "classification": "CONTINUOUS", "floor_flag": False}
        eval_a = {"overall": "PASS", "gates": []}
        eval_b = {"overall": "FAIL", "gates": [
            {"name": "G9_sharpe", "passed": False, "value": 0.47}]}

        # When
        rec = g13_aggregate.neighbor_result_from_evals(meta, eval_a, eval_b)

        # Then: the gate carries its numeric value and 0.47 (>= 0.5 * 0.9 = 0.45) is a near-miss
        g9 = rec["failing_gates"][0]
        self.assertEqual(g9["value"], 0.47)
        self.assertTrue(g13_aggregate.is_continuous_near_miss(g9))

    def test_g9_sharpe_outside_band_is_not_near_miss(self):
        # Given: G9 Sharpe at 0.40 — below the 0.45 near-miss floor
        self.assertFalse(g13_aggregate.is_continuous_near_miss(
            {"name": "G9_sharpe", "value": 0.40}))

    def test_g15_calmar_near_miss_within_ten_percent(self):
        # Given: G15 absolute Calmar at 1.40 — within 10% rel of the 1.5 floor (>= 1.35)
        self.assertTrue(g13_aggregate.is_continuous_near_miss(
            {"name": "G15_calmar", "value": 1.40}))

    def test_g15_calmar_outside_band_is_not_near_miss(self):
        # Given: G15 Calmar at 1.30 — below the 1.35 near-miss floor (marginal book)
        self.assertFalse(g13_aggregate.is_continuous_near_miss(
            {"name": "G15_calmar", "value": 1.30}))

    def test_provisional_when_floor_flagged_and_all_pass(self):
        # Given: all neighbors pass but one tunable is floor-pinned (one-sided)
        results = [neighbor_result(name="maxPositions", direction="+1", floor_flag=True, passed=True)]

        outcome = g13_aggregate.g13_outcome(results)
        self.assertEqual(outcome["outcome"], "PROVISIONAL")
        self.assertEqual(outcome["reason"], "g13_floor_pinned")


if __name__ == "__main__":
    unittest.main(verbosity=2)
