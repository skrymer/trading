#!/usr/bin/env python3
"""Apply the v4 gates to one layer's walk-forward result.

Per .claude/skills/validate-candidate/SKILL.md (refined framework 2026-05-28):
Block A (binding) + Block B (binding) + 25y aggregate (binding) + Block C (informational).

Gates G1-G9 are universal; G6/G7 are layer-specific; G4 falls back to G4a/G4b for N < 4 OOS windows.

G10 is enforced by the orchestrator (user confirmation), not here.
G11 (cross-block edge decay A→B) is computed in summarize.py, not here.
G12 is a per-layer aggregate trade count, handled here.

Block C is informational — gates are still evaluated and reported, but additionally a
non_catastrophic check is emitted (|edge| <= 0.5% AND DD <= 20%). summarize.py reads
this flag to decide PROVISIONAL vs TRADABLE, but Block C gate failures never trigger
REJECTED on their own.

Usage:
  eval-block.py <wf-result.json> --block {A,B,C,25y} [--label NAME] [--risk PCT]
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
        "binding": True,
    },
    "B": {
        "name": "Block B (2014-2021H1 incl COVID)",
        "regime_mandate_year": "2020",
        "regime_mandate_label": "2020 COVID",
        "chop_years": ["2015-H2", "2018-Q4"],
        "binding": True,
        # G6 splits into G6a/G6b for Block B only (issue #51). "2020 positive overall" can
        # mask a strategy that bled in the March crash and got rescued by the rally — the split
        # forces a separate read on crash survival vs regime re-entry. The Jan-Apr / May-Dec cut
        # is asymmetric and COVID-specific (not a calendar half-year); each half's edge is
        # recomputed from the per-window monthly entry-date buckets (ADR-0006).
        "regime_mandate_split": [
            {
                "name": "G6a_crash_survival",
                "label": "2020 crash survival (Jan-Apr entries)",
                "months": [f"2020-{m:02d}" for m in range(1, 5)],
                "min_edge": -0.5,
                "strict": False,
            },
            {
                "name": "G6b_recovery",
                "label": "2020 recovery (May-Dec entries)",
                "months": [f"2020-{m:02d}" for m in range(5, 13)],
                "min_edge": 0.0,
                "strict": True,
            },
        ],
    },
    "25y": {
        "name": "25-year aggregate (2000-2025)",
        "regime_mandate_year": "2008",
        "regime_mandate_label": "2008 GFC",
        "chop_years": ["2004", "2011", "2015-H1"],
        "binding": True,
    },
    "C": {
        "name": "Block C (2021-2025, informational)",
        "regime_mandate_year": "2022",
        "regime_mandate_label": "2022 inflation bear",
        "chop_years": None,
        "binding": False,
    },
}

def gate(name, passed, value, threshold, note=""):
    return {"name": name, "passed": passed, "value": value, "threshold": threshold, "note": note}

def edge_over_months(windows, months):
    """Recompute Edge over every `outOfSampleStatsByEntryMonth` bucket whose key is in `months`,
    summed across all windows. Edge is non-linear over subsets, so the additive raw fields
    (trades / winners / sumWinPercent / sumLossPercent) are summed and Edge recomputed once —
    matching TradeStatsSummary.edge / BacktestReport.edge. Returns None when no trade fell in
    those months (the sub-gate cannot be confirmed)."""
    month_set = set(months)
    trades = winners = 0
    sum_win = sum_loss = 0.0
    for w in windows:
        buckets = w.get("outOfSampleStatsByEntryMonth") or {}
        for key, b in buckets.items():
            if key in month_set:
                trades += b.get("trades", 0)
                winners += b.get("winners", 0)
                sum_win += b.get("sumWinPercent", 0.0)
                sum_loss += b.get("sumLossPercent", 0.0)
    if trades == 0:
        return None
    losers = trades - winners
    win_rate = winners / trades
    avg_win = (sum_win / winners) if winners else 0.0
    avg_loss = abs(sum_loss / losers) if losers else 0.0
    return win_rate * avg_win - (1.0 - win_rate) * avg_loss

def main():
    p = argparse.ArgumentParser()
    p.add_argument("path")
    p.add_argument("--block", required=True, choices=["A", "B", "C", "25y"])
    p.add_argument("--label", default="")
    p.add_argument("--risk", type=float, default=1.25)
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

    # G1: CAGR floor. Quant-verified at max(10%, SPY+2%, 30%) but 30% dominates
    # for every block — Block A 2000-2014 SPY CAGR ~4-5%, Block B 2014-2021 ~12-15%,
    # Block C 2021-2025 ~10-12%; SPY+2 < 30 in all cases. Hardcoded as 30 so
    # summarize.py's GATE_METADATA stays in sync without a runtime-flag desync risk.
    # Revisit if SPY CAGR ever exceeds 28% over a relevant block.
    #
    # When N<4 windows, G4b enforces the same CAGR check (block-aggregate >=
    # g1_floor). Emitting G1 too would double-count one underlying metric and
    # eat two of the ≤2 tight-margin slots in NEAR_MISS evaluation. So G1 is
    # skipped when G4b will fire.
    g1_floor = 30.0
    gates = []
    if n >= 4:
        gates.append(gate(
            "G1_cagr",
            agg_cagr is not None and agg_cagr >= g1_floor,
            agg_cagr,
            f">= {g1_floor:.2f}% (max of 10, SPY+2, 30) — 30 dominates",
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

    # G6: regime mandate for this block. Block B splits into G6a (crash survival) + G6b
    # (recovery) via per-window monthly buckets; other blocks use the single window-aggregate G6.
    split = block_cfg.get("regime_mandate_split")
    if split:
        for sub in split:
            sub_edge = edge_over_months(windows, sub["months"])
            if sub["strict"]:
                passed = sub_edge is not None and sub_edge > sub["min_edge"]
                threshold = f"{sub['label']} OOS edge > {sub['min_edge']:.1f}%"
            else:
                passed = sub_edge is not None and sub_edge >= sub["min_edge"]
                threshold = f"{sub['label']} OOS edge >= {sub['min_edge']:.1f}%"
            gates.append(gate(
                sub["name"],
                passed,
                f"{sub['label']} OOS edge = {sub_edge}",
                threshold,
            ))
    else:
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

    # G16: SPY buy-and-hold Calmar baseline (ADR 0013). Engine-computed verdict — the skill only
    # reads it, no Calmar comparison here. Binding on A/B/25y, informational on C. Only a FAIL
    # binds; INCONCLUSIVE (no-bind guardrail) and an absent benchmark (no SPY data) are non-fatal.
    spy = data.get("spyBaselineComparison") or {}
    spy_verdict = spy.get("verdict")
    if spy_verdict is None:
        g16_value = "no SPY benchmark available (non-fatal)"
    elif spy_verdict == "INCONCLUSIVE":
        g16_value = f"INCONCLUSIVE: {spy.get('inconclusiveReason')}"
    else:
        g16_value = (
            f"{spy_verdict} (strategy Calmar={spy.get('strategyCalmar')} "
            f"vs SPY Calmar={spy.get('benchmarkCalmar')})"
        )
    gates.append(gate(
        "G16_spy_baseline",
        spy_verdict != "FAIL",
        g16_value,
        "strategy stitched Calmar >= SPY (binding A/B/25y; informational C)",
    ))
    # Loud flag: an INCONCLUSIVE verdict on the 25-year aggregate should never happen on 25y of
    # data — too-short support or trivially-tiny maxDD signals something degenerate upstream.
    spy_baseline_inconclusive_aggregate = args.block == "25y" and spy_verdict == "INCONCLUSIVE"

    passed_count = sum(1 for g in gates if g["passed"])
    failed = [g for g in gates if not g["passed"]]
    overall = "PASS" if not failed else "FAIL"

    # Block C non-catastrophic check (informational only). summarize.py reads this
    # to decide PROVISIONAL vs TRADABLE; Block C gate failures DO NOT trigger REJECTED.
    # Threshold: |edge| <= 0.5% AND DD <= 20% (quant-verified 2026-05-28).
    non_catastrophic = None
    if args.block == "C":
        edge_ok = agg_edge is not None and abs(agg_edge) <= 0.5
        dd_ok = agg_dd is not None and agg_dd <= 20.0
        non_catastrophic = edge_ok and dd_ok

    summary = {
        "label": args.label or Path(args.path).stem,
        "block": args.block,
        "block_name": block_cfg["name"],
        "binding": block_cfg.get("binding", True),
        "overall": overall,
        "first_failure": failed[0]["name"] if failed else None,
        "passed_gates": passed_count,
        "failed_gates": len(failed),
        "total_gates": len(gates),
        "non_catastrophic": non_catastrophic,
        "spy_baseline_verdict": spy_verdict,
        "spy_baseline_inconclusive_aggregate": spy_baseline_inconclusive_aggregate,
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
    binding_note = "" if block_cfg.get("binding", True) else " (informational)"
    nc_note = ""
    if args.block == "C":
        nc_note = f" non_catastrophic={non_catastrophic}"
    print(
        f"[{summary['label']} Block {args.block}{binding_note}] {overall} | "
        f"edge={edge_str} sharpe={sharpe_str} cagr={cagr_str} dd={dd_str} | "
        f"{passed_count}/{len(gates)} gates pass | first_failure={summary['first_failure']}{nc_note}",
        file=sys.stderr,
    )

    # Block C is informational: exit 0 regardless of gate failures (overall is reported
    # but doesn't bind orchestration). Binding layers exit 1 on FAIL to halt run-pipeline.sh.
    if not block_cfg.get("binding", True):
        sys.exit(0)
    sys.exit(0 if overall == "PASS" else 1)

if __name__ == "__main__":
    main()
