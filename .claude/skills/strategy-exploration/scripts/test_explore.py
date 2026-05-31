#!/usr/bin/env python3
"""Tests for the explore CLI glue where its logic is load-bearing (ADR 0008).

Most of explore.py is thin glue over tested modules, but two behaviors guard the brake and are
regression-tested here: a record must pair against the OPEN fire (not a resolved one), and a DEAD
record must persist its neighbour closure so the cross-candidate brake can see it.

Run: python3 test_explore.py
"""
import json
import os
import shutil
import tempfile
import unittest

import dossier
import explore


def write_template(path, max_positions=15):
    with open(path, "w") as f:
        json.dump({
            "maxPositions": max_positions, "entryDelayDays": 1,
            "positionSizing": {"startingCapital": 10000,
                               "sizer": {"type": "atrRisk", "riskPercentage": 1.25, "nAtr": 2.0}},
            "entryStrategy": {"type": "custom", "conditions": [{"type": "pullback", "parameters": {"lookbackDays": 10}}]},
            "exitStrategy": {"type": "custom", "conditions": [{"type": "marketDowntrend"}]},
            "ranker": "SectorEdge",
        }, f)


def write_summarize(path, verdict):
    with open(path, "w") as f:
        json.dump({"verdict": verdict}, f)


class RecordPairsWithOpenFireOnly(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.dossier = os.path.join(self.dir, "MR4.jsonl")
        self.tmpl = os.path.join(self.dir, "mr4.json")
        self.summ = os.path.join(self.dir, "summ.json")
        write_template(self.tmpl)
        write_summarize(self.summ, "REJECTED")

    def tearDown(self):
        shutil.rmtree(self.dir)

    def test_record_without_open_fire_is_refused(self):
        # Given a fired-then-recorded layer (the fire is resolved)
        self.assertEqual(explore.main(["fire", self.dossier, "validate:BlockA", self.tmpl]), 0)
        self.assertEqual(explore.main(["record", self.dossier, self.summ, self.tmpl]), 0)

        # When a second record is attempted with nothing in flight
        rc = explore.main(["record", self.dossier, self.summ, self.tmpl])

        # Then it is refused — a record must bind to an OPEN fire, not a resolved one
        self.assertNotEqual(rc, 0)

    def test_dead_record_persists_neighbour_closure(self):
        # Given a fired layer that came back REJECTED
        explore.main(["fire", self.dossier, "validate:BlockA", self.tmpl])
        explore.main(["record", self.dossier, self.summ, self.tmpl])

        # When the dossier is read
        last = dossier.read_events(self.dossier)[-1]

        # Then the DEAD record carries a non-empty neighbour closure for the cross-candidate brake
        self.assertEqual(last["state"], "DEAD")
        self.assertTrue(last.get("neighbour_hashes"))


if __name__ == "__main__":
    unittest.main()
