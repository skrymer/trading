---
name: Always run /pre-commit skill before any git commit
description: Invoke the /pre-commit skill (not individual gradle/pnpm commands) before creating any commit in this repo — it runs all 5 quality checks plus CLAUDE.md freshness verification that manual runs skip.
type: feedback
originSessionId: 66ceed94-85a1-4732-b2d1-ab41dcf2ae97
---
Before running `git commit` in this repo, ALWAYS invoke the `/pre-commit` skill via the Skill tool. Do NOT substitute manual runs of `./gradlew test ktlintCheck detekt` — even if those pass individually, the skill covers checks they don't.

**Why:** The project CLAUDE.md states this explicitly ("ALWAYS run `/pre-commit` before committing"). The skill runs all 5 quality checks (backend tests, ktlint, detekt, frontend lint, frontend typecheck) *and* verifies CLAUDE.md files are still accurate vs. the codebase. On 2026-04-20 I committed after running only the backend 3 checks manually, missing the frontend and CLAUDE.md coverage. The user flagged it and asked this be saved.

**How to apply:**
- When the user asks to commit: call `Skill` with `skill: "pre-commit"` first; only proceed with `git commit` after it reports clean.
- If `/pre-commit` surfaces issues in areas I didn't touch (e.g., pre-existing frontend lint failures), follow the existing memory rule *"Always fix pre-existing issues"* and fix them before committing.
- The rule applies to *every* commit, including small ones (log-line tweaks, doc edits) — not just large feature work. One-file commits still go through the skill.
