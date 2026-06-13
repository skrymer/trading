#!/usr/bin/env python3
"""Assessment pre-flight (Step 0 of /assess-strategy) — mechanical checks before the battery fires.

Contract (ADR 0022): the pre-flight blocks ONLY on methodological impossibility (the report would
be garbage or wasted compute), SHAPES the battery for known config classes, and ADVISES on
everything else. It never judges strategy quality — that is the firewall's job, and the assessment
funnel is non-adjudicating by design.

Pure module: callers pass the request JSON plus the discovery payloads and local state; no network.

Report shape:
  {"blockers": [..], "battery_shaping": [..], "advisories": [..]}
Exit semantics for the CLI: exit 1 when any blocker exists, else 0.
"""
import json
import re
import sys

# Condition types whose underlying data does not span the 25y assessment window. A 25y spine over
# a ~5y signal is silently empty for 20 years — the recurring funnel-disqualification tell
# (Ovtlyr/Forseti 2026-06-04). Extend as new limited-history signals are ingested.
LIMITED_HISTORY_CONDITION_PREFIXES = ("ovtlyr",)

# Regime-label gates may only be assessed once the read-out's anchor check accepted the classifier
# — PASS or ACCEPT_WITH_LIMITATIONS (ADR 0024: v2 is accepted with limitations, and gating is
# permitted on the gateable labels). Which labels are legal is enforced downstream by
# RegimeLabelCondition.GATEABLE_LABELS, not here; the pre-flight only confirms acceptance.
REGIME_CONDITION_TYPES = {"regimelabelin", "regimelabelexit"}
ACCEPTED_ANCHOR_VERDICTS = {"PASS", "ACCEPT_WITH_LIMITATIONS"}

# Inline-script shapes that historically smell of lookahead. A hit is an advisory, not proof —
# static scanning flags, /create-condition discipline + G14 + engine tests prove.
LOOKAHEAD_PATTERNS = [
    (re.compile(r"\.last\(\)"), "reads the series tail (`.last()`) — the final bar is the future on most days"),
    (re.compile(r"quotes\s*\[\s*\w+\s*\+"), "indexes quotes forward of the current bar"),
    (re.compile(r"subList\s*\(", re.IGNORECASE), "slices the quote series — verify the slice ends at the current bar"),
]


def condition_types(strategy_node):
    """Every condition type mentioned in an entry/exit strategy tree, lowercased, groups flattened."""
    types = []

    def walk(node):
        if not isinstance(node, dict):
            return
        node_type = node.get("type")
        if isinstance(node_type, str):
            types.append(node_type.lower())
        for key in ("conditions", "members", "exitConditions"):
            for child in node.get(key) or []:
                walk(child)

    walk(strategy_node or {})
    return types


def inline_scripts(strategy_node):
    """Every inline `script` body in the strategy tree."""
    scripts = []

    def walk(node):
        if not isinstance(node, dict):
            return
        script = node.get("script") or (node.get("parameters") or {}).get("script")
        if isinstance(script, str):
            scripts.append(script)
        for key in ("conditions", "members", "exitConditions"):
            for child in node.get(key) or []:
                walk(child)

    walk(strategy_node or {})
    return scripts


def check(request, known_conditions, known_rankers, anchor_status=None, dead_hashes=frozenset(), config_hash=None):
    """Run every pre-flight check. Pure: all platform state arrives as arguments.

    known_conditions / known_rankers: lowercase type identifiers from the discovery endpoints.
    anchor_status: the recorded anchor-check outcome dict ({"verdict": "PASS"|"FAIL", ...}) or None.
    dead_hashes: config hashes DEAD in the validation funnel (autopsy framing, never refusal).
    """
    blockers, shaping, advisories = [], [], []

    entry_types = condition_types(request.get("entryStrategy"))
    exit_types = condition_types(request.get("exitStrategy"))
    all_types = entry_types + exit_types

    # --- Blockers: the report would be garbage or wasted compute ---
    known = set(known_conditions)
    for t in all_types:
        if t in ("predefined", "custom", "script", "group", "and", "or", "not"):
            continue
        if t not in known:
            blockers.append(f"unknown condition type '{t}' — the engine would reject or ignore it")

    ranker = (request.get("ranker") or {}).get("type") if isinstance(request.get("ranker"), dict) else request.get("ranker")
    if ranker and str(ranker).lower() not in set(known_rankers):
        blockers.append(f"unknown ranker '{ranker}'")

    for t in all_types:
        if t.startswith(LIMITED_HISTORY_CONDITION_PREFIXES):
            blockers.append(
                f"'{t}' rests on a limited-history signal that does not span the 25y window — "
                "the spine would be silently empty for most of its range (span-disqualified)"
            )

    if any(t in REGIME_CONDITION_TYPES for t in all_types):
        verdict = (anchor_status or {}).get("verdict")
        if verdict not in ACCEPTED_ANCHOR_VERDICTS:
            blockers.append(
                "strategy gates on the regime read-out but the anchor acceptance check has not been "
                "accepted (PASS or ACCEPT_WITH_LIMITATIONS) — the classifier must be validated before "
                "anything consumes it (ADR 0023 first-compute gate / ADR 0024 acceptance)"
            )

    # --- Battery shaping: change what gets run, not whether ---
    if ranker:
        if str(ranker).lower() == "random":
            shaping.append("ranker is Random — single run is one draw; expand the spine to a multi-seed sweep")
        else:
            shaping.append(
                f"ranker '{ranker}' selects the cohort — if the entry is permissive, add the byte-identical "
                "Random-baseline arm (confirm permissiveness with the operator before skipping)"
            )
        if request.get("randomSeed") is None:
            shaping.append("no randomSeed pinned — tie-break jitter makes reruns non-reproducible; pin one")

    # --- Advisories: stamped into the report, never block ---
    scripts = inline_scripts(request.get("entryStrategy")) + inline_scripts(request.get("exitStrategy"))
    if scripts:
        advisories.append(
            "inline `script` condition(s) present — not tradable without promotion via /create-condition + G14, "
            "regardless of how the assessment reads"
        )
        for script in scripts:
            for pattern, smell in LOOKAHEAD_PATTERNS:
                if pattern.search(script):
                    advisories.append(f"lookahead smell in inline script: {smell} (flag, not proof)")

    if config_hash and config_hash in dead_hashes:
        advisories.append(
            "this config is DEAD in the validation funnel — this assessment is an autopsy; "
            "the report informs a redesign, never a re-run"
        )

    return {"blockers": blockers, "battery_shaping": shaping, "advisories": advisories}


def main():
    if len(sys.argv) < 4:
        print("usage: preflight.py <request.json> <conditions.json> <rankers.json> [anchor-status.json]", file=sys.stderr)
        sys.exit(2)
    with open(sys.argv[1]) as f:
        request = json.load(f)
    with open(sys.argv[2]) as f:
        conditions_payload = json.load(f)
    with open(sys.argv[3]) as f:
        rankers_payload = json.load(f)
    anchor_status = None
    if len(sys.argv) > 4:
        with open(sys.argv[4]) as f:
            anchor_status = json.load(f)

    known_conditions = [c.get("type", "").lower() for c in conditions_payload if isinstance(c, dict)]
    known_rankers = [r.get("type", r.get("name", "")).lower() for r in rankers_payload if isinstance(r, dict)]
    report = check(request, known_conditions, known_rankers, anchor_status)
    print(json.dumps(report, indent=2))
    sys.exit(1 if report["blockers"] else 0)


if __name__ == "__main__":
    main()
