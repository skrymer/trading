#!/usr/bin/env python3
"""The dead-hash registry — the cross-dossier input to the interlock (ADR 0008).

"Dead" is global, not per-candidate: a config that reached REJECTED/NEAR_MISS in any candidate's
dossier must block a parameter-nudge successor in any other. The registry scans every dossier in
the directory and returns the set of config hashes whose recorded interlock state is DEAD.
"""
import glob
import os

import dossier


def collect_dead_hashes(dossier_dir):
    """Return the set of config hashes marked DEAD across all dossiers in the directory."""
    dead = set()
    for path in glob.glob(os.path.join(dossier_dir, "*.jsonl")):
        for event in dossier.read_events(path):
            if event.get("state") == "DEAD" and "hash" in event:
                dead.add(event["hash"])
    return dead


def collect_dead_neighbour_hashes(dossier_dir):
    """Return the union of every DEAD config's persisted single-step neighbour closure.

    A candidate whose own hash lands in this set is a one-step nudge of a corpse — the disguised
    re-run the interlock refuses (the direction that catches continuous-knob nudges).
    """
    closure = set()
    for path in glob.glob(os.path.join(dossier_dir, "*.jsonl")):
        for event in dossier.read_events(path):
            if event.get("state") == "DEAD":
                closure.update(event.get("neighbour_hashes", []))
    return closure
