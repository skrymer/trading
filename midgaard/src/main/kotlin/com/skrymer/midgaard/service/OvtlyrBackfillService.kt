package com.skrymer.midgaard.service

import com.skrymer.midgaard.integration.ProviderIds
import com.skrymer.midgaard.integration.ovtlyr.OvtlyrClient
import com.skrymer.midgaard.integration.ovtlyr.OvtlyrCredentials
import com.skrymer.midgaard.integration.ovtlyr.OvtlyrPayloadMapper
import com.skrymer.midgaard.repository.OvtlyrSignalRepository
import com.skrymer.midgaard.repository.SymbolRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicInteger

/**
 * Live progress of a backfill run. `active` is true while the coroutine is running.
 * Constructed per run; exposed for the `/ingestion` UI to poll.
 */
data class OvtlyrBackfillProgress(
    val total: Int,
    val processed: AtomicInteger = AtomicInteger(0),
    val signalsWritten: AtomicInteger = AtomicInteger(0),
    @Volatile var active: Boolean = true,
)

/**
 * One-time backfill of Ovtlyr signals. Loops the symbol universe, fetches each symbol's
 * full ovtlyr history, extracts buy/sell calls, and upserts them. Manually triggered —
 * there is no scheduled variant (see the "Ovtlyr signal" grilling decisions).
 *
 * The loop is sequential and the HTTP calls are rate-limited inside [OvtlyrClient] (ovtlyr.com
 * is a private endpoint authenticated by a replayed browser session — over-fetching risks
 * getting the cookies banned). Only one backfill runs at a time.
 */
@Service
class OvtlyrBackfillService(
    private val symbolRepository: SymbolRepository,
    private val ovtlyrClient: OvtlyrClient,
    private val signalRepository: OvtlyrSignalRepository,
    private val apiKeyService: ApiKeyService,
    private val rateLimiterService: RateLimiterService,
) {
    @Volatile
    var progress: OvtlyrBackfillProgress? = null

    @Volatile
    private var currentJob: Job? = null

    /**
     * Launches the backfill on a background coroutine and returns immediately — the caller
     * (an HTTP trigger) must not block for the multi-minute run. Returns the [Job] so tests
     * can await completion. Synchronized + idempotent: if a run is already active, the
     * in-flight Job is returned rather than launching a second, overlapping backfill.
     */
    @Synchronized
    fun runBackfill(): Job {
        currentJob?.takeIf { progress?.active == true }?.let { return it }

        val credentials =
            OvtlyrCredentials(
                cookieUserId = apiKeyService.getOvtlyrCookieUserId(),
                cookieToken = apiKeyService.getOvtlyrCookieToken(),
                projectId = apiKeyService.getOvtlyrProjectId(),
            )
        // Skip symbols that already have stored signals — a re-run after a partial/blocked
        // backfill then only fetches what's missing. Known limitation: a symbol ovtlyr covered
        // but that had zero buy/sell calls writes no rows, so it isn't recorded as done and
        // gets re-fetched on every run. That set is negligible for a buy/sell-signal service,
        // so it isn't worth a separate processed-symbol tracking table.
        val alreadyLoaded = signalRepository.findDistinctSymbols()
        val symbols = symbolRepository.findAll().filterNot { it.symbol in alreadyLoaded }
        val runProgress = OvtlyrBackfillProgress(total = symbols.size)
        progress = runProgress

        // launch() returns the coroutine's own Job, which completes when the body finishes —
        // so callers can join() it. (A standalone Job() would never complete on its own.)
        val job =
            CoroutineScope(Dispatchers.IO).launch {
                for (symbol in symbols) {
                    // Paces the loop against ovtlyr's rate limit — suspends here until a
                    // permit frees up, so the backfill can't over-fetch and trip a ban.
                    rateLimiterService.acquirePermit(ProviderIds.OVTLYR)
                    // Best-effort per symbol: one symbol's failure must not abort a
                    // multi-thousand-symbol backfill. Log and move on.
                    runCatching {
                        val payload = ovtlyrClient.getStockInformation(symbol.symbol, credentials)
                        if (payload != null) {
                            val signals = OvtlyrPayloadMapper.toSignals(symbol.symbol, payload)
                            signalRepository.upsert(signals)
                            runProgress.signalsWritten.addAndGet(signals.size)
                        }
                    }.onFailure { logger.error("Ovtlyr backfill failed for ${symbol.symbol}: ${it.javaClass.simpleName}") }
                    runProgress.processed.incrementAndGet()
                }
                runProgress.active = false
            }
        currentJob = job
        return job
    }

    companion object {
        private val logger = LoggerFactory.getLogger(OvtlyrBackfillService::class.java)
    }
}
