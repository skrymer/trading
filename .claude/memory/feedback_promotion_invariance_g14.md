---
name: feedback-promotion-invariance-g14
description: "Promoting an inline-script condition to a first-class condition class can silently shift the trade population via a hidden tunable (e.g. a history-buffer constant), flipping a binding firewall gate even when headline metrics barely move. G14 / the /verify-promotion skill diffs the two trade lists by (entry_date, symbol); DIFFERS voids the inline verdict and forces full re-validation of the promoted version — it does NOT auto-REJECT."
metadata: 
  node_type: memory
  type: feedback
  originSessionId: f30b261d-3f8b-4dae-b8b9-2cc5471a46b6
---

When an inline `{"type":"script"}` condition is promoted to a named `EntryCondition`/`ExitCondition` class, the promoted code is NOT automatically trade-equivalent to the script the firewall validated — a hidden engine-side tunable can shift the trade population. **Empirically caught 2026-05-29 (Idunn):** the promoted `Pullback2of3Condition` used a dynamic history buffer `max(20, lookbackDays*2+10)` = 28 days where the inline script hardcoded 20. The 8 extra days admitted ~2 more bars per stock-date, firing on a few more thin-history symbols. Headline metrics barely moved (Block B edge +0.48% → +0.36%, 912 → 914 trades) but the binding 2020 COVID OOS edge flipped +0.31% → −0.07% across the G6 zero-threshold.

**Why:** an identity gate, not an edge gate. Aggregate metrics smear away a small population shift; only a trade-by-trade diff surfaces it. This is the failure mode [[feedback-script-conditions-must-be-promoted]] couldn't catch on its own.

**How to apply:**
- Run `/verify-promotion <label> <inline-config> <promoted-config>` (or pass the inline template as the 3rd arg to `/validate-candidate`'s `run-pipeline.sh` to fire G14 before Block A).
- Match key is `(entry_date, symbol)`; entry-set membership must be EXACT (Jaccard 1.0), exit dates exact, P&L within 1e-3 relative (harmless float noise only). Binary PASS/DIFFERS — no graded "minor".
- **Precedence (quant 2026-05-29, correcting the issue draft):** DIFFERS does NOT auto-REJECT. It VOIDS the reusable inline verdict and mandates that the promoted config run the full binding firewall on its own and meet TRADABLE independently. The inline result is discarded, never blended. Net effect for a lazy promoter = REJECTED, without a special case for legitimate intentional-fix promotions. ERROR (configs differ in anything but condition representation, incl. a `randomSeed` mismatch) halts the pipeline.
- Scope extends beyond promotion: any change to an existing condition's per-bar `evaluate()` or a buffer constant it depends on invalidates the firewall verdict of every strategy referencing it (advisory until a condition→verdict registry exists).

Lives in `.claude/skills/verify-promotion/` (SKILL.md + REFERENCE.md + scripts) and is wired into `/validate-candidate` as gate G14. Linked: [[feedback-script-conditions-must-be-promoted]], [[feedback-parameter-fragility-must-be-verified]].
