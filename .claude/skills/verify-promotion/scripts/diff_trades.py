#!/usr/bin/env python3
"""Diff two backtest trade lists for Implementation Invariance (G14).

Compares the inline-script research candidate's trade list against the promoted
first-class-condition version's trade list over the SAME universe + dates + capital
settings. The two must be the *same logical strategy*; only the condition
representation differs.

Per quant sign-off 2026-05-29:
- Match key is (entry_date, symbol). One trading decision = one (symbol, date) event
  under the daily timeframe (one position per symbol-date).
- Entry-set membership must be EXACT (Jaccard == 1.0). A single differing entry is a
  real population shift — the failure mode that flipped Idunn's 2020 COVID edge sign.
- Three divergence buckets off the match key:
    ENTRY — keys in one list but not the other (the population shift).
    EXIT  — matched key, different exit date.
    PNL   — matched key, profit beyond tolerance.
- Verdict is binary PASS / DIFFERS. No graded "DIFFERS-MINOR": an identity gate with a
  tolerance band on *which trades exist* is a contradiction (Idunn proved a ~2-trade
  shift flips a binding gate). ERROR (configs not the same logical comparison) is decided
  upstream by config_equivalence.py, not here.

P&L tolerance: the quant specified 0.1% of entry notional, but `sharesReserved` is
@JsonIgnore'd on the Trade DTO so notional isn't serialized. We realize the same intent
— tolerate float noise that changes no decision — as a relative tolerance on the profit
magnitude itself (profit scales with notional, so this is equivalent for the
harmless-noise purpose). Default 1e-3 relative; float noise is ~1e-9 relative, so 1e-3
is generous headroom while still catching a real threshold-boundary flip.

Usage:
  diff_trades.py <inline-trades.json> <promoted-trades.json>
      [--inline-label NAME] [--promoted-label NAME]
      [--inline-edge FLOAT] [--promoted-edge FLOAT]
      [--inline-cagr FLOAT] [--promoted-cagr FLOAT]
      [--pnl-tol FLOAT]

Exit code: 0 = PASS, 1 = DIFFERS, 2 = ERROR (unreadable/empty input).
"""
import argparse
import json
import sys
from pathlib import Path

PNL_TOL_DEFAULT = 1e-3


def trade_key(trade):
    """(entry_date, symbol) — the one-trading-decision identity under daily timeframe."""
    entry = trade.get("entryQuote") or {}
    return (entry.get("date"), trade.get("stockSymbol"))


def exit_date(trade):
    """Exit date is derived: the last quote's date (Trade has no stored exit quote).

    Matches Trade.tradingDays which spans entryQuote.date -> quotes.last().date.
    """
    quotes = trade.get("quotes") or []
    if quotes:
        return quotes[-1].get("date")
    return (trade.get("entryQuote") or {}).get("date")


def pnl_within_tol(a, b, tol):
    pa = a.get("profit")
    pb = b.get("profit")
    if pa is None or pb is None:
        return pa == pb
    return abs(pa - pb) <= tol * max(abs(pa), abs(pb), 1e-9)


def index_by_key(trades, label):
    """Map (entry_date, symbol) -> trade. Duplicate keys are a data anomaly under the
    one-position-per-symbol-date invariant; surface them rather than silently overwriting."""
    index = {}
    dups = []
    for t in trades:
        k = trade_key(t)
        if k in index:
            dups.append(k)
        index[k] = t
    return index, dups


