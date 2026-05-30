#!/usr/bin/env python3
"""Tests for the dead-hash registry — the cross-dossier brake input (ADR 0008).

The interlock refuses a candidate whose hash (or single-step neighbour) is dead. "Dead" spans every
candidate's dossier: a config REJECTED while exploring candidate X must also block a parameter-nudge
"new" candidate Y. The registry aggregates DEAD hashes across all dossiers in a directory.

Run: python3 test_registry.py
"""
import os
import shutil
import tempfile
import unittest

import dossier
import registry


class RegistryAggregatesDeadHashesAcrossDossiers(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.dir)

    def test_dead_hash_from_one_dossier_is_collected(self):
        # Given two candidate dossiers, one of which recorded a death
        a = os.path.join(self.dir, "MR4.jsonl")
        b = os.path.join(self.dir, "MR5.jsonl")
        dossier.append(a, {"ev": "RECORD", "verdict": "REJECTED", "state": "DEAD", "hash": "a91f3c"})
        dossier.append(b, {"ev": "RECORD", "verdict": "TRADABLE", "state": "SETTLED", "hash": "ffffff"})

        # When the registry collects dead hashes across the directory
        dead = registry.collect_dead_hashes(self.dir)

        # Then only the DEAD config's hash is present
        self.assertEqual(dead, {"a91f3c"})

    def test_dead_neighbour_closure_is_collected(self):
        # Given a death whose RECORD persisted its single-step neighbour closure
        a = os.path.join(self.dir, "MR4.jsonl")
        dossier.append(a, {"ev": "RECORD", "verdict": "REJECTED", "state": "DEAD",
                           "hash": "a91f3c", "neighbour_hashes": ["bbbb11", "cccc22"]})

        # When the registry collects the dead neighbour closure across the directory
        closure = registry.collect_dead_neighbour_hashes(self.dir)

        # Then the persisted neighbour hashes are present (a candidate landing on one is a nudge of a corpse)
        self.assertEqual(closure, {"bbbb11", "cccc22"})


if __name__ == "__main__":
    unittest.main()
