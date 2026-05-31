#!/usr/bin/env python3
"""config_hash — the candidate fingerprint that anchors the data-mining interlock (ADR 0008).

Projects a validation request onto the G10 design-isolation freeze set and hashes it. Dates are
excluded because they vary per firewall block by design, so one candidate keeps a single hash
across Block A / B / 25y. The freeze field set is identical to the one run-pipeline.sh's G10
confirmation prints.
"""
import hashlib
import json
import sys

# The G10 design-isolation freeze set (validate-candidate/scripts/run-pipeline.sh g10_confirmation).
FREEZE_FIELDS = (
    "entryStrategy",
    "exitStrategy",
    "ranker",
    "rankerConfig",
    "maxPositions",
    "entryDelayDays",
    "positionSizing",
    "randomSeed",
)


def freeze_projection(template):
    """Return only the freeze-set fields present in the template (dates and everything else dropped)."""
    return {k: template[k] for k in FREEZE_FIELDS if k in template}


def config_hash(template):
    """Stable short fingerprint of a candidate's freeze projection."""
    canonical = json.dumps(freeze_projection(template), sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode()).hexdigest()[:12]


def main(argv):
    """CLI: print the config_hash of a template JSON file. The single source of truth the
    run-pipeline.sh G10 autoconfirm shells out to, so the pipeline never re-implements the hash."""
    with open(argv[0]) as f:
        print(config_hash(json.load(f)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
