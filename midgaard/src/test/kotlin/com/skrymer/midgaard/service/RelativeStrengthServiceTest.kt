package com.skrymer.midgaard.service

import com.skrymer.midgaard.repository.QuoteRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.concurrent.CountDownLatch
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RelativeStrengthServiceTest {
    @Test
    fun `recomputeAll runs the full pass from the earliest trusted date`() {
        // Given a service over a repository
        val quoteRepository: QuoteRepository = mock()
        val service = RelativeStrengthService(quoteRepository)

        // When a full recompute is requested
        service.recomputeAll()

        // Then the pass runs from the earliest date with the design constants
        verify(quoteRepository).recomputeRelativeStrengthPercentiles(
            RelativeStrengthService.EARLIEST_DATE,
            RelativeStrengthService.LOOKBACK_BARS,
            RelativeStrengthService.MIN_PEERS,
            RelativeStrengthService.EARLIEST_DATE,
        )
    }

    @Test
    fun `recomputeAllAsync runs the full recompute off the calling thread`() =
        runBlocking {
            // Given a service over a repository
            val quoteRepository: QuoteRepository = mock()
            val service = RelativeStrengthService(quoteRepository)

            // When the async trigger is fired and its job awaited
            service.recomputeAllAsync().join()

            // Then the full recompute still runs to completion
            verify(quoteRepository).recomputeRelativeStrengthPercentiles(
                RelativeStrengthService.EARLIEST_DATE,
                RelativeStrengthService.LOOKBACK_BARS,
                RelativeStrengthService.MIN_PEERS,
                RelativeStrengthService.EARLIEST_DATE,
            )
        }

    @Test
    fun `recomputeAllAsync is active while running, idle after, and never overlaps a second call`() =
        runBlocking {
            // Given a recompute that blocks until released, so the first job stays active
            val latch = CountDownLatch(1)
            val quoteRepository: QuoteRepository = mock()
            quoteRepository.stub {
                on { recomputeRelativeStrengthPercentiles(any(), any(), any(), any()) } doAnswer {
                    latch.await()
                    0
                }
            }
            val service = RelativeStrengthService(quoteRepository)

            // When a second recompute is requested while the first is still running
            val first = service.recomputeAllAsync()
            val second = service.recomputeAllAsync()

            // Then a recompute is reported active, the same in-flight job is returned, and it runs once
            assertTrue(service.isRecomputeActive())
            assertSame(first, second)
            latch.countDown()
            first.join()
            assertFalse(service.isRecomputeActive())
            verify(quoteRepository, times(1)).recomputeRelativeStrengthPercentiles(any(), any(), any(), any())
        }
}
