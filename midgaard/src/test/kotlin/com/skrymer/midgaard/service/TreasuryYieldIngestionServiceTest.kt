package com.skrymer.midgaard.service

import com.skrymer.midgaard.integration.eodhd.EodhdGovBondClient
import com.skrymer.midgaard.model.TreasuryYield
import com.skrymer.midgaard.repository.TreasuryYieldRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.wheneverBlocking
import java.time.LocalDate
import kotlin.test.assertEquals

class TreasuryYieldIngestionServiceTest {
    private lateinit var govBondClient: EodhdGovBondClient
    private lateinit var repository: TreasuryYieldRepository
    private lateinit var service: TreasuryYieldIngestionService

    @BeforeEach
    fun setUp() {
        govBondClient = mock()
        repository = mock()
        service = TreasuryYieldIngestionService(govBondClient, repository)
    }

    @Test
    fun `ingest fetches the 3-month T-bill series and upserts the gross yields`() {
        // Given: the gov-bond client returns the US3M series
        val series =
            listOf(
                TreasuryYield("US3M", LocalDate.of(2014, 6, 2), 0.035),
                TreasuryYield("US3M", LocalDate.of(2025, 5, 1), 4.2931),
            )
        wheneverBlocking { govBondClient.fetchYields(eq("US3M"), eq("US3M.GBOND"), any()) }.thenReturn(series)

        // When
        val count = runBlocking { service.ingest() }

        // Then: the fetched series is persisted as-is (gross), and the stored count is returned
        verify(repository).upsert(series)
        assertEquals(2, count)
    }

    @Test
    fun `ingest persists nothing when the series is unavailable rather than storing an empty series`() {
        // Given: the gov-bond fetch fails (null) — a missing series must never be silently stored
        wheneverBlocking { govBondClient.fetchYields(any(), any(), any()) }.thenReturn(null)

        // When
        val count = runBlocking { service.ingest() }

        // Then: no upsert, zero stored
        verify(repository, never()).upsert(any())
        assertEquals(0, count)
    }
}
