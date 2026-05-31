#!/usr/bin/env python3
"""Tests for the candidate dossier — the crash-safe append-only JSONL journal (ADR 0008).

The dossier is the system of record for a candidate's passage through the funnel. It is append-only
JSONL: one self-contained event per line, the last well-formed line being the authoritative state.
A mid-write crash truncates at most the final line; all prior events survive.

Run: python3 test_dossier.py
"""
import os
import tempfile
import unittest

import dossier


class DossierRoundTrip(unittest.TestCase):
    def setUp(self):
        fd, self.path = tempfile.mkstemp(suffix=".jsonl")
        os.close(fd)
        os.unlink(self.path)  # start with no file — append must create it

    def tearDown(self):
        if os.path.exists(self.path):
            os.unlink(self.path)

    def test_appended_events_read_back_in_order(self):
        # Given two events appended to a fresh dossier
        dossier.append(self.path, {"ev": "DRAFT", "hash": "a91f3c"})
        dossier.append(self.path, {"ev": "FIRED", "target": "validate:BlockA", "status": "PENDING"})

        # When the dossier is read
        events = dossier.read_events(self.path)

        # Then both events come back, in append order
        self.assertEqual([e["ev"] for e in events], ["DRAFT", "FIRED"])


class DossierSurvivesCrashTruncation(unittest.TestCase):
    def setUp(self):
        fd, self.path = tempfile.mkstemp(suffix=".jsonl")
        os.close(fd)

    def tearDown(self):
        os.unlink(self.path)

    def test_truncated_final_line_is_ignored_priors_intact(self):
        # Given a dossier whose last write was cut off mid-line by a crash
        dossier.append(self.path, {"ev": "DRAFT", "hash": "a91f3c"})
        dossier.append(self.path, {"ev": "RECORD", "verdict": "PASS"})
        with open(self.path, "a") as f:
            f.write('{"ev": "FIRED", "target": "validate:Bloc')  # no newline, partial JSON

        # When the dossier is read
        events = dossier.read_events(self.path)

        # Then the two complete events survive and the partial line is dropped
        self.assertEqual([e["ev"] for e in events], ["DRAFT", "RECORD"])


class DossierDetectsInFlightBacktest(unittest.TestCase):
    def setUp(self):
        fd, self.path = tempfile.mkstemp(suffix=".jsonl")
        os.close(fd)

    def tearDown(self):
        os.unlink(self.path)

    def test_fired_pending_without_record_is_in_flight(self):
        # Given a backtest was fired but never recorded (crash mid-run)
        dossier.append(self.path, {"ev": "FIRED", "target": "validate:BlockA", "status": "PENDING", "hash": "a91f3c"})

        # When the dossier is checked for in-flight work
        pending = dossier.pending_inflight(self.path)

        # Then the unresolved FIRED event is surfaced — resume must check for a backtestId
        self.assertIsNotNone(pending)
        self.assertEqual(pending["target"], "validate:BlockA")

    def test_fired_pending_resolved_by_record_is_not_in_flight(self):
        # Given a backtest was fired AND its result later recorded
        dossier.append(self.path, {"ev": "FIRED", "target": "validate:BlockA", "status": "PENDING", "hash": "a91f3c"})
        dossier.append(self.path, {"ev": "RECORD", "target": "validate:BlockA", "verdict": "PASS"})

        # When the dossier is checked for in-flight work
        # Then nothing is in flight
        self.assertIsNone(dossier.pending_inflight(self.path))


class DossierCurrentState(unittest.TestCase):
    def setUp(self):
        fd, self.path = tempfile.mkstemp(suffix=".jsonl")
        os.close(fd)

    def tearDown(self):
        os.unlink(self.path)

    def test_current_state_is_the_last_wellformed_event(self):
        # Given a dossier with several transitions
        dossier.append(self.path, {"ev": "DRAFT", "stage": "DRAFT"})
        dossier.append(self.path, {"ev": "RECORD", "stage": "SCREENED", "verdict": "PASS"})

        # When the current state is read
        state = dossier.current_state(self.path)

        # Then it is the last well-formed event
        self.assertEqual(state["stage"], "SCREENED")

    def test_current_state_of_empty_dossier_is_none(self):
        # Given a brand-new empty dossier, current state is None
        self.assertIsNone(dossier.current_state(self.path))


if __name__ == "__main__":
    unittest.main()
