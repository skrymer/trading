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
 * the design constants and the full-vs-incremental choice.
 */
@Service
class RelativeStrengthService(
    private val quoteRepository: QuoteRepository,
) {
    private val logger = LoggerFactory.getLogger(RelativeStrengthService::class.java)

    @Volatile
    private var currentJob: Job? = null

    /** Full recompute from the earliest trusted date — used after an initial/bulk rebuild. */
    fun recomputeAll() {
        logger.info("Relative-strength pass: full recompute from $EARLIEST_DATE")
        quoteRepository.recomputeRelativeStrengthPercentiles(EARLIEST_DATE, LOOKBACK_BARS, MIN_PEERS, EARLIEST_DATE)
    }

    /**
     * Fire-and-forget a full recompute on a background thread — used by the manual UI/API trigger
     * so a one-off recompute (pure SQL over existing quotes, no OHLCV re-fetch) doesn't block the
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

    /**
     * Incremental recompute of just the most recent dates — used after a daily update. A date's
     * percentile depends only on that date's cross-section, so recomputing a trailing buffer (to
     * cover any lagging symbols) yields the same values as a full pass for those dates, while
     * avoiding a whole-table rewrite.
     */
    fun recomputeRecent() {
        val maxDate = quoteRepository.maxQuoteDate() ?: return
        val fromDate = maxDate.minusDays(RECENT_BUFFER_DAYS)
        logger.info("Relative-strength pass: incremental recompute from $fromDate")
        quoteRepository.recomputeRelativeStrengthPercentiles(fromDate, LOOKBACK_BARS, MIN_PEERS, EARLIEST_DATE)
    }

    companion object {
        /** Trailing window for the return metric, in trading bars (≈ one year). */
        const val LOOKBACK_BARS = 252

        /** Minimum qualifying peers on a date for its percentiles to mean "vs the market". */
        const val MIN_PEERS = 100

        /** Hard floor below which the pre-2000 universe is too survivorship-tilted to rank. */
        val EARLIEST_DATE: LocalDate = LocalDate.of(2000, 1, 1)

        /** Calendar-day buffer the incremental pass reaches back over to catch lagging symbols. */
        const val RECENT_BUFFER_DAYS = 14L
    }
}
