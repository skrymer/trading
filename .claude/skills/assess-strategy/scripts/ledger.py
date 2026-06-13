#!/usr/bin/env python3
"""The assessment ledger — crash-safe append-only JSONL journal per candidate (ADR 0022).

One assessment directory per candidate under `strategy_exploration/assessments/<candidate>/`:
  - `<candidate>.request.json` — the exact validated request (ADR 0017 discipline)
  - `ledger.jsonl`             — append-only events (DRAFT, PREFLIGHT, FIRED, RUN_RECORDED,
                                 C_EYEBALLED, RATINGS, DECISION)
  - `assessment.md`            — the analyst's report (written by the assessment-analyst)

The ledger is the assessment funnel's machine record. It never adjudicates: there is no verdict
event and no dead state — the only terminal event is the operator's DECISION. Two couplings bind
it to the platform's statistical accounting (ADR 0022): every assessed config is a firewall trial
(collect_assessment_trials feeds the deflated-Sharpe N alongside the dossier lineages), and the
C_EYEBALLED annotation is permanent for the candidate's family.
"""
import json
import os

LEDGER_FILENAME = "ledger.jsonl"


def assessment_dir(assessments_root, candidate):
    return os.path.join(assessments_root, candidate)


def ledger_path(assessments_root, candidate):
    return os.path.join(assessment_dir(assessments_root, candidate), LEDGER_FILENAME)


def append(path, event):
    """Append one event as a JSON line, creating the ledger (and its directory) if absent."""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "a") as f:
        f.write(json.dumps(event, separators=(",", ":")) + "\n")


def read_events(path):
    """Every well-formed event in append order, tolerating a crash-truncated final line only."""
    events = []
    with open(path) as f:
        lines = f.readlines()
    for i, line in enumerate(lines):
        line = line.strip()
        if not line:
            continue
        try:
            events.append(json.loads(line))
        except json.JSONDecodeError:
            if i == len(lines) - 1:
                break
            raise
    return events


def record_draft(assessments_root, candidate, config_hash, request_json):
    """Open an assessment: persist the request JSON beside the ledger and record DRAFT."""
    directory = assessment_dir(assessments_root, candidate)
    os.makedirs(directory, exist_ok=True)
    with open(os.path.join(directory, f"{candidate}.request.json"), "w") as f:
        json.dump(request_json, f, indent=2)
    append(ledger_path(assessments_root, candidate), {"ev": "DRAFT", "candidate": candidate, "hash": config_hash})


def record_c_eyeballed(assessments_root, candidate, config_hash):
    """The permanent disclosure: the operator has seen per-window 2021-2025 numbers for this family."""
    append(
        ledger_path(assessments_root, candidate),
        {"ev": "C_EYEBALLED", "candidate": candidate, "hash": config_hash, "operator_eyeballed_c": True},
    )


def record_ratings(assessments_root, candidate, config_hash, ratings):
    """Record the assessment's descriptive applicability ratings (ADR 0025) — a separate event from the
    DECISION. `ratings` is a list of per-dimension blocks (`{"dimension", "label", evidence/caveat/...}`).
    Descriptive, never a verdict; the DECISION stays the only terminal event.
    """
    allowed = {"favourable", "neutral", "adverse", "unrateable"}
    for rating in ratings:
        label = rating.get("label")
        if label not in allowed:
            raise ValueError(f"rating label must be one of {sorted(allowed)}, got {label!r}")
    append(
        ledger_path(assessments_root, candidate),
        {"ev": "RATINGS", "candidate": candidate, "hash": config_hash, "ratings": ratings},
    )


def record_decision(assessments_root, candidate, decision, why, dimension=None):
    """The operator's post-report decision — the ledger's only terminal event kind. `dimension` (optional)
    names the deploy-targeting axis it targets (e.g. "broad", "regime:THRUST", "sector:XLK") so a
    `shelve`-broad + `redesign`-toward-a-regime-specialist intent is not collapsed (ADR 0025)."""
    allowed = {"redesign", "send-to-firewall", "paper-trade", "deploy-at-own-risk", "shelve"}
    if decision not in allowed:
        raise ValueError(f"decision must be one of {sorted(allowed)}, got {decision!r}")
    event = {"ev": "DECISION", "candidate": candidate, "decision": decision, "why": why}
    if dimension is not None:
        event["dimension"] = dimension
    append(ledger_path(assessments_root, candidate), event)


def collect_assessment_trials(assessments_root):
    """Every assessed config as a firewall trial: {"lineage", "hash"} per distinct hash per candidate.

    An assessment run observes binding-grade stitched-OOS Sharpes on the shared tape, so it counts
    toward the deflated-Sharpe N exactly like a firewall RECORD (ADR 0014: hidden N is the sin).
    One assessment directory = one lineage; re-assessing the same hash in the same lineage does not
    inflate the count. The caller unions these with registry.collect_firewall_trials output.
    """
    trials = []
    if not os.path.isdir(assessments_root):
        return trials
    for candidate in sorted(os.listdir(assessments_root)):
        path = ledger_path(assessments_root, candidate)
        if not os.path.isfile(path):
            continue
        seen = set()
        for event in read_events(path):
            config_hash = event.get("hash")
            if event.get("ev") == "DRAFT" and config_hash and config_hash not in seen:
                seen.add(config_hash)
                trials.append({"lineage": f"assessment:{candidate}", "hash": config_hash})
    return trials


def family_eyeballed_c(assessments_root, candidate):
    """True once any C_EYEBALLED event exists for the candidate — the annotation never resets."""
    path = ledger_path(assessments_root, candidate)
    if not os.path.isfile(path):
        return False
    return any(e.get("ev") == "C_EYEBALLED" for e in read_events(path))
