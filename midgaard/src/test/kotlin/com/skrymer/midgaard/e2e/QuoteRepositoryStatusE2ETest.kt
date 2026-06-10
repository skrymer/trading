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
import kotlin.test.assertTrue

/**
 * Integration coverage for the cross-sectional freshness status (relative strength / quality) the
 * ingestion dashboard shows. Exercises the `max(quote_date)` vs `max(quote_date) filter(...)` comparison
 * against a real Postgres.
 */
class QuoteRepositoryStatusE2ETest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var quoteRepository: QuoteRepository

    @Autowired
    private lateinit var dsl: DSLContext

    @BeforeEach
    fun clear() {
        dsl.execute("DELETE FROM quotes")
    }

    private fun quote(
        symbol: String,
        date: LocalDate,
        relativeStrengthPercentile: BigDecimal? = null,
        qualityPercentile: BigDecimal? = null,
    ) = Quote(
        symbol = symbol,
        date = date,
        open = BigDecimal.ONE,
        high = BigDecimal.ONE,
        low = BigDecimal.ONE,
        close = BigDecimal.ONE,
        volume = 0L,
        relativeStrengthPercentile = relativeStrengthPercentile,
        qualityPercentile = qualityPercentile,
    )

    @Test
    fun `relativeStrengthStatus is stale when newer quotes lack the percentile`() {
        // Given quotes through Jan 3, but relative strength computed only through Jan 2 (Jan 3 re-ingested,
        // not yet recomputed)
        quoteRepository.upsertQuotes(
            listOf(
                quote("RSA", LocalDate.of(2024, 1, 1), BigDecimal("10.0000")),
                quote("RSA", LocalDate.of(2024, 1, 2), BigDecimal("20.0000")),
                quote("RSA", LocalDate.of(2024, 1, 3), null),
            ),
        )

        // When
        val status = quoteRepository.relativeStrengthStatus()

        // Then it is stale, populated through Jan 2 while quotes run to Jan 3
        assertTrue(status.stale)
        assertEquals(LocalDate.of(2024, 1, 2), status.latestPopulated)
        assertEquals(LocalDate.of(2024, 1, 3), status.latestQuote)
    }

    @Test
    fun `qualityPercentileStatus reads the quality column independently of relative strength`() {
        // Given quotes with quality populated up to the latest bar, but relative strength never computed
        quoteRepository.upsertQuotes(
            listOf(
                quote("QA", LocalDate.of(2024, 2, 1), qualityPercentile = BigDecimal("10.0000")),
                quote("QA", LocalDate.of(2024, 2, 2), qualityPercentile = BigDecimal("20.0000")),
            ),
        )

        // When
        val quality = quoteRepository.qualityPercentileStatus()
        val relativeStrength = quoteRepository.relativeStrengthStatus()

        // Then quality is current (populated to the latest quote), relative strength is missing — distinct columns
        assertTrue(quality.current)
        assertEquals(LocalDate.of(2024, 2, 2), quality.latestPopulated)
        assertTrue(relativeStrength.missing)
    }
}
