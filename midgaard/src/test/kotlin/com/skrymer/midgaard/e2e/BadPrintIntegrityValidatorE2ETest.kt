package com.skrymer.midgaard.e2e

import com.skrymer.midgaard.integrity.BadPrintIntegrityValidator
import com.skrymer.midgaard.integrity.Severity
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
    fun `validate flags a symbol with a split-adjustment jump that holds`() {
        // Given: 1 -> 10 -> 10 — close 10x prior bar AND next bar holds the new
        // level. Canonical split-adjustment-failure signature: the provider
        // never carried the split factor back to historical bars. V1 misses
        // this (no reversion); V2 catches it.
        quoteRepository.upsertQuotes(
            listOf(
                quoteAt("SPLT", LocalDate.of(2020, 1, 2), close = BigDecimal("1.00")),
                quoteAt("SPLT", LocalDate.of(2020, 1, 3), close = BigDecimal("10.00")),
                quoteAt("SPLT", LocalDate.of(2020, 1, 6), close = BigDecimal("10.00")),
            ),
        )

        // When
        val violations = validator.validate()

        // Then: one V2 violation, HIGH severity (recoverable provider issue,
        // not CRITICAL data corruption like V1)
        assertEquals(1, violations.size)
        val v = violations.single()
        assertEquals("V2", v.invariant)
        assertEquals(Severity.HIGH, v.severity)
        assertEquals(1, v.count)
        assertEquals(listOf("SPLT"), v.sampleSymbols)
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
    fun `validate ignores a 5x jump whose reversion is between V1 and V2 thresholds`() {
        // Given: 1 -> 5 -> 2 — meets the 5x jump threshold, but next is 40% of
        // the spike: above V1's 20% reversion ceiling AND below V2's 50% hold
        // floor. The "ambiguous middle" between V1 and V2: not corruption, not
        // a held jump — likely a real run-up that partially faded.
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
    fun `validate ignores a stub first bar followed by the symbol's real listing price`() {
        // Given: $0.0001 stub bar (placeholder, not a real trade) followed by
        // the symbol's first real listing price at $3.56. A "jump" from a stub
        // is a midgaard-side data artefact, not a provider split-adjustment
        // failure. V2 must skip it (prev_close < 1 cent minimum).
        quoteRepository.upsertQuotes(
            listOf(
                quoteAt("STUB", LocalDate.of(2003, 4, 8), close = BigDecimal("0.0001")),
                quoteAt("STUB", LocalDate.of(2003, 4, 15), close = BigDecimal("3.5620")),
                quoteAt("STUB", LocalDate.of(2003, 4, 16), close = BigDecimal("3.3149")),
            ),
        )

        // When
        val violations = validator.validate()

        // Then: no violation — stub-bar prev_close is below the 1-cent floor
        assertEquals(emptyList(), violations)
    }

    @Test
    fun `validate flags exactly at the V2 50 percent hold boundary`() {
        // Given: 1 -> 10 -> 5.00 — close 10x prev AND next is exactly 50% of
        // the spike. Locks the inclusive ge(0.50) on V2 so a future flip to
        // strict gt is caught. Mirrors the V1 boundary test (EDGE).
        quoteRepository.upsertQuotes(
            listOf(
                quoteAt("V2BD", LocalDate.of(2020, 1, 2), close = BigDecimal("1.00")),
                quoteAt("V2BD", LocalDate.of(2020, 1, 3), close = BigDecimal("10.00")),
                quoteAt("V2BD", LocalDate.of(2020, 1, 6), close = BigDecimal("5.00")),
            ),
        )

        // When
        val violations = validator.validate()

        // Then: one V2 violation
        assertEquals(1, violations.size)
        assertEquals("V2", violations.single().invariant)
    }

    @Test
    fun `validate flags a 10x jump that holds under V2 not V1`() {
        // Given: 1 -> 10 -> 10 — close 10x prev AND next holds the level.
        // V1 requires reversion to <= 20% of spike (10/10 = 1.0 fails);
        // V2 catches it as split-adjustment failure (next >= 50% of close).
        quoteRepository.upsertQuotes(
            listOf(
                quoteAt("HOLD", LocalDate.of(2020, 1, 2), close = BigDecimal("1.00")),
                quoteAt("HOLD", LocalDate.of(2020, 1, 3), close = BigDecimal("10.00")),
                quoteAt("HOLD", LocalDate.of(2020, 1, 6), close = BigDecimal("10.00")),
            ),
        )

        // When
        val violations = validator.validate()

        // Then: exactly one V2 violation (HIGH), no V1
        assertEquals(1, violations.size)
        val v = violations.single()
        assertEquals("V2", v.invariant)
        assertEquals(Severity.HIGH, v.severity)
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
