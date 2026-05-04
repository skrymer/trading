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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Behaviour tests for the delisted-discovery pipeline. Verifies the catalogue
 * filter, the per-sector cap walk, fallback sector resolution when CIK or SIC
 * are missing, and the safety budget that bounds API spend.
 *
 * The pipeline does not trigger an OHLCV ingest — discovery is decoupled from
 * the bar-fetch step so the symbol catalogue is the contract.
 */
class DelistedIngestionServiceTest {
    @Test
    fun `filters out non-Common-Stock and non-major-exchange and weird tickers from the catalogue`() {
        // Given: a catalogue mixing real stocks with junk that should be dropped pre-enrichment
        val catalogue =
            listOf(
                dto("AAAA", type = "Common Stock", exchange = "NASDAQ"), // keep
                dto("BBBB", type = "ETF", exchange = "NASDAQ"), // drop: not common stock
                dto("CCCC", type = "Common Stock", exchange = "PINK"), // drop: penny exchange
                dto("DDDD", type = "Common Stock", exchange = "NASDAQ"), // keep
                dto("0P0001U", type = "Common Stock", exchange = "NASDAQ"), // drop: weird ticker pattern
                dto("BRK-B", type = "Common Stock", exchange = "NYSE"), // keep: class share with valid pattern
            )
        val fixture =
            fixture(
                catalogue = catalogue,
                fundamentals =
                    mapOf(
                        "AAAA" to fundamentals(cik = "1", delistedDate = "2020-01-01"),
                        "DDDD" to fundamentals(cik = "2", delistedDate = "2021-02-02"),
                        "BRK-B" to fundamentals(cik = "3", delistedDate = "2022-03-03"),
                    ),
                edgar =
                    mapOf(
                        "1" to edgar(sic = "3571"), // TECHNOLOGY
                        "2" to edgar(sic = "6021"), // FINANCIAL SERVICES
                        "3" to edgar(sic = "6021"), // FINANCIAL SERVICES
                    ),
            )

        // When
        runBlocking { fixture.runDirect() }

        // Then: only the three viable candidates were enriched + persisted
        verify(fixture.symbolRepository, times(3)).upsertSymbol(any())
        // And the run stats reflect the catalogue filter
        assertEquals(3, fixture.service.lastRunStats?.viableCount)
        assertEquals(3, fixture.service.lastRunStats?.persistedCount)
    }

    @Test
    fun `caps each sector at perSectorCap so distribution is balanced`() {
        // Given: 6 candidates that all map to TECHNOLOGY, with a per-sector cap of 2
        val codes = listOf("AAAA", "AAAB", "AAAC", "AAAD", "AAAE", "AAAF")
        val candidates = codes.map { dto(it, type = "Common Stock", exchange = "NASDAQ") }
        val fundamentalsByCode =
            codes
                .mapIndexed { idx, code ->
                    code to fundamentals(cik = (idx + 1).toString(), delistedDate = "2020-01-01")
                }.toMap()
        val edgarByCik = codes.mapIndexed { idx, _ -> (idx + 1).toString() to edgar(sic = "3571") }.toMap()
        val fixture = fixture(catalogue = candidates, fundamentals = fundamentalsByCode, edgar = edgarByCik)

        // When
        runBlocking { fixture.service.runPipeline(DelistedIngestionService.Config(perSectorCap = 2, totalCap = 100)) }

        // Then: exactly 2 stored, the rest skipped despite being viable
        verify(fixture.symbolRepository, times(2)).upsertSymbol(any())
        assertEquals(
            2,
            fixture.service.lastRunStats
                ?.bySector
                ?.get("TECHNOLOGY"),
        )
    }

    @Test
    fun `stops walking when total cap is reached even if individual sectors have headroom`() {
        // Given: 5 candidates spread across 3 sectors, total cap = 3, per-sector cap = 5 (won't bind)
        val catalogue =
            listOf(
                dto("AAAA", type = "Common Stock", exchange = "NASDAQ"),
                dto("BBBB", type = "Common Stock", exchange = "NASDAQ"),
                dto("CCCC", type = "Common Stock", exchange = "NASDAQ"),
                dto("DDDD", type = "Common Stock", exchange = "NASDAQ"),
                dto("EEEE", type = "Common Stock", exchange = "NASDAQ"),
            )
        val fundamentalsByCode =
            mapOf(
                "AAAA" to fundamentals(cik = "1", delistedDate = "2020-01-01"),
                "BBBB" to fundamentals(cik = "2", delistedDate = "2021-01-01"),
                "CCCC" to fundamentals(cik = "3", delistedDate = "2022-01-01"),
                "DDDD" to fundamentals(cik = "4", delistedDate = "2023-01-01"),
                "EEEE" to fundamentals(cik = "5", delistedDate = "2024-01-01"),
            )
        val edgarByCik =
            mapOf(
                "1" to edgar(sic = "3571"), // TECHNOLOGY
                "2" to edgar(sic = "6021"), // FINANCIAL SERVICES
                "3" to edgar(sic = "8060"), // HEALTHCARE
                "4" to edgar(sic = "3571"), // TECHNOLOGY (would still fit per-sector but total is full)
                "5" to edgar(sic = "3571"),
            )
        val fixture = fixture(catalogue = catalogue, fundamentals = fundamentalsByCode, edgar = edgarByCik)

        // When
        runBlocking { fixture.service.runPipeline(DelistedIngestionService.Config(perSectorCap = 5, totalCap = 3)) }

        // Then: walked alphabetically, accepted A/B/C, then stopped at total cap
        verify(fixture.symbolRepository, times(3)).upsertSymbol(any())
    }

