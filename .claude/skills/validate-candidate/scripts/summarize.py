#!/usr/bin/env python3
"""Aggregate per-block evals into a final verdict.

Verdict tiers (per /validate-candidate SKILL.md):
- TRADABLE         pass A+B+C, G11 ok
- PROVISIONAL      pass A+B+C, G11 fail
- INCONCLUSIVE_G11 pass A+B+C, G11 not applicable
- NEAR_MISS        fail any block BUT all failures within tight margin AND <= 2 tight failures total
- REJECTED         any other failure

Emits both JSON (stdout) and a human-readable markdown report (stderr).

Usage:
  summarize.py <candidate> <eval-A.json> [eval-B.json] [eval-C.json]
"""
import argparse
import json
import sys
from datetime import datetime
from pathlib import Path

# Quant-verified 2026-05-28. MUST stay in sync with eval-block.py's gates.
# `direction`: "ge" = pass when value >= threshold; "le" = pass when value <= threshold.
# `type` drives the tight-margin band:
#   - percentage: failed if relative miss <= 5% (e.g. 28.5/30 fails by 5.0%, tight)
#   - ratio: failed if relative miss <= 20% (CoV / Sharpe etc.)
#   - count: failed if absolute miss <= 1 unit
#   - count_pct: trade count gates — failed if relative miss <= 20%
#   - strict: NEVER tight (regime mandates, G6)
GATE_METADATA = {
    "G1_cagr":            {"threshold": 30.0,  "direction": "ge", "type": "percentage"},
    "G2_dd_aggregated":   {"threshold": 25.0,  "direction": "le", "type": "percentage"},
    "G3_dd_per_window":   {"threshold": 20.0,  "direction": "le", "type": "percentage"},
    "G4_positive_pct":    {"threshold": 75.0,  "direction": "ge", "type": "percentage"},
    "G4a_no_blowup":      {"threshold": -5.0,  "direction": "ge", "type": "percentage"},
    "G4b_block_cagr":     {"threshold": 30.0,  "direction": "ge", "type": "percentage"},
    "G5_cov_edge":        {"threshold": 1.5,   "direction": "le", "type": "ratio"},
    "G6_regime_mand":     {"threshold": 0.0,   "direction": "gt", "type": "strict"},
    "G7_regime_chop":     {"threshold": None,  "direction": None, "type": "strict"},
    "G8_min_trades":      {"threshold": 30,    "direction": "ge", "type": "count"},
    "G9_sharpe_calmar":   {"threshold": None,  "direction": None, "type": "ratio"},
    "G12_block_trades":   {"threshold": 100,   "direction": "ge", "type": "count_pct"},
}

def is_tight_margin(gate: dict) -> bool:
    """Is this failed gate close enough to its threshold to qualify for NEAR_MISS?

    Returns False (never tight) for unknown gate names — safe default that keeps
    a stale metadata table from accidentally relaxing the firewall.
    """
    if gate.get("passed"):
        return True  # passed gates trivially satisfy "within margin"
    meta = GATE_METADATA.get(gate["name"])
    if meta is None or meta["type"] == "strict":
        return False
    value = gate.get("value")
    threshold = meta["threshold"]
    direction = meta["direction"]
    if threshold is None or direction is None:
        return False
    # G4_positive_pct's value can be a string "8/11 = 72.7%"; parse the trailing %.
    if isinstance(value, str):
        try:
            value = float(value.rstrip("%").split("=")[-1].strip().rstrip("%"))
        except ValueError:
            return False
    if not isinstance(value, (int, float)):
        return False
    if meta["type"] == "percentage":
        # Relative miss vs threshold; "ge" failure means value < threshold.
        if direction == "ge":
            return value >= threshold * 0.95
        else:  # "le"
            return value <= threshold * 1.05
    if meta["type"] == "ratio":
        if direction == "ge":
            return value >= threshold * 0.80
        else:  # "le"
            return value <= threshold * 1.20
    if meta["type"] == "count":
        # 1-unit miss allowed
        if direction == "ge":
            return value >= threshold - 1
        else:
            return value <= threshold + 1
    if meta["type"] == "count_pct":
        # Trade count gates use 20% relative band
        if direction == "ge":
            return value >= threshold * 0.80
        else:
            return value <= threshold * 1.20
    return False

