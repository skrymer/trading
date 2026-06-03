package com.skrymer.midgaard.e2e

import com.skrymer.midgaard.model.Quote
import com.skrymer.midgaard.repository.QuoteRepository
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Integration coverage for the SQL cross-sectional relative-strength recompute (ADR 0009).
 * Exercises the window-function math against a real Postgres: midpoint ranking, the min-history
 * exclusion, the peer-count floor, the stale-value reset, and the incremental fromDate bound.
 */
class RelativeStrengthRecomputeE2ETest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var quoteRepository: QuoteRepository

    @Autowired
    private lateinit var dsl: DSLContext

    private val earliest = LocalDate.of(2000, 1, 1)

    @BeforeEach
    fun clearQuotes() {
        dsl.execute("DELETE FROM quotes")
    }

    private fun quote(
        symbol: String,
        date: LocalDate,
        close: Double,
    ) = Quote(
        symbol = symbol,
        date = date,
        open = BigDecimal.valueOf(close),
        high = BigDecimal.valueOf(close),
        low = BigDecimal.valueOf(close),
        close = BigDecimal.valueOf(close),
        volume = 0L,
    )

    private fun percentileOn(
        symbol: String,
        date: LocalDate,
    ): Double? =
        quoteRepository
            .findBySymbol(symbol)
            .first { it.date == date }
            .relativeStrengthPercentile
            ?.toDouble()

    @Test
    fun `ranks symbols into midpoint percentiles and excludes the insufficient-history bar`() {
        // Given three symbols whose day-2 1-bar returns are B=0.05 < A=0.10 < C=0.20
        val d1 = LocalDate.of(2024, 1, 1)
        val d2 = LocalDate.of(2024, 1, 2)
        quoteRepository.upsertQuotes(
            listOf(
                quote("RSAA", d1, 100.0),
                quote("RSAA", d2, 110.0),
                quote("RSAB", d1, 100.0),
                quote("RSAB", d2, 105.0),
                quote("RSAC", d1, 100.0),
                quote("RSAC", d2, 120.0),
            ),
        )

        // When the pass recomputes with a 1-bar lookback and no peer floor
        val written =
            quoteRepository.recomputeRelativeStrengthPercentiles(earliest, lookbackBars = 1, minPeers = 1, earliestDate = earliest)

        // Then it reports the 3 day-2 rows written, day 2 gets midpoint percentiles, day 1 has none
        assertEquals(3, written)
        assertEquals(100.0 * 0.5 / 3, percentileOn("RSAB", d2)!!, 1e-3)
        assertEquals(100.0 * 1.5 / 3, percentileOn("RSAA", d2)!!, 1e-3)
        assertEquals(100.0 * 2.5 / 3, percentileOn("RSAC", d2)!!, 1e-3)
        assertNull(percentileOn("RSAA", d1))
    }

    @Test
    fun `omits a date whose qualifying peer count is below the floor`() {
        // Given only two symbols qualify on day 2 but the floor is three
        val d1 = LocalDate.of(2024, 2, 1)
        val d2 = LocalDate.of(2024, 2, 2)
        quoteRepository.upsertQuotes(
            listOf(
                quote("RSBA", d1, 100.0),
                quote("RSBA", d2, 110.0),
                quote("RSBB", d1, 100.0),
                quote("RSBB", d2, 120.0),
            ),
        )

        // When recomputed with a peer floor of 3
        quoteRepository.recomputeRelativeStrengthPercentiles(earliest, lookbackBars = 1, minPeers = 3, earliestDate = earliest)

        // Then a thin cross-section yields no percentile
        assertNull(percentileOn("RSBA", d2))
        assertNull(percentileOn("RSBB", d2))
    }

    @Test
    fun `reset clears a stale percentile when a later run no longer qualifies the date`() {
        // Given two symbols ranked on day 2 by a first pass with no peer floor
        val d1 = LocalDate.of(2024, 3, 1)
        val d2 = LocalDate.of(2024, 3, 2)
        quoteRepository.upsertQuotes(
            listOf(
                quote("RSCA", d1, 100.0),
                quote("RSCA", d2, 110.0),
                quote("RSCB", d1, 100.0),
                quote("RSCB", d2, 120.0),
            ),
        )
        quoteRepository.recomputeRelativeStrengthPercentiles(earliest, lookbackBars = 1, minPeers = 1, earliestDate = earliest)
        assertEquals(100.0 * 0.5 / 2, percentileOn("RSCA", d2)!!, 1e-3)

        // When a second pass applies a peer floor the date no longer clears
        quoteRepository.recomputeRelativeStrengthPercentiles(earliest, lookbackBars = 1, minPeers = 3, earliestDate = earliest)

        // Then the previously-set value is reset to null, not left stale
        assertNull(percentileOn("RSCA", d2))
    }

    @Test
    fun `incremental recompute only touches dates on or after fromDate`() {
        // Given three symbols over three days, with returns defined on day 2 and day 3
        val d1 = LocalDate.of(2024, 4, 1)
        val d2 = LocalDate.of(2024, 4, 2)
        val d3 = LocalDate.of(2024, 4, 3)
        quoteRepository.upsertQuotes(
            listOf(
                quote("RSDA", d1, 100.0),
                quote("RSDA", d2, 110.0),
                quote("RSDA", d3, 130.0),
                quote("RSDB", d1, 100.0),
                quote("RSDB", d2, 105.0),
                quote("RSDB", d3, 102.0),
                quote("RSDC", d1, 100.0),
                quote("RSDC", d2, 120.0),
                quote("RSDC", d3, 140.0),
            ),
        )

        // When the recompute is bounded to start at day 3
        quoteRepository.recomputeRelativeStrengthPercentiles(d3, lookbackBars = 1, minPeers = 1, earliestDate = earliest)

        // Then day 3 is ranked (RSDA +18% > RSDC +16.7% > RSDB −2.9%, so RSDC is the middle rank)
        // but day 2 (before fromDate) is left untouched
        assertEquals(100.0 * 2.5 / 3, percentileOn("RSDA", d3)!!, 1e-3)
        assertEquals(100.0 * 1.5 / 3, percentileOn("RSDC", d3)!!, 1e-3)
        assertNull(percentileOn("RSDA", d2))
    }
}
