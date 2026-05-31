#!/usr/bin/env python3
"""explore — thin CLI wiring the strategy-exploration mechanics (ADR 0008).

This is glue, not logic: every decision lives in the unit-tested modules (config_hash, dossier,
interlock, record, registry). The orchestrator skill itself (judgment, agent-spawning, choosing
the next leaf skill) is Claude-driven per SKILL.md — this CLI only performs the deterministic
dossier/interlock/hash mechanics and prints what it found. It never calls the backtest API.

Subcommands:
  status <dossier>                                  current state + any in-flight backtest
  check  <dossier-dir> <template.json>              interlock decision for a candidate
  fire   <dossier> <target> <template.json>         append a FIRED…PENDING event
  record <dossier> <summarize.json> <template.json> assert hash, append RECORD, print interlock state
"""
import json
import sys

import config_hash
import dossier
import interlock
import record
import registry


def _load(path):
    with open(path) as f:
        return json.load(f)


def _cmd_status(args):
    path = args[0]
    state = dossier.current_state(path)
    pending = dossier.pending_inflight(path)
    print(json.dumps({"current_state": state, "in_flight": pending}, indent=2))


def _cmd_check(args):
    dossier_dir, template_path = args
    template = _load(template_path)
    dead = registry.collect_dead_hashes(dossier_dir)
    dead_neighbours = registry.collect_dead_neighbour_hashes(dossier_dir)
    result = interlock.check(template, dead, dead_neighbours)
    result["candidate_hash"] = config_hash.config_hash(template)
    print(json.dumps(result, indent=2))
    return 0 if result["decision"] == "ADVANCE" else 1


def _cmd_fire(args):
    path, target, template_path = args
    template = _load(template_path)
    event = {"ev": "FIRED", "target": target, "status": "PENDING", "hash": config_hash.config_hash(template)}
    dossier.append(path, event)
    print(json.dumps(event))


def _cmd_record(args):
    path, summarize_path, template_path = args
    template = _load(template_path)
    summarize_result = _load(summarize_path)
    verdict = record.parse_verdict(summarize_result)

    # Bind the result to the OPEN fire (RECORD-aware — a resolved fire is not eligible). Recording
    # with nothing in flight is a methodology error, not a silent re-pair against a stale fire.
    fired = dossier.pending_inflight(path)
    if fired is None:
        print("REFUSED: no in-flight fire to record against — fire the step first.", file=sys.stderr)
        return 4
    # Assert the fired template matches the FIRED event we are resolving (printed X, ran Y guard).
    record.assert_hash_match(fired["hash"], template)

    state = record.verdict_state(verdict)
    event = {"ev": "RECORD", "target": fired["target"], "verdict": verdict, "state": state,
             "hash": config_hash.config_hash(template), "artifact": summarize_path}
    # Persist the dead config's neighbour closure so the cross-candidate interlock can refuse a
    # one-step nudge of it (the only direction that catches continuous-knob nudges).
    if state == "DEAD":
        event["neighbour_hashes"] = record.dead_neighbour_hashes(template)
    dossier.append(path, event)
    print(json.dumps(event))
    return 0


_COMMANDS = {"status": _cmd_status, "check": _cmd_check, "fire": _cmd_fire, "record": _cmd_record}


def main(argv):
    if not argv or argv[0] not in _COMMANDS:
        print(__doc__, file=sys.stderr)
        return 2
    try:
        return _COMMANDS[argv[0]](argv[1:]) or 0
    except record.ConfigMismatch as e:
        print(f"REFUSED: config mismatch — the fired template is not the one recorded as FIRED.\n  {e}",
              file=sys.stderr)
        return 3


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
