#!/usr/bin/env python3
"""G13 Parameter Robustness — neighbor generation.

Reads a walk-forward request JSON, discovers the in-scope numeric tunables, classifies each
discrete/continuous, and emits ±-step neighbor request JSONs with metadata. See REFERENCE.md
"G13 — Parameter Robustness" for the signed-off design (classification map is source of truth;
discrete ±1; continuous ×0.9/×1.1 rounded to nominal precision; fail-closed on no-op rounding;
floor-flag on a discrete tunable pinned at its natural floor).

Pure functions (generate_neighbors, classify, step_values) are unit-tested; main() is the CLI.
"""

# Explicit param-name classification map is the source of truth (quant 2026-05-29).
# Subtype (int→discrete, float→continuous) is only the fallback for an unrecognized name,
# and the fallback is flagged loudly so the map gets extended rather than silently relied on.
DISCRETE_NAMES = {"maxPositions", "entryDelayDays", "lookbackDays"}
CONTINUOUS_NAMES = {"riskPercentage", "nAtr", "atrMultiplier", "leverageRatio"}
# Suffix rules (checked after exact-name match).
DISCRETE_SUFFIXES = ("Days",)
CONTINUOUS_SUFFIXES = ("Pct", "Percentage", "Multiplier", "Fraction")


def classify(name, value):
    """Return (classification, subtype_fallback: bool).

    classification is "DISCRETE" or "CONTINUOUS". subtype_fallback is True when neither the
    name map nor the suffix rules matched and we fell back to JSON numeric subtype.
    """
    if name in DISCRETE_NAMES:
        return "DISCRETE", False
    if name in CONTINUOUS_NAMES:
        return "CONTINUOUS", False
    if name.endswith(DISCRETE_SUFFIXES):
        return "DISCRETE", False
    if name.endswith(CONTINUOUS_SUFFIXES):
        return "CONTINUOUS", False
    # Subtype fallback: bool is a subclass of int — exclude it (not a tunable anyway).
    if isinstance(value, bool):
        return "DISCRETE", True
    if isinstance(value, int):
        return "DISCRETE", True
    return "CONTINUOUS", True


def generate_neighbors(request):
    """Discover in-scope tunables and return a list of neighbor descriptors.

    Each descriptor carries the tunable path/name, classification, nominal, the perturbation
    direction, requested + fired values, floor/no-op flags, and the full neighbor request JSON
    (dates left untouched — the orchestrator overrides them per layer).
    """
    neighbors = []
    for path, name, value in _discover_tunables(request):
        classification, fallback = classify(name, value)
        for direction, requested, fired, floor_flag, no_op in _step_values(classification, value):
            neighbor_req = _clone_with(request, path, fired)
            neighbors.append({
                "tunable": path,
                "name": name,
                "classification": classification,
                "nominal": value,
                "direction": direction,
                "requested": requested,
                "fired": fired,
                "floor_flag": floor_flag,
                "no_op_widened": no_op,
                "subtype_fallback": fallback,
                "request": neighbor_req,
            })
    return neighbors


def pm2_neighbor(request, probe):
    """Build the ±2 carve-out neighbor for a step-1 failing tunable+direction.

    Discrete: nominal ± 2. Continuous: nominal × 0.8 / × 1.2 (one step beyond the ±1 ×0.9/×1.1).
    `probe` is the `pm2_probe` block emitted by g13_aggregate.g13_outcome.
    """
    path = probe["tunable"]
    nominal = _get_path(request, path)
    direction = probe["direction"]
    up = _is_up(direction)
    if probe["classification"] == "DISCRETE":
        fired = nominal + 2 if up else nominal - 2
        requested = fired
        label = "+2" if up else "-2"
    else:
        decimals = _decimals(nominal)
        requested = nominal * (1.2 if up else 0.8)
        fired = _round_half_up(requested, decimals)
        label = "x1.2" if up else "x0.8"
    return {
        "tunable": path,
        "name": probe["name"],
        "classification": probe["classification"],
        "nominal": nominal,
        "direction": label,
        "step": 2,
        "requested": requested,
        "fired": fired,
        "floor_flag": False,
        "no_op_widened": False,
        "subtype_fallback": False,
        "request": _clone_with(request, path, fired),
    }


def _is_up(direction):
    return direction.startswith("+") or direction.startswith("x1")


def _get_path(obj, path):
    cur = obj
    for p in _split_path(path):
        cur = cur[p]
    return cur


