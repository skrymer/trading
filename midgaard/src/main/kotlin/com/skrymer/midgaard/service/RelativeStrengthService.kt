package com.skrymer.midgaard.service

import com.skrymer.midgaard.repository.QuoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Drives the cross-sectional relative-strength pass (ADR 0009). The whole-universe sort, ranking
 * and write are delegated to a single SQL statement in [QuoteRepository]; this service only owns
 * the design constants. The pass is triggered manually (UI/API), decoupled from ingestion, so a
 * fast re-ingest is never slowed by it.
 */
@Service
class RelativeStrengthService(
    private val quoteRepository: QuoteRepository,
) {
    private val logger = LoggerFactory.getLogger(RelativeStrengthService::class.java)

    @Volatile
    private var currentJob: Job? = null

    /** Full recompute from the earliest trusted date, over the quotes already stored. */
    fun recomputeAll() {
        logger.info("Relative-strength pass: full recompute from $EARLIEST_DATE")
        val written = quoteRepository.recomputeRelativeStrengthPercentiles(EARLIEST_DATE, LOOKBACK_BARS, MIN_PEERS, EARLIEST_DATE)
        logger.info("Relative-strength pass: complete — wrote percentiles to $written rows")
    }

    /**
     * Fire-and-forget a full recompute on a background thread — the manual UI/API trigger so a
     * one-off recompute (pure SQL over existing quotes, no OHLCV re-fetch) doesn't block the
     * request thread. Returns the [Job] so callers/tests can await completion.
     *
     * Synchronized + idempotent: if a recompute is already in flight, its Job is returned rather
     * than launching a second overlapping whole-universe UPDATE (which would contend or deadlock).
     * Failures are logged, never silently swallowed by the coroutine's default handler.
     */
    @Synchronized
    fun recomputeAllAsync(): Job {
        currentJob?.takeIf { it.isActive }?.let { return it }
        val job =
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { recomputeAll() }
                    .onFailure { logger.error("Manual relative-strength recompute failed", it) }
            }
        currentJob = job
        return job
    }

    /** Whether a recompute is currently running — surfaced to the UI so it can show progress. */
    fun isRecomputeActive(): Boolean = currentJob?.isActive == true

    companion object {
        /** Trailing window for the return metric, in trading bars (≈ one year). */
        const val LOOKBACK_BARS = 252

        /** Minimum qualifying peers on a date for its percentiles to mean "vs the market". */
        const val MIN_PEERS = 100

        /** Hard floor below which the pre-2000 universe is too survivorship-tilted to rank. */
        val EARLIEST_DATE: LocalDate = LocalDate.of(2000, 1, 1)
    }
}
