package com.skrymer.midgaard.e2e

import com.skrymer.midgaard.integrity.BadPrintIntegrityValidator
import com.skrymer.midgaard.jooq.tables.references.QUOTES
import com.skrymer.midgaard.model.Quote
import com.skrymer.midgaard.repository.QuoteRepository
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

class BadPrintIntegrityValidatorE2ETest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var validator: BadPrintIntegrityValidator

    @Autowired
    private lateinit var quoteRepository: QuoteRepository

    @Autowired
    private lateinit var dsl: DSLContext

    @BeforeEach
    fun clearQuotes() {
        dsl.deleteFrom(QUOTES).execute()
    }

    @Test
    fun `validate flags a symbol containing a V-shape bad-print bar`() {
        // Given: one symbol with three consecutive bars forming a clean V-shape
        // (close 1 -> 10 -> 1), the canonical bad-print signature.
        quoteRepository.upsertQuotes(
            listOf(
                quoteAt("BAD", LocalDate.of(2020, 1, 2), close = BigDecimal("1.00")),
                quoteAt("BAD", LocalDate.of(2020, 1, 3), close = BigDecimal("10.00")),
                quoteAt("BAD", LocalDate.of(2020, 1, 6), close = BigDecimal("1.00")),
            ),
        )

        // When
        val violations = validator.validate()

        // Then: one rolled-up violation with the offending symbol sampled
        assertEquals(1, violations.size)
        val v = violations.single()
        assertEquals("V1", v.invariant)
        assertEquals(1, v.count)
        assertEquals(listOf("BAD"), v.sampleSymbols)
    }

    @Test
    fun `validate rolls 12 V-shape symbols into one violation with sample capped at 10`() {
        // Given: 12 distinct symbols, each with a V-shape bad-print bar
        val symbols = ('A'..'L').map { "BAD$it" }
        val rows =
            symbols.flatMap { sym ->
                listOf(
                    quoteAt(sym, LocalDate.of(2020, 1, 2), close = BigDecimal("1.00")),
                    quoteAt(sym, LocalDate.of(2020, 1, 3), close = BigDecimal("10.00")),
                    quoteAt(sym, LocalDate.of(2020, 1, 6), close = BigDecimal("1.00")),
                )
            }
        quoteRepository.upsertQuotes(rows)

        // When
        val violations = validator.validate()

        // Then: single roll-up — count = 12, samples = first 10 alphabetical
        assertEquals(1, violations.size)
        val v = violations.single()
        assertEquals(12, v.count)
        assertEquals(symbols.take(10), v.sampleSymbols)
    }

    @Test
    fun `validate returns no violations when quotes is empty`() {
        // Given: nothing in quotes (BeforeEach already cleared)

        // When
        val violations = validator.validate()

        // Then: no violation, not a count=0 row
        assertEquals(emptyList(), violations)
    }

    @Test
    fun `validate ignores a 5x jump whose reversion is too shallow`() {
        // Given: 1 -> 5 -> 2 — meets the 5x jump threshold, but next close is
        // 40% of the spike (above the 20% reversion threshold). Classified as a
        // real run-up that partially faded, not a corrupt single-bar spike.
        quoteRepository.upsertQuotes(
            listOf(
                quoteAt("PRTL", LocalDate.of(2020, 1, 2), close = BigDecimal("1.00")),
                quoteAt("PRTL", LocalDate.of(2020, 1, 3), close = BigDecimal("5.00")),
                quoteAt("PRTL", LocalDate.of(2020, 1, 6), close = BigDecimal("2.00")),
            ),
        )

        // When
        val violations = validator.validate()

        // Then: no violation — next close >20% of the spike means it is not a V
        assertEquals(emptyList(), violations)
    }

    @Test
    fun `validate flags exactly at the 5x jump and 20 percent reversion boundary`() {
        // Given: 1 -> 5 -> 1 — exactly at the 5x threshold (>=) and exactly at
        // the 20% reversion threshold (<=). Locks the inclusive comparison so a
        // future flip to strict gt/lt is caught.
        quoteRepository.upsertQuotes(
            listOf(
                quoteAt("EDGE", LocalDate.of(2020, 1, 2), close = BigDecimal("1.00")),
                quoteAt("EDGE", LocalDate.of(2020, 1, 3), close = BigDecimal("5.00")),
                quoteAt("EDGE", LocalDate.of(2020, 1, 6), close = BigDecimal("1.00")),
            ),
        )

        // When
        val violations = validator.validate()

        // Then: counts as a violation
        assertEquals(1, violations.size)
        assertEquals(listOf("EDGE"), violations.single().sampleSymbols)
    }

    @Test
    fun `validate ignores a spike just under the 5x jump threshold`() {
        // Given: 1 -> 4.99 -> 1 — fails the 5x threshold by a hair. Pinned so a
        // future widening of the threshold below 5x is caught.
        quoteRepository.upsertQuotes(
            listOf(
                quoteAt("UNDR", LocalDate.of(2020, 1, 2), close = BigDecimal("1.00")),
                quoteAt("UNDR", LocalDate.of(2020, 1, 3), close = BigDecimal("4.99")),
                quoteAt("UNDR", LocalDate.of(2020, 1, 6), close = BigDecimal("1.00")),
            ),
        )

        // When
        val violations = validator.validate()

        // Then: no violation
        assertEquals(emptyList(), violations)
    }

    @Test
    fun `validate counts a symbol with two V-shape bars as one offender`() {
        // Given: one symbol containing two distinct V-shape bars on different
        // dates. selectDistinct(symbol) means the symbol is reported once.
        quoteRepository.upsertQuotes(
            listOf(
                quoteAt("TWOV", LocalDate.of(2020, 1, 2), close = BigDecimal("1.00")),
                quoteAt("TWOV", LocalDate.of(2020, 1, 3), close = BigDecimal("10.00")),
                quoteAt("TWOV", LocalDate.of(2020, 1, 6), close = BigDecimal("1.00")),
                quoteAt("TWOV", LocalDate.of(2020, 1, 7), close = BigDecimal("1.00")),
                quoteAt("TWOV", LocalDate.of(2020, 1, 8), close = BigDecimal("20.00")),
                quoteAt("TWOV", LocalDate.of(2020, 1, 9), close = BigDecimal("1.00")),
            ),
        )

        // When
        val violations = validator.validate()

        // Then: one violation, count = 1 (the symbol, not the bar)
        assertEquals(1, violations.size)
        val v = violations.single()
        assertEquals(1, v.count)
        assertEquals(listOf("TWOV"), v.sampleSymbols)
    }

    @Test
    fun `validate ignores a small spike-then-revert below the jump threshold`() {
        // Given: 1 -> 4 -> 1 — a full V-shape, but the jump is only 4x. Under
        // the 5x threshold; classified as ordinary volatility, not a bad print.
        quoteRepository.upsertQuotes(
            listOf(
                quoteAt("SMLV", LocalDate.of(2020, 1, 2), close = BigDecimal("1.00")),
                quoteAt("SMLV", LocalDate.of(2020, 1, 3), close = BigDecimal("4.00")),
                quoteAt("SMLV", LocalDate.of(2020, 1, 6), close = BigDecimal("1.00")),
            ),
        )

        // When
        val violations = validator.validate()

        // Then: no violation — under the 5x jump threshold
        assertEquals(emptyList(), violations)
    }

    @Test
    fun `validate ignores a legitimate 10x jump that holds at the new level`() {
        // Given: 1 -> 10 -> 10 — a real earnings/news move that holds. Spike
        // threshold met (10x prev) but no reversion, so it is not a bad print.
        quoteRepository.upsertQuotes(
            listOf(
                quoteAt("GOOD", LocalDate.of(2020, 1, 2), close = BigDecimal("1.00")),
                quoteAt("GOOD", LocalDate.of(2020, 1, 3), close = BigDecimal("10.00")),
                quoteAt("GOOD", LocalDate.of(2020, 1, 6), close = BigDecimal("10.00")),
            ),
        )

        // When
        val violations = validator.validate()

        // Then: no violation — the bar after the jump must revert for it to count
        assertEquals(emptyList(), violations)
    }

    private fun quoteAt(
        symbol: String,
        date: LocalDate,
        close: BigDecimal,
    ): Quote =
        Quote(
            symbol = symbol,
            date = date,
            open = close,
            high = close,
            low = close,
            close = close,
            volume = 100L,
        )
}
