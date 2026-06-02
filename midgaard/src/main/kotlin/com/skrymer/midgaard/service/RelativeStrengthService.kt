package com.skrymer.midgaard.service

import com.skrymer.midgaard.repository.QuoteRepository
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

    /** Full recompute from the earliest trusted date — used after an initial/bulk rebuild. */
    fun recomputeAll() {
        logger.info("Relative-strength pass: full recompute from $EARLIEST_DATE")
        quoteRepository.recomputeRelativeStrengthPercentiles(EARLIEST_DATE, LOOKBACK_BARS, MIN_PEERS, EARLIEST_DATE)
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
