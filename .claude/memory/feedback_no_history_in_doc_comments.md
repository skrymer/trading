---
name: No historical context in doc comments
description: KDoc / Javadoc should describe current behavior + non-obvious invariants only. Do NOT reference what was there before, what was replaced, or migration history — that belongs in the commit message
type: feedback
originSessionId: 399c9a2a-ecaf-4456-befd-f2cc7b6af5cc
---
Don't write doc comments that reference past state. Phrases like:

- "Replaces the prior in-memory Caffeine cache"
- "Used to be implemented via X, now uses Y"
- "Migrated from N+1 queries to a single batch fetch"
- "Was previously called `oldMethodName`"

These belong in the **commit message** for the change that introduced the new behavior, not in the code itself. The KDoc should describe the *current* design + any non-obvious invariants.

**Why:** Code comments outlive their usefulness — readers six months from now don't care what was there before; they care what's there now. History belongs in `git log` / `git blame`, not in the source. Comments that paraphrase history rot, get stale, and create confusion when someone makes a change that contradicts the now-stale comment.

**How to apply:**
- KDoc: explain *what this does* (when not obvious from the name) and *why constraints/invariants matter*. Skip the "this replaces X" preamble.
- If you want to capture migration context: put it in the commit message body.
- This sharpens the existing rule [feedback_doc_comments_focus_on_non_obvious.md](feedback_doc_comments_focus_on_non_obvious.md) — that rule says don't paraphrase method names; this rule extends it to "don't paraphrase history either."

**Example of what to write instead:**

❌ Bad — references history:
```kotlin
/**
 * Persistence facade for backtest results. Replaces the prior in-memory Caffeine cache
 * (which held only the most-recent backtest) with Postgres-backed storage...
 */
```

✅ Good — describes current invariants only:
```kotlin
/**
 * Stores `BacktestReport` as JSONB in `backtest_reports`; scalar summary fields are
 * extracted to columns so the listing endpoint reads without parsing the blob.
 *
 * Retention is "day or two max" — cleanup is manual via `BacktestReportController`,
 * not a scheduled job.
 */
```
