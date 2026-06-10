package com.skrymer.midgaard.e2e

import com.skrymer.midgaard.model.Fundamental
import com.skrymer.midgaard.repository.FundamentalsRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * Integration coverage for point-in-time fundamentals persistence (ADR 0019 L1). Distinct symbols per
 * test isolate against the shared container (no per-test rollback). BigDecimal literals carry scale 4
 * to match the `DECIMAL(38,4)` read-back so whole-object equality holds.
 */
class FundamentalsRepositoryE2ETest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var repository: FundamentalsRepository

    @Test
    fun `saved fundamentals are returned by findBySymbol, ordered by fiscal date ascending`() {
        // Given two quarters for one symbol, persisted newest-first
        val newer =
            Fundamental(
                symbol = "FUNDA",
                fiscalDateEnding = LocalDate.of(2024, 6, 30),
                filingDate = LocalDate.of(2024, 8, 1),
                grossProfit = BigDecimal("42000.0000"),
                totalAssets = BigDecimal("350000.0000"),
            )
        val older =
            Fundamental(
                symbol = "FUNDA",
                fiscalDateEnding = LocalDate.of(2024, 3, 31),
                filingDate = LocalDate.of(2024, 5, 2),
                grossProfit = BigDecimal("40000.0000"),
                totalAssets = BigDecimal("345000.0000"),
            )
        repository.upsert(listOf(newer, older))

        // When
        val result = repository.findBySymbol("FUNDA")

        // Then both round-trip, oldest first
        assertEquals(listOf(older, newer), result)
    }

    @Test
    fun `upsert on the same symbol and fiscal date overwrites the prior values`() {
        // Given a fundamental persisted, then re-filed with a restated gross profit and later filing date
        val original =
            Fundamental(
                symbol = "FUNDB",
                fiscalDateEnding = LocalDate.of(2024, 3, 31),
                filingDate = LocalDate.of(2024, 5, 2),
                grossProfit = BigDecimal("40000.0000"),
            )
        repository.upsert(listOf(original))
        repository.upsert(listOf(original.copy(grossProfit = BigDecimal("41000.0000"), filingDate = LocalDate.of(2024, 9, 1))))

        // When
        val result = repository.findBySymbol("FUNDB")

        // Then a single row reflects the restated values (conflict on (symbol, fiscal_date_ending))
        assertEquals(1, result.size)
        assertEquals(BigDecimal("41000.0000"), result.first().grossProfit)
        assertEquals(LocalDate.of(2024, 9, 1), result.first().filingDate)
    }

    @Test
    fun `persists a line item whose magnitude exceeds the old DECIMAL 19 4 ceiling`() {
        // Given an EODHD bad-print value ~1e16 (real example: ASND cost_of_revenue), above DECIMAL(19,4)'s
        // ~1e15 cap — which previously failed the whole batch and lost the symbol's good fields too
        val badPrint = BigDecimal("10849442617772000.0000")
        val fundamental =
            Fundamental(
                symbol = "FUNDBIG",
                fiscalDateEnding = LocalDate.of(2018, 9, 30),
                filingDate = LocalDate.of(2018, 11, 28),
                grossProfit = BigDecimal("20000.0000"),
                costOfRevenue = badPrint,
                totalAssets = BigDecimal("335234000.0000"),
            )
        repository.upsert(listOf(fundamental))

        // When
        val result = repository.findBySymbol("FUNDBIG").single()

        // Then the oversized value round-trips and the good fields persist alongside it
        assertEquals(badPrint, result.costOfRevenue)
        assertEquals(BigDecimal("20000.0000"), result.grossProfit)
        assertEquals(BigDecimal("335234000.0000"), result.totalAssets)
    }
}
