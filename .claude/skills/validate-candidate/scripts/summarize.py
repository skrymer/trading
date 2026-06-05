#!/usr/bin/env python3
"""Aggregate per-layer evals into a final verdict (refined framework 2026-05-28).

Binding layers: Block A v4, Block B v4, 25-year aggregate v4.
Informational layer: Block C (non-catastrophic check only; gate failures don't bind).

Verdict tiers (per /validate-candidate SKILL.md):
- TRADABLE          pass A + B + 25y, G11 (A→B) ok, Block C non-catastrophic
- PROVISIONAL       pass A + B + 25y but EITHER G11 (A→B) failed OR Block C catastrophic
- INCONCLUSIVE_G11  pass A + B + 25y but G11 not applicable (missing data or Block A non-positive)
- NEAR_MISS         binding-layer failure(s) BUT all failures within tight margin AND <= 2 tight failures total
- REJECTED          any other binding-layer failure

Block C failures alone NEVER trigger REJECTED — Block C is informational only.

Emits both JSON (stdout) and a human-readable markdown report (stderr).

Usage:
  summarize.py <candidate> <eval-A.json> <eval-B.json> <eval-25y.json> [eval-C.json] [--script-conditions N]
"""
import argparse
import json
import sys
from datetime import datetime
from pathlib import Path

# Quant-verified 2026-05-28. MUST stay in sync with eval-block.py's gates.
GATE_METADATA = {
    "G1_cagr":            {"threshold": 30.0,  "direction": "ge", "type": "percentage"},
    "G2_dd_aggregated":   {"threshold": 25.0,  "direction": "le", "type": "percentage"},
    "G3_dd_per_window":   {"threshold": 20.0,  "direction": "le", "type": "percentage"},
    "G4_positive_pct":    {"threshold": 75.0,  "direction": "ge", "type": "percentage"},
    "G4a_no_blowup":      {"threshold": -5.0,  "direction": "ge", "type": "percentage"},
    "G4b_block_cagr":     {"threshold": 30.0,  "direction": "ge", "type": "percentage"},
    "G5_cov_edge":        {"threshold": 1.5,   "direction": "le", "type": "ratio"},
    "G6_regime_mand":     {"threshold": 0.0,   "direction": "gt", "type": "strict"},
    # Block-B G6 split (issue #51). Both "strict": a regime-mandate sub-gate failure is
    # structural (crash survival / regime re-entry), never a tight-margin NEAR_MISS — same
    # treatment as the single G6.
    "G6a_crash_survival": {"threshold": -0.5,  "direction": "ge", "type": "strict"},
    "G6b_recovery":       {"threshold": 0.0,   "direction": "gt", "type": "strict"},
    "G7_regime_chop":     {"threshold": None,  "direction": None, "type": "strict"},
    "G8_min_trades":      {"threshold": 30,    "direction": "ge", "type": "count"},
    "G9_sharpe_calmar":   {"threshold": None,  "direction": None, "type": "ratio"},
    "G12_block_trades":   {"threshold": 100,   "direction": "ge", "type": "count_pct"},
    # G16 (ADR 0013): losing to buy-and-hold SPY on Calmar is structural beta-delivery, never a
    # tight-margin near-miss. INCONCLUSIVE doesn't fail the gate (eval-block marks it passed), so
    # only a FAIL reaches here — and a FAIL is always REJECTED, like the other strict gates.
    "G16_spy_baseline":   {"threshold": None,  "direction": None, "type": "strict"},
}

# Binding-layer labels. Block C is informational only — its failures don't trigger
# REJECTED and its tight margins don't count toward the NEAR_MISS cap.
BINDING_LAYERS = {"A", "B", "25y"}


