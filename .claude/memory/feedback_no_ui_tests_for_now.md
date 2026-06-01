---
name: No UI tests for asgaard at the moment
description: Skip writing Vue/component tests in asgaard during the current phase; verify frontend changes via manual smoke instead
type: feedback
originSessionId: 399c9a2a-ecaf-4456-befd-f2cc7b6af5cc
---
Don't write Vue component tests, page tests, or any frontend test files (`*.test.ts`, `*.spec.ts`) when implementing asgaard changes. The user is not maintaining a UI test suite at the moment.

**Why:** The user said so explicitly during a plan review (rejected a plan that proposed `ReportsTable.test.ts`). Implies the project has decided the maintenance cost of UI tests outweighs the benefit at the current phase, and that frontend regressions are caught via manual smoke + the type system + ESLint.

**How to apply:**
- When planning frontend work, do NOT include UI test files in the file list.
- Verification for frontend changes goes in a "manual smoke" section: e.g. "open `http://localhost:3000/<page>`, click X, assert Y rendered".
- Backend tests (Kotlin/Spring/jOOQ) are unaffected — those are still required per the project memory rules.
- If the user changes their mind later, they'll reverse this preference; until then default to skipping.
