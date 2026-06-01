---
name: When characterising existing code, fix broken behaviour — don't pin it
description: Coverage build-up PRs should fix bugs alongside the test that exposed them, not write characterisation tests against known-broken behaviour. Sharper variant of the "always fix pre-existing issues" rule.
type: feedback
originSessionId: 905edcc1-6b5a-4473-a3fb-5b891b5c76fd
---
When writing tests for existing code (characterisation tests, coverage build-up, audit-driven testing), **fix broken/buggy behaviour as you write the test — don't pin it**. Writing tests against known-broken behaviour is wasted work: those tests will need rewriting in the follow-up that fixes the bug.

**Why:** time wasted creating tests for broken scenarios (user's exact phrasing). Sharper variant of the standing rule "Always fix pre-existing issues" ([feedback_clean_build_deploy.md](feedback_clean_build_deploy.md), pinned in MEMORY.md), applied specifically to test-writing.

**How to apply:**
- During audit-driven planning (e.g., the `improve-codebase-architecture` skill, or any deepening-candidate plan), distinguish "coverage gaps" from "bugs revealed by the gap."
- Coverage gaps → write the test pinning current behaviour.
- Bugs revealed by the gap → fix the bug *first*, write the test asserting the *correct* behaviour.
- This applies even when the fix is small (e.g., HTTP 404/409 string-match → typed exception, silent truncate → explicit 400 rejection, duplicated formula → entity-rule lift per ADR 0001).
- Keep the *cost-benefit* lens: pure coverage gaps that aren't broken (e.g., `getDrawdownStats` math turned out to be correct, just untested) — characterise only. The principle is "don't pin BROKEN behaviour", not "fix everything you touch."
- For decisions that require behaviour judgement (e.g., should silent truncate become a 400 reject, a response-warning, or a higher cap?), grill the user before fixing — the fix path may not be obvious.

**Worked example — ScannerService deepening (candidate #3):**
The plan initially proposed pure characterisation (Phase 1 = tests only, fixes deferred to Phase 1.5). User rejected that framing because the audit had surfaced 5 broken/buggy patterns (404/409 string-match, MAX_VALIDATE_SYMBOLS silent truncate, PnL formula duplicated 4×, etc.). Plan revised to fold corrections into PR 0 alongside the entity-rule lift. See `docs/architecture/scanner-service-deepening.md`.
