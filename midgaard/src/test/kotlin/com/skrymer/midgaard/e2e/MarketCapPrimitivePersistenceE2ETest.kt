package com.skrymer.midgaard.e2e

import com.skrymer.midgaard.model.Fundamental
import com.skrymer.midgaard.model.Quote
import com.skrymer.midgaard.model.Split
import com.skrymer.midgaard.repository.FundamentalsRepository
import com.skrymer.midgaard.repository.QuoteRepository
import com.skrymer.midgaard.repository.SplitRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * Integration coverage for the three stored inputs of the point-in-time market cap (ADR 0027): the raw
 * close on a quote, the split-adjusted share count on a fundamental, and a symbol's split history.
 * Distinct symbols per test isolate against the shared container.
 */
class MarketCapPrimitivePersistenceE2ETest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var quoteRepository: QuoteRepository

    @Autowired
    private lateinit var fundamentalsRepository: FundamentalsRepository

    @Autowired
    private lateinit var splitRepository: SplitRepository

    @Test
    fun `a quote's raw close persists alongside its adjusted close`() {
        // Given a bar whose un-adjusted raw close (364) differs from the adjusted close (90)
        val quote =
            Quote(
                symbol = "CAPQ",
                date = LocalDate.of(2020, 6, 30),
                open = BigDecimal("90.0000"),
                high = BigDecimal("92.0000"),
                low = BigDecimal("89.0000"),
                close = BigDecimal("90.0000"),
                rawClose = BigDecimal("364.0000"),
                volume = 1_000_000L,
            )
        quoteRepository.upsertQuotes(listOf(quote))

        // When read back
        val stored = quoteRepository.findBySymbol("CAPQ").single()

        // Then the raw close round-trips independently of the adjusted close
        assertEquals(BigDecimal("364.0000"), stored.rawClose)
        assertEquals(BigDecimal("90.0000"), stored.close)
    }

    @Test
    fun `a fundamental's shares outstanding persists`() {
        // Given a fundamental carrying a split-adjusted share count
        val fundamental =
            Fundamental(
                symbol = "CAPF",
                fiscalDateEnding = LocalDate.of(2024, 3, 31),
                filingDate = LocalDate.of(2024, 5, 2),
                sharesOutstanding = 16_000_000_000L,
            )
        fundamentalsRepository.upsert(listOf(fundamental))

        // When read back
        val stored = fundamentalsRepository.findBySymbol("CAPF").single()

        // Then the share count round-trips
        assertEquals(16_000_000_000L, stored.sharesOutstanding)
    }

    @Test
    fun `splits persist and read back ordered by ex-date ascending`() {
        // Given two splits stored out of order
        splitRepository.replaceForSymbol(
            "CAPS",
            listOf(
                Split("CAPS", LocalDate.of(2020, 8, 31), 4.0),
                Split("CAPS", LocalDate.of(2014, 6, 9), 7.0),
            ),
        )

        // When read back
        val stored = splitRepository.findBySymbol("CAPS")

        // Then both round-trip, oldest ex-date first
        assertEquals(
            listOf(
                Split("CAPS", LocalDate.of(2014, 6, 9), 7.0),
                Split("CAPS", LocalDate.of(2020, 8, 31), 4.0),
            ),
            stored,
        )
    }

    @Test
    fun `replaceForSymbol replaces a symbol's prior splits wholesale`() {
        // Given a symbol with one stored split
        splitRepository.replaceForSymbol("CAPR", listOf(Split("CAPR", LocalDate.of(2014, 6, 9), 7.0)))

        // When re-ingested with a different set (the provider's current view)
        splitRepository.replaceForSymbol("CAPR", listOf(Split("CAPR", LocalDate.of(2020, 8, 31), 4.0)))

        // Then only the latest set remains — no stale rows accumulate
        assertEquals(listOf(Split("CAPR", LocalDate.of(2020, 8, 31), 4.0)), splitRepository.findBySymbol("CAPR"))
    }
}
