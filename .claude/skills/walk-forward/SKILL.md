---
name: walk-forward
description: Run walk-forward validation against the Udgaard API and delegate WFE interpretation + per-window stability analysis to the walk-forward-analyst sub-agent. Use to test whether a strategy's edge persists out-of-sample before live trading.
argument-hint: "[strategy-name] [cadence]"
---

# Walk-Forward Validation

Answers one question: **"Does this degrade smoothly out-of-sample, or is it regime-dependent?"**

Validates that strategy edge persists on unseen data and isn't curve-fit. Returns Walk-Forward Efficiency (WFE = aggregate OOS edge / aggregate IS edge) + per-window IS/OOS breakdown. For initial edge measurement use `/backtest`. For path/edge confidence use `/monte-carlo`.

This skill is strategy-neutral. Substitute the user's actual strategy/exit/dates in every example.

## Quick start

Smallest viable invocation — default 5y IS / 1y OOS / 1y step. POSTs go through `.claude/scripts/udgaard-post.sh`, which adds the `X-API-Key` header and fails loudly on non-2xx responses.

```bash
.claude/scripts/udgaard-post.sh /api/backtest/walk-forward '{
  "assetTypes": ["STOCK"],
  "entryStrategy": {"type": "predefined", "name": "<entry>"},
  "exitStrategy":  {"type": "predefined", "name": "<exit>"},
  "startDate": "<YYYY-MM-DD>",
  "endDate":   "<YYYY-MM-DD>",
  "inSampleYears": 5,
  "outOfSampleYears": 1,
  "stepYears": 1
}' /tmp/walk-forward-quick.json
```

Custom cadence, position-sizing, ranking, and multi-seed variants are in [SCENARIOS.md](SCENARIOS.md). Output fields, report template, decision thresholds, and known limitations are in [REFERENCE.md](REFERENCE.md).

## Discovery

Same endpoints as `/backtest`. Discovery is GET, so use raw `curl`:

```bash
curl -s -H "X-API-Key: $API_KEY" http://localhost:9080/udgaard/api/backtest/strategies
curl -s -H "X-API-Key: $API_KEY" http://localhost:9080/udgaard/api/backtest/conditions
curl -s -H "X-API-Key: $API_KEY" http://localhost:9080/udgaard/api/backtest/rankers
```

## Choosing a scenario

| Question being asked | Use |
|----------------------|-----|
| Do my entry/exit conditions still produce per-trade edge OOS? | §1 / §2 (unsized — every signal taken, max statistical power) |
| Does my full system (entry + exit + ranker + sizer) survive OOS? | §3 (position-sized) |
| Strategy uses an IS-tunable ranker (e.g. SectorEdge sector-priority)? | §3 — the ranker is the most overfittable part and must be stress-tested OOS |
| Reproducibility of a non-deterministic ranker? | §5 (multi-seed) on top of §1 or §3 |

Unsized walk-forward is faster and tighter on edge CIs. Sized is what you want before live trading. They answer different questions — pick deliberately.

## How to run

- **Always show the POST first and wait for explicit user approval before firing.** Walk-forward is N× more expensive than a single backtest — a wrong config burns 30+ min and blocks the engine. Output the full request body and stop. Wait for "go" / "fire it" / "approved" / equivalent. If the user amends the config, re-show the updated POST — don't fire it. The only exception is when the user explicitly says "fire it without showing me" in the same turn.
- **Endpoint:** `POST /api/backtest/walk-forward` on PRD port `9080` with `X-API-Key` header
- **One run at a time** — same OOM concern as `/backtest`
- **Save raw response to `/tmp/walk-forward[-suffix].json`** — analyst agent reads from disk
- **Wall time:** roughly `(windows × IS-backtest time) + (windows × OOS-backtest time)`. A 10y/5y-IS/1y-OOS run is ~10 backtests. Plan for 30+ minutes on broad universes.

## Agent delegation

After the API call returns, spawn `walk-forward-analyst` with the path to the saved JSON. The agent computes per-window WFE, OOS-positive window count, std-dev of OOS edge across windows, highlights worst/best windows + regime, flags overfitting (OOS << IS, or aggregate WFE < 0.30), and comments on `derivedSectorRanking` stability. The skill itself does API orchestration + per-window table assembly; statistical interpretation is the agent's job.

## Critical warnings

- **Walk-forward is the antidote to multiple-testing.** If the user has tried > 5 parameter variations on `/backtest`, walk-forward is mandatory before any live-trade decision.
- **Don't tune on OOS.** If walk-forward shows weakness and the user changes parameters, the new run is no longer truly out-of-sample for the same data — it's been peeked at. Acknowledge this in the report.
- **WFE alone is thin.** Always cross-reference with OOS-positive window count and OOS edge std-dev. Aggregate WFE 0.6 from "every window 0.6" vs "half 1.2, half 0.0" are completely different decisions.
