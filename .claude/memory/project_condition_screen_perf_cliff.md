---
name: project_condition_screen_perf_cliff
description: Three entry conditions are non-terminating in practice under /api/conditions/screen auto-sweep — known engine perf cliff
metadata: 
  node_type: memory
  type: project
  originSessionId: b1bece2d-17a1-44a6-80b3-733368d34a6c
---

`/api/conditions/screen` (the /condition-screen diagnostic endpoint) has a verified performance
cliff under its automatic parameter-sweep on three entry conditions — observed 2026-05-31 on PRD
1.0.72 during a whole-library sanity sweep:

- **marketBreadthIncreasing** — timed out >900s on BOTH the full 3,905-symbol universe AND a reduced
  300-symbol universe. Cost does **not** scale with the equity universe (breadth is a market-wide
  series), so shrinking `symbols` does NOT help. The expense is the "strict-increase-over-N-days"
  logic × the `days` auto-sweep.
- **sectorBreadthIncreasing** — same increase-over-N-days pattern (+ `sectorSymbol` param), same cliff.
- **aboveBearishOrderBlock** — timed out >900s even on a 50-symbol universe; pathological per-bar
  order-block iteration × auto-sweep. (Other order-block conditions screen fine: belowOrderBlock,
  notInOrderBlock, orderBlockBreakout, orderBlockRejection all returned in <70s on 50 symbols.)

**Why:** client `curl --max-time` does NOT stop the server-side computation — a timed-out screen
leaves an orphaned thread pegging a core (one ran ~20h at 100% CPU before being noticed). After any
screen timeout, restart udgaard to reclaim the box; diagnose hangs with `docker stats` + the
`Condition screen:` log lines, don't theorize.

**How to apply:** don't expect a smaller `symbols` universe to rescue these three — it won't for the
breadth pair. Treat them as coverage gaps; per quant, order-block parameter-robustness is acceptably
deferred to the firewall G13 gate. Worth a separate engine perf investigation (cap/disable the
auto-sweep for these, or memoize the breadth series across sweep cells). See
[[feedback_diagnose_before_theorizing]].
