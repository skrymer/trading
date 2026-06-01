---
name: VCP uses VcpExitStrategy, not MjolnirExitStrategy
description: For the VCP strategy, the exit is VcpExitStrategy — do not use MjolnirExitStrategy from the run-backtest skill examples
type: feedback
originSessionId: d792d813-430d-444e-ba50-c6a1cf205bd2
---
The VCP strategy pairs with `VcpExitStrategy` (emaCross(10,20) + stopLoss(2.5 ATR) + stagnation(3%, 15d)), NOT `MjolnirExitStrategy`, even though several examples in the `/run-backtest` skill docs show `MjolnirExitStrategy` next to Vcp.

**Why:** The VCP trading plan (`strategy_exploration/VCP_TRADING_PLAN.md`) and all validated performance numbers for VCP are anchored to `VcpExitStrategy`. Mixing exits silently changes the trade distribution and invalidates comparisons to baseline.

**How to apply:** Whenever running a VCP backtest or writing VCP-related docs/examples, use `"exitStrategy": {"type": "predefined", "name": "VcpExitStrategy"}`. Treat the skill's MjolnirExitStrategy examples as generic illustrations, not VCP defaults.
