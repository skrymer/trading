# Pre-Commit Checks

Run all code quality checks before committing. This ensures backend tests, linting, static analysis, frontend validation all pass, and documentation is up to date.

## Instructions

Run all 5 code checks in parallel from the project root. Then verify CLAUDE.md files are up to date. Report results for each check clearly, and flag any failures.

### 1. Backend Tests
```bash
cd /home/sonni/development/git/trading/udgaard && ./gradlew test
```

### 2. Backend Linting (ktlint)
```bash
cd /home/sonni/development/git/trading/udgaard && ./gradlew ktlintCheck
```

### 3. Backend Static Analysis (detekt)
```bash
cd /home/sonni/development/git/trading/udgaard && ./gradlew detekt
```

### 4. Frontend Linting (ESLint)
```bash
cd /home/sonni/development/git/trading/asgaard && npm run lint
```

### 5. Frontend TypeScript Validation
```bash
cd /home/sonni/development/git/trading/asgaard && npm run typecheck
```

### 6. Update CLAUDE.md Files

After the 5 code checks complete, review the three CLAUDE.md files for accuracy against the current codebase:

- `CLAUDE.md` (project root) - Project overview, architecture, tech stack versions, project structure
- `udgaard/claude.md` - Backend tech stack, package structure, strategies, DSL conditions, development commands
- `asgaard/claude.md` - Frontend tech stack, pages, components, chart libraries

**Check for:**
- Version numbers (Kotlin, Spring Boot, Nuxt, NuxtUI, etc.) matching `build.gradle` and `package.json`
- Package/directory structure matching actual codebase layout
- Listed strategies, conditions, rankers, and services matching what actually exists
- Listed pages and components matching what actually exists
- No references to deleted files or removed features

If any CLAUDE.md file is outdated, update it. If all are current, note "CLAUDE.md files are up to date" in the report.

## Reporting

After all checks complete, provide a summary table:

| Check | Status |
|-------|--------|
| Backend Tests | PASS / FAIL |
| Backend Lint (ktlint) | PASS / FAIL |
| Backend Static Analysis (detekt) | PASS / FAIL |
| Frontend Lint (ESLint) | PASS / FAIL |
| Frontend Typecheck | PASS / FAIL |
| CLAUDE.md files | UP TO DATE / UPDATED |

If any check fails, show the relevant error output and suggest fixes:
- **ktlint failures**: Run `./gradlew ktlintFormat` to auto-fix
- **detekt failures**: Fix the flagged code smells, or run `./gradlew detektBaseline` to update the baseline
- **ESLint failures**: Run `npm run lint -- --fix` to auto-fix
- **Typecheck failures**: Review and fix the type errors manually
- **Test failures**: Investigate and fix the failing tests

## Important

- All 5 checks MUST pass before committing
- The H2 database server must be running for backend tests (`./gradlew startH2Server`)
- Run checks from the project root using absolute paths
