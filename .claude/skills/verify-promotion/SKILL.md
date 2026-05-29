---
name: verify-promotion
description: Verify that a promoted first-class trading condition produces the same trade population as the inline-script research candidate it was promoted from (Implementation Invariance / G14). Diffs two backtests' trade lists by (entry_date, symbol) and emits PASS / DIFFERS / ERROR. Use after promoting an inline `script` condition to a named EntryCondition/ExitCondition via /create-condition, before reusing the inline-script firewall verdict, or when an existing condition's per-bar logic changed and you need to know which TRADABLE verdicts it puts at risk.
argument-hint: "[label] [inline-config.json] [promoted-config.json]"
---

# Verify Promotion — Implementation Invariance (G14)

A research candidate authored with inline `{"type":"script"}` conditions is fast to iterate but is not unit-tested, not lookahead-audited, and drifts silently. Before it can trade, each script is promoted to a named, version-controlled condition class (via `/create-condition`). **The promoted code must produce the same trades as the script the firewall validated.** This skill proves it — or proves it doesn't.

It exists because of Idunn (2026-05-29): the promoted `Pullback2of3Condition` used a 28-day history buffer where the inline script used 20. The 8 extra days fired on a few more thin-history symbols, shifting the trade population enough to flip the 2020 COVID OOS edge from +0.31% to −0.07% — across the binding G6 zero-threshold. A bit-identical trade-list check would have caught it instantly.

## Quick start

```bash
.claude/skills/verify-promotion/scripts/run-verify-promotion.sh \
    <label> <inline-script-config.json> <promoted-condition-config.json> [diff-start] [diff-end]
```

Default diff window is the full **25y (2000-01-01 → 2025-12-31)** — the union of all binding firewall layers, which maximizes the population-shift signal on thin-history symbols. Two single `/api/backtest` runs (~minutes each), far cheaper than a walk-forward.

## What it does

1. **ERROR precondition** — `config_equivalence.py` confirms the two configs are the *same logical strategy* modulo condition representation: identical `startDate`, `endDate`, `stockSymbols`, `assetTypes`, `includeSectors`, `excludeSectors`, `ranker`, `rankerConfig`, `maxPositions`, `entryDelayDays`, `positionSizing` (which carries `startingCapital`), and `randomSeed`. Anything else differing = **ERROR** (the diff is meaningless), hard-stop before any backtest fires.
2. **Two single backtests** over the diff window (restart-clean heap per run, sequential — the engine OOMs on concurrent backtests). Each returns a `backtestId`; the full trade list is pulled via `GET /api/backtest/{id}/trades`.
3. **Trade-list diff** — `diff_trades.py` keys every trade by `(entry_date, symbol)` and reports three divergence buckets (ENTRY / EXIT / PNL).

## Verdict (binary — no graded "minor")

| Outcome | Meaning | Action |
|---|---|---|
| **PASS** | entry-set Jaccard == 1.0 AND zero EXIT divergences AND zero PNL beyond tolerance | Inline verdict **transfers** to the promoted config. No re-validation needed. |
| **DIFFERS** | any entry-set difference, exit mismatch, or PNL beyond tolerance | Inline verdict **VOID**. Promoted config must run the **full binding firewall independently** and meet TRADABLE on its own. The inline result is discarded, never blended. |
| **ERROR** | configs aren't the same logical comparison | Methodology fault — fix configs, re-run. |

Match key and tolerances are quant-signed-off (2026-05-29): entry-set membership is EXACT (a single different entry is a real population shift); P&L on matched trades gets a 1e-3 relative tolerance for harmless float noise; exit dates match exactly. Rationale in [REFERENCE.md](REFERENCE.md).

## Relationship to /validate-candidate (G14)

`verify-promotion` is the practical surface for the firewall's **G14 gate**. When `/validate-candidate` runs a promoted candidate with `--inline <inline-config>`, it fires this check BEFORE Block A. G14 **does not auto-REJECT** on DIFFERS — it voids the reusable inline verdict and forces the promoted config through the full firewall, which the pipeline then does anyway. PASS lets the operator trust the prior inline verdict. See the validate-candidate REFERENCE.md "G14" section.

## How to run

- **Always show the plan + wait for explicit user approval before firing** (two 25y backtests ≈ 10-20 min total). Per project convention: never fire a backtest without a "go".
- **One backtest at a time** — sequential, restart-clean heap between runs.
- A `randomSeed` mismatch between the two configs false-DIFFERS on path noise — `config_equivalence.py` catches it as ERROR.

## Scope beyond promotion

G14 also applies when an **existing** first-class condition's per-bar `evaluate()` logic (or a history-buffer constant it depends on) changes — that invalidates the firewall verdict of every strategy referencing it. Run `verify-promotion` as `old-build-config vs new-build-config` over the same window. This is advisory until a condition-class → live-verdict registry exists; see [REFERENCE.md](REFERENCE.md#scope).
