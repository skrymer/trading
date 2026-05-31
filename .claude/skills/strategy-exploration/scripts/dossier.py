#!/usr/bin/env python3
"""The candidate dossier — crash-safe append-only JSONL journal (ADR 0008).

One self-contained JSON event per line. The last well-formed line is the authoritative state.
A mid-write crash truncates at most the final line, so reads tolerate a trailing malformed line
rather than failing the whole dossier. Written immediately on every transition, never batched.
"""
import json


def append(path, event):
    """Append one event as a JSON line, creating the dossier if absent."""
    with open(path, "a") as f:
        f.write(json.dumps(event, separators=(",", ":")) + "\n")


def read_events(path):
    """Return every well-formed event in append order, skipping a crash-truncated final line."""
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
            # Only the final line may be a crash-truncated partial write; a malformed earlier
            # line is real corruption and must surface.
            if i == len(lines) - 1:
                break
            raise
    return events


def current_state(path):
    """The authoritative current state — the last well-formed event, or None for an empty dossier."""
    events = read_events(path)
    return events[-1] if events else None


def pending_inflight(path):
    """Return the unresolved FIRED…PENDING event (no later RECORD for its target), else None.

    This is the crash-during-backtest signal: on resume the operator must check for a completed
    backtestId before re-firing the same layer.
    """
    pending = {}
    for event in read_events(path):
        target = event.get("target")
        if target is None:
            continue
        if event.get("ev") == "FIRED" and event.get("status") == "PENDING":
            pending[target] = event
        elif event.get("ev") == "RECORD":
            pending.pop(target, None)
    if not pending:
        return None
    # Most recent unresolved fire.
    return list(pending.values())[-1]