def derive_remediation_hint(failed_gates: list[dict]) -> str:
    """Map the failed-gate pattern to a recommended remediation axis.

    Returns a short token the operator picks up — see SKILL.md for the hint
    catalogue. Empty string if no gates failed (or no hint applies).
    """
    if not failed_gates:
        return ""
    names = {g["name"] for g in failed_gates}
    # G6 is always structural — fundamental redesign needed
    if "G6_regime_mand" in names:
        return "regime_survival_redesign"
    # G1 alone or with consistency gates → sizer change
    if "G1_cagr" in names and not (names & {"G3_dd_per_window", "G4_positive_pct"}):
        return "tune_position_sizing"
    # G3 + G4 + G5 cluster (consistency-cluster pattern) → regime filter
    consistency_cluster = {"G3_dd_per_window", "G4_positive_pct", "G5_cov_edge"}
    if len(names & consistency_cluster) >= 2:
        return "add_regime_filter"
    # G2/G3 alone → exit-condition tightening
    if names & {"G2_dd_aggregated", "G3_dd_per_window"} and "G1_cagr" not in names:
        return "tighten_exit_or_reduce_positions"
    # G8/G12 (trade count) → universe expansion
    if names & {"G8_min_trades", "G12_block_trades"}:
        return "expand_universe_or_loosen_entry"
    return "review_failed_gates"

def load(path):
    return json.loads(Path(path).read_text()) if path and Path(path).exists() else None

def fmt_pct(v):
    return f"{v:.2f}%" if isinstance(v, (int, float)) else "n/a"

def fmt_num(v):
    return f"{v:.2f}" if isinstance(v, (int, float)) else "n/a"

