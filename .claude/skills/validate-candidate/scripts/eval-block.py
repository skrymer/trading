#!/usr/bin/env python3
"""Apply the v4 gates to one block's walk-forward result.

Per .claude/skills/validate-candidate/SKILL.md. Gates G1-G9 are universal;
G6/G7 are block-specific; G4 falls back to G4a/G4b for N < 4 OOS windows.

G10 is enforced by the orchestrator (user confirmation), not here.
G11 is computed across blocks in summarize.py, not here.
G12 is a per-block aggregate trade count, handled here.

Usage:
  eval-block.py <wf-result.json> --block {A,B,C} [--label NAME] [--risk PCT] [--spy-cagr PCT]
"""
import argparse
import json
import statistics
import sys
from pathlib import Path

BLOCK_CONFIG = {
    "A": {
        "name": "Block A (2000-2014)",
        "regime_mandate_year": "2008",
        "regime_mandate_label": "2008 GFC",
        "chop_years": ["2004", "2011", "2015-H1"],
    },
    "B": {
        "name": "Block B (2014-2020 incl COVID)",
        "regime_mandate_year": "2020",
        "regime_mandate_label": "2020 COVID",
        "chop_years": ["2015-H2", "2018-Q4"],
    },
    "C": {
        "name": "Block C (2021-2025)",
        "regime_mandate_year": "2022",
        "regime_mandate_label": "2022 inflation bear",
        "chop_years": None,  # skipped per quant
    },
}

def gate(name, passed, value, threshold, note=""):
    return {"name": name, "passed": passed, "value": value, "threshold": threshold, "note": note}

