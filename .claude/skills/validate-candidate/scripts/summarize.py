#!/usr/bin/env python3
"""Aggregate per-block evals into a final TRADABLE / PROVISIONAL / REJECTED verdict.

Reads up to 3 eval JSONs (Block A/B/C). If any block FAILed, overall verdict
is REJECTED. If all 3 pass, applies G11 (cross-block edge decay): edge_C
>= 0.5 * edge_A AND cagr_C >= 0.5 * cagr_A. Pass -> TRADABLE. Fail -> PROVISIONAL.

Emits both JSON (stdout) and a human-readable markdown report (stderr).

Per .claude/skills/validate-candidate/SKILL.md.

Usage:
  summarize.py <candidate> <eval-A.json> [eval-B.json] [eval-C.json]
"""
import argparse
import json
import sys
from datetime import datetime
from pathlib import Path

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

    if any_fail:
        verdict = "REJECTED"
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
        md_lines.append("All 3 blocks passed AND G11 cross-block decay check passed. Eligible for live deployment.")
        md_lines.append("Next step: `/monte-carlo` against the Block C result for path-risk quantification before final sizing.")
    elif verdict == "PROVISIONAL":
        md_lines.append("All 3 blocks passed but G11 cross-block decay failed (>50% drop in edge or CAGR from Block A to Block C).")
        md_lines.append("Strategy was likely fit to the early regime. **Paper-trade only.** Do NOT commit capital before diagnosing decay.")
    elif verdict == "INCONCLUSIVE_G11":
        md_lines.append("All 3 blocks passed gates, but G11 cross-block decay could not be evaluated.")
        md_lines.append(f"Reason: {g11['reason']}.")
        md_lines.append("**This is NOT a TRADABLE verdict.** Investigate the data anomaly before treating the candidate as ready for live deployment.")
    elif verdict == "REJECTED":
        fail_block = next((k for k, v in completed.items() if v["overall"] == "FAIL"), "?")
        md_lines.append(f"Failed Block {fail_block}. Candidate config is burned for this firewall run.")
        md_lines.append("Modifying this config and re-running is data-mining, NOT validation. Re-design the variant and re-enter via `/strategy-screen`.")
    else:
        md_lines.append("Pipeline incomplete (not all 3 blocks ran). Verdict pending.")

    print("\n".join(md_lines), file=sys.stderr)

if __name__ == "__main__":
    main()
