package com.skrymer.midgaard.service

import com.skrymer.midgaard.repository.QuoteRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import kotlin.test.assertSame

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
    fun `recomputeAllAsync returns the in-flight job instead of launching a second overlapping pass`() =
        runBlocking {
            // Given a recompute that blocks until released, so the first job stays active
            val latch = CountDownLatch(1)
            val quoteRepository: QuoteRepository = mock()
            quoteRepository.stub {
                on { recomputeRelativeStrengthPercentiles(any(), any(), any(), any()) } doAnswer { latch.await() }
            }
            val service = RelativeStrengthService(quoteRepository)

            // When a second recompute is requested while the first is still running
            val first = service.recomputeAllAsync()
            val second = service.recomputeAllAsync()

            // Then the same in-flight job is returned and the pass runs only once
            assertSame(first, second)
            latch.countDown()
            first.join()
            verify(quoteRepository, times(1)).recomputeRelativeStrengthPercentiles(any(), any(), any(), any())
        }

    @Test
    fun `recomputeRecent recomputes only the trailing buffer before the latest date`() {
        // Given the latest quote in the universe is 2024-06-20
        val quoteRepository: QuoteRepository = mock()
        quoteRepository.stub { on { maxQuoteDate() } doReturn LocalDate.of(2024, 6, 20) }
        val service = RelativeStrengthService(quoteRepository)

        // When an incremental recompute is requested
        service.recomputeRecent()

        // Then it recomputes from the latest date minus the buffer, not the whole history
        verify(quoteRepository).recomputeRelativeStrengthPercentiles(
            LocalDate.of(2024, 6, 20).minusDays(RelativeStrengthService.RECENT_BUFFER_DAYS),
            RelativeStrengthService.LOOKBACK_BARS,
            RelativeStrengthService.MIN_PEERS,
            RelativeStrengthService.EARLIEST_DATE,
        )
    }

    @Test
    fun `recomputeRecent does nothing when the universe is empty`() {
        // Given no quotes exist
        val quoteRepository: QuoteRepository = mock()
        quoteRepository.stub { on { maxQuoteDate() } doReturn null }
        val service = RelativeStrengthService(quoteRepository)

        // When an incremental recompute is requested
        service.recomputeRecent()

        // Then no recompute is attempted
        verify(quoteRepository, never()).recomputeRelativeStrengthPercentiles(any(), any(), any(), any())
    }
}
