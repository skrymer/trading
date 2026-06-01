---
name: feedback-never-leave-main-with-failing-tests
description: "Never proceed with new work when pre-commit surfaces failing tests, even \"pre-existing\" ones. Stop and fix on a scoped branch first — a broken main blocks every future PR and hides real regressions."
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 876abf58-fbe1-4ff6-a22b-465a1d0c28a0
---

When `/pre-commit` (or any test run) surfaces failing tests, **stop the current work and fix them first**, even if the failures pre-date the current branch. Do not treat "pre-existing" as a reason to defer.

**Why:** a broken main is high-risk:
- Every future `/pre-commit` run becomes ambiguous — you can no longer tell if new work caused regressions
- Real regressions get masked by the existing failure noise ("oh those tests, they were already broken")
- The longer it stays broken the harder it is to bisect when more failures pile on top
- Memory `feedback_ci_no_docker` notes that CI doesn't run backend tests because no Docker — `/pre-commit` is the only gate. A broken main with the only gate broken = no safety net at all
- Specific bad pattern observed: PR opened against broken main → pre-commit fails → reviewer says "pre-existing, safe to commit" → both PRs go in → main now has 2× failures stacked

**How to apply:**
1. When pre-commit shows red, do NOT continue with the current task — *stop*
2. Investigate the failure: reproduce on main (`git stash; git checkout main; ./gradlew test --tests "..."`) to confirm it's truly pre-existing
3. **Before declaring main truly broken, reset the dev environment** — a failure that reproduces on main locally but only in a contaminated dev env is *not* a main bug. Specific traps with jOOQ: codegen reads schema from the live dev Postgres, not classpath migrations. If you've applied a sibling branch's migration to dev (`./gradlew initDatabase` on a feature branch), the regenerated POJOs will reference columns that main's classpath migrations don't create — TestContainers tests then fail with `column ... does not exist`. Rollback the sibling migration on dev (`ALTER TABLE ... DROP COLUMN`, `DELETE FROM flyway_schema_history WHERE version = 'XX'`), `./gradlew generateJooq`, then re-test. If main now passes, the original "broken main" was *environment contamination*, not a real bug.
4. If main is genuinely broken (reproduces on a clean env): create a tightly-scoped fix branch off main, fix the failure, run /pre-commit, commit, open PR for the fix
5. Get the fix merged before resuming the original work — even if it delays the sweep / feature / deploy
6. The "current work" can wait. The broken main can't.

This is a sharper variant of [[feedback_always_fix_pre_existing_issues]] (broader: lint, security, perf, etc.) — failing tests are the strongest "fix before you continue" signal because they break the gate that protects all future work.
