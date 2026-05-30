#!/usr/bin/env python3
"""The interlock — the data-mining brake (ADR 0008).

A config_hash that reached REJECTED/NEAR_MISS is dead. The interlock refuses to advance a dead
hash or any single-step G13 neighbour of one (the configs reachable by one G13 perturbation —
±1 for discrete tunables, ×0.9/×1.1 for continuous — reusing g13_neighbors' classification).
There is no override; the only forward path is a redesigned candidate on a new lineage.
"""
import config_hash
import g13_neighbors


def check(request, dead_hashes, dead_neighbour_hashes=frozenset()):
    """Decide whether a candidate may advance.

    Returns {"decision": "ADVANCE"|"REFUSE", "reason": str, "matched_hash": str|None}.

    A candidate is refused if any of:
      - its own hash is dead;
      - its own hash is in a dead config's stored neighbour closure (catches the candidate that IS a
        one-step nudge of a dead config — the only direction that catches continuous ×0.9/×1.1 knobs,
        since those steps are not inverses);
      - any of its own single-step G13 neighbours hashes onto a dead config (catches the dead config
        that is a one-step nudge of the candidate — symmetric for discrete tunables).
    """
    own = config_hash.config_hash(request)
    if own in dead_hashes:
        return {"decision": "REFUSE", "reason": "dead_hash", "matched_hash": own}
    if own in dead_neighbour_hashes:
        return {"decision": "REFUSE", "reason": "dead_neighbour", "matched_hash": own}
    for neighbour in g13_neighbors.generate_neighbors(request):
        neighbour_hash = config_hash.config_hash(neighbour["request"])
        if neighbour_hash in dead_hashes:
            return {"decision": "REFUSE", "reason": "dead_neighbour", "matched_hash": neighbour_hash}
    return {"decision": "ADVANCE", "reason": None, "matched_hash": None}
