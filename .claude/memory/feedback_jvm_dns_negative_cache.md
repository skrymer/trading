---
name: JVM negative DNS cache poisons parallel cold-start lookups
description: When a Spring Boot app fires N concurrent first-time DNS lookups for the same host, a single transient resolver failure poisons the JVM's negative cache for ~10s and ALL parallel lookups during that window see UnknownHostException. The signature is "first ~N alphabetically-early symbols fail, rest succeed, single retry works, bulk retry hits same wall."
type: feedback
originSessionId: 399c9a2a-ecaf-4456-befd-f2cc7b6af5cc
---
**Symptom signature** (highly specific — if you see this, almost certainly this bug):

1. Bulk operation that fires parallel network calls (e.g. `IngestionService.runParallelInitialIngest` with parallelism=10) starts.
2. **First ~100 alphabetically-early items fail** with `UnknownHostException: <hostname>`.
3. Then the next ~4000 items succeed.
4. Re-running just the failed N (the bulk retry path) **fails the same way again**.
5. Per-symbol retry of any one of the failed items **succeeds**.
6. Host resolves fine from `getent hosts <name>` on both host machine AND inside the container.

**Why this happens:**

The JVM's `java.net.InetAddress` has two DNS caches with different TTLs:
- `networkaddress.cache.ttl=-1` (forever) for **positive** results
- `networkaddress.cache.negative.ttl=10` (seconds) for **negative** results

When N coroutines hit `InetAddress.getByName(host)` simultaneously for the first time:
- ONE happens to fail (transient resolver jitter, network blip, slow DNS server).
- JVM populates negative cache: `host → UnknownHostException`.
- All other parallel coroutines that haven't resolved yet hit the cached negative.
- For the next ~10s, **every** lookup of that host returns UnknownHostException, even though DNS is actually fine.
- After 10s, cache TTL expires, next lookup succeeds, gets cached positively forever.

**Why bulk retry fails identically:** opens fresh parallel cold lookups → same race → same negative cache poisoning. Not a "different state, different result" — the deterministic JVM behaviour repeats.

**Why per-symbol retry works:** single sequential lookup → no race → no negative cache poisoning.

**Fix (in order of preference):**

1. **Pre-warm DNS at app startup** via `@Async @EventListener(ApplicationReadyEvent)` calling `InetAddress.getByName(host)` once per upstream host. Single sequential lookup populates positive cache before any parallel work. See `midgaard/src/main/kotlin/com/skrymer/midgaard/config/DnsPrewarmer.kt`.
2. **`-Dnetworkaddress.cache.negative.ttl=0`** in JVM args. Forces re-lookup on every failure. Defends against the cache poisoning but doesn't prevent the initial parallel race.
3. **Retry-with-backoff on `UnknownHostException`** in the HTTP client. Most robust but most code.

**Diagnostic shortcut:** if you see the symptom signature, jump straight to checking parallelism + JVM startup ordering. Don't waste time inspecting DNS configuration, container networking, provider rate limits, or connection pools — none of those reproduce the "first N fail then succeed, deterministic" pattern.
