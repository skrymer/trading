---
name: Backtest engine must simulate real trading
description: The backtest engine's purpose is to simulate real trading. Don't frame engine constraints as "pathological" if they reflect real-world capital limits — that's the engine doing its job.
type: feedback
originSessionId: a1232822-4edf-4931-a833-5f468c42f49d
---
The backtest engine's purpose is to simulate real trading. Anything the engine does that matches real-world execution constraints (capital limits, cash accounts, unfundable positions) is the engine doing its job correctly — not a regime to work around.

**Why:** A backtest that ignores real capital constraints produces misleading results. The point of validation is to show what a real trader can actually do with their real account, not what a perfect god-mode strategy could do. If the engine says "you can only fit 6-7 positions at $10K with 1.5% risk", that's reality — the plan should reflect that, not the engine.

**How to apply:**
- When backtest results diverge from a plan, the plan is usually wrong, not the engine.
- Don't propose engine changes to "loosen" real-world behavior (e.g., removing leverage cap, ignoring unfundable trades).
- Frame constraints the engine surfaces as information the trader needs, not obstacles to overcome.
- Sizing experiments are legitimate (what risk% fits N positions?), but evaluate them against what a real trader would actually do.
