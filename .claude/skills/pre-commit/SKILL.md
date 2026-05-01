---
name: pre-commit
description: Run pre-commit checks for changed projects (udgaard, midgaard, asgaard) and verify CLAUDE.md files. Use before committing code changes.
disable-model-invocation: true
---

Run all code quality checks before committing. Only run checks for projects that have changes. This ensures backend tests, linting, static analysis, frontend validation all pass, and documentation is up to date.

## Instructions

First, detect which projects have changes:

```bash
.claude/scripts/changed-projects.sh
```

Prints one line per affected project, drawn from `udgaard`, `midgaard`, `asgaard`. Empty output means no relevant changes — skip the agent fan-out and report `SKIPPED (no changes)`. The script unions tracked changes (`git diff --name-only HEAD`) and untracked files (`git ls-files --others --exclude-standard`).

Then check whether backend changes touch code that the public-API skills document:

```bash
.claude/scripts/skill-impact-check.sh
```

Non-empty output lists changed files under skill-relevant paths (controllers, DTOs, models, sizers, rankers, conditions, DSL, `BacktestResultStore`) and which skill files may need updating. Pass the full output verbatim to **docs-reviewer** so it reviews the listed skill files alongside CLAUDE.md. Empty output → no skill-staleness risk; record `Skill files (API docs): SKIPPED (no impact)`.

## Sub-Agent Delegation

Delegate checks to specialized sub-agents (defined in `.claude/agents/`). Launch all applicable agents **in parallel as background tasks**.

### Available Sub-Agents

| Sub-Agent | When to Use |
|-----------|------------|
| **backend-reviewer** | When `udgaard/` or `midgaard/` has changes. Tell it which projects changed. Runs tests, ktlint, detekt, compiler warnings. Auto-fixes issues. |
| **frontend-reviewer** | When `asgaard/` has changes. Runs ESLint, typecheck. Auto-fixes lint issues. |
| **docs-reviewer** | Always. Reviews and updates CLAUDE.md files for accuracy. When `skill-impact-check.sh` produces non-empty output, also reviews the listed skill files (`.claude/skills/*/SKILL.md`, `SCENARIOS.md`, `REFERENCE.md`) against the changed app code. Pass the script output verbatim. |
| **voltagent-qa-sec:code-reviewer** *(voltagent plugin)* | One instance per changed project. Reviews changed code for security issues, code quality, and QA concerns. Launched as separate parallel agents (subagents cannot spawn other subagents). |

### Workflow

1. Detect which projects have changes (`changed-projects.sh`)
2. Run `skill-impact-check.sh` and capture the output for the docs-reviewer task
3. Launch applicable agents in parallel:
   - **backend-reviewer** (if udgaard/ or midgaard/ changed) — tell it which projects: "udgaard", "midgaard", or both
   - **frontend-reviewer** (if asgaard/ changed)
   - **docs-reviewer** (always) — pass the skill-impact-check output if non-empty
   - **voltagent-qa-sec:code-reviewer** for backend (if udgaard/ or midgaard/ changed) — provide changed backend files
   - **voltagent-qa-sec:code-reviewer** for frontend (if asgaard/ changed) — provide changed frontend files
4. Wait for all agents to complete
5. Collect results and present the summary table

## Reporting

After all agents complete, present a unified summary table. Only include rows for projects that had changes. Mark skipped projects as "SKIPPED (no changes)":

| Check | Status |
|-------|--------|
| Udgaard Tests | PASS / FAIL / FIXED / SKIPPED |
| Udgaard Lint (ktlint) | PASS / FAIL / FIXED / SKIPPED |
| Udgaard Static Analysis (detekt) | PASS / FAIL / FIXED / SKIPPED |
| Udgaard Compiler Warnings | PASS / FAIL / FIXED / SKIPPED |
| Midgaard Compile | PASS / FAIL / FIXED / SKIPPED |
| Midgaard Lint (ktlint) | PASS / FAIL / FIXED / SKIPPED |
| Midgaard Static Analysis (detekt) | PASS / FAIL / FIXED / SKIPPED |
| Frontend Lint (ESLint) | PASS / FAIL / FIXED / SKIPPED |
| Frontend Typecheck | PASS / FAIL / FIXED / SKIPPED |
| CLAUDE.md files | UP TO DATE / UPDATED |
| Skill files (API docs) | UP TO DATE / UPDATED / SKIPPED (no impact) |
| Backend Code Review (QA/Security) | PASS / ISSUES FOUND / SKIPPED |
| Frontend Code Review (QA/Security) | PASS / ISSUES FOUND / SKIPPED |
| Test Coverage (new functionality) | PASS / MISSING TESTS / SKIPPED |

If any check has status FAIL (not auto-fixable), show the relevant error output and highlight what needs manual attention.

If agents auto-fixed issues (status FIXED), list the files that were modified.

If the **voltagent-qa-sec:code-reviewer** reports any **Critical** or **High** severity issues, list them separately after the summary table with file paths and a brief description. These may need fixing before committing.

## Important

- All checks for changed projects MUST pass before committing
- Docker must be running for backend tests (TestContainers)
- Follow the clean code principles defined in `.claude/skills/clean-code/SKILL.md` — review changed code for SRP, DRY, KISS, clear naming, small functions, guard clauses, and no unnecessary comments before committing
- **Test coverage for new functionality:** Any new or significantly changed logic MUST have test coverage. This includes:
  - New public service methods or controller endpoints
  - New private methods with non-trivial logic (complex calculations, branching, data transformations)
  - Changed behavior in existing methods (new branches, guard clauses, enrichment logic)
  - Bug fixes (regression test to prevent reintroduction)
  
  Verify by reading the test files for the changed services and checking whether the new behavior paths are exercised. If tests are missing, flag this in the summary table as `MISSING TESTS` with a description of the untested logic. Do NOT auto-generate tests — just report what's missing so it can be addressed before committing.
