---
name: feedback_dep_bump_use_latest
description: "When bumping a dependency for compatibility (not a deliberate version-pinned upgrade), go straight to the latest stable — don't guess at intermediate versions"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 185506d9-5aab-4adc-bef2-82e39d635563
---

When a plugin or library version needs to change to resolve an incompatibility (e.g. Gradle 9 broke `io.gatling.gradle:3.13.5` via removed `javaexec()` API), update to the latest stable release. Don't pick an arbitrary middle version on a hunch.

Concrete instance: I bumped `io.gatling.gradle` from 3.13.5 → 3.14.3 without checking what was current; the user pointed out the latest was 3.15.0.3. The intermediate 3.14.3 was a guess, not a rationale.

**Why:** picking an intermediate version is wasted iteration (might still have other bugs the latest fixes) and indefensible to the reader of the diff (no answer to "why this version specifically?"). Going to latest stable gives the maximum chance of fixes + a clear rationale.

**How to apply:** when bumping for compatibility, run `gradle dependencies` / `npm view` / equivalent to discover the latest, OR ask the user. Don't reach for "the next minor version" as if it's a safe step — there's no safety in stopping short.
