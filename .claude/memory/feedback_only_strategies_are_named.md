---
name: feedback_only_strategies_are_named
description: "Only strategies get proper (Norse god) names; conditions are referred to by their condition type, never named"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 4eb0f4f6-36ef-415a-911e-b2fdf920b0c1
---

Only **strategies** are given proper names (the Norse-god convention: VCP, Fenrir, Tyr, Baldr, …). Individual **conditions** are referred to by their condition type (e.g. `orderBlockBreakout`, `marketBreadthRecovering`), never given a strategy-style name.

**Why:** During a strategy-exploration session I screened the bare condition `orderBlockBreakout` in isolation and named that screen candidate "Vidar". Naming a single condition as if it were a strategy created confusion about whether the condition would be *traded standalone* as the entire entry signal — when in fact a condition screened in isolation is just the alpha-engine diagnostic, and the eventual tradable strategy wraps it with filters + an exit. The name belongs to that assembled strategy, not the condition.

**How to apply:** In the strategy-exploration funnel, only register/name a candidate once it is an actual **strategy** (entry stack + exit). A condition undergoing design-time `/condition-screen` in isolation stays referred to by its condition type; record it as a condition-level finding (e.g. dossier file `condition-<type>.jsonl`), not a named funnel candidate. Assign the strategy name only when the strategy is assembled around the screened condition. Related: [[feedback_strategy_neutral_skills]].