def main():
    p = argparse.ArgumentParser()
    p.add_argument("candidate")
    p.add_argument("eval_a")
    p.add_argument("eval_b", nargs="?", default=None)
    p.add_argument("eval_c", nargs="?", default=None)
    p.add_argument("--script-conditions", type=int, default=0,
                   help="Count of inline-script conditions in the candidate request template")
    args = p.parse_args()

    blocks = {
        "A": load(args.eval_a),
        "B": load(args.eval_b),
        "C": load(args.eval_c),
    }
    if blocks["A"] is None:
        print(f"ERROR: missing Block A eval at {args.eval_a}", file=sys.stderr)
        sys.exit(2)

    completed = {k: v for k, v in blocks.items() if v is not None}
    any_fail = any(v["overall"] == "FAIL" for v in completed.values())

    g11 = {"applicable": False, "passed": None, "reason": None,
           "edge_decay": None, "cagr_decay": None}
    if not any_fail and blocks["A"] and blocks["C"]:
        edge_a = blocks["A"].get("aggregate_edge")
        edge_c = blocks["C"].get("aggregate_edge")
        cagr_a = blocks["A"].get("aggregate_cagr")
        cagr_c = blocks["C"].get("aggregate_cagr")
        missing = [k for k, v in {"edge_a": edge_a, "edge_c": edge_c,
                                  "cagr_a": cagr_a, "cagr_c": cagr_c}.items()
                   if v is None]
        if missing:
            g11["reason"] = f"missing data in Block A or C: {', '.join(missing)}"
        elif edge_a <= 0 or cagr_a <= 0:
            g11["reason"] = (f"Block A reference values non-positive "
                             f"(edge_a={edge_a}, cagr_a={cagr_a}) — "
                             f"shouldn't have passed Block A; treat as data anomaly")
        else:
            edge_decay = (edge_a - edge_c) / edge_a
            cagr_decay = (cagr_a - cagr_c) / cagr_a
            g11["applicable"] = True
            g11["passed"] = (edge_c >= 0.5 * edge_a) and (cagr_c >= 0.5 * cagr_a)
            g11["edge_decay"] = edge_decay
            g11["cagr_decay"] = cagr_decay
            g11["edge_a"] = edge_a
            g11["edge_c"] = edge_c
            g11["cagr_a"] = cagr_a
            g11["cagr_c"] = cagr_c

    # Collect every failed gate across all completed blocks (with block label
    # attached). Used for both NEAR_MISS detection and remediation_hint.
    # Legacy schema: older eval JSONs had `gates` as a dict keyed by name.
    # Normalise to the current list-of-dicts shape before iteration.
    failed_gates_all_blocks = []
    for block_label, block in completed.items():
        gates = block.get("gates", [])
        if isinstance(gates, dict):
            gates = [{"name": k, **v} for k, v in gates.items() if isinstance(v, dict)]
        for g in gates:
            if not isinstance(g, dict):
                continue
            if not g.get("passed"):
                failed_gates_all_blocks.append({**g, "block": block_label})

    # NEAR_MISS detection: every failed gate within tight margin AND <=2 total.
    # G6 strict (any G6 failure auto-disqualifies). Multi-gate cap is 2: 3+
    # tight failures = "multi-dimensional drift" = REJECTED per quant 2026-05-28.
    near_miss_eligible = (
        any_fail
        and len(failed_gates_all_blocks) <= 2
        and all(is_tight_margin(g) for g in failed_gates_all_blocks)
    )

    remediation_hint = derive_remediation_hint(failed_gates_all_blocks)

    if any_fail:
        verdict = "NEAR_MISS" if near_miss_eligible else "REJECTED"
    elif blocks["A"] and blocks["B"] and blocks["C"]:
        if g11["applicable"]:
            verdict = "TRADABLE" if g11["passed"] else "PROVISIONAL"
        else:
            # All 3 blocks passed but G11 couldn't be evaluated — distinct
            # from "G11 failed". Surface as INCONCLUSIVE so the user knows
            # the firewall is incomplete rather than negative.
            verdict = "INCONCLUSIVE_G11"
    else:
        verdict = "INCOMPLETE"

    summary = {
        "candidate": args.candidate,
        "verdict": verdict,
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "blocks": {k: v for k, v in blocks.items() if v is not None},
        "g11_cross_block_decay": g11,
        "failed_gates_count": len(failed_gates_all_blocks),
        "tight_margin_failures": sum(1 for g in failed_gates_all_blocks if is_tight_margin(g)),
        "remediation_hint": remediation_hint,
        "script_conditions_in_template": args.script_conditions,
    }
    print(json.dumps(summary, indent=2, default=str))

    md_lines = []
    md_lines.append(f"# Validation Report — {args.candidate}")
    md_lines.append("")
    md_lines.append(f"**Verdict: {verdict}**  ·  Generated {summary['generated_at']}")
    md_lines.append("")
    md_lines.append("## Per-block summary")
    md_lines.append("")
    md_lines.append("| Block | Range | Verdict | First failure | CAGR | DD | Sharpe | Calmar | Trades |")
    md_lines.append("|---|---|---|---|---:|---:|---:|---:|---:|")
    for label in ["A", "B", "C"]:
        b = blocks[label]
        if not b:
            md_lines.append(f"| {label} | — | NOT RUN | — | — | — | — | — | — |")
            continue
        md_lines.append(
            f"| {label} | {b.get('block_name','')} | {b['overall']} | "
            f"{b.get('first_failure') or '—'} | {fmt_pct(b.get('aggregate_cagr'))} | "
            f"{fmt_pct(b.get('aggregate_max_dd'))} | {fmt_num(b.get('aggregate_sharpe'))} | "
            f"{fmt_num(b.get('aggregate_calmar'))} | {b.get('block_trades','n/a')} |"
        )

    # G11 only meaningful when all 3 blocks pass; skip the section otherwise so
    # REJECTED/NEAR_MISS/INCOMPLETE reports don't show "Could not evaluate G11: None"
    # (cosmetic but reads like a real evaluation failure).
    if verdict in ("TRADABLE", "PROVISIONAL", "INCONCLUSIVE_G11"):
        md_lines.append("")
        md_lines.append("## G11 — cross-block edge decay")
        md_lines.append("")
        if g11["applicable"]:
            md_lines.append(f"- edge A→C: {fmt_pct(g11['edge_a'])} → {fmt_pct(g11['edge_c'])} (decay {g11['edge_decay']*100:.1f}%)")
            md_lines.append(f"- CAGR A→C: {fmt_pct(g11['cagr_a'])} → {fmt_pct(g11['cagr_c'])} (decay {g11['cagr_decay']*100:.1f}%)")
            md_lines.append(f"- **G11 verdict**: {'PASS' if g11['passed'] else 'FAIL'} (edge_C >= 0.5 x edge_A AND cagr_C >= 0.5 x cagr_A)")
        else:
            md_lines.append(f"- **Could not evaluate G11**: {g11['reason']}")
        md_lines.append("")

    md_lines.append("## Per-block gate detail")
    md_lines.append("")
    for label in ["A", "B", "C"]:
        b = blocks[label]
        if not b:
            continue
        md_lines.append(f"### Block {label} — {b.get('block_name','')}")
        md_lines.append("")
        md_lines.append("| Gate | Status | Value | Threshold |")
        md_lines.append("|---|---|---|---|")
        for g in b.get("gates", []):
            status = "PASS" if g["passed"] else "FAIL"
            md_lines.append(f"| {g['name']} | {status} | {g.get('value')} | {g.get('threshold')} |")
        md_lines.append("")

    md_lines.append("## Verdict explanation")
    md_lines.append("")
    if verdict == "TRADABLE":
        if args.script_conditions > 0:
            md_lines.append(f"All 3 blocks passed AND G11 cross-block decay check passed — BUT the candidate uses **{args.script_conditions} inline `script` condition(s)** in its entry/exit strategy.")
            md_lines.append("**Verdict is TRADABLE-PENDING-PROMOTION**, not final. Promote each inline script to a real named condition class via `/create-condition` (lookahead-audited + unit-tested), then re-enter the firewall from Block A with the promoted-condition request. The pre-promotion and post-promotion runs are NOT interchangeable.")
        else:
            md_lines.append("All 3 blocks passed AND G11 cross-block decay check passed. Eligible for live deployment.")
            md_lines.append("Next step: `/monte-carlo` against the Block C result for path-risk quantification before final sizing.")
    elif verdict == "PROVISIONAL":
        md_lines.append("All 3 blocks passed but G11 cross-block decay failed (>50% drop in edge or CAGR from Block A to Block C).")
        md_lines.append("Strategy was likely fit to the early regime. **Paper-trade only.** Do NOT commit capital before diagnosing decay.")
    elif verdict == "NEAR_MISS":
        md_lines.append(f"Failed {len(failed_gates_all_blocks)} gate(s) but all failures within tight margin (≤2 failures, no G6 fail).")
        md_lines.append("**NEAR_MISS is NOT tradable.** Treat as 'one design iteration away' — not 'almost tradable'.")
        if remediation_hint:
            md_lines.append(f"Recommended remediation axis: **{remediation_hint}**.")
        md_lines.append("Re-enter via `/strategy-screen` with the proposed modification, then re-run /validate-candidate on the new config from Block A.")
    elif verdict == "INCONCLUSIVE_G11":
        md_lines.append("All 3 blocks passed gates, but G11 cross-block decay could not be evaluated.")
        md_lines.append(f"Reason: {g11['reason']}.")
        md_lines.append("**This is NOT a TRADABLE verdict.** Investigate the data anomaly before treating the candidate as ready for live deployment.")
    elif verdict == "REJECTED":
        fail_block = next((k for k, v in completed.items() if v["overall"] == "FAIL"), "?")
        md_lines.append(f"Failed Block {fail_block}. Candidate config is burned for this firewall run.")
        n_failures = len(failed_gates_all_blocks)
        n_tight = sum(1 for g in failed_gates_all_blocks if is_tight_margin(g))
        if n_tight == n_failures and n_failures > 2:
            md_lines.append(f"All {n_failures} failed gates are within tight margin individually, but the count exceeds the NEAR_MISS cap (≤2). This is the 'multi-dimensional drift' pattern — multiple independent signals point at structural issues, not a single iteration miss.")
        if remediation_hint:
            md_lines.append(f"Indicated remediation axis: **{remediation_hint}** (informational; firewall does NOT pre-approve specific changes).")
        md_lines.append("Modifying this config and re-running is data-mining, NOT validation. Re-design the variant and re-enter via `/strategy-screen`.")
    else:
        md_lines.append("Pipeline incomplete (not all 3 blocks ran). Verdict pending.")

    print("\n".join(md_lines), file=sys.stderr)

if __name__ == "__main__":
    main()
