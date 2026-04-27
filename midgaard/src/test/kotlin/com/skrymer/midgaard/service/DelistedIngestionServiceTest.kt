package com.skrymer.midgaard.service

import com.skrymer.midgaard.integration.edgar.EdgarClient
import com.skrymer.midgaard.integration.edgar.dto.EdgarSubmissionDto
import com.skrymer.midgaard.integration.eodhd.EodhdFundamentalsClient
import com.skrymer.midgaard.integration.eodhd.EodhdSymbolListClient
import com.skrymer.midgaard.integration.eodhd.dto.EodhdDelistedSymbolDto
import com.skrymer.midgaard.integration.eodhd.dto.EodhdFundamentalsResponse
import com.skrymer.midgaard.integration.eodhd.dto.EodhdGeneralSection
import com.skrymer.midgaard.model.Symbol
import com.skrymer.midgaard.repository.SymbolRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end behaviour test for the delisted-ingest pipeline. Exercises:
 *   - Common Stock filtering at the catalogue boundary
 *   - Enrichment with CIK + delistedDate from cached fundamentals
 *   - Sector resolution via EDGAR's SIC code + the static GICS map
 *   - Fallback to "INDUSTRIALS" when CIK is missing or EDGAR returns no SIC
 *   - Hand-off to `IngestionService.runParallelInitialIngest` for OHLCV/indicator fetch
 *
 * The `liquidityFilter` is replaced with an identity passthrough mock — that
 * service has its own dedicated test, so this fixture stays focused on the
 * orchestration logic.
 */
class DelistedIngestionServiceTest {
    @Test
    fun `pipeline filters non-common-stock then enriches and persists survivors`() {
        // Given: catalogue has one stock + one ETF + one warrant; only the stock should survive
        val catalogue =
            listOf(
                dto("ABCD", type = "Common Stock"),
                dto("ETFE", type = "ETF"),
                dto("WARR", type = "Warrant"),
            )
        val fixture =
            fixture(
                catalogue = catalogue,
                fundamentals =
                    mapOf(
                        "ABCD" to fundamentals(cik = "320193", delistedDate = "2020-06-15"),
                    ),
                edgar = mapOf("320193" to edgar(sic = "3571")), // Computers → TECHNOLOGY
            )

        // When
        runBlocking { fixture.runDirect() }

        // Then: SymbolRepository receives one upsert with sector=TECHNOLOGY, delistedAt set, cik populated
        val captor = argumentCaptor<Symbol>()
        verify(fixture.symbolRepository).upsertSymbol(captor.capture())
        val saved = captor.firstValue
        assertEquals("ABCD", saved.symbol)
        assertEquals(LocalDate.of(2020, 6, 15), saved.delistedAt)
        assertEquals("TECHNOLOGY", saved.sector)
        assertEquals("XLK", saved.sectorSymbol)
        assertEquals("320193", saved.cik)
    }

    @Test
    fun `defaults to INDUSTRIALS when CIK is missing from fundamentals`() {
        // Given: a delisted symbol whose EODHD fundamentals don't include a CIK
        val catalogue = listOf(dto("NOCK", type = "Common Stock"))
        val fixture =
            fixture(
                catalogue = catalogue,
                fundamentals = mapOf("NOCK" to fundamentals(cik = null, delistedDate = "2018-03-01")),
                edgar = emptyMap(),
            )

        // When
        runBlocking { fixture.runDirect() }

        // Then: ingest didn't fail, sector defaults to INDUSTRIALS
        val captor = argumentCaptor<Symbol>()
        verify(fixture.symbolRepository).upsertSymbol(captor.capture())
        assertEquals("INDUSTRIALS", captor.firstValue.sector)
        assertNull(captor.firstValue.cik)
    }

    @Test
    fun `defaults to INDUSTRIALS when EDGAR has no submission for the CIK`() {
        // Given: EODHD returns a CIK but EDGAR returns null (recently delisted, foreign filer, etc.)
        val catalogue = listOf(dto("FRGN", type = "Common Stock"))
        val fixture =
            fixture(
                catalogue = catalogue,
                fundamentals = mapOf("FRGN" to fundamentals(cik = "9999999", delistedDate = "2022-09-30")),
                edgar = emptyMap(),
            )

        // When
        runBlocking { fixture.runDirect() }

        // Then: sector defaults to INDUSTRIALS, cik still persisted (so a future run could retry)
        val captor = argumentCaptor<Symbol>()
        verify(fixture.symbolRepository).upsertSymbol(captor.capture())
        assertEquals("INDUSTRIALS", captor.firstValue.sector)
        assertEquals("9999999", captor.firstValue.cik)
    }

