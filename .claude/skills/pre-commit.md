# Pre-Commit Checks

Run all code quality checks before committing. Only run checks for projects that have changes. This ensures backend tests, linting, static analysis, frontend validation all pass, and documentation is up to date.

## Instructions

First, detect which projects have changes by running `git diff --name-only HEAD` and `git diff --name-only --cached` and checking for untracked files with `git ls-files --others --exclude-standard`. Combine the results and determine which projects are affected:

- **Udgaard**: any changed files under `udgaard/`
- **Midgaard**: any changed files under `midgaard/`
- **Asgaard**: any changed files under `asgaard/`

## Sub-Agent Delegation

Delegate checks to specialized sub-agents (defined in `.claude/agents/`). Launch all applicable agents **in parallel as background tasks**.

### Available Sub-Agents

| Sub-Agent | When to Use |
|-----------|------------|
| **backend-reviewer** | When `udgaard/` or `midgaard/` has changes. Tell it which projects changed. Runs tests, ktlint, detekt, compiler warnings. Auto-fixes issues. |
| **frontend-reviewer** | When `asgaard/` has changes. Runs ESLint, typecheck. Auto-fixes lint issues. |
| **docs-reviewer** | Always. Reviews and updates CLAUDE.md files for accuracy. |
| **voltagent-qa-sec:code-reviewer** *(voltagent plugin)* | One instance per changed project. Reviews changed code for security issues, code quality, and QA concerns. Launched as separate parallel agents (subagents cannot spawn other subagents). |

### Workflow

1. Detect which projects have changes
2. Launch applicable agents in parallel:
   - **backend-reviewer** (if udgaard/ or midgaard/ changed) — tell it which projects: "udgaard", "midgaard", or both
   - **frontend-reviewer** (if asgaard/ changed)
   - **docs-reviewer** (always)
   - **voltagent-qa-sec:code-reviewer** for backend (if udgaard/ or midgaard/ changed) — provide changed backend files
   - **voltagent-qa-sec:code-reviewer** for frontend (if asgaard/ changed) — provide changed frontend files
3. Wait for all agents to complete
4. Collect results and present the summary table

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
| Backend Code Review (QA/Security) | PASS / ISSUES FOUND / SKIPPED |
| Frontend Code Review (QA/Security) | PASS / ISSUES FOUND / SKIPPED |

If any check has status FAIL (not auto-fixable), show the relevant error output and highlight what needs manual attention.

If agents auto-fixed issues (status FIXED), list the files that were modified.

If the **voltagent-qa-sec:code-reviewer** reports any **Critical** or **High** severity issues, list them separately after the summary table with file paths and a brief description. These may need fixing before committing.

## Important

- All checks for changed projects MUST pass before committing
- Docker must be running for backend tests (TestContainers)
- Follow the clean code principles defined in `.claude/skills/clean_code.md` — review changed code for SRP, DRY, KISS, clear naming, small functions, guard clauses, and no unnecessary comments before committing
