package com.skrymer.midgaard.service

import com.skrymer.midgaard.integration.ovtlyr.OvtlyrClient
import com.skrymer.midgaard.integration.ovtlyr.OvtlyrPayloadDto
import com.skrymer.midgaard.integration.ovtlyr.OvtlyrQuoteDto
import com.skrymer.midgaard.model.AssetType
import com.skrymer.midgaard.model.OvtlyrSignal
import com.skrymer.midgaard.model.OvtlyrSignalType
import com.skrymer.midgaard.model.Symbol
import com.skrymer.midgaard.repository.OvtlyrSignalRepository
import com.skrymer.midgaard.repository.SymbolRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class OvtlyrBackfillServiceTest {
    private lateinit var symbolRepository: SymbolRepository
    private lateinit var ovtlyrClient: OvtlyrClient
    private lateinit var signalRepository: OvtlyrSignalRepository
    private lateinit var apiKeyService: ApiKeyService
    private lateinit var rateLimiterService: RateLimiterService
    private lateinit var service: OvtlyrBackfillService

    @BeforeEach
    fun setUp() {
        symbolRepository = mock()
        ovtlyrClient = mock()
        signalRepository = mock()
        apiKeyService = mock()
        // Unstubbed: acquirePermit is a no-op on the mock — no real pacing delay in tests.
        rateLimiterService = mock()
        whenever(apiKeyService.getOvtlyrCookieUserId()).thenReturn("u")
        whenever(apiKeyService.getOvtlyrCookieToken()).thenReturn("t")
        whenever(apiKeyService.getOvtlyrProjectId()).thenReturn("p")
        service =
            OvtlyrBackfillService(symbolRepository, ovtlyrClient, signalRepository, apiKeyService, rateLimiterService)
    }

    @Test
    fun `runBackfill fetches each symbol, extracts its signals, and upserts them`() {
        // Given: two symbols, each returning one call from ovtlyr
        whenever(symbolRepository.findAll()).thenReturn(listOf(symbol("AAPL"), symbol("MSFT")))
        whenever(ovtlyrClient.getStockInformation(eq("AAPL"), any())).thenReturn(payload("AAPL", "2026-05-11", "Buy"))
        whenever(ovtlyrClient.getStockInformation(eq("MSFT"), any())).thenReturn(payload("MSFT", "2026-05-12", "Sell"))

        // When
        runBlocking { service.runBackfill().join() }

        // Then: each symbol's extracted signals were upserted
        verify(signalRepository).upsert(listOf(OvtlyrSignal("AAPL", LocalDate.of(2026, 5, 11), OvtlyrSignalType.BUY)))
        verify(signalRepository).upsert(listOf(OvtlyrSignal("MSFT", LocalDate.of(2026, 5, 12), OvtlyrSignalType.SELL)))
    }

    @Test
    fun `a symbol that fails to process does not abort the rest of the run`() {
        // Given: two symbols; processing the first one throws (e.g. a transient DB error)
        whenever(symbolRepository.findAll()).thenReturn(listOf(symbol("AAPL"), symbol("MSFT")))
        whenever(ovtlyrClient.getStockInformation(any(), any()))
            .thenAnswer { payload(it.getArgument(0), "2026-05-12", "Buy") }
        whenever(signalRepository.upsert(any()))
            .thenThrow(RuntimeException("db blip"))
            .thenAnswer { }

        // When
        runBlocking { service.runBackfill().join() }

        // Then: both symbols were attempted — the first failure didn't kill the loop
        verify(signalRepository, times(2)).upsert(any())
        verify(signalRepository).upsert(listOf(OvtlyrSignal("MSFT", LocalDate.of(2026, 5, 12), OvtlyrSignalType.BUY)))
    }

    @Test
    fun `progress reflects totals and completes after the run`() {
        // Given: two symbols, each yielding one signal
        whenever(symbolRepository.findAll()).thenReturn(listOf(symbol("AAPL"), symbol("MSFT")))
        whenever(ovtlyrClient.getStockInformation(any(), any()))
            .thenAnswer { payload(it.getArgument(0), "2026-05-12", "Buy") }

        // When
        runBlocking { service.runBackfill().join() }

        // Then: progress reports the universe size, all symbols processed, and the run finished
        val progress = assertNotNull(service.progress)
        assertEquals(2, progress.total)
        assertEquals(2, progress.processed.get())
        assertEquals(2, progress.signalsWritten.get())
        assertEquals(false, progress.active)
    }

    @Test
    fun `runBackfill does not start a second run while one is already active`() {
        // Given: enough symbols that the first run is still in flight when we re-call
        whenever(symbolRepository.findAll()).thenReturn((1..5).map { symbol("S$it") })
        whenever(ovtlyrClient.getStockInformation(any(), any()))
            .thenAnswer { payload(it.getArgument(0), "2026-05-12", "Buy") }

        // When: a second trigger fires while the first run is active
        val first = service.runBackfill()
        val second = service.runBackfill()

        // Then: the second call returns the in-flight job — no overlapping run launched
        assertSame(first, second)
        runBlocking { first.join() }
    }

    @Test
    fun `runBackfill skips symbols that already have stored signals`() {
        // Given: AAPL is already covered, MSFT is not
        whenever(symbolRepository.findAll()).thenReturn(listOf(symbol("AAPL"), symbol("MSFT")))
        whenever(signalRepository.findDistinctSymbols()).thenReturn(setOf("AAPL"))
        whenever(ovtlyrClient.getStockInformation(any(), any()))
            .thenAnswer { payload(it.getArgument(0), "2026-05-12", "Buy") }

        // When
        runBlocking { service.runBackfill().join() }

        // Then: only the uncovered symbol is fetched — no ovtlyr request spent on AAPL
        verify(ovtlyrClient).getStockInformation(eq("MSFT"), any())
        verify(ovtlyrClient, never()).getStockInformation(eq("AAPL"), any())
    }

    private fun symbol(ticker: String) = Symbol(symbol = ticker, assetType = AssetType.STOCK)

    private fun payload(
        ticker: String,
        date: String,
        finalCalls: String?,
    ) = OvtlyrPayloadDto(quotes = listOf(OvtlyrQuoteDto(ticker, LocalDate.parse(date), finalCalls)))
}
