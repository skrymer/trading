#!/usr/bin/env python3
"""ERROR precondition for G14 / verify-promotion: confirm two request configs are the
SAME logical strategy modulo condition representation.

Per quant sign-off 2026-05-29: ERROR is not a soft DIFFERS — it means the trade diff is
meaningless and must hard-stop before any backtest fires. A diff is only meaningful when
the two configs share everything that shapes the trade population EXCEPT how the entry/exit
conditions are expressed (inline `script` vs promoted first-class condition).

Fields that MUST match (mismatch => ERROR):
- startDate, endDate (the diff window)
- stockSymbols / symbol universe selection (plus assetTypes / includeSectors / excludeSectors,
  the other universe-selection inputs)
- ranker, rankerConfig
- maxPositions, entryDelayDays
- positionSizing (the entire sizer config — sizer type + every param + leverage cap +
  startingCapital, which lives nested inside this object, not at the top level)
- randomSeed (a single-path backtest with differing seeds false-DIFFERS on noise, not a
  code-identity problem)

Fields that are EXPECTED to differ (this is the whole point — not flagged):
- entryStrategy.conditions / exitStrategy.conditions condition TYPES and their params
  (one side uses {"type":"script"}, the other a promoted {"type":"<name>"})

Usage:
  config_equivalence.py <inline-config.json> <promoted-config.json>
Exit code: 0 = equivalent (diff is meaningful), 2 = ERROR (mismatch; reasons on stdout+stderr).
"""
import argparse
import json
import sys
from pathlib import Path

# Top-level request fields that must be byte-equal for the comparison to be meaningful.
# Names match the BacktestRequest DTO (e.g. `stockSymbols`, not `symbols`); `startingCapital`
# is intentionally absent because it lives nested inside `positionSizing`, which is guarded
# as a whole object below.
GUARDED_FIELDS = [
    "startDate",
    "endDate",
    "stockSymbols",
    "assetTypes",
    "includeSectors",
    "excludeSectors",
    "ranker",
    "rankerConfig",
    "maxPositions",
    "entryDelayDays",
    "positionSizing",
    "randomSeed",
]


def normalize(value):
    """Order-insensitive for the symbol list; identity otherwise. A reordered universe
    is the same universe, so we don't want to false-ERROR on list order."""
    if isinstance(value, list):
        try:
            return sorted(value, key=lambda x: json.dumps(x, sort_keys=True))
        except TypeError:
            return value
    return value


def check(inline_cfg, promoted_cfg):
    mismatches = []
    for field in GUARDED_FIELDS:
        a = normalize(inline_cfg.get(field))
        b = normalize(promoted_cfg.get(field))
        if a != b:
            mismatches.append({"field": field, "inline": inline_cfg.get(field),
                               "promoted": promoted_cfg.get(field)})

    # Both sides must actually have entry+exit strategies to compare condition output.
    for side, cfg in (("inline", inline_cfg), ("promoted", promoted_cfg)):
        if not cfg.get("entryStrategy"):
            mismatches.append({"field": f"{side}.entryStrategy", "reason": "missing"})

    return {
        "equivalent": not mismatches,
        "mismatches": mismatches,
        "guarded_fields": GUARDED_FIELDS,
    }


def main():
    p = argparse.ArgumentParser()
    p.add_argument("inline_config")
    p.add_argument("promoted_config")
    args = p.parse_args()

    try:
        inline_cfg = json.loads(Path(args.inline_config).read_text())
        promoted_cfg = json.loads(Path(args.promoted_config).read_text())
    except (OSError, json.JSONDecodeError) as e:
        print(json.dumps({"equivalent": False, "error": str(e)}))
        print(f"ERROR: unreadable config: {e}", file=sys.stderr)
        sys.exit(2)

    result = check(inline_cfg, promoted_cfg)
    print(json.dumps(result, indent=2, default=str))
    if result["equivalent"]:
        print("Config-equivalence OK — the two configs differ only in condition representation. "
              "Trade diff is meaningful.", file=sys.stderr)
        sys.exit(0)
    print("ERROR: configs are NOT the same logical strategy. Mismatched fields:", file=sys.stderr)
    for m in result["mismatches"]:
        print(f"  - {m.get('field')}: inline={m.get('inline')!r} promoted={m.get('promoted')!r}"
              f"{' (' + m['reason'] + ')' if m.get('reason') else ''}", file=sys.stderr)
    print("A trade diff between non-equivalent configs is meaningless. Fix the configs and re-run.",
          file=sys.stderr)
    sys.exit(2)


if __name__ == "__main__":
    main()
