#!/usr/bin/env python3
"""Tests for record — how a leaf-skill result enters the dossier (ADR 0008).

record parses the machine verdict token from summarize.py, asserts the fired config matches what
was printed (catching 'printed X, ran tweaked Y'), and maps the verdict to an interlock state.
Never an operator-typed verdict.

Run: python3 test_record.py
"""
import json
import unittest

import config_hash
import record


def request_with(max_positions=15):
    return {
        "maxPositions": max_positions,
        "entryDelayDays": 1,
        "positionSizing": {"startingCapital": 10000, "sizer": {"type": "atrRisk", "riskPercentage": 1.25, "nAtr": 2.0}},
        "entryStrategy": {"type": "custom", "conditions": [{"type": "pullback", "parameters": {"lookbackDays": 10}}]},
        "exitStrategy": {"type": "custom", "conditions": [{"type": "marketDowntrend"}]},
        "ranker": "SectorEdge",
    }


class VerdictMapsToInterlockState(unittest.TestCase):
    def test_rejected_is_dead(self):
        # Given a REJECTED verdict
        # When mapped to an interlock state
        # Then the config is DEAD
        self.assertEqual(record.verdict_state("REJECTED"), "DEAD")

    def test_every_summarize_verdict_has_a_defined_state(self):
        # Given every verdict token summarize.py can emit (ADR 0008 — no outcome left undefined)
        expected = {
            "REJECTED": "DEAD",
            "NEAR_MISS": "DEAD",
            "TRADABLE": "SETTLED",
            "PROVISIONAL": "SETTLED",
            "INCONCLUSIVE_G11": "BLOCKED",
            "INCOMPLETE": "FAULT",
            "ERROR": "FAULT",
        }
        # When each is mapped
        # Then it lands on the interlock state ADR 0008 specifies
        for verdict, state in expected.items():
            self.assertEqual(record.verdict_state(verdict), state, verdict)


class ParseVerdictFromSummarizeOutput(unittest.TestCase):
    def test_reads_the_verdict_field(self):
        # Given a summarize.py result (its JSON carries a top-level "verdict")
        summarize_result = {"verdict": "TRADABLE", "layers": {}, "script_conditions_in_template": 0}

        # When the verdict is parsed
        # Then the token is extracted, not re-derived
        self.assertEqual(record.parse_verdict(summarize_result), "TRADABLE")


class DeadNeighbourClosure(unittest.TestCase):
    def test_closure_includes_the_continuous_plus10pct_neighbour(self):
        # Given a dead config with a continuous sizer knob (riskPercentage 1.25)
        dead = {
            "maxPositions": 15, "entryDelayDays": 1,
            "positionSizing": {"startingCapital": 10000,
                               "sizer": {"type": "atrRisk", "riskPercentage": 1.25, "nAtr": 2.0}},
            "entryStrategy": {"type": "custom", "conditions": [{"type": "pullback", "parameters": {"lookbackDays": 10}}]},
            "exitStrategy": {"type": "custom", "conditions": [{"type": "marketDowntrend"}]},
            "ranker": "SectorEdge",
        }
        # When its single-step neighbour closure is computed
        closure = record.dead_neighbour_hashes(dead)

        # Then the ×1.1 successor (1.25 → 1.38) — the realistic nudge — is in the closure
        nudged = json.loads(json.dumps(dead))
        nudged["positionSizing"]["sizer"]["riskPercentage"] = 1.38
        self.assertIn(config_hash.config_hash(nudged), closure)


class AssertHashMatchGuardsConfigSubstitution(unittest.TestCase):
    def test_matching_template_passes(self):
        # Given a FIRED event's recorded hash and the template that was supposed to run
        template = request_with(max_positions=15)
        fired_hash = config_hash.config_hash(template)

        # When the fired template still hashes the same, the assertion passes (returns None / no raise)
        record.assert_hash_match(fired_hash, template)

    def test_substituted_template_is_rejected(self):
        # Given a FIRED event for one config but a DIFFERENT template at record time (printed X, ran Y)
        fired_hash = config_hash.config_hash(request_with(max_positions=15))
        substituted = request_with(max_positions=10)

        # When the mismatch is checked
        # Then it is rejected — the result can't be recorded against the printed config's slot
        with self.assertRaises(record.ConfigMismatch):
            record.assert_hash_match(fired_hash, substituted)


if __name__ == "__main__":
    unittest.main()