def is_tight_margin(gate: dict) -> bool:
    """Is this failed gate close enough to its threshold to qualify for NEAR_MISS?"""
    if gate.get("passed"):
        return True
    meta = GATE_METADATA.get(gate["name"])
    if meta is None or meta["type"] == "strict":
        return False
    value = gate.get("value")
    threshold = meta["threshold"]
    direction = meta["direction"]
    if threshold is None or direction is None:
        return False
    if isinstance(value, str):
        try:
            value = float(value.rstrip("%").split("=")[-1].strip().rstrip("%"))
        except ValueError:
            return False
    if not isinstance(value, (int, float)):
        return False
    if meta["type"] == "percentage":
        if direction == "ge":
            return value >= threshold * 0.95
        return value <= threshold * 1.05
    if meta["type"] == "ratio":
        if direction == "ge":
            return value >= threshold * 0.80
        return value <= threshold * 1.20
    if meta["type"] == "count":
        if direction == "ge":
            return value >= threshold - 1
        return value <= threshold + 1
    if meta["type"] == "count_pct":
        if direction == "ge":
            return value >= threshold * 0.80
        return value <= threshold * 1.20
    return False


def derive_remediation_hint(failed_gates: list[dict]) -> str:
    """Map the failed-gate pattern to a recommended remediation axis."""
    if not failed_gates:
        return ""
    names = {g["name"] for g in failed_gates}
    if names & {"G6_regime_mand", "G6a_crash_survival", "G6b_recovery"}:
        return "regime_survival_redesign"
    if "G1_cagr" in names and not (names & {"G3_dd_per_window", "G4_positive_pct"}):
        return "tune_position_sizing"
    consistency_cluster = {"G3_dd_per_window", "G4_positive_pct", "G5_cov_edge"}
    if len(names & consistency_cluster) >= 2:
        return "add_regime_filter"
    if names & {"G2_dd_aggregated", "G3_dd_per_window"} and "G1_cagr" not in names:
        return "tighten_exit_or_reduce_positions"
    if names & {"G8_min_trades", "G12_block_trades"}:
        return "expand_universe_or_loosen_entry"
    return "review_failed_gates"


def load(path):
    return json.loads(Path(path).read_text()) if path and Path(path).exists() else None


def fmt_pct(v):
    return f"{v:.2f}%" if isinstance(v, (int, float)) else "n/a"


def fmt_num(v):
    return f"{v:.2f}" if isinstance(v, (int, float)) else "n/a"


def compute_g11(block_a, block_b):
    """Cross-block edge decay A→B. Pass if edge_B >= 0.5 × edge_A AND CAGR_B >= 0.5 × CAGR_A.

    Refined framework: G11 binds between Block A and Block B (the binding blocks),
    NOT A→C. Block C is informational so doesn't participate in decay binding.
    """
    g11 = {"applicable": False, "passed": None, "reason": None,
           "edge_decay": None, "cagr_decay": None}
    if not (block_a and block_b):
        g11["reason"] = "missing Block A or Block B eval"
        return g11
    edge_a = block_a.get("aggregate_edge")
    edge_b = block_b.get("aggregate_edge")
    cagr_a = block_a.get("aggregate_cagr")
    cagr_b = block_b.get("aggregate_cagr")
    missing = [k for k, v in {"edge_a": edge_a, "edge_b": edge_b,
                              "cagr_a": cagr_a, "cagr_b": cagr_b}.items()
               if v is None]
    if missing:
        g11["reason"] = f"missing data: {', '.join(missing)}"
        return g11
    if edge_a <= 0 or cagr_a <= 0:
        g11["reason"] = (f"Block A reference values non-positive "
                         f"(edge_a={edge_a}, cagr_a={cagr_a}) — "
                         f"data anomaly, shouldn't have passed Block A")
        return g11
    g11["applicable"] = True
    g11["edge_decay"] = (edge_a - edge_b) / edge_a
    g11["cagr_decay"] = (cagr_a - cagr_b) / cagr_a
    g11["passed"] = (edge_b >= 0.5 * edge_a) and (cagr_b >= 0.5 * cagr_a)
    g11["edge_a"] = edge_a
    g11["edge_b"] = edge_b
    g11["cagr_a"] = cagr_a
    g11["cagr_b"] = cagr_b
    return g11


