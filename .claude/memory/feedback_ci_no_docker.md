---
name: CI has no Docker — backend test/quality CI failures are expected
description: GitHub Actions for this repo runs without Docker, so TestContainers-backed Backend Tests and Code Quality Checks always fail in CI. Don't investigate or block merges on them.
type: feedback
originSessionId: 905edcc1-6b5a-4473-a3fb-5b891b5c76fd
---
CI has no Docker environment, so the "Backend Tests" and "Code Quality Checks" jobs in the GitHub Actions workflow always fail (TestContainers can't spin up Postgres). The user runs the full test sweep locally via `/pre-commit` and that's the source of truth.

**Why:** infrastructure choice — CI is for the cheap checks (frontend, integration build); the heavy backend suite stays local.

**How to apply:** When PR mergeability shows "UNSTABLE" with Backend Tests / Code Quality Checks failing but local `./gradlew test ktlintCheck detekt` passed, treat the CI failures as expected and proceed with merge (with explicit user "go"). Don't dig into the CI logs or try to "fix" them. Frontend Tests passing + a green local sweep is the real gate.
