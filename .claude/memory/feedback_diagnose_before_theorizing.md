---
name: feedback-diagnose-before-theorizing
description: "When something is anomalously slow or hung, run a real diagnostic (thread dump, logs, process inspection) before offering an explanation"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 876abf58-fbe1-4ff6-a22b-465a1d0c28a0
---

When a build/test/process is anomalously slow or apparently hung, **diagnose with real tools before theorizing**. Thread-dump the stuck JVM (`jstack <pid>`), read the actual log, inspect process state — *then* explain.

**Why:** During the ovtlyr work the Midgaard suite "took 15 minutes." I offered two confident explanations in a row — "resource contention" and then "Spring context not being reused" — both wrong. The user pushed back three times ("are you stuck?", "where is it stuck?", "taking way longer than udgaard"). A single `jstack` of the test worker found the real cause in seconds: a `runBackfill()` that returned a manually-created `CompletableJob` (`Job()`) which is never `complete()`d, so `.join()` blocked forever. Speculation wasted the user's time and eroded trust; the diagnostic was instant.

**How to apply:** The moment something looks hung or anomalously slow, before writing any explanation: thread-dump the process, grep the live log, check `ps`/mtimes. Lead with evidence, not hypotheses. "X is slow, probably because Y" is only acceptable *after* Y has been observed. If a teammate's instinct contradicts your theory ("but Udgaard with hundreds of tests is faster"), treat that as a strong signal your theory is wrong — go measure.
