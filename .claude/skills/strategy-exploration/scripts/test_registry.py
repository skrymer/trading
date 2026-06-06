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


class CollectFirewallTrialsForDsrFlag(unittest.TestCase):
    """The firewall-trial register feeding the Deflated-Sharpe flag (ADR 0014).

    A trial is a distinct config_hash that reached the firewall — a RECORD carrying a
    /validate-candidate verdict (any of TRADABLE/PROVISIONAL/NEAR_MISS/REJECTED/INCONCLUSIVE,
    incl. a rejected run). Counted GLOBALLY across every dossier (shared data = one experiment),
    tagged with its source file (the lineage = clustering boundary for N_eff).
    """

    def setUp(self):
        self.dir = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.dir)

    def test_firewall_records_collected_across_dossiers(self):
        # Given two candidate dossiers, each with one firewall RECORD (a rejected run still counts)
        a = os.path.join(self.dir, "MR4.jsonl")
        b = os.path.join(self.dir, "MR5.jsonl")
        dossier.append(a, {"ev": "RECORD", "target": "validate:final", "verdict": "REJECTED",
                           "state": "DEAD", "hash": "a91f3c", "artifact": "/tmp/a.json"})
        dossier.append(b, {"ev": "RECORD", "target": "validate:final", "verdict": "TRADABLE",
                           "state": "SETTLED", "hash": "ffffff", "artifact": "/tmp/b.json"})

        # When the firewall-trial register is collected across the directory
        trials = registry.collect_firewall_trials(self.dir)

        # Then both trials are present, each tagged with its lineage (source dossier file)
        by_hash = {t["hash"]: t for t in trials}
        self.assertEqual(set(by_hash), {"a91f3c", "ffffff"})
        self.assertEqual(by_hash["a91f3c"]["lineage"], "MR4")
        self.assertEqual(by_hash["ffffff"]["lineage"], "MR5")

    def test_fired_without_record_is_not_a_trial(self):
        # Given a dossier with an in-flight fire that never produced a RECORD (no Sharpe observed)
        a = os.path.join(self.dir, "MR4.jsonl")
        dossier.append(a, {"ev": "FIRED", "target": "validate:final", "status": "PENDING",
                           "hash": "a91f3c"})

        # When the firewall-trial register is collected
        trials = registry.collect_firewall_trials(self.dir)

        # Then the pending fire is excluded — a trial requires an observed firewall Sharpe
        self.assertEqual(trials, [])

    def test_screen_record_is_not_a_firewall_trial(self):
        # Given a dossier whose only RECORD is a strategy-screen result (PASS/FAIL, a cheaper metric)
        a = os.path.join(self.dir, "MR4.jsonl")
        dossier.append(a, {"ev": "RECORD", "target": "screen", "verdict": "PASS",
                           "state": "SETTLED", "hash": "a91f3c"})

        # When the firewall-trial register is collected
        trials = registry.collect_firewall_trials(self.dir)

        # Then the screen survivor is excluded — it competed on WFE/edge, not the firewall Sharpe
        self.assertEqual(trials, [])


if __name__ == "__main__":
    unittest.main()
