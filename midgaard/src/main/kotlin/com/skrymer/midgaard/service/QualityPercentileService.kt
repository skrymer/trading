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
 * Drives the cross-sectional gross-profitability quality pass (ADR 0019 L2). The whole-universe as-of
 * join, TTM sum, ranking and write are delegated to a single SQL statement in [QuoteRepository]; this
 * service only owns the design constants. Triggered manually (UI/API) and kept *separate* from the
 * relative-strength pass so the quality metric can be re-run without touching RS (and vice versa).
 */
@Service
class QualityPercentileService(
    private val quoteRepository: QuoteRepository,
) {
    private val logger = LoggerFactory.getLogger(QualityPercentileService::class.java)

    @Volatile
    private var currentJob: Job? = null

    @Volatile
    private var lastRunRowsWritten: Int? = null

    /** Full recompute from the earliest trusted date, over the quotes and fundamentals already stored. */
    fun recomputeAll() {
        logger.info("Quality-percentile pass: full recompute from $EARLIEST_DATE")
        val written = quoteRepository.recomputeQualityPercentiles(EARLIEST_DATE, MIN_PEERS, EARLIEST_DATE)
        lastRunRowsWritten = written
        logger.info("Quality-percentile pass: complete — wrote percentiles to $written rows")
    }

    /**
     * Fire-and-forget a full recompute on a background thread — the manual UI/API trigger so a one-off
     * recompute (pure SQL over existing data, no re-fetch) doesn't block the request thread. Returns the
     * [Job] so callers/tests can await completion. Synchronized + idempotent: a second call while one is
     * in flight returns the running Job rather than launching an overlapping whole-universe UPDATE.
     */
    @Synchronized
    fun recomputeAllAsync(): Job {
        currentJob?.takeIf { it.isActive }?.let { return it }
        val job =
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { recomputeAll() }
                    .onFailure { logger.error("Manual quality-percentile recompute failed", it) }
            }
        currentJob = job
        return job
    }

    /** Whether a recompute is currently running — surfaced to the UI so it can show progress. */
    fun isRecomputeActive(): Boolean = currentJob?.isActive == true

    /** Rows the most recent completed recompute wrote, or null if none has run this session. */
    fun lastRunRowsWritten(): Int? = lastRunRowsWritten

    companion object {
        /** Minimum qualifying peers on a date for its percentiles to mean "vs the market" (ADR 0009). */
        const val MIN_PEERS = 100

        /** Hard floor below which the pre-2000 universe is too survivorship-tilted to rank. */
        val EARLIEST_DATE: LocalDate = LocalDate.of(2000, 1, 1)
    }
}
