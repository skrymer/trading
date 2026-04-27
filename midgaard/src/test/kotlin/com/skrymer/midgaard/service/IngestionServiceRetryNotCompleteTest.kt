package com.skrymer.midgaard.service

import com.skrymer.midgaard.integration.CompanyInfoProvider
import com.skrymer.midgaard.integration.EarningsProvider
import com.skrymer.midgaard.integration.IndicatorProvider
import com.skrymer.midgaard.integration.OhlcvProvider
import com.skrymer.midgaard.repository.IngestionStatusRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import kotlin.test.assertEquals

/**
 * Behaviour test for `IngestionService.retryNotComplete`. Verifies that the
 * service consults the repository's "not-complete" finder (which covers
 * FAILED + PENDING + missing-row) and hands the result to the bulk-initial
 * pipeline. The bulk pipeline itself runs asynchronously on a background
 * coroutine, so we check that the work was QUEUED via BulkProgress.total
 * rather than waiting for it to finish.
 */
class IngestionServiceRetryNotCompleteTest {
    @Test
    fun `retryNotComplete pulls the not-complete list from the repository and queues a bulk run`() {
        // Given: the repository reports three symbols not yet ingested
        val notComplete = listOf("AAPL", "DELISTED1", "DELISTED2")
        val ingestionStatusRepository: IngestionStatusRepository = mock()
        ingestionStatusRepository.stub { on { findNotCompleteSymbols() } doReturn notComplete }
        val service = serviceWith(ingestionStatusRepository)

        // When
        service.retryNotComplete()

        // Then: repository was consulted via the not-complete finder
        verify(ingestionStatusRepository).findNotCompleteSymbols()
        // And the bulk pipeline picked up the right total — proves the symbols flowed through
        assertEquals(3, service.bulkProgress?.total)
    }

    @Test
    fun `retryNotComplete with empty list still queues a no-op run rather than throwing`() {
        // Given: nothing left to ingest
        val ingestionStatusRepository: IngestionStatusRepository = mock()
        ingestionStatusRepository.stub { on { findNotCompleteSymbols() } doReturn emptyList() }
        val service = serviceWith(ingestionStatusRepository)

        // When
        service.retryNotComplete()

        // Then: progress is initialised with 0 total — no exception
        assertEquals(0, service.bulkProgress?.total)
    }

    private fun serviceWith(ingestionStatusRepository: IngestionStatusRepository): IngestionService =
        IngestionService(
            ohlcv = mock<OhlcvProvider>(),
            dailyUpdateOhlcv = mock<OhlcvProvider>(),
            indicators = mock<IndicatorProvider>(),
            earnings = mock<EarningsProvider>(),
            companyInfo = mock<CompanyInfoProvider>(),
            indicatorCalculator =
                mock<IndicatorCalculator> {
                    on { calculateAllEMAs(any()) } doReturn emptyMap()
                    on { calculateDonchianUpper(any(), any()) } doReturn emptyList()
                    on { calculateATR(any(), any()) } doReturn emptyList()
                    on { calculateADX(any(), any()) } doReturn emptyList()
                },
            quoteRepository = mock(),
            earningsRepository = mock(),
            symbolRepository = mock(),
            ingestionStatusRepository = ingestionStatusRepository,
            indicatorsMode = IndicatorsMode.LOCAL,
        )
}
