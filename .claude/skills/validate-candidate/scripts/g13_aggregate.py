#!/usr/bin/env python3
"""G13 Parameter Robustness — verdict aggregation (advisory in v1).

Given the per-neighbor Block A + Block B pass/fail results for a TRADABLE center config,
compute the G13 outcome per the signed-off precedence (REFERENCE.md "G13 — Parameter Robustness"):

- all neighbors PASS                        -> TRADABLE  (unless a tunable is floor-pinned -> PROVISIONAL)
- exactly 1 neighbor fails, continuous near-miss, one-directional -> PROVISIONAL, but first
  resolve the ±2 carve-out (NEEDS_PM2_PROBE until the ±2 neighbor result is supplied)
- regime/binary-gate failure, non-near-miss continuous failure, or >=2 failing neighbors -> REJECTED

G13 is advisory: `binding` is always False until the calibration sweeps land. The orchestrator
reports the outcome but does not let it change the firewall verdict yet.
"""

# Continuous gates eligible for the PROVISIONAL near-miss escape (quant 2026-05-29). Every other
# gate failure (G2/G3 DD, G4 positivity, G6/G7 regime, G8/G12 counts) is non-eligible -> REJECTED.
# G4b is the N<4 block-aggregate CAGR fallback for G1 — same 30% floor, same near-miss band.
_NEAR_MISS_GATES = {"G1_cagr", "G4b_block_cagr", "G5_cov_edge", "G9_sharpe_calmar"}


def is_continuous_near_miss(gate):
    """Is this failing gate within G13's continuous near-miss band? (REFERENCE.md verdict table)

    G1/G4b CAGR (>= 30): within 10% relative -> value >= 27.
    G5 CoV (<= 1.5, lower better; a failure is ABOVE 1.5): within 0.15 absolute -> value <= 1.65.
    G9 Sharpe(>=0.8)+Calmar(>=0.5): within 10% relative -> sharpe >= 0.72 AND calmar >= 0.45.
    """
    name = gate.get("name")
    if name not in _NEAR_MISS_GATES:
        return False
    if name in ("G1_cagr", "G4b_block_cagr"):
        v = gate.get("value")
        return isinstance(v, (int, float)) and v >= 30.0 * 0.9
    if name == "G5_cov_edge":
        v = gate.get("value")
        return isinstance(v, (int, float)) and v <= 1.5 + 0.15
    if name == "G9_sharpe_calmar":
        sharpe = gate.get("sharpe")
        calmar = gate.get("calmar")
        return (isinstance(sharpe, (int, float)) and isinstance(calmar, (int, float))
                and sharpe >= 0.8 * 0.9 and calmar >= 0.5 * 0.9)
    return False


def neighbor_result_from_evals(meta, eval_a, eval_b):
    """Build a neighbor result record from its Block A + Block B eval-block JSONs.

    A neighbor PASSES iff both blocks' overall == PASS. Failing gates are collected across both
    blocks; G9's "sharpe=X calmar=Y" value string is parsed onto sharpe/calmar so the near-miss
    test can read them numerically.
    """
    passed = eval_a.get("overall") == "PASS" and eval_b.get("overall") == "PASS"
    failing = []
    for ev in (eval_a, eval_b):
        for g in ev.get("gates", []):
            if g.get("passed"):
                continue
            failing.append(_normalize_gate(g))
    return {
        "tunable": meta["tunable"],
        "name": meta["name"],
        "direction": meta["direction"],
        "step": meta.get("step", 1),
        "classification": meta["classification"],
        "floor_flag": meta.get("floor_flag", False),
        "passed": passed,
        "failing_gates": failing,
    }


def _normalize_gate(gate):
    out = {"name": gate.get("name"), "value": gate.get("value")}
    if gate.get("name") == "G9_sharpe_calmar":
        for token in str(gate.get("value", "")).split():
            if token.startswith("sharpe="):
                out["sharpe"] = _to_float(token.split("=", 1)[1])
            elif token.startswith("calmar="):
                out["calmar"] = _to_float(token.split("=", 1)[1])
    return out


