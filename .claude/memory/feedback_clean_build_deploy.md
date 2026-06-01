---
name: Clean build before deploy
description: Deploy script may use cached JARs - always clean build/libs before bootJar to avoid stale code in production
type: feedback
---

The deploy script (`deploy-prd.fish`) runs `./gradlew bootJar -x test -x generateJooq` which can produce `UP-TO-DATE` results if Gradle caching doesn't detect changes. Multiple versioned JARs accumulate in `build/libs/` and the Dockerfile's `COPY build/libs/udgaard-*.jar app.jar` may pick up stale ones.

**Why:** Prod container ran old code despite deploying after committing changes. The `bootJar` task was cached and didn't recompile.

**How to apply:** When deploying and changes aren't reflected in prod, delete old JARs from `build/libs/` or run `generateJooq bootJar` (not just `bootJar`) to force recompilation. Consider updating `deploy-prd.fish` to clean old JARs before building.
