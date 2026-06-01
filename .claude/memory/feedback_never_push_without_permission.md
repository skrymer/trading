---
name: Never push without explicit user permission
description: Always pause for explicit approval before `git push`, `gh pr create`, or any commit-publishing action — even when the workflow seems to imply it
type: feedback
originSessionId: 399c9a2a-ecaf-4456-befd-f2cc7b6af5cc
---
Never run `git push`, `gh pr create`, or any other action that publishes commits to a remote without an explicit "push" / "yes push" / "open the PR" / equivalent from the user in the same turn.

**Why:** The user wants a checkpoint between local commit and remote publish, regardless of how the surrounding workflow looks. A skill like `/pre-commit` ending with "commit + push + open PR" in its task list is *intent*, not pre-authorization — the user still wants to gate the actual push. Past sessions have pushed straight after committing because the skill's task description implied it; the user interrupted to make this rule explicit.

**How to apply:** After committing locally, stop and ask "Push and open PR?" (or wait for the user's instruction). This applies to every branch, every PR — there is no "obviously fine to push" case. Approval to commit ≠ approval to push.
