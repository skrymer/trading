---
name: monte-carlo
description: Run Monte Carlo simulations against the Udgaard API on a saved backtest and delegate percentile / risk-of-ruin interpretation to the monte-carlo-analyst sub-agent. Use to quantify path risk and edge confidence before sizing up a strategy.
argument-hint: "[backtestId] [technique]"
---

# Monte Carlo Validation

Answers one question: **"What's the probability the live path is worse than I can stomach?"**

Quantifies path risk (max-drawdown distribution under random trade ordering) and edge confidence (return / win-rate / edge distribution under resampling). For initial edge measurement use `/backtest`. For OOS persistence use `/walk-forward`.

This skill is strategy-neutral. Substitute the user's actual `backtestId` / sizing in every example.

## Prerequisite

`/monte-carlo` requires an existing `backtestId` from a recent `/backtest` run. **The backend only retains the most recent backtest in memory** (see [REFERENCE.md → Known limitations](REFERENCE.md#known-limitations)) — run `/backtest` immediately before this skill.

## Quick start

Edge confidence on the most recent backtest. POSTs go through `.claude/scripts/udgaard-post.sh`, which adds the `X-API-Key` header and fails loudly on non-2xx responses.

```bash
.claude/scripts/udgaard-post.sh /api/monte-carlo/simulate '{
  "backtestId": "<BACKTEST_ID>",
  "technique": "BOOTSTRAP_RESAMPLING",
  "iterations": 10000
}' /tmp/mc-bootstrap.json
```

For path risk (trade shuffling), position-sized variants, and the typical both-back-to-back run see [SCENARIOS.md](SCENARIOS.md). Output fields, report template, decision thresholds, and known limitations are in [REFERENCE.md](REFERENCE.md).

## How to run

- **Always show the POST first and wait for explicit user approval before firing.** Output the full request body and stop. Wait for "go" / "fire it" / "approved" / equivalent. If the user amends the config (different technique, iterations, sizing, backtestId), re-show the updated POST — don't fire it. The only exception is when the user explicitly says "fire it without showing me" in the same turn.
- **Endpoint:** `POST /api/monte-carlo/simulate` on PRD port `9080` with `X-API-Key` header
- **Iterations:** 10,000 is the default sweet spot. Range allowed: 100 – 100,000. Below 1,000 percentile estimates are noisy; above 10,000 wall time grows without much precision gain.
- **Wall time:** 10–60 seconds for 10k iterations on a typical backtest. Grows linearly with iterations × trade count.
- **Run sequentially**, not concurrently — same engine constraint as `/backtest`.
- **Save raw response to `/tmp/mc-<technique>.json`** — analyst agent reads from disk.

Capture `executionTimeMs` from the response if reporting cost.

## Agent delegation

After both API calls return, spawn `monte-carlo-analyst` with the paths to both saved JSONs. The agent combines shuffling + bootstrap into one report, renders `statistics.drawdownThresholdProbabilities` (`P(max DD > X%)` + CVaR — request the typical thresholds 20/25/30/35% via `drawdownThresholds` on the shuffling POST), locates the original backtest's metrics in the resampled distribution, flags lucky/unlucky paths, compares actual DD to the MC shuffled distribution (actual >> p95 indicates structural correlation), and produces a verdict + position-sizing recommendation. The skill itself does API orchestration + raw report assembly; statistical interpretation is the agent's job.

## Critical warnings

- **Don't run shuffling without `positionSizing` on large trade sets.** Without sizing, shuffled compounded returns over 9,000+ trades become numerically meaningless (single trades can compound to absurd values). Either include `positionSizing` or restrict to bootstrap.
- **Don't conflate bootstrap and shuffling.** Bootstrap validates that the edge is statistically significant given the trade sample; it does NOT validate that the edge will persist forward. That's `/walk-forward`'s job.
- **`P(max DD > pain threshold)` is the single most important number** for sizing decisions. If the user has a personal pain threshold (e.g. -25%), the analyst should compute and headline this probability — not bury it in the percentile table.
