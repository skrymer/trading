package com.skrymer.midgaard.service

import com.skrymer.midgaard.repository.QuoteRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.concurrent.CountDownLatch
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class QualityPercentileServiceTest {
    @Test
    fun `recomputeAll runs the full pass from the earliest trusted date with the peer floor`() {
        // Given a service over a repository
        val quoteRepository: QuoteRepository = mock()
        val service = QualityPercentileService(quoteRepository)

        // When a full recompute is requested
        service.recomputeAll()

        // Then the pass runs from the earliest date with the design constants
        verify(quoteRepository).recomputeQualityPercentiles(
            QualityPercentileService.EARLIEST_DATE,
            QualityPercentileService.MIN_PEERS,
            QualityPercentileService.EARLIEST_DATE,
        )
    }

    @Test
    fun `recomputeAll records the last-run row count for the UI`() {
        // Given a recompute that writes 9876 rows
        val quoteRepository: QuoteRepository = mock()
        quoteRepository.stub {
            on { recomputeQualityPercentiles(any(), any(), any()) } doReturn 9876
        }
        val service = QualityPercentileService(quoteRepository)
        assertNull(service.lastRunRowsWritten())

        // When a full recompute runs
        service.recomputeAll()

        // Then the written count is retained for the UI to surface
        assertEquals(9876, service.lastRunRowsWritten())
    }

    @Test
    fun `recomputeAllAsync runs the full recompute off the calling thread`() =
        runBlocking {
            // Given a service over a repository
            val quoteRepository: QuoteRepository = mock()
            val service = QualityPercentileService(quoteRepository)

            // When the async trigger is fired and its job awaited
            service.recomputeAllAsync().join()

            // Then the full recompute still runs to completion
            verify(quoteRepository).recomputeQualityPercentiles(
                QualityPercentileService.EARLIEST_DATE,
                QualityPercentileService.MIN_PEERS,
                QualityPercentileService.EARLIEST_DATE,
            )
        }

    @Test
    fun `recomputeAllAsync is active while running, idle after, and never overlaps a second call`() =
        runBlocking {
            // Given a recompute that blocks until released, so the first job stays active
            val latch = CountDownLatch(1)
            val quoteRepository: QuoteRepository = mock()
            quoteRepository.stub {
                on { recomputeQualityPercentiles(any(), any(), any()) } doAnswer {
                    latch.await()
                    0
                }
            }
            val service = QualityPercentileService(quoteRepository)

            // When a second recompute is requested while the first is still running
            val first = service.recomputeAllAsync()
            val second = service.recomputeAllAsync()

            // Then a recompute is reported active, the same in-flight job is returned, and it runs once
            assertTrue(service.isRecomputeActive())
            assertSame(first, second)
            latch.countDown()
            first.join()
            assertFalse(service.isRecomputeActive())
            verify(quoteRepository, times(1)).recomputeQualityPercentiles(any(), any(), any())
        }
}
