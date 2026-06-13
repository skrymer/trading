#!/usr/bin/env python3
"""Tests for the assessment pre-flight — blocks only on impossibility, shapes, advises (ADR 0022).

Run: python3 test_preflight.py
"""
import unittest

import preflight

KNOWN_CONDITIONS = ["adxrange", "regimelabelin", "regimelabelexit", "pricesabovesma", "ovtlyrbuysignal"]
KNOWN_RANKERS = ["random", "trailingreturn", "fundamentalquality"]


def request_with(entry_conditions, ranker=None, seed=None, exit_conditions=None):
    request = {
        "entryStrategy": {"type": "custom", "conditions": entry_conditions},
        "exitStrategy": {"type": "custom", "exitConditions": exit_conditions or []},
    }
    if ranker:
        request["ranker"] = {"type": ranker}
    if seed is not None:
        request["randomSeed"] = seed
    return request


class PreflightBlockers(unittest.TestCase):
    def test_an_unknown_condition_type_blocks(self):
        # Given a strategy referencing a condition the engine does not know
        request = request_with([{"type": "definitelyNotACondition"}])

        # When / Then
        report = preflight.check(request, KNOWN_CONDITIONS, KNOWN_RANKERS)
        self.assertTrue(any("unknown condition" in b for b in report["blockers"]))

    def test_a_limited_history_signal_blocks_as_span_disqualified(self):
        # Given a strategy gating on a signal that does not span the 25y window
        request = request_with([{"type": "ovtlyrBuySignal"}])

        # When / Then
        report = preflight.check(request, KNOWN_CONDITIONS, KNOWN_RANKERS)
        self.assertTrue(any("span" in b for b in report["blockers"]))

    def test_a_regime_gate_requires_an_accepted_anchor_check(self):
        # Given a regime-gated strategy and no recorded anchor acceptance
        request = request_with([{"type": "regimeLabelIn"}])

        # When / Then: blocked without acceptance, clear on PASS or ACCEPT_WITH_LIMITATIONS
        # (ACCEPT_WITH_LIMITATIONS is an acceptance — the gateable-label fence lives in
        # RegimeLabelCondition.GATEABLE_LABELS, not here; ADR 0024).
        blocked = preflight.check(request, KNOWN_CONDITIONS, KNOWN_RANKERS, anchor_status=None)
        self.assertTrue(any("anchor" in b for b in blocked["blockers"]))
        passed = preflight.check(request, KNOWN_CONDITIONS, KNOWN_RANKERS, anchor_status={"verdict": "PASS"})
        self.assertEqual([], passed["blockers"])
        accepted = preflight.check(
            request, KNOWN_CONDITIONS, KNOWN_RANKERS, anchor_status={"verdict": "ACCEPT_WITH_LIMITATIONS"}
        )
        self.assertEqual([], accepted["blockers"])

    def test_a_nested_group_condition_is_still_seen(self):
        # Given the unknown condition hidden inside an OR-group
        request = request_with([{"type": "group", "conditions": [{"type": "ghostCondition"}]}])

        # When / Then: the walk recurses into groups
        report = preflight.check(request, KNOWN_CONDITIONS, KNOWN_RANKERS)
        self.assertTrue(any("ghostcondition" in b for b in report["blockers"]))


class PreflightShaping(unittest.TestCase):
    def test_a_random_ranker_shapes_a_multi_seed_sweep(self):
        # Given a candidate whose own ranker is Random
        request = request_with([{"type": "adxRange"}], ranker="Random", seed=42)

        # When / Then
        report = preflight.check(request, KNOWN_CONDITIONS, KNOWN_RANKERS)
        self.assertEqual([], report["blockers"])
        self.assertTrue(any("multi-seed" in s for s in report["battery_shaping"]))

    def test_a_selecting_ranker_shapes_the_random_baseline_arm(self):
        # Given a deterministic ranker selecting the cohort
        request = request_with([{"type": "adxRange"}], ranker="FundamentalQuality", seed=42)

        # When / Then
        report = preflight.check(request, KNOWN_CONDITIONS, KNOWN_RANKERS)
        self.assertTrue(any("Random-baseline" in s for s in report["battery_shaping"]))

    def test_a_missing_seed_shapes_a_pinned_seed(self):
        # Given a ranker-selecting candidate with no randomSeed
        request = request_with([{"type": "adxRange"}], ranker="FundamentalQuality")

        # When / Then: tie-break jitter makes reruns non-reproducible
        report = preflight.check(request, KNOWN_CONDITIONS, KNOWN_RANKERS)
        self.assertTrue(any("randomSeed" in s for s in report["battery_shaping"]))


class PreflightAdvisories(unittest.TestCase):
    def test_an_inline_script_with_a_series_tail_read_is_flagged(self):
        # Given an inline script reading the tail of the quote series
        request = request_with([{"type": "script", "script": "stock.quotes.last().closePrice > 10"}])

        # When / Then: promotion reminder + lookahead smell, but never a blocker
        report = preflight.check(request, KNOWN_CONDITIONS, KNOWN_RANKERS)
        self.assertEqual([], report["blockers"])
        self.assertTrue(any("promotion" in a for a in report["advisories"]))
        self.assertTrue(any("lookahead" in a for a in report["advisories"]))

    def test_a_firewall_dead_config_gets_autopsy_framing_not_refusal(self):
        # Given a config whose hash is DEAD in the validation funnel
        request = request_with([{"type": "adxRange"}])

        # When / Then: the assessment proceeds, framed as an autopsy
        report = preflight.check(
            request, KNOWN_CONDITIONS, KNOWN_RANKERS, dead_hashes={"deadbeef"}, config_hash="deadbeef"
        )
        self.assertEqual([], report["blockers"])
        self.assertTrue(any("autopsy" in a for a in report["advisories"]))


if __name__ == "__main__":
    unittest.main()
