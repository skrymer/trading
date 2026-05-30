#!/usr/bin/env python3
"""Tests for the interlock — the data-mining brake (ADR 0008).

A config_hash that reached REJECTED/NEAR_MISS is dead. The interlock refuses to advance a dead
hash OR a single-step G13 neighbour of one (reusing g13_neighbors' classification). No override.

Run: python3 test_interlock.py
"""
import unittest

import config_hash
import interlock
import record


def request_with(max_positions=15, risk=1.25, lookback=10):
    """A validation template with a tunable surface g13_neighbors can perturb."""
    return {
        "startDate": "2000-01-01",
        "endDate": "2014-01-01",
        "maxPositions": max_positions,
        "entryDelayDays": 1,
        "positionSizing": {
            "startingCapital": 10000,
            "sizer": {"type": "atrRisk", "riskPercentage": risk, "nAtr": 2.0},
            "leverageRatio": 1.0,
        },
        "entryStrategy": {"type": "custom", "conditions": [{"type": "pullback", "parameters": {"lookbackDays": lookback}}]},
        "exitStrategy": {"type": "custom", "conditions": [{"type": "marketDowntrend"}]},
        "ranker": "SectorEdge",
    }


class InterlockRefusesDeadHash(unittest.TestCase):
    def test_exact_dead_hash_is_refused(self):
        # Given a candidate whose config is already on the dead list (it was REJECTED)
        candidate = request_with(max_positions=15)
        dead = {config_hash.config_hash(candidate)}

        # When the interlock checks it
        result = interlock.check(candidate, dead)

        # Then it is refused — re-running a dead config is data-mining
        self.assertEqual(result["decision"], "REFUSE")


class InterlockAdvancesCleanConfig(unittest.TestCase):
    def test_unrelated_config_advances(self):
        # Given a candidate unrelated to anything on the dead list
        candidate = request_with(max_positions=15)
        dead = {config_hash.config_hash(request_with(max_positions=3, risk=5.0, lookback=99))}

        # When the interlock checks it
        result = interlock.check(candidate, dead)

        # Then it advances
        self.assertEqual(result["decision"], "ADVANCE")


class InterlockRefusesNeighbourOfDead(unittest.TestCase):
    def test_single_step_neighbour_of_dead_hash_is_refused(self):
        # Given a dead config at maxPositions=15 (it was REJECTED)
        dead = {config_hash.config_hash(request_with(max_positions=15))}
        # And a candidate one discrete step away (maxPositions=16) — a parameter nudge
        candidate = request_with(max_positions=16)

        # When the interlock checks the nudged candidate
        result = interlock.check(candidate, dead)

        # Then it is refused as a single-step neighbour — the disguised re-run the brake exists to stop
        self.assertEqual(result["decision"], "REFUSE")
        self.assertEqual(result["reason"], "dead_neighbour")

    def test_continuous_successor_of_dead_is_refused(self):
        # Given a dead config with a continuous sizer knob (riskPercentage 1.25), and its stored
        # neighbour closure (×0.9/×1.1 are not inverses, so the candidate-side check alone misses this)
        dead_req = request_with()
        dead_req["positionSizing"]["sizer"]["riskPercentage"] = 1.25
        dead = {config_hash.config_hash(dead_req)}
        dead_neighbours = set(record.dead_neighbour_hashes(dead_req))
        # And a candidate the operator produced by nudging the dead knob up one step (1.25 → 1.38)
        candidate = request_with()
        candidate["positionSizing"]["sizer"]["riskPercentage"] = 1.38

        # When the interlock checks it against the dead set + closure
        result = interlock.check(candidate, dead, dead_neighbours)

        # Then it is refused — the continuous-knob disguised re-run is caught
        self.assertEqual(result["decision"], "REFUSE")
        self.assertEqual(result["reason"], "dead_neighbour")


if __name__ == "__main__":
    unittest.main()
