---
name: Run backtests sequentially
description: Backend OOMs if multiple backtests run concurrently — run one at a time
type: feedback
---

Run backtests one at a time. The backend will run out of memory if multiple position-sized backtests run concurrently (~12GB heap needed per run).

**Why:** User experienced OOM crash when two backtests were running in parallel.

**How to apply:** Never launch concurrent backtest API calls. Wait for one to complete before starting the next. Also avoid concurrent background polling commands that could trigger additional requests.
