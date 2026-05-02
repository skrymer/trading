---
name: backtest
description: Run a single backtest against the Udgaard API, save raw results, and delegate post-hoc analysis (Sharpe / Sortino / drawdown duration / SPY correlation) to the post-backtest-analyst sub-agent. Use when the user asks to backtest a strategy, measure edge, or check whether a strategy is fit to trade.
argument-hint: "[strategy-name] [scenario]"
---

# Backtest a Trading Strategy

Answers one question: **"Would I have stuck with this strategy through its worst stretch?"**

Returns trade-by-trade performance with a verdict on profit, edge stability, drawdown depth + duration, and regime sensitivity. For walk-forward validation use `/walk-forward`. For path/edge confidence use `/monte-carlo`.

This skill is strategy-neutral. Substitute the user's actual strategy/exit/sizer/ranker in every example.

## Quick start

Smallest viable invocation — a quick edge check on a predefined strategy. POSTs go through `.claude/scripts/udgaard-post.sh`, which adds the `X-API-Key` header and fails loudly on non-2xx responses.

```bash
.claude/scripts/udgaard-post.sh /api/backtest '{
  "assetTypes": ["STOCK"],
  "entryStrategy": {"type": "predefined", "name": "<entry>"},
  "exitStrategy":  {"type": "predefined", "name": "<exit>"},
  "startDate": "<YYYY-MM-DD>",
  "endDate":   "<YYYY-MM-DD>"
}' /tmp/backtest-quick.json
```

Position-sizing, ranking, custom DSL, symbol subsets, and multi-seed runs are in [SCENARIOS.md](SCENARIOS.md). Output fields, report template, decision thresholds, and known limitations are in [REFERENCE.md](REFERENCE.md).

## Discovery

Run before assembling any request unless the user has named exact strategies. Discovery endpoints are GETs, so use raw `curl` (the post script is POST-only):

```bash
curl -s -H "X-API-Key: $API_KEY" http://localhost:9080/udgaard/api/backtest/strategies   # entry + exit names
curl -s -H "X-API-Key: $API_KEY" http://localhost:9080/udgaard/api/backtest/conditions   # custom DSL conditions + parameters
curl -s -H "X-API-Key: $API_KEY" http://localhost:9080/udgaard/api/backtest/rankers      # rankers (type/displayName/category/parameters/usesRandomTieBreaks)
```

## Scenarios

Pick the one that matches the user's ask, then assemble the request from [SCENARIOS.md](SCENARIOS.md):

| # | Scenario | Use when |
|---|---|---|
| 1 | Quick edge check (unlimited) | Sanity-check a freshly registered strategy or run an ablation |
| 2 | Position-sized realism | Live-trade go/no-go decision |
| 3 | Ranking | Position-sized run with > `maxPositions` signals on the same bar |
| 4 | Custom DSL strategy | Inline conditions instead of a predefined name |
| 5 | Targeted symbol subset | Debugging, watchlist, fast iteration |
| 6 | Multi-seed sanity check | Non-deterministic ranker with `usesRandomTieBreaks: true` |

## How to run

- **Always show the POST first and wait for explicit user approval before firing.** Backtests are expensive (10–15 min for position-sized 10y runs) and serialised — a wrong config wastes the user's time and blocks the next run. Output the full request body and stop. Wait for "go" / "fire it" / "approved" / equivalent. If the user amends the config, re-show the updated POST — don't fire it. The only exception is when the user explicitly says "fire it without showing me" in the same turn.
- **Endpoint:** `POST /api/backtest` on PRD port `9080` with `X-API-Key` header
- **One backtest at a time** — backend OOMs with concurrent backtests
- **Save raw response to `/tmp/backtest-<id>.json`** — analyst agent reads from disk
- **Position-sized 10y+ runs can take 10–15 minutes** — set timeout accordingly

After the request returns, capture `backtestId` from the response (used by `/monte-carlo`).

## Agent delegation

After the API call returns, spawn `post-backtest-analyst` with the path to the saved JSON. The backend pre-computes risk-adjusted metrics (Sharpe / Sortino / Calmar / SQN / tailRatio), CAGR, benchmark comparison vs SPY (correlation / beta / activeReturnVsBenchmark — NOT Jensen's alpha), and the top-10 drawdown episodes; the agent **interprets** these values, applies thresholds, and produces a verdict + next-step recommendation. The skill itself does API orchestration + raw report assembly; statistical interpretation is the agent's job.

## Critical warnings

- **Multiple-testing**: every parameter retried inflates false-positive rate. If the user has tried > 5 variations, push for `/walk-forward` before further tuning.
- **< 30 trades**: results are noise. Refuse to draw conclusions; widen the date range or universe.
- **Don't reject on win rate alone** — trend-following ~45% win rate / 3:1 W/L is normal; mean-reversion 65–80% with sub-1 W/L is normal. Judge by edge × stability.
