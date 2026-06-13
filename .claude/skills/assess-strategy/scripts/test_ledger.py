#!/usr/bin/env python3
"""Tests for the assessment ledger — the non-adjudicating funnel's machine record (ADR 0022).

Run: python3 test_ledger.py
"""
import json
import os
import shutil
import tempfile
import unittest

import ledger


class AssessmentLedger(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def test_draft_persists_the_request_beside_the_ledger(self):
        # Given a fresh candidate with a validated request
        request = {"entryStrategy": {"type": "custom"}, "maxPositions": 8}

        # When the assessment opens
        ledger.record_draft(self.root, "loki", "abc123", request)

        # Then the request JSON sits beside the ledger and DRAFT is the first event
        with open(os.path.join(self.root, "loki", "loki.request.json")) as f:
            self.assertEqual(request, json.load(f))
        events = ledger.read_events(ledger.ledger_path(self.root, "loki"))
        self.assertEqual([{"ev": "DRAFT", "candidate": "loki", "hash": "abc123"}], events)

    def test_assessed_configs_count_as_firewall_trials_once_per_hash(self):
        # Given two candidates, one of them drafted twice with the same hash
        ledger.record_draft(self.root, "loki", "abc123", {})
        ledger.record_draft(self.root, "loki", "abc123", {})
        ledger.record_draft(self.root, "sif", "def456", {})

        # When the trial register is collected
        trials = ledger.collect_assessment_trials(self.root)

        # Then each distinct hash counts once, clustered by its assessment lineage
        self.assertEqual(
            [
                {"lineage": "assessment:loki", "hash": "abc123"},
                {"lineage": "assessment:sif", "hash": "def456"},
            ],
            trials,
        )

    def test_the_c_annotation_is_permanent_for_the_family(self):
        # Given a candidate that has been assessed
        ledger.record_draft(self.root, "loki", "abc123", {})
        self.assertFalse(ledger.family_eyeballed_c(self.root, "loki"))

        # When the operator views the C-span numbers
        ledger.record_c_eyeballed(self.root, "loki", "abc123")

        # Then the annotation reads true from then on, regardless of later events
        ledger.record_decision(self.root, "loki", "redesign", "edge concentrated in THRUST")
        self.assertTrue(ledger.family_eyeballed_c(self.root, "loki"))

    def test_a_decision_outside_the_recorded_vocabulary_is_rejected(self):
        # Given an open assessment
        ledger.record_draft(self.root, "loki", "abc123", {})

        # When an off-vocabulary decision is recorded, Then it raises rather than polluting the ledger
        with self.assertRaises(ValueError):
            ledger.record_decision(self.root, "loki", "yolo", "feels good")


if __name__ == "__main__":
    unittest.main()
