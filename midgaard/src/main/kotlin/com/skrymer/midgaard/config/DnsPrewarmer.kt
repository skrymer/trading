package com.skrymer.midgaard.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.net.URI

/**
 * Resolves all upstream provider hostnames once at app startup and lets the JVM
 * positively cache them. Defends against the negative-DNS-cache trap: when a
 * burst of parallel coroutines all try to resolve the same hostname for the
 * first time and one fails (network jitter on a cold lookup), the JVM caches
 * the negative result for `networkaddress.cache.negative.ttl` (default 10s) and
 * every concurrent attempt sees `UnknownHostException`. Pre-warming serializes
 * the first lookup, so the cache populates positively before any bulk operation
 * can race it.
 *
 * Fires asynchronously on `ApplicationReadyEvent` so a slow DNS server doesn't
 * block app readiness — `@Async` puts each invocation on a worker thread instead
 * of the event publisher's main thread. Each lookup is wrapped in `runCatching`
 * so a failed pre-warm is logged but never crashes the app; the actual ingest
 * call will retry through the normal RestClient path.
 *
 * Race window: the user could theoretically click "Initial Ingest All" in the
 * sub-second between app-ready and pre-warm completion, putting us back in the
 * negative-cache trap. Per the single-user-app design contract, that race is
 * vanishingly small in practice — manual operational triggers, no auto-fire.
 */
@Component
class DnsPrewarmer(
    @param:Value("\${alphavantage.api.baseUrl:}") private val avBaseUrl: String,
    @param:Value("\${massive.api.baseUrl:}") private val massiveBaseUrl: String,
    @param:Value("\${finnhub.api.baseUrl:}") private val finnhubBaseUrl: String,
    @param:Value("\${eodhd.api.baseUrl:}") private val eodhdBaseUrl: String,
) {
    private val logger = LoggerFactory.getLogger(DnsPrewarmer::class.java)

    @Async
    @EventListener(ApplicationReadyEvent::class)
    fun prewarmDns() {
        val urls = listOf(avBaseUrl, massiveBaseUrl, finnhubBaseUrl, eodhdBaseUrl)
        val hosts = extractHosts(urls)
        val configuredCount = urls.count { it.isNotBlank() }
        if (configuredCount > hosts.size) {
            // A non-blank URL slipped through without a host — usually means the env-var
            // override omitted the scheme (e.g. `eodhd.com/api` instead of `https://...`).
            // Surface it instead of silently dropping the host from the pre-warm.
            logger.warn(
                "$configuredCount provider URLs configured but only ${hosts.size} hosts extracted; " +
                    "check that all URLs include a scheme (https://...)",
            )
        }
        if (hosts.isEmpty()) {
            logger.info("DNS pre-warm skipped: no provider URLs configured")
            return
        }
        hosts.forEach { host ->
            val start = System.currentTimeMillis()
            runCatching { InetAddress.getByName(host) }
                .onSuccess {
                    logger.info(
                        "Pre-warmed DNS for $host -> ${it.hostAddress} " +
                            "(${System.currentTimeMillis() - start}ms)",
                    )
                }.onFailure {
                    // Don't crash startup — the lazy retry path will pick this up later.
                    // The point of pre-warming is to AVOID the cache poisoning, not to
                    // hard-block on transient DNS issues.
                    logger.warn(
                        "DNS pre-warm failed for $host (will be retried lazily on first call): " +
                            "${it::class.simpleName}: ${it.message}",
                    )
                }
        }
    }

    companion object {
        /**
         * Parses each URL string and extracts its host. Filters out blanks (un-configured
         * providers) and de-duplicates. Returns a stable order so logs are reproducible.
         */
        fun extractHosts(urls: List<String>): List<String> =
            urls
                .filter { it.isNotBlank() }
                .mapNotNull { runCatching { URI(it).host }.getOrNull() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
    }
}
