---
name: Do not pkill with broad classpath patterns
description: pkill -f patterns can match any running JVM whose classpath contains the pattern, killing the wrong process.
type: feedback
originSessionId: a1232822-4edf-4931-a833-5f468c42f49d
---
Do not use `pkill -f <pattern>` to target JVM daemons (gradle, kotlin compiler, etc.) based on a classpath substring. The running application JVMs (like udgaard dev server) include those same jars on their classpath and will be killed too.

**Why:** Happened 2026-04-17 — ran `pkill -f "kotlin-compiler-embeddable"` to stop the kotlin daemon, and it killed the udgaard dev server because its classpath included `kotlin-compiler-embeddable-2.3.0.jar`.

**How to apply:**
- To stop gradle daemon, use `./gradlew --stop` (already safe).
- To stop kotlin compiler daemon, let it idle-time out, or target by the daemon's unique initiator marker (`kotlin.daemon.initiator.marker.file`), not by jar name.
- Before running any `pkill -f`, first preview with `pgrep -af <pattern>` and confirm the match list is what you expect.
- Prefer `kill -TERM <pid>` with a specific PID over pattern-based kills.
