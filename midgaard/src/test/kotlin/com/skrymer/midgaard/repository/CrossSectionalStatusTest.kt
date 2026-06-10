package com.skrymer.midgaard.repository

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrossSectionalStatusTest {
    @Test
    fun `missing when no rows are populated`() {
        // Given a metric the recompute has never populated
        val status = CrossSectionalStatus(populatedRows = 0, latestPopulated = null, latestQuote = LocalDate.of(2024, 1, 5))

        // Then it reports missing — nothing computed yet
        assertTrue(status.missing)
        assertFalse(status.stale)
        assertFalse(status.current)
    }

    @Test
    fun `stale when the populated date is behind the latest ingested quote`() {
        // Given quotes ingested through Jan 5 but the percentile only computed through Jan 3
        val status =
            CrossSectionalStatus(populatedRows = 100, latestPopulated = LocalDate.of(2024, 1, 3), latestQuote = LocalDate.of(2024, 1, 5))

        // Then it reports stale — a re-ingest moved ahead of the last recompute
        assertTrue(status.stale)
        assertFalse(status.missing)
        assertFalse(status.current)
    }

    @Test
    fun `current when the percentile is populated up to the latest quote`() {
        // Given the recompute has caught up to the latest ingested bar
        val status =
            CrossSectionalStatus(populatedRows = 100, latestPopulated = LocalDate.of(2024, 1, 5), latestQuote = LocalDate.of(2024, 1, 5))

        // Then it reports current
        assertTrue(status.current)
        assertFalse(status.missing)
        assertFalse(status.stale)
    }
}