    @Test
    fun `defaults to INDUSTRIALS when CIK is missing or EDGAR returns no SIC`() {
        // Given: one candidate has CIK + valid SIC, one has missing CIK, one has CIK but EDGAR returns null
        val catalogue =
            listOf(
                dto("AAAA", type = "Common Stock", exchange = "NASDAQ"),
                dto("NOCK", type = "Common Stock", exchange = "NASDAQ"),
                dto("FRGN", type = "Common Stock", exchange = "NASDAQ"),
            )
        val fixture =
            fixture(
                catalogue = catalogue,
                fundamentals =
                    mapOf(
                        "AAAA" to fundamentals(cik = "1", delistedDate = "2020-01-01"),
                        "NOCK" to fundamentals(cik = null, delistedDate = "2020-01-01"),
                        "FRGN" to fundamentals(cik = "9999", delistedDate = "2020-01-01"),
                    ),
                edgar = mapOf("1" to edgar(sic = "3571")),
            )

        // When
        runBlocking { fixture.runDirect() }

        // Then: AAAA → TECHNOLOGY, NOCK + FRGN → INDUSTRIALS
        val captor = argumentCaptor<Symbol>()
        verify(fixture.symbolRepository, times(3)).upsertSymbol(captor.capture())
        val sectors = captor.allValues.associateBy { it.symbol }
        assertEquals("TECHNOLOGY", sectors["AAAA"]?.sector)
        assertEquals("INDUSTRIALS", sectors["NOCK"]?.sector)
        assertEquals("INDUSTRIALS", sectors["FRGN"]?.sector)
    }

    @Test
    fun `skips candidates whose fundamentals lack a delisting date`() {
        // Given: a candidate whose General.DelistedDate is null (rare data gap)
        val catalogue = listOf(dto("GAPE", type = "Common Stock", exchange = "NASDAQ"))
        val fixture =
            fixture(
                catalogue = catalogue,
                fundamentals = mapOf("GAPE" to fundamentals(cik = "1", delistedDate = null)),
                edgar = mapOf("1" to edgar(sic = "3571")),
            )

        // When
        runBlocking { fixture.runDirect() }

        // Then: nothing persisted — delisting date is mandatory
        verify(fixture.symbolRepository, never()).upsertSymbol(any())
    }

    @Test
    fun `aborts cleanly when the catalogue fetch fails`() {
        // Given: EodhdSymbolListClient returns null
        val fixture = fixture(catalogueResponse = null, fundamentals = emptyMap(), edgar = emptyMap())

        // When
        runBlocking { fixture.runDirect() }

        // Then: nothing persisted, lastRunStats reflects failure
        verify(fixture.symbolRepository, never()).upsertSymbol(any())
        assertNotNull(fixture.service.lastRunStats)
        assertTrue(fixture.service.lastRunStats!!.failed)
    }

    @Test
    fun `respects the fundamentals safety budget even if sectors not yet full`() {
        // Given: 5 viable candidates but a hard budget of 2 fundamentals fetches
        val codes = listOf("BBAA", "BBAB", "BBAC", "BBAD", "BBAE")
        val catalogue = codes.map { dto(it, type = "Common Stock", exchange = "NASDAQ") }
        val fundamentalsByCode =
            codes
                .mapIndexed { idx, code ->
                    code to fundamentals(cik = (idx + 1).toString(), delistedDate = "2020-01-01")
                }.toMap()
        val edgarByCik = codes.mapIndexed { idx, _ -> (idx + 1).toString() to edgar(sic = "8060") }.toMap()
        val fixture = fixture(catalogue = catalogue, fundamentals = fundamentalsByCode, edgar = edgarByCik)

        // When
        runBlocking { fixture.service.runPipeline(DelistedIngestionService.Config(fundamentalsBudget = 2)) }

        // Then: only 2 enriched + persisted; budget honoured before sectors fill
        verify(fixture.symbolRepository, times(2)).upsertSymbol(any())
        assertEquals(2, fixture.service.lastRunStats?.fundamentalsFetched)
    }

    // ── Test fixture ─────────────────────────────────────────────────────────────

    private data class Fixture(
        val service: DelistedIngestionService,
        val symbolListClient: EodhdSymbolListClient,
        val fundamentalsClient: EodhdFundamentalsClient,
        val edgarClient: EdgarClient,
        val symbolRepository: SymbolRepository,
    ) {
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
        val symbolRepository: SymbolRepository = mock()

        runBlocking {
            whenever(symbolListClient.getDelistedSymbols()).thenReturn(catalogueResponse)
            fundamentals.forEach { (code, response) ->
                whenever(fundamentalsClient.fetch(eq(code), eq("$code.US"))).thenReturn(response)
            }
            edgar.forEach { (cik, submission) ->
                whenever(edgarClient.getSubmission(eq(cik))).thenReturn(submission)
            }
        }

        val service =
            DelistedIngestionService(
                symbolListClient = symbolListClient,
                fundamentalsClient = fundamentalsClient,
                edgarClient = edgarClient,
                symbolRepository = symbolRepository,
                dataIntegrityService = mock(),
            )
        return Fixture(service, symbolListClient, fundamentalsClient, edgarClient, symbolRepository)
    }

    private fun dto(
        code: String,
        type: String,
        exchange: String,
        name: String = "$code Corp",
    ): EodhdDelistedSymbolDto = EodhdDelistedSymbolDto(code = code, name = name, type = type, exchange = exchange)

    private fun fundamentals(
        cik: String?,
        delistedDate: String?,
    ): EodhdFundamentalsResponse =
        EodhdFundamentalsResponse(
            general = EodhdGeneralSection(cik = cik, delistedDate = delistedDate, isDelistedRaw = "1"),
        )

    private fun edgar(sic: String): EdgarSubmissionDto = EdgarSubmissionDto(cik = "test", sic = sic, sicDescription = "TEST")
}
