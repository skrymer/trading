# Monte Carlo Scenarios

Request templates that build on the Quick start in [SKILL.md](SKILL.md). Pick one technique or run both back-to-back. Replace `<BACKTEST_ID>` with the UUID returned from the prior `/backtest` call.

All scenarios POST through `.claude/scripts/udgaard-post.sh` (auth + error handling). See [SKILL.md](SKILL.md) for the approval-gate rules and the prerequisite that `/backtest` ran immediately before.

## 1. Trade shuffling (path risk / drawdown distribution)

Resamples trade **order**. Distribution of max drawdowns answers "how unlucky could the path be?" Almost always run with `positionSizing` — un-sized shuffling on large trade sets produces meaningless compounded returns.

```bash
.claude/scripts/udgaard-post.sh /api/monte-carlo/simulate '{
  "backtestId": "<BACKTEST_ID>",
  "technique": "TRADE_SHUFFLING",
  "iterations": 10000,
  "positionSizing": {
    "startingCapital": <dollars>,
    "sizer": <SIZER>,
    "leverageRatio": 1.0
  }
}' /tmp/mc-shuffling.json
```

## 2. Bootstrap resampling (edge confidence)

Resamples trades **with replacement**. Distribution of return / edge / win-rate answers "is the observed edge real, or could I have gotten this lucky from a no-edge sample?"

```bash
.claude/scripts/udgaard-post.sh /api/monte-carlo/simulate '{
  "backtestId": "<BACKTEST_ID>",
  "technique": "BOOTSTRAP_RESAMPLING",
  "iterations": 10000
}' /tmp/mc-bootstrap.json
```

## 3. Both techniques back-to-back

The typical full validation. Run shuffling + bootstrap; the analyst combines them into one report.

```bash
# 1. shuffling
.claude/scripts/udgaard-post.sh /api/monte-carlo/simulate \
  '{"backtestId": "<BACKTEST_ID>", "technique": "TRADE_SHUFFLING", "iterations": 10000, "positionSizing": {...}}' \
  /tmp/mc-shuffling.json

# 2. bootstrap (sequentially — see SKILL.md → How to run)
.claude/scripts/udgaard-post.sh /api/monte-carlo/simulate \
  '{"backtestId": "<BACKTEST_ID>", "technique": "BOOTSTRAP_RESAMPLING", "iterations": 10000}' \
  /tmp/mc-bootstrap.json
```

## 4. Position-sized MC

Pass `positionSizing` (same shape as `/backtest` SCENARIOS.md §2) so percentile equity curves are dollar-comparable to the original run. Required for shuffling to be meaningful; optional but recommended for bootstrap.

```jsonc
{
  "backtestId": "<BACKTEST_ID>",
  "technique": "TRADE_SHUFFLING",
  "iterations": 10000,
  "positionSizing": {
    "startingCapital": <dollars>,
    "sizer": {"type": "atrRisk", "riskPercentage": <r>, "nAtr": <n>},
    "leverageRatio": 1.0
  },
  "seed": <int>,                  // optional, for reproducibility
  "includeAllEquityCurves": false // true returns N curves; usually not needed (percentile fan is computed regardless)
}
```
