package com.skrymer.midgaard.service

import com.skrymer.midgaard.repository.QuoteRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import java.time.LocalDate

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
        verify(quoteRepository, org.mockito.kotlin.never()).recomputeRelativeStrengthPercentiles(
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
            org.mockito.kotlin.any(),
        )
    }
}