def main():
    p = argparse.ArgumentParser()
    p.add_argument("candidate")
    p.add_argument("eval_a", help="Block A eval JSON (binding)")
    p.add_argument("eval_b", nargs="?", default=None, help="Block B eval JSON (binding)")
    p.add_argument("eval_25y", nargs="?", default=None, help="25y aggregate eval JSON (binding)")
    p.add_argument("eval_c", nargs="?", default=None, help="Block C eval JSON (informational)")
    p.add_argument("--script-conditions", type=int, default=0,
                   help="Count of inline-script conditions in the candidate request template")
    p.add_argument("--g13", default=None,
                   help="Optional G13 advisory outcome JSON (from run-g13.sh). Surfaced but never binds.")
    p.add_argument("--g14", default=None,
                   help="Optional G14 Implementation Invariance diff JSON (from /verify-promotion). "
                        "PASS = inline verdict transfers; DIFFERS = inline verdict void, promoted "
                        "config validated from scratch (this run). G14 never flips a binding-PASS to "
                        "REJECTED — it annotates which verdict the operator may trust.")
    args = p.parse_args()

    layers = {
        "A": load(args.eval_a),
        "B": load(args.eval_b),
        "25y": load(args.eval_25y),
        "C": load(args.eval_c),
    }
    if layers["A"] is None:
        print(f"ERROR: missing Block A eval at {args.eval_a}", file=sys.stderr)
        sys.exit(2)

    completed = {k: v for k, v in layers.items() if v is not None}
    binding_completed = {k: v for k, v in completed.items() if k in BINDING_LAYERS}
    binding_fail = any(v["overall"] == "FAIL" for v in binding_completed.values())

    g11 = compute_g11(layers["A"], layers["B"])

    # Block C non-catastrophic check (informational only).
    # The check itself was computed in eval-block.py and stored as `non_catastrophic`.
    block_c_non_catastrophic = None
    if layers["C"] is not None:
        block_c_non_catastrophic = layers["C"].get("non_catastrophic")

    # G16 loud flag: an INCONCLUSIVE SPY-baseline on the 25-year aggregate should never happen on
    # 25y of data (ADR 0013) — it signals degenerate stitched support, not a real no-bind. Surface
    # it regardless of the verdict so the analyst investigates before trusting the result.
    spy_baseline_inconclusive_aggregate = (
        layers["25y"] is not None
        and layers["25y"].get("spy_baseline_inconclusive_aggregate") is True
    )

    # Failed gates across BINDING layers only. Block C tight margins don't count
    # toward the NEAR_MISS cap because Block C is informational.
    failed_gates_binding = []
    for layer_label, layer in binding_completed.items():
        gates = layer.get("gates", [])
        if isinstance(gates, dict):
            gates = [{"name": k, **v} for k, v in gates.items() if isinstance(v, dict)]
        for g in gates:
            if not isinstance(g, dict):
                continue
            if not g.get("passed"):
                failed_gates_binding.append({**g, "layer": layer_label})

    near_miss_eligible = (
        binding_fail
        and len(failed_gates_binding) <= 2
        and all(is_tight_margin(g) for g in failed_gates_binding)
    )

    remediation_hint = derive_remediation_hint(failed_gates_binding)

    # G13 is advisory (calibration-pending) — surfaced but NEVER changes the verdict below.
    g13_advisory = load(args.g13) if args.g13 else None

    # G14 Implementation Invariance (promoted candidates only) — surfaced but NEVER flips a
    # binding-PASS to REJECTED. Per quant 2026-05-29: DIFFERS voids the *reusable inline verdict*
    # and mandates full promoted-config validation (which is exactly this run); it is not an
    # independent rejection reason. PASS records that the prior inline verdict transfers.
    g14 = load(args.g14) if args.g14 else None

    all_binding_complete = all(layers[k] is not None for k in BINDING_LAYERS)

    if binding_fail:
        verdict = "NEAR_MISS" if near_miss_eligible else "REJECTED"
    elif all_binding_complete:
        # All binding layers passed. Verdict depends on G11 + Block C non-catastrophic.
        if not g11["applicable"]:
            verdict = "INCONCLUSIVE_G11"
        elif not g11["passed"]:
            verdict = "PROVISIONAL"
        elif layers["C"] is not None and block_c_non_catastrophic is False:
            verdict = "PROVISIONAL"
        else:
            verdict = "TRADABLE"
    else:
        verdict = "INCOMPLETE"

    summary = {
        "candidate": args.candidate,
        "verdict": verdict,
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "framework": "refined-2026-05-28 (Block A + Block B + 25y binding; Block C informational)",
        "layers": {k: v for k, v in layers.items() if v is not None},
        "g11_cross_block_decay": g11,
        "block_c_non_catastrophic": block_c_non_catastrophic,
        "spy_baseline_inconclusive_aggregate": spy_baseline_inconclusive_aggregate,
        "failed_gates_count_binding": len(failed_gates_binding),
        "tight_margin_failures_binding": sum(1 for g in failed_gates_binding if is_tight_margin(g)),
        "remediation_hint": remediation_hint,
        "script_conditions_in_template": args.script_conditions,
        "g13_advisory": g13_advisory,
        "g14_implementation_invariance": g14,
    }
    print(json.dumps(summary, indent=2, default=str))

    md_lines = []
    md_lines.append(f"# Validation Report — {args.candidate}")
    md_lines.append("")
    md_lines.append(f"**Verdict: {verdict}**  ·  Generated {summary['generated_at']}")
    md_lines.append("")
    md_lines.append(f"_Framework: {summary['framework']}_")
    md_lines.append("")
    if spy_baseline_inconclusive_aggregate:
        md_lines.append(
            "> ⚠️ **G16 SPY-baseline INCONCLUSIVE on the 25-year aggregate.** Over 25 years the "
            "stitched OOS support should never be too short or its maxDD too tiny — this signals "
            "something degenerate upstream. Investigate before trusting the verdict (ADR 0013)."
        )
        md_lines.append("")
    md_lines.append("## Per-layer summary")
    md_lines.append("")
    md_lines.append("| Layer | Binding | Range | Verdict | First failure | CAGR | DD | Sharpe | Calmar | Trades |")
    md_lines.append("|---|---|---|---|---|---:|---:|---:|---:|---:|")
    for label in ["A", "B", "25y", "C"]:
        layer = layers[label]
        is_binding = label in BINDING_LAYERS
        binding_str = "yes" if is_binding else "**info**"
        if not layer:
            md_lines.append(f"| {label} | {binding_str} | — | NOT RUN | — | — | — | — | — | — |")
            continue
        md_lines.append(
            f"| {label} | {binding_str} | {layer.get('block_name','')} | {layer['overall']} | "
            f"{layer.get('first_failure') or '—'} | {fmt_pct(layer.get('aggregate_cagr'))} | "
            f"{fmt_pct(layer.get('aggregate_max_dd'))} | {fmt_num(layer.get('aggregate_sharpe'))} | "
            f"{fmt_num(layer.get('aggregate_calmar'))} | {layer.get('block_trades','n/a')} |"
        )

    # Block C non-catastrophic line (only meaningful when Block C ran)
    if layers["C"] is not None:
        md_lines.append("")
        md_lines.append("## Block C non-catastrophic check (informational)")
        md_lines.append("")
        if block_c_non_catastrophic is True:
            nc_status = "PASS"
        elif block_c_non_catastrophic is False:
            nc_status = "FAIL"
        else:
            nc_status = "n/a (legacy Block C eval; re-run with current eval-block.py to populate)"
        md_lines.append(f"- **{nc_status}** — `|edge| <= 0.5% AND DD <= 20%`")
        md_lines.append(f"- edge: {fmt_pct(layers['C'].get('aggregate_edge'))}")
        md_lines.append(f"- max DD: {fmt_pct(layers['C'].get('aggregate_max_dd'))}")

    # G11 only meaningful when binding layers pass
    if verdict in ("TRADABLE", "PROVISIONAL", "INCONCLUSIVE_G11"):
        md_lines.append("")
        md_lines.append("## G11 — cross-block edge decay (A→B)")
        md_lines.append("")
        if g11["applicable"]:
            md_lines.append(f"- edge A→B: {fmt_pct(g11['edge_a'])} → {fmt_pct(g11['edge_b'])} (decay {g11['edge_decay']*100:.1f}%)")
            md_lines.append(f"- CAGR A→B: {fmt_pct(g11['cagr_a'])} → {fmt_pct(g11['cagr_b'])} (decay {g11['cagr_decay']*100:.1f}%)")
            md_lines.append(f"- **G11 verdict**: {'PASS' if g11['passed'] else 'FAIL'} (edge_B >= 0.5 × edge_A AND cagr_B >= 0.5 × cagr_A)")
        else:
            md_lines.append(f"- **Could not evaluate G11**: {g11['reason']}")
        md_lines.append("")

    md_lines.append("## Per-layer gate detail")
    md_lines.append("")
    for label in ["A", "B", "25y", "C"]:
        layer = layers[label]
        if not layer:
            continue
        binding_note = "" if label in BINDING_LAYERS else " (informational only — failures don't bind)"
        md_lines.append(f"### Layer {label}{binding_note} — {layer.get('block_name','')}")
        md_lines.append("")
        md_lines.append("| Gate | Status | Value | Threshold |")
        md_lines.append("|---|---|---|---|")
        for g in layer.get("gates", []):
            status = "PASS" if g["passed"] else "FAIL"
            md_lines.append(f"| {g['name']} | {status} | {g.get('value')} | {g.get('threshold')} |")
        md_lines.append("")

    if g14 is not None:
        g14_outcome = g14.get("outcome")
        md_lines.append("## G14 — Implementation Invariance (promotion fidelity)")
        md_lines.append("")
        md_lines.append(f"- **Outcome: {g14_outcome}** — trade-list diff (promoted config vs inline-script config), match key `(entry_date, symbol)`")
        if g14_outcome == "PASS":
            md_lines.append("- The promoted config produces an **identical** trade population (Jaccard 1.0, no exit/PnL divergence). The prior inline-script firewall verdict **transfers** — the verdict above is confirmed on the shippable code.")
        elif g14_outcome == "DIFFERS":
            jac = g14.get("jaccard")
            md_lines.append(f"- The promoted config produces a **different** trade population (Jaccard {jac}, entry divergences {g14.get('entry_divergence_count')}, exit {g14.get('exit_divergence_count')}, PnL {g14.get('pnl_divergence_count')}).")
            md_lines.append("- **The inline-script verdict is VOID** — it described trades the shippable code does not produce. The verdict above is the PROMOTED config's OWN full-firewall result (validated from scratch this run); the inline result is discarded, never blended.")
            fd = g14.get("first_divergent_trade")
            if fd:
                md_lines.append(f"- First divergent trade ({fd.get('bucket')}): `{fd.get('symbol')}` entered {fd.get('entry_date')} — inspect that symbol's bar coverage / history-buffer at that date.")
        elif g14_outcome == "ERROR":
            md_lines.append("- **ERROR** — the two configs are not the same logical strategy; the diff is meaningless. (The pipeline should have halted upstream.)")
        md_lines.append("")

    if g13_advisory is not None:
        md_lines.append("## G13 — Parameter Robustness (advisory, calibration-pending)")
        md_lines.append("")
        md_lines.append(f"- **Advisory outcome: {g13_advisory.get('outcome')}** (reason: {g13_advisory.get('reason') or '—'})")
        md_lines.append("- This does NOT change the verdict above — G13 is calibration-pending. Treat as a yellow flag.")
        floor = g13_advisory.get("floor_flagged_tunables") or []
        if floor:
            md_lines.append(f"- Floor-flagged tunables (one-sided robustness only): {', '.join(floor)}")
        for nb in g13_advisory.get("failing_neighbors") or []:
            md_lines.append(f"- Fragile neighbor: `{nb.get('name')}` {nb.get('direction')} failed {', '.join(nb.get('failing_gates') or [])}")
        md_lines.append("")

    md_lines.append("## Verdict explanation")
    md_lines.append("")
    if verdict == "TRADABLE":
        if args.script_conditions > 0:
            md_lines.append(f"All 3 binding layers passed (Block A + Block B + 25y), G11 (A→B) ok, Block C non-catastrophic — BUT the candidate uses **{args.script_conditions} inline `script` condition(s)** in its entry/exit strategy.")
            md_lines.append("**Verdict is TRADABLE-PENDING-PROMOTION**, not final. Promote each inline script to a real named condition class via `/create-condition` (lookahead-audited + unit-tested), then re-enter the firewall from Block A with the promoted-condition request. The pre-promotion and post-promotion runs are NOT interchangeable.")
        else:
            md_lines.append("All 3 binding layers passed (Block A + Block B + 25y), G11 (A→B) ok, Block C non-catastrophic. Eligible for live deployment.")
            md_lines.append("Per quant 2026-05-28: paper-trade burn-in still recommended (track live edge vs the 25y per-window distribution before committing full capital). Next step: `/monte-carlo` against the 25y result for path-risk quantification.")
    elif verdict == "PROVISIONAL":
        reasons = []
        if g11["applicable"] and not g11["passed"]:
            reasons.append("G11 (A→B) edge decay > 50%")
        if layers["C"] is not None and block_c_non_catastrophic is False:
            reasons.append("Block C catastrophic (|edge| > 0.5% OR DD > 20%)")
        md_lines.append(f"All 3 binding layers passed but: {' AND '.join(reasons) if reasons else 'a binding-passing-but-degraded condition was triggered'}.")
        md_lines.append("**Paper-trade only.** Per quant 2026-05-28: 6 months of paper-trade-vs-25y-distribution monitoring. If live edge sits in the +0.30 to +0.80% band of the 25y per-window distribution, promote to live capital. If sub-zero, edge decay caught early.")
    elif verdict == "NEAR_MISS":
        md_lines.append(f"Failed {len(failed_gates_binding)} binding-layer gate(s) but all failures within tight margin (≤2 failures, no regime-mandate fail).")
        md_lines.append("**NEAR_MISS is NOT tradable.** Treat as 'one design iteration away' — not 'almost tradable'.")
        if remediation_hint:
            md_lines.append(f"Recommended remediation axis: **{remediation_hint}**.")
        md_lines.append("Re-enter via `/strategy-screen` with the proposed modification, then re-run /validate-candidate on the new config from Block A.")
    elif verdict == "INCONCLUSIVE_G11":
        md_lines.append("All 3 binding layers passed, but G11 cross-block decay could not be evaluated.")
        md_lines.append(f"Reason: {g11['reason']}.")
        md_lines.append("**This is NOT a TRADABLE verdict.** Investigate the data anomaly before treating the candidate as ready.")
    elif verdict == "REJECTED":
        fail_layer = next((k for k, v in binding_completed.items() if v["overall"] == "FAIL"), "?")
        md_lines.append(f"Failed binding layer {fail_layer}. Candidate config is burned for this firewall run.")
        n_failures = len(failed_gates_binding)
        n_tight = sum(1 for g in failed_gates_binding if is_tight_margin(g))
        if n_tight == n_failures and n_failures > 2:
            md_lines.append(f"All {n_failures} failed gates are within tight margin individually, but the count exceeds the NEAR_MISS cap (≤2). This is the 'multi-dimensional drift' pattern — multiple independent signals point at structural issues, not a single iteration miss.")
        if remediation_hint:
            md_lines.append(f"Indicated remediation axis: **{remediation_hint}** (informational; firewall does NOT pre-approve specific changes).")
        md_lines.append("Modifying this config and re-running is data-mining, NOT validation. Re-design the variant and re-enter via `/strategy-screen`.")
        if layers["C"] is not None and layers["C"]["overall"] == "FAIL":
            md_lines.append("")
            md_lines.append("_Note: Block C also failed gates, but Block C is informational only — the REJECTED verdict comes from the binding-layer failure above, not from Block C._")
    else:
        md_lines.append("Pipeline incomplete (not all 3 binding layers ran). Verdict pending.")

    print("\n".join(md_lines), file=sys.stderr)


if __name__ == "__main__":
    main()
