---
name: Plans must include explicit test coverage
description: When writing implementation plans, include a dedicated test-coverage section mapping each new component/branch to specific test cases — not just a list of test files
type: feedback
originSessionId: bf9ab2ae-922b-4ac8-b95e-79588cf96477
---
When writing implementation plans (e.g. via plan mode), every plan MUST include an explicit test-coverage section that maps each new component, function, or branch to specific test cases. Listing test file names alone is not enough.

**Why:** The user reviews plans to verify completeness, and "we'll add tests" without specifics hides gaps. A table mapping component → test class → cases makes coverage gaps obvious before implementation starts. The pre-commit skill already enforces "missing tests" reporting at commit time; pushing this earlier — into the plan — catches gaps before code is written.

**How to apply:**
- Use a markdown table: `| Component | Test class | Cases |`.
- One row per new function, branch, or fixed bug.
- "Cases" column lists specific scenarios in plain English (happy path + edge cases + failure modes).
- Follow existing Given/When/Then conventions and provider-neutral naming (see related memories `feedback_given_when_then_tests.md` and `feedback_provider_neutral_tests.md`).
- Include integration smoke checks separately if they're manual.