def _to_float(s):
    try:
        return float(s)
    except (TypeError, ValueError):
        return None


def _is_near_miss_neighbor(neighbor):
    """A failing neighbor qualifies for PROVISIONAL iff EVERY failing gate is a continuous near-miss."""
    gates = neighbor.get("failing_gates") or []
    return bool(gates) and all(is_continuous_near_miss(g) for g in gates)


def g13_outcome(neighbor_results):
    """Compute the advisory G13 outcome. `neighbor_results` carries step-1 and (optionally) step-2 records."""
    step1 = [n for n in neighbor_results if n.get("step", 1) == 1]
    floor_tunables = sorted({n["tunable"] for n in step1 if n.get("floor_flag")})
    failing = [n for n in step1 if not n["passed"]]

    base = {
        "binding": False,
        "floor_flagged_tunables": floor_tunables,
        "failing_neighbors": [_describe(n) for n in failing],
        "pm2_probe": None,
        "reason": None,
    }

    if len(failing) >= 2:
        return {**base, "outcome": "REJECTED", "reason": "g13_parameter_fragile"}

    if len(failing) == 0:
        if floor_tunables:
            return {**base, "outcome": "PROVISIONAL", "reason": "g13_floor_pinned"}
        return {**base, "outcome": "TRADABLE"}

    # Exactly one failing neighbor.
    nb = failing[0]
    if not _is_near_miss_neighbor(nb):
        return {**base, "outcome": "REJECTED", "reason": "g13_parameter_fragile"}

    # One-directional check: the opposite ±1 neighbor on the same tunable must pass clean.
    opposite = [n for n in step1 if n["tunable"] == nb["tunable"] and n["direction"] != nb["direction"]]
    if not (opposite and all(o["passed"] for o in opposite)):
        return {**base, "outcome": "REJECTED", "reason": "g13_parameter_fragile"}

    # ±2 carve-out: resolve with a step-2 neighbor on the failing tunable+direction if present.
    pm2 = _find_pm2(neighbor_results, nb)
    if pm2 is None:
        return {**base, "outcome": "NEEDS_PM2_PROBE",
                "pm2_probe": {"tunable": nb["tunable"], "name": nb["name"],
                              "classification": nb["classification"], "direction": nb["direction"]}}
    if not pm2["passed"]:
        return {**base, "outcome": "REJECTED", "reason": "g13_parameter_fragile_pm2_cliff"}
    return {**base, "outcome": "PROVISIONAL", "reason": "g13_regime_sensitive_neighbor"}


def _find_pm2(neighbor_results, nb):
    for n in neighbor_results:
        if n.get("step") == 2 and n["tunable"] == nb["tunable"] and _same_side(n["direction"], nb["direction"]):
            return n
    return None


def _same_side(direction_pm2, direction_pm1):
    """Both increasing (+1/+2, x1.1/x1.2) or both decreasing (-1/-2, x0.9/x0.8)."""
    return _is_up(direction_pm2) == _is_up(direction_pm1)


def _is_up(direction):
    return direction.startswith("+") or direction.startswith("x1")


def _describe(n):
    return {"tunable": n["tunable"], "name": n["name"], "direction": n["direction"],
            "failing_gates": [g.get("name") for g in (n.get("failing_gates") or [])]}


def main():
    import argparse
    import json
    import sys
    from pathlib import Path

    p = argparse.ArgumentParser(description="Aggregate G13 neighbor results into an advisory outcome.")
    p.add_argument("neighbor_results", help="JSON file: list of neighbor result records")
    args = p.parse_args()

    results = json.loads(Path(args.neighbor_results).read_text())
    outcome = g13_outcome(results)
    print(json.dumps(outcome, indent=2, default=str))
    # NEEDS_PM2_PROBE exits 2 so the orchestrator knows to fire the ±2 neighbor and re-aggregate.
    sys.exit(2 if outcome["outcome"] == "NEEDS_PM2_PROBE" else 0)


if __name__ == "__main__":
    main()
