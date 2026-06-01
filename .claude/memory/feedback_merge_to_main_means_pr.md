---
name: feedback-merge-to-main-means-pr
description: "When the user says \"merge to main\" (or similar — \"land on main\", \"ship it\"), the project convention is **push the feature branch + open a PR**, NOT a local fast-forward merge. Even a clean single-commit fix goes through PR review."
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 7571f7f6-71bf-476b-8e88-d8d2a44467a6
---

When the user instructs me to "merge to main" / "land on main" / "ship X", do NOT run `git merge --ff-only` (or any local merge) on main. The instruction should resolve to:

1. Push the feature branch to origin (`git push -u origin <branch>`)
2. Open a PR with `gh pr create` titled and described from the commit
3. Wait for the user to merge via GitHub

**Why:** the project workflow is feature-branch → PR → human review → merge. Even a tightly-scoped correctness fix with green tests and a clean diff goes through the PR. Local fast-forwarding to main bypasses code review and skips the GitHub history that the user relies on. Caught when I did a local FF for `fix/ob-lookahead` after they said "merge to main" — they were specifically asking for the PR step.

**How to apply:**
- Always confirm "merge to main" maps to "push + open PR" — never a local merge to main.
- If unsure, explicitly ask: "Push the branch and open the PR, or just push the branch?"
- Per [[feedback_never_push_without_permission]] the push itself also needs explicit permission, which "merge to main" supplies for the feature branch — but does NOT supply for pushing main directly.

Cross-reference: [[feedback_feature_branch_not_main]] (the general rule); [[feedback_never_push_without_permission]] (push needs explicit ask).