def diff(inline_trades, promoted_trades, pnl_tol, labels, scalars):
    inline_idx, inline_dups = index_by_key(inline_trades, labels[0])
    promoted_idx, promoted_dups = index_by_key(promoted_trades, labels[1])

    inline_keys = set(inline_idx)
    promoted_keys = set(promoted_idx)
    inter = inline_keys & promoted_keys
    union = inline_keys | promoted_keys

    inline_only = sorted(inline_keys - promoted_keys)
    promoted_only = sorted(promoted_keys - inline_keys)
    jaccard = (len(inter) / len(union)) if union else 1.0

    exit_divergences = []
    pnl_divergences = []
    for k in sorted(inter):
        a = inline_idx[k]
        b = promoted_idx[k]
        if exit_date(a) != exit_date(b):
            exit_divergences.append({
                "symbol": k[1], "entry_date": k[0],
                "inline_exit": exit_date(a), "promoted_exit": exit_date(b),
            })
        elif not pnl_within_tol(a, b, pnl_tol):
            pnl_divergences.append({
                "symbol": k[1], "entry_date": k[0],
                "inline_profit": a.get("profit"), "promoted_profit": b.get("profit"),
            })

    passed = (
        jaccard == 1.0
        and not exit_divergences
        and not pnl_divergences
        and not inline_dups
        and not promoted_dups
    )
    outcome = "PASS" if passed else "DIFFERS"

    # Headline deltas — diagnostic only, NEVER a pass lever. The aggregate edge delta
    # and a per-direction-of-divergence count let the human see at a glance whether a
    # binding gate is at risk; the verdict is purely the identity check above.
    edge_delta = _delta(scalars.get("inline_edge"), scalars.get("promoted_edge"))
    cagr_delta = _delta(scalars.get("inline_cagr"), scalars.get("promoted_cagr"))

    return {
        "outcome": outcome,
        "match_key": "(entry_date, symbol)",
        "pnl_tolerance_relative": pnl_tol,
        "inline_label": labels[0],
        "promoted_label": labels[1],
        "inline_trade_count": len(inline_trades),
        "promoted_trade_count": len(promoted_trades),
        "trade_count_delta": len(promoted_trades) - len(inline_trades),
        "jaccard": jaccard,
        "matched_trades": len(inter),
        "entry_divergence_count": len(inline_only) + len(promoted_only),
        "inline_only": [{"symbol": s, "entry_date": d} for d, s in inline_only],
        "promoted_only": [{"symbol": s, "entry_date": d} for d, s in promoted_only],
        "exit_divergence_count": len(exit_divergences),
        "exit_divergences": exit_divergences,
        "pnl_divergence_count": len(pnl_divergences),
        "pnl_divergences": pnl_divergences,
        "duplicate_keys_inline": [{"symbol": s, "entry_date": d} for d, s in inline_dups],
        "duplicate_keys_promoted": [{"symbol": s, "entry_date": d} for d, s in promoted_dups],
        "aggregate_edge_inline": scalars.get("inline_edge"),
        "aggregate_edge_promoted": scalars.get("promoted_edge"),
        "aggregate_edge_delta": edge_delta,
        "aggregate_cagr_inline": scalars.get("inline_cagr"),
        "aggregate_cagr_promoted": scalars.get("promoted_cagr"),
        "aggregate_cagr_delta": cagr_delta,
        "first_divergent_trade": _first_divergent(inline_only, promoted_only,
                                                  exit_divergences, pnl_divergences),
    }


def _delta(a, b):
    if isinstance(a, (int, float)) and isinstance(b, (int, float)):
        return b - a
    return None


def _first_divergent(inline_only, promoted_only, exit_div, pnl_div):
    """The first divergent (symbol, date) — points the human at the culprit symbol so
    they can inspect its history-buffer / bar coverage at that date (the Idunn signature)
    without any static Kotlin parsing."""
    if inline_only:
        d, s = inline_only[0]
        return {"bucket": "ENTRY", "side": "inline_only", "symbol": s, "entry_date": d}
    if promoted_only:
        d, s = promoted_only[0]
        return {"bucket": "ENTRY", "side": "promoted_only", "symbol": s, "entry_date": d}
    if exit_div:
        e = exit_div[0]
        return {"bucket": "EXIT", "symbol": e["symbol"], "entry_date": e["entry_date"]}
    if pnl_div:
        p = pnl_div[0]
        return {"bucket": "PNL", "symbol": p["symbol"], "entry_date": p["entry_date"]}
    return None