def main():
    p = argparse.ArgumentParser()
    p.add_argument("path")
    p.add_argument("--block", required=True, choices=["A", "B", "C"])
    p.add_argument("--label", default="")
    p.add_argument("--risk", type=float, default=1.25)
    p.add_argument("--spy-cagr", type=float, default=8.0, help="SPY CAGR for the block (for G1 SPY+2 check)")
    args = p.parse_args()

    data = json.loads(Path(args.path).read_text())
    block_cfg = BLOCK_CONFIG[args.block]
    windows = data.get("windows", [])
    n = len(windows)

    edges = [w.get("outOfSampleEdge") for w in windows if w.get("outOfSampleEdge") is not None]
    dds = [w.get("outOfSampleMaxDrawdownPct") for w in windows if w.get("outOfSampleMaxDrawdownPct") is not None]
    cagrs_w = [w.get("outOfSampleCagr") for w in windows if w.get("outOfSampleCagr") is not None]
    trades_w = [w.get("outOfSampleTrades") for w in windows if w.get("outOfSampleTrades") is not None]
    n_positive = sum(1 for e in edges if e > 0)
    median_dd = statistics.median(dds) if dds else 0.0
    median_edge = statistics.median(edges) if edges else 0.0
    block_trades = data.get("aggregateOosTrades") or sum(trades_w)

    agg_edge = data.get("aggregateOosEdge")
    agg_cagr = data.get("aggregateOosCagr")
    agg_dd = data.get("aggregateOosMaxDrawdownPct")
    rm = data.get("aggregateOosRiskMetrics") or {}
    agg_sharpe = rm.get("sharpeRatio")
    agg_calmar = rm.get("calmarRatio")

    # G1: CAGR floor (max of 10%, SPY+2%, 30%)
    g1_floor = max(10.0, args.spy_cagr + 2.0, 30.0)
    gates = []
    gates.append(gate(
        "G1_cagr",
        agg_cagr is not None and agg_cagr >= g1_floor,
        agg_cagr,
        f">= {g1_floor:.2f}% (max of 10, SPY+2={args.spy_cagr+2:.1f}, 30%)",
    ))
    gates.append(gate(
        "G2_dd_aggregated",
        agg_dd is not None and agg_dd <= 25.0,
        agg_dd,
        "<= 25%",
    ))
    worst_window_dd = max(dds) if dds else 0.0
    gates.append(gate(
        "G3_dd_per_window",
        worst_window_dd <= 20.0,
        worst_window_dd,
        "<= 20% in worst OOS window",
    ))

    # G4: > 75% positive on N >= 4, else G4a + G4b
    if n >= 4:
        pct = (n_positive / n * 100) if n else 0
        gates.append(gate(
            "G4_positive_pct",
            pct >= 75.0,
            f"{n_positive}/{n} = {pct:.1f}%",
            ">= 75% positive (N >= 4 rule)",
        ))
    else:
        worst_cagr = min(cagrs_w) if cagrs_w else 0.0
        gates.append(gate(
            "G4a_no_blowup",
            worst_cagr >= -5.0,
            f"worst window CAGR = {worst_cagr}",
            ">= -5% (N < 4 fallback)",
        ))
        gates.append(gate(
            "G4b_block_cagr",
            agg_cagr is not None and agg_cagr >= g1_floor,
            agg_cagr,
            f">= {g1_floor:.2f}% (block-aggregate; N < 4 fallback)",
        ))

    # G5: CoV of per-window edges
    if len(edges) >= 2 and statistics.mean(edges) != 0:
        cov = statistics.stdev(edges) / abs(statistics.mean(edges))
    else:
        cov = None
    gates.append(gate(
        "G5_cov_edge",
        cov is not None and cov <= 1.5,
        cov,
        "stdev/mean <= 1.5",
    ))

    # G6: regime mandate for this block
    mandate_year = block_cfg["regime_mandate_year"]
    mandate_window = next(
        (w for w in windows if (w.get("outOfSampleStart") or "")[:4] == mandate_year),
        None,
    )
    mandate_edge = mandate_window.get("outOfSampleEdge") if mandate_window else None
    gates.append(gate(
        "G6_regime_mand",
        mandate_edge is not None and mandate_edge > 0,
        f"{block_cfg['regime_mandate_label']} OOS edge = {mandate_edge}",
        f"{block_cfg['regime_mandate_label']} OOS > 0",
    ))

    # G7: chop regime (skip if block has none)
    if block_cfg["chop_years"]:
        chop_pass = False
        chop_results = []
        for chop_label in block_cfg["chop_years"]:
            # Match year prefix; H1/H2/Q4 use the year part for matching
            chop_year = chop_label[:4]
            chop_window = next(
                (w for w in windows if (w.get("outOfSampleStart") or "")[:4] == chop_year),
                None,
            )
            if chop_window:
                e = chop_window.get("outOfSampleEdge")
                chop_results.append(f"{chop_label}={e}")
                if e is not None and e > 0:
                    chop_pass = True
        gates.append(gate(
            "G7_regime_chop",
            chop_pass,
            "; ".join(chop_results) if chop_results else "no matching windows in block",
            f">= 1 of {{{', '.join(block_cfg['chop_years'])}}} positive",
        ))
    else:
        gates.append(gate(
            "G7_regime_chop",
            True,
            "skipped for this block",
            "block has no defined chop regime",
            note="SKIPPED",
        ))

    # G8: min trades per window
    min_trades = min(trades_w) if trades_w else 0
    gates.append(gate(
        "G8_min_trades",
        min_trades >= 30,
        min_trades,
        ">= 30 per OOS window",
    ))

    # G9: Sharpe + Calmar
    sharpe_ok = agg_sharpe is not None and agg_sharpe >= 0.8
    calmar_ok = agg_calmar is not None and agg_calmar >= 0.5
    gates.append(gate(
        "G9_sharpe_calmar",
        sharpe_ok and calmar_ok,
        f"sharpe={agg_sharpe} calmar={agg_calmar}",
        "Sharpe >= 0.8 AND Calmar >= 0.5",
    ))

    # G12: block-aggregate trade count
    gates.append(gate(
        "G12_block_trades",
        block_trades >= 100,
        block_trades,
        ">= 100 trades in block aggregate",
    ))

    passed_count = sum(1 for g in gates if g["passed"])
    failed = [g for g in gates if not g["passed"]]
    overall = "PASS" if not failed else "FAIL"

    summary = {
        "label": args.label or Path(args.path).stem,
        "block": args.block,
        "block_name": block_cfg["name"],
        "overall": overall,
        "first_failure": failed[0]["name"] if failed else None,
        "passed_gates": passed_count,
        "failed_gates": len(failed),
        "total_gates": len(gates),
        "aggregate_edge": agg_edge,
        "aggregate_cagr": agg_cagr,
        "aggregate_max_dd": agg_dd,
        "aggregate_sharpe": agg_sharpe,
        "aggregate_calmar": agg_calmar,
        "windows": n,
        "block_trades": block_trades,
        "gates": gates,
    }
    print(json.dumps(summary, indent=2, default=str))

    sharpe_str = f"{agg_sharpe:.2f}" if agg_sharpe is not None else "null"
    cagr_str = f"{agg_cagr:.2f}%" if agg_cagr is not None else "null"
    dd_str = f"{agg_dd:.2f}%" if agg_dd is not None else "null"
    edge_str = f"{agg_edge:.3f}%" if agg_edge is not None else "null"
    print(
        f"[{summary['label']} Block {args.block}] {overall} | "
        f"edge={edge_str} sharpe={sharpe_str} cagr={cagr_str} dd={dd_str} | "
        f"{passed_count}/{len(gates)} gates pass | first_failure={summary['first_failure']}",
        file=sys.stderr,
    )

    sys.exit(0 if overall == "PASS" else 1)

if __name__ == "__main__":
    main()
