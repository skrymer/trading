#!/usr/bin/env python3
"""record — how a leaf-skill result enters the dossier (ADR 0008).

Parses the machine verdict token from a summarize.py result, maps it to an interlock state, and
asserts the fired config's hash matches the FIRED event's (catching 'printed X, ran tweaked Y').
Verdicts never enter the dossier by operator typing — only via a parsed artifact.
"""
import config_hash
import g13_neighbors

# Maps every /validate-candidate verdict token (summarize.py) to its interlock state. See ADR 0008
# "Verdict → interlock mapping". DEAD configs (and their single-step neighbours) are refused
# re-runs; SETTLED ones advance; BLOCKED is passed-but-unevaluable; FAULT is a re-runnable
# methodology/data error, not a verdict.
VERDICT_STATE = {
    "REJECTED": "DEAD",
    "NEAR_MISS": "DEAD",
    "TRADABLE": "SETTLED",
    "PROVISIONAL": "SETTLED",
    "INCONCLUSIVE_G11": "BLOCKED",
    "INCOMPLETE": "FAULT",
    "ERROR": "FAULT",
}


def verdict_state(verdict):
    """Map a verdict token to its interlock state (DEAD / SETTLED / BLOCKED / FAULT)."""
    return VERDICT_STATE[verdict]


def parse_verdict(summarize_result):
    """Extract the verdict token from a summarize.py result — read, never re-derive."""
    return summarize_result["verdict"]


def dead_neighbour_hashes(template):
    """Hashes of every single-step G13 neighbour of a (dead) config.

    Persisted at death so the interlock can refuse a candidate that IS a neighbour of a dead config,
    not only one whose neighbours include a dead config. The two directions differ for continuous
    tunables (×0.9/×1.1 are not inverses), so storing the dead config's closure is what closes the
    continuous-knob disguised-re-run hole.
    """
    return sorted({config_hash.config_hash(n["request"]) for n in g13_neighbors.generate_neighbors(template)})


class ConfigMismatch(Exception):
    """Raised when the template at record time does not hash to the FIRED event's config_hash.

    The signature of 'printed config X, ran tweaked config Y' — recording Y's result against X's
    slot would corrupt the dossier and let a dead config look alive.
    """


def assert_hash_match(fired_hash, template):
    """Assert the fired template still hashes to the FIRED event's config_hash, else ConfigMismatch."""
    actual = config_hash.config_hash(template)
    if actual != fired_hash:
        raise ConfigMismatch(f"fired hash {fired_hash} != template hash {actual}")