    @Test
    fun `skips symbols whose fundamentals lack a delisting date`() {
        // Given: fundamentals returned but General.DelistedDate is null (rare data gap)
        val catalogue = listOf(dto("GAPE", type = "Common Stock"))
        val fixture =
            fixture(
                catalogue = catalogue,
                fundamentals = mapOf("GAPE" to fundamentals(cik = "1", delistedDate = null)),
                edgar = mapOf("1" to edgar(sic = "1311")),
            )

        // When
        runBlocking { fixture.runDirect() }

        // Then: nothing persisted — delisting date is mandatory for backtest cutoff
        verify(fixture.symbolRepository, never()).upsertSymbol(any())
    }

    @Test
    fun `aborts when symbol-list fetch fails`() {
        // Given: EodhdSymbolListClient returns null (HTTP failure)
        val fixture =
            fixture(
                catalogueResponse = null,
                fundamentals = emptyMap(),
                edgar = emptyMap(),
            )

        // When
        runBlocking { fixture.runDirect() }

        // Then: nothing persisted, lastRunStats reflects failure
        verify(fixture.symbolRepository, never()).upsertSymbol(any())
        assertNotNull(fixture.service.lastRunStats)
        assertTrue(fixture.service.lastRunStats!!.failed)
    }

    @Test
    fun `hands persisted symbol list off to runParallelInitialIngest`() {
        // Given: two delisted survivors
        val catalogue = listOf(dto("AAA", type = "Common Stock"), dto("BBB", type = "Common Stock"))
        val fixture =
            fixture(
                catalogue = catalogue,
                fundamentals =
                    mapOf(
                        "AAA" to fundamentals(cik = "1", delistedDate = "2019-01-15"),
                        "BBB" to fundamentals(cik = "2", delistedDate = "2021-04-30"),
                    ),
                edgar = mapOf("1" to edgar("3571"), "2" to edgar("6021")),
            )

        // When
        runBlocking { fixture.runDirect() }

        // Then: bulk ingest pipeline invoked exactly once with both symbols
        verify(fixture.ingestionService).runParallelInitialIngest(eq("delisted-bootstrap"), eq(listOf("AAA", "BBB")))
    }

    // ── Test fixture ─────────────────────────────────────────────────────────────

    private data class Fixture(
        val service: DelistedIngestionService,
        val symbolListClient: EodhdSymbolListClient,
        val fundamentalsClient: EodhdFundamentalsClient,
        val edgarClient: EdgarClient,
        val symbolRepository: SymbolRepository,
        val ingestionService: IngestionService,
    ) {
        // Drives the pipeline synchronously instead of waiting on the background
        // coroutine. `runPipeline` is `internal` for exactly this reason.
        suspend fun runDirect() = service.runPipeline()
    }

    private fun fixture(
        catalogue: List<EodhdDelistedSymbolDto> = emptyList(),
        catalogueResponse: List<EodhdDelistedSymbolDto>? = catalogue,
        fundamentals: Map<String, EodhdFundamentalsResponse> = emptyMap(),
        edgar: Map<String, EdgarSubmissionDto> = emptyMap(),
    ): Fixture {
        val symbolListClient: EodhdSymbolListClient = mock()
        val fundamentalsClient: EodhdFundamentalsClient = mock()
        val edgarClient: EdgarClient = mock()
        val liquidityFilter: DelistedLiquidityFilter = mock()
        val symbolRepository: SymbolRepository = mock()
        val ingestionService: IngestionService = mock()

        runBlocking {
            whenever(symbolListClient.getDelistedSymbols()).thenReturn(catalogueResponse)
            fundamentals.forEach { (code, response) ->
                whenever(fundamentalsClient.fetch(eq(code), eq("$code.US"))).thenReturn(response)
            }
            edgar.forEach { (cik, submission) ->
                whenever(edgarClient.getSubmission(eq(cik))).thenReturn(submission)
            }
            // Liquidity filter is identity — passes everything through. The dedicated
            // DelistedLiquidityFilterTest covers its actual filtering behaviour.
            whenever(liquidityFilter.filter(any(), any())).thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                invocation.arguments[0] as List<DelistedCandidate>
            }
        }

        val service =
            DelistedIngestionService(
                symbolListClient = symbolListClient,
                fundamentalsClient = fundamentalsClient,
                edgarClient = edgarClient,
                liquidityFilter = liquidityFilter,
                symbolRepository = symbolRepository,
                ingestionService = ingestionService,
            )
        return Fixture(service, symbolListClient, fundamentalsClient, edgarClient, symbolRepository, ingestionService)
    }

    private fun dto(
        code: String,
        type: String,
        name: String = "$code Corp",
    ): EodhdDelistedSymbolDto = EodhdDelistedSymbolDto(code = code, name = name, type = type, exchange = "US")

    private fun fundamentals(
        cik: String?,
        delistedDate: String?,
    ): EodhdFundamentalsResponse =
        EodhdFundamentalsResponse(
            general = EodhdGeneralSection(cik = cik, delistedDate = delistedDate, isDelistedRaw = "1"),
        )

    private fun edgar(sic: String): EdgarSubmissionDto = EdgarSubmissionDto(cik = "320193", sic = sic, sicDescription = "TEST")
}
