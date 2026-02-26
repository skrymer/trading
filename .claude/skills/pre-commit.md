# Pre-Commit Checks

Run all code quality checks before committing. Only run checks for projects that have changes. This ensures backend tests, linting, static analysis, frontend validation all pass, and documentation is up to date.

## Instructions

First, detect which projects have changes by running `git diff --name-only HEAD` and `git diff --name-only --cached` and checking for untracked files with `git ls-files --others --exclude-standard`. Combine the results and determine which projects are affected:

- **Udgaard**: any changed files under `udgaard/`
- **Midgaard**: any changed files under `midgaard/`
- **Asgaard**: any changed files under `asgaard/`

Only run checks for projects with changes. Run all applicable checks in parallel. Then verify CLAUDE.md files are up to date. Report results for each check clearly, and flag any failures.

### Udgaard (Backend) — only if changes in `udgaard/`

#### 1. Backend Tests
```bash
cd /home/sonni/development/git/trading/udgaard && ./gradlew test
```

#### 2. Backend Linting (ktlint)
```bash
cd /home/sonni/development/git/trading/udgaard && ./gradlew ktlintCheck
```

#### 3. Backend Static Analysis (detekt)
```bash
cd /home/sonni/development/git/trading/udgaard && ./gradlew detekt
```

### Midgaard (Reference Data Service) — only if changes in `midgaard/`

#### 4. Midgaard Compile
```bash
cd /home/sonni/development/git/trading/midgaard && ./gradlew compileKotlin
```

#### 5. Midgaard Linting (ktlint)
```bash
cd /home/sonni/development/git/trading/midgaard && ./gradlew ktlintCheck
```

#### 6. Midgaard Static Analysis (detekt)
```bash
cd /home/sonni/development/git/trading/midgaard && ./gradlew detekt
```

### Asgaard (Frontend) — only if changes in `asgaard/`

#### 7. Frontend Linting (ESLint)
```bash
cd /home/sonni/development/git/trading/asgaard && npm run lint
```

#### 8. Frontend TypeScript Validation
```bash
cd /home/sonni/development/git/trading/asgaard && npm run typecheck
```

### 9. Update CLAUDE.md Files

After the code checks complete, review the CLAUDE.md files for accuracy against the current codebase:

- `CLAUDE.md` (project root) - Project overview, architecture, tech stack versions, project structure
- `udgaard/claude.md` - Backend tech stack, package structure, strategies, DSL conditions, development commands
- `midgaard/claude.md` - Reference data service tech stack, package structure, providers, development commands
- `asgaard/claude.md` - Frontend tech stack, pages, components, chart libraries

**Check for:**
- Version numbers (Kotlin, Spring Boot, Nuxt, NuxtUI, etc.) matching `build.gradle` and `package.json`
- Package/directory structure matching actual codebase layout
- Listed strategies, conditions, rankers, and services matching what actually exists
- Listed pages and components matching what actually exists
- No references to deleted files or removed features

If any CLAUDE.md file is outdated, update it. If all are current, note "CLAUDE.md files are up to date" in the report.

## Reporting

After all checks complete, provide a summary table. Only include rows for projects that had changes. Mark skipped projects as "SKIPPED (no changes)":

| Check | Status |
|-------|--------|
| Udgaard Tests | PASS / FAIL / SKIPPED |
| Udgaard Lint (ktlint) | PASS / FAIL / SKIPPED |
| Udgaard Static Analysis (detekt) | PASS / FAIL / SKIPPED |
| Midgaard Compile | PASS / FAIL / SKIPPED |
| Midgaard Lint (ktlint) | PASS / FAIL / SKIPPED |
| Midgaard Static Analysis (detekt) | PASS / FAIL / SKIPPED |
| Frontend Lint (ESLint) | PASS / FAIL / SKIPPED |
| Frontend Typecheck | PASS / FAIL / SKIPPED |
| CLAUDE.md files | UP TO DATE / UPDATED |

If any check fails, show the relevant error output and suggest fixes:
- **ktlint failures**: Run `./gradlew ktlintFormat` to auto-fix
- **detekt failures**: Fix the flagged code smells, or run `./gradlew detektBaseline` to update the baseline
- **ESLint failures**: Run `npm run lint -- --fix` to auto-fix
- **Typecheck failures**: Review and fix the type errors manually
- **Test failures**: Investigate and fix the failing tests

## Important

- All checks for changed projects MUST pass before committing
- Docker must be running for backend tests (TestContainers)
- Run checks from the project root using absolute paths
