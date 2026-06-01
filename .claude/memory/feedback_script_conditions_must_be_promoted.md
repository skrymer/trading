---
name: feedback-script-conditions-must-be-promoted
description: "A candidate using inline `script` conditions in its entryStrategy or exitStrategy is NOT yet a tradable strategy, even if it passes /validate-candidate's 3-block firewall. The script must first be promoted to a real, named, version-controlled EntryCondition / ExitCondition class via /create-condition. The strategy then has to re-enter the firewall with the promoted condition before TRADABLE applies."
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 7571f7f6-71bf-476b-8e88-d8d2a44467a6
---

A candidate strategy whose `entryStrategy.conditions` or `exitStrategy.conditions` contain `{"type": "script", "parameters": {"script": "..."}}` entries is NOT yet a tradable strategy, even after `/validate-candidate` returns TRADABLE.

**Why:**
- **Audit trail**: inline scripts are text in a request JSON, not version-controlled engine code. They aren't subject to the `/create-condition` lookahead audits (PR #34 — `AboveBearishOrderBlockCondition` future-OB bug class), aren't unit-tested, and can silently drift between sessions.
- **Reproducibility**: a real condition is a registered `@Component` with a stable `type` discoverable via `/api/backtest/conditions`. Backtest results refer to it by name. Inline scripts depend on the exact text passed at backtest time, which is fragile.
- **Performance**: real Kotlin code is compiled into the engine; scripts go through `ScriptPredicateCompiler` per backtest. Marginal but real.
- **Safety**: the helpers documented in `/create-condition` (lookahead-safe accessors on `Stock` / `OrderBlock` / `Earning`) make it easy to write a correct condition. Inline scripts can accidentally use unsafe access patterns.

**How to apply:**

1. After `/validate-candidate` returns TRADABLE on a script-based candidate, treat the verdict as **TRADABLE-PENDING-PROMOTION** rather than final.
2. Use `/create-condition` to write real `EntryCondition` / `ExitCondition` Kotlin classes that encode the script logic. Tests + lookahead audits per the skill.
3. After promotion: rewrite the candidate's request JSON to use the new condition `type` (e.g. `"type": "sectorBreadthDivergence"` instead of `"type": "script"`).
4. **Re-enter the firewall from Block A** with the promoted-condition request. The pre-promotion run and the post-promotion run are not interchangeable — even if the script and the condition are byte-equivalent, the firewall semantics require validation against the exact config that will ship.
5. Only after the post-promotion `/validate-candidate` run returns TRADABLE is the candidate genuinely ready for `/monte-carlo` + Phase 1 paper-trade.

Applies to:
- `/strategy-screen` survivors (typically include 1-2 script-based conditions for the strategy's signature)
- `/validate-candidate` outputs (TRADABLE on a script candidate is conditional, not final)
- The validation-candidates.md doc (script candidates should be flagged as "pending promotion")

Currently affected (May 2026): VZ3 and MR3 both use inline scripts for their entry conditions; DV1 (near-miss) also does. None are tradable as-is.

Linked: [[feedback-strategy-neutral-skills]] (the rule that the skills themselves don't bake strategies in) and [[feedback-min-cagr-tradable]] (30% CAGR floor is one tradability gate; script-promotion is another).
