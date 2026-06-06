#!/usr/bin/env python3
"""The dead-hash registry — the cross-dossier input to the interlock (ADR 0008).

"Dead" is global, not per-candidate: a config that reached REJECTED/NEAR_MISS in any candidate's
dossier must block a parameter-nudge successor in any other. The registry scans every dossier in
the directory and returns the set of config hashes whose recorded interlock state is DEAD.
"""
import glob
import os

import dossier

# Verdicts emitted only by /validate-candidate's summarize.py. A RECORD carrying one of these
# reached the firewall and produced a stitched-OOS Sharpe — the statistic the Deflated-Sharpe flag
# deflates. Screen RECORDs (PASS/FAIL) and FAULT verdicts (INCOMPLETE/ERROR — no Sharpe observed)
# are NOT trials. See ADR 0014 and CONTEXT "Firewall trial".
FIREWALL_VERDICTS = frozenset({"TRADABLE", "PROVISIONAL", "NEAR_MISS", "REJECTED", "INCONCLUSIVE_G11"})


def collect_firewall_trials(dossier_dir):
    """Return the global firewall-trial register for the Deflated-Sharpe flag (ADR 0014).

    A trial is a distinct config_hash whose RECORD carries a /validate-candidate verdict (any of
    FIREWALL_VERDICTS, incl. REJECTED — a rejected run still consumed a look at the binding Sharpe).
    FIRED-but-no-RECORD is excluded (no Sharpe observed); screen survivors are excluded (different,
    cheaper metric). Counted across EVERY dossier — shared data makes the whole search one
    multiple-testing experiment. Each trial is tagged with its `lineage` (source dossier basename),
    the clustering boundary for the effective trial count.
    """
    seen = set()
    trials = []
    for path in sorted(glob.glob(os.path.join(dossier_dir, "*.jsonl"))):
        lineage = os.path.splitext(os.path.basename(path))[0]
        for event in dossier.read_events(path):
            if event.get("ev") != "RECORD" or event.get("verdict") not in FIREWALL_VERDICTS:
                continue
            h = event.get("hash")
            if h is None or h in seen:
                continue
            seen.add(h)
            trials.append({"lineage": lineage, "hash": h,
                           "verdict": event["verdict"], "artifact": event.get("artifact")})
    return trials


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
