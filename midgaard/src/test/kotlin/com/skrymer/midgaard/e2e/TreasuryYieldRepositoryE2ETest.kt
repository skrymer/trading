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
    fun `status reports the stored row count and the latest date for a maturity`() {
        // Given three yields persisted for one maturity (distinct key for container isolation)
        repository.upsert(
            listOf(
                TreasuryYield("STAT3M", LocalDate.of(2024, 1, 2), 5.20),
                TreasuryYield("STAT3M", LocalDate.of(2024, 1, 4), 5.22),
                TreasuryYield("STAT3M", LocalDate.of(2024, 1, 3), 5.21),
            ),
        )

        // When
        val status = repository.status("STAT3M")

        // Then the count and the most-recent date are reported (so the UI can show populated-and-fresh)
        assertEquals(3, status.count)
        assertEquals(LocalDate.of(2024, 1, 4), status.latestDate)
    }

    @Test
    fun `status of an un-ingested maturity is zero rows and no date`() {
        // Given nothing persisted for this maturity (the freshly-migrated empty-table case)

        // When
        val status = repository.status("NOPE3M")

        // Then the count is zero and the latest date is null — the UI shows "not ingested"
        assertEquals(0, status.count)
        assertEquals(null, status.latestDate)
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