def _step_values(classification, value):
    """Yield (direction, requested, fired, floor_flag, no_op_widened) tuples for one tunable."""
    if classification == "DISCRETE":
        down = value - 1
        # Natural floor is 1 for these tunables (0 positions / 0-day lookback is invalid).
        if down >= 1:
            yield "-1", down, down, False, False
        else:
            # Floor-pinned: only +1 is testable; caps G13 at PROVISIONAL.
            yield "+1", value + 1, value + 1, True, False
            return
        yield "+1", value + 1, value + 1, False, False
        return
    # CONTINUOUS: ×0.9 / ×1.1 rounded to nominal precision; fail-closed if it rounds to center.
    decimals = _decimals(value)
    for factor, label in ((0.9, "x0.9"), (1.1, "x1.1")):
        requested = value * factor
        fired = _round_half_up(requested, decimals)
        no_op = False
        if fired == value:
            # Rounded back to center == not tested. Widen to the smallest representable step.
            step = 10 ** (-decimals)
            fired = _round_half_up(value - step if factor < 1 else value + step, decimals)
            no_op = True
        yield label, round(requested, decimals + 2), fired, False, no_op


def _discover_tunables(request):
    """Yield (json_path, name, value) for each in-scope numeric tunable.

    Scope: maxPositions, entryDelayDays, positionSizing.sizer.* numerics, leverageRatio, and
    every numeric under entry/exit conditions[].parameters. Categorical/structural fields and
    startingCapital are excluded.
    """
    for top in ("maxPositions", "entryDelayDays"):
        if isinstance(request.get(top), (int, float)) and not isinstance(request.get(top), bool):
            yield top, top, request[top]

    ps = request.get("positionSizing") or {}
    if isinstance(ps.get("leverageRatio"), (int, float)) and not isinstance(ps.get("leverageRatio"), bool):
        yield "positionSizing.leverageRatio", "leverageRatio", ps["leverageRatio"]
    sizer = ps.get("sizer") or {}
    for k, v in sizer.items():
        if k == "type":
            continue
        if isinstance(v, (int, float)) and not isinstance(v, bool):
            yield f"positionSizing.sizer.{k}", k, v

    for strat in ("entryStrategy", "exitStrategy"):
        conditions = (request.get(strat) or {}).get("conditions") or []
        for i, cond in enumerate(conditions):
            params = cond.get("parameters") or {}
            for k, v in params.items():
                if isinstance(v, (int, float)) and not isinstance(v, bool):
                    yield f"{strat}.conditions[{i}].parameters.{k}", k, v


def _clone_with(request, path, value):
    """Deep-copy request and set the leaf at `path` to `value`."""
    import copy
    out = copy.deepcopy(request)
    _set_path(out, path, value)
    return out


def _set_path(obj, path, value):
    cur = obj
    parts = _split_path(path)
    for p in parts[:-1]:
        cur = cur[p]
    cur[parts[-1]] = value


def _split_path(path):
    """Split a dotted/bracketed path into keys and int indices: a.b[0].c -> ['a','b',0,'c']."""
    parts = []
    for token in path.split("."):
        while "[" in token:
            name, _, rest = token.partition("[")
            if name:
                parts.append(name)
            idx, _, token = rest.partition("]")
            parts.append(int(idx))
        if token:
            parts.append(token)
    return parts


def _decimals(value):
    if isinstance(value, int):
        return 0
    s = repr(float(value))
    return len(s.split(".")[1]) if "." in s else 0


def _round_half_up(value, decimals):
    """Round ties away from zero to `decimals` places, preserving int-ness for 0 decimals."""
    from decimal import ROUND_HALF_UP, Decimal
    q = Decimal(10) ** -decimals
    r = Decimal(repr(value)).quantize(q, rounding=ROUND_HALF_UP)
    return int(r) if decimals == 0 else float(r)


def main():
    import argparse
    import json
    from pathlib import Path

    p = argparse.ArgumentParser()
    p.add_argument("request", help="Walk-forward request template JSON")
    p.add_argument("--out-dir", required=True, help="Directory to write neighbor request JSONs + manifest")
    p.add_argument("--candidate", default="candidate")
    args = p.parse_args()

    request = json.loads(Path(args.request).read_text())
    neighbors = generate_neighbors(request)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    manifest = []
    for idx, n in enumerate(neighbors):
        req_path = out_dir / f"g13-{args.candidate}-neighbor-{idx}.json"
        req_path.write_text(json.dumps(n["request"], indent=2))
        manifest.append({k: v for k, v in n.items() if k != "request"} | {"request_path": str(req_path)})

    (out_dir / f"g13-{args.candidate}-manifest.json").write_text(json.dumps(manifest, indent=2, default=str))
    print(json.dumps(manifest, indent=2, default=str))


if __name__ == "__main__":
    main()
