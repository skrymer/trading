---
name: Never fire a backtest without user approval
description: Always show the POST request and wait for explicit user approval before executing any backtest, walk-forward, or monte-carlo run
type: feedback
originSessionId: e9db63a6-5658-4517-8fe1-254dedcdebcf
---
Never fire a backtest (or walk-forward / monte-carlo) before the user explicitly approves the request. Always show the full curl/POST first, then wait for the user to say "go", "fire it", "approved", "yes", or equivalent.

**Why:** backtests are expensive (10-15 min for position-sized 10y runs, ~12GB heap) and serialised — a wrong config wastes the user's time and blocks the next run. The user wants to inspect dates, sizing, conditions, parameters, and seed before committing the resources. This applies even when the previous turn ended with the user approving a related but different config — every new POST needs its own explicit green light.

**How to apply:**
- After constructing a backtest/walk-forward/monte-carlo POST, output the full request body and stop. Do **not** chain into the curl call in the same turn, even when the request looks "obviously correct" or follows directly from the user's previous instruction.
- If the user's message includes a config change ("run from 2016-2025", "add gapandcrap", "use seed 7"), treat it as an *amendment to the request body* — re-show the updated POST, don't fire it.
- The only exception is when the user explicitly says "fire it without showing me" or equivalent in the same turn.
- Applies to all three backtest skills: `/backtest`, `/walk-forward`, `/monte-carlo`.
