---
name: Skills must be strategy-neutral
description: Backtest skills (and other reusable docs) must not bake VCP-specific defaults, examples, or thresholds. Examples should use placeholders or be illustrative-only.
type: feedback
originSessionId: e9db63a6-5658-4517-8fe1-254dedcdebcf
---
When writing skills, agent docs, design drafts, or example commands for the backtest workflow, do not assume the VCP strategy — and **do not name specific rejected strategies at all**. As of 2026-05-29, VCP, Mjolnir, and Idunn are all rejected/invalidated; none may be referenced in skills or docs (not even as "known-passer" / "known-failer" calibration anchors). Describe the empirical pattern generically instead (e.g. "a candidate whose lookback off-by-one flipped the verdict", "the first strategy to clear the firewall serves as the passer calibration").

**Why:** VCP is one of several strategies the user backtests. Skills baking VCP defaults (e.g. `atrRisk(1.25%, 2.0)` from `project_sizer_sweep_2026_04_17`, "edge halves in bear regimes" framing, VCP/VcpExitStrategy in examples) push other strategies through a VCP-shaped lens and bias the analyst sub-agents. Naming rejected strategies as calibration anchors also rots immediately — there is currently **no known-passer strategy** (0/4 components passing), so any "use Mjolnir/VCP to calibrate" instruction is dead on arrival.

**How to apply:**
- Use placeholder names in command examples (`<entry-strategy>`, `<exit-strategy>`) or generic names (`MyStrategy`).
- Present sizer options (`atrRisk` / `percentEquity` / `kelly` / `volTarget`) without designating one as the default — the user picks based on the strategy's volatility / asymmetry / equity-curve shape.
- Decision-framework thresholds (edge ≥ 1.5%, EC ≥ 60, Sharpe > 1, etc.) are general systematic-trading conventions and stay; they're not VCP-specific.
- Caveats about asymmetric / low-WR strategies should describe the principle (low WR with high W/L can still have edge) without naming a specific strategy.
- Example reports should illustrate the shape of the output, not real metrics from a VCP run.
