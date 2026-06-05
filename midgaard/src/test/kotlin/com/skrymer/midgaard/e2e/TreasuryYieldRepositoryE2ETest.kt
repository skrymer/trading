package com.skrymer.midgaard.e2e

import com.skrymer.midgaard.model.TreasuryYield
import com.skrymer.midgaard.repository.TreasuryYieldRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import kotlin.test.assertEquals

class TreasuryYieldRepositoryE2ETest : AbstractIntegrationTest() {
    @Autowired
    private lateinit var repository: TreasuryYieldRepository

    @Test
    fun `saved treasury yields are returned by findByMaturity, ordered by date`() {
        // Given: two yields for one maturity, persisted newest-first
        val newer = TreasuryYield("US3M", LocalDate.of(2025, 5, 1), 4.2931)
        val older = TreasuryYield("US3M", LocalDate.of(2014, 6, 2), 0.035)
        repository.upsert(listOf(newer, older))

        // When
        val result = repository.findByMaturity("US3M")

        // Then: both round-trip, oldest first
        assertEquals(listOf(older, newer), result)
    }

    @Test
    fun `upsert is idempotent — re-running on an existing key replaces the yield, never duplicates`() {
        // Given: a yield persisted for one maturity+date (a provisional same-day print).
        // A distinct maturity key isolates this test from the shared (non-rolled-back) container.
        val date = LocalDate.of(2025, 5, 1)
        repository.upsert(listOf(TreasuryYield("TEST3M", date, 4.20)))

        // When: the same (maturity, date) is re-ingested with the settled value
        repository.upsert(listOf(TreasuryYield("TEST3M", date, 4.2931)))

        // Then: still one row, yield updated to the latest value
        val result = repository.findByMaturity("TEST3M")
        assertEquals(listOf(TreasuryYield("TEST3M", date, 4.2931)), result)
    }
}
