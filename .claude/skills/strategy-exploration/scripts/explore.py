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
  dsr-flag <dossier-dir> <candidate-summarize.json> <n-obs>   assemble the Deflated-Sharpe request
                                                    (POST /api/risk/deflated-sharpe) + lineage list
"""
import json
import sys

import config_hash
import dossier
import dsr_flag
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


def _trial_sharpe(trial):
    """The trial's stitched-OOS aggregate Sharpe — read from its recorded firewall artifact."""
    artifact = trial.get("artifact")
    if not artifact:
        return None
    try:
        layers = _load(artifact).get("layers", {})
    except (OSError, ValueError):
        return None
    agg = layers.get("25y") or {}
    return agg.get("aggregate_sharpe")


def _cmd_dsr_flag(args):
    """Assemble the Deflated-Sharpe request from the GLOBAL firewall-trial register (ADR 0014).

    Prints the search-agnostic engine payload + the always-published itemized lineage list. Claude
    POSTs the payload to /api/risk/deflated-sharpe, then appends a DSR_FLAG event to the candidate's
    dossier. Phase 1 ships the conservative N_high endpoint only (amber-or-clear). Per the engine
    contract, skew/kurtosis default to Gaussian (0, 3) — the firewall summary does not carry the
    return-shape moments; supply them only if known. n-obs = stitched-OOS trading-day count.
    """
    dossier_dir, summary_path, n_obs = args[0], args[1], int(args[2])
    trials = registry.collect_firewall_trials(dossier_dir)
    trial_sharpes = [s for s in (_trial_sharpe(t) for t in trials) if s is not None]
    observed = (_load(summary_path).get("layers", {}).get("25y") or {}).get("aggregate_sharpe")
    if observed is None:
        print("REFUSED: candidate summary has no 25y aggregate_sharpe to deflate.", file=sys.stderr)
        return 4
    request = dsr_flag.build_request(
        observed_sharpe_annualized=observed,
        n_eff=dsr_flag.n_high(trials),
        trial_sharpes_annualized=trial_sharpes,
        skew=0.0,
        kurtosis=3.0,
        n_obs=n_obs,
    )
    print(json.dumps({
        "request": request,
        "n_high": dsr_flag.n_high(trials),
        "trial_count": len(trials),
        "lineages": dsr_flag.lineage_clusters(trials),
        "amber_rule": "AMBER if deflatedSharpe < 0.95; N_low/red tier pending (phase 2)",
    }, indent=2))
    return 0


_COMMANDS = {"status": _cmd_status, "check": _cmd_check, "fire": _cmd_fire, "record": _cmd_record,
             "dsr-flag": _cmd_dsr_flag}


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