def render_markdown(result):
    lines = []
    o = result["outcome"]
    lines.append(f"# Promotion Invariance (G14) — {o}")
    lines.append("")
    lines.append(f"- **Match key**: {result['match_key']}")
    lines.append(f"- **Inline**: `{result['inline_label']}` — {result['inline_trade_count']} trades")
    lines.append(f"- **Promoted**: `{result['promoted_label']}` — {result['promoted_trade_count']} trades")
    lines.append(f"- **Trade count delta**: {result['trade_count_delta']:+d}")
    lines.append(f"- **Entry-set Jaccard**: {result['jaccard']:.6f} (PASS requires 1.0)")
    lines.append(f"- **Matched trades**: {result['matched_trades']}")
    if result["aggregate_edge_delta"] is not None:
        lines.append(
            f"- **Aggregate edge**: {result['aggregate_edge_inline']} → "
            f"{result['aggregate_edge_promoted']} (Δ {result['aggregate_edge_delta']:+.4f}) — diagnostic only")
    lines.append("")
    lines.append("## Divergences")
    lines.append("")
    lines.append(f"- ENTRY (population shift): {result['entry_divergence_count']}")
    lines.append(f"- EXIT (matched key, different exit date): {result['exit_divergence_count']}")
    lines.append(f"- PNL (matched key, profit beyond tolerance): {result['pnl_divergence_count']}")
    if result["duplicate_keys_inline"] or result["duplicate_keys_promoted"]:
        lines.append(f"- DUPLICATE keys (data anomaly): "
                     f"inline={len(result['duplicate_keys_inline'])}, "
                     f"promoted={len(result['duplicate_keys_promoted'])}")
    fd = result.get("first_divergent_trade")
    if fd:
        lines.append("")
        lines.append(f"**First divergent trade** ({fd['bucket']}): "
                     f"`{fd['symbol']}` entered {fd['entry_date']} — "
                     f"inspect this symbol's bar coverage / history-buffer at that date.")
    if o == "PASS":
        lines.append("")
        lines.append("Inline-script verdict **transfers** to the promoted config — the trade "
                     "populations are identical. No re-validation required.")
    else:
        lines.append("")
        lines.append("Inline-script verdict is **VOID** — the promoted code produces a different "
                     "trade population than the firewall validated. The promoted config must run "
                     "the **full binding firewall independently** and meet TRADABLE on its own. "
                     "The inline result is discarded, never blended in.")
    return "\n".join(lines)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("inline_trades")
    p.add_argument("promoted_trades")
    p.add_argument("--inline-label", default="inline-script")
    p.add_argument("--promoted-label", default="promoted-condition")
    p.add_argument("--inline-edge", type=float, default=None)
    p.add_argument("--promoted-edge", type=float, default=None)
    p.add_argument("--inline-cagr", type=float, default=None)
    p.add_argument("--promoted-cagr", type=float, default=None)
    p.add_argument("--pnl-tol", type=float, default=PNL_TOL_DEFAULT)
    args = p.parse_args()

    try:
        inline_trades = json.loads(Path(args.inline_trades).read_text())
        promoted_trades = json.loads(Path(args.promoted_trades).read_text())
    except (OSError, json.JSONDecodeError) as e:
        print(json.dumps({"outcome": "ERROR", "reason": f"unreadable trades input: {e}"}))
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(2)

    if not isinstance(inline_trades, list) or not isinstance(promoted_trades, list):
        print(json.dumps({"outcome": "ERROR", "reason": "trades input is not a JSON array"}))
        sys.exit(2)
    if not inline_trades and not promoted_trades:
        print(json.dumps({"outcome": "ERROR", "reason": "both trade lists empty — nothing to compare"}))
        print("ERROR: both trade lists empty", file=sys.stderr)
        sys.exit(2)

    result = diff(
        inline_trades, promoted_trades, args.pnl_tol,
        (args.inline_label, args.promoted_label),
        {
            "inline_edge": args.inline_edge, "promoted_edge": args.promoted_edge,
            "inline_cagr": args.inline_cagr, "promoted_cagr": args.promoted_cagr,
        },
    )
    print(json.dumps(result, indent=2, default=str))
    print(render_markdown(result), file=sys.stderr)
    sys.exit(0 if result["outcome"] == "PASS" else 1)


if __name__ == "__main__":
    main()
