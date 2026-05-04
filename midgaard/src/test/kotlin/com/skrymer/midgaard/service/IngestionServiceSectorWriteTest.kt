package com.skrymer.midgaard.service

import com.skrymer.midgaard.integration.CompanyInfoProvider
import com.skrymer.midgaard.integration.EarningsProvider
import com.skrymer.midgaard.integration.IndicatorProvider
import com.skrymer.midgaard.integration.OhlcvProvider
import com.skrymer.midgaard.integrity.DataIntegrityService
import com.skrymer.midgaard.model.AssetType
import com.skrymer.midgaard.model.CompanyInfo
import com.skrymer.midgaard.model.RawBar
import com.skrymer.midgaard.model.Symbol
import com.skrymer.midgaard.repository.IngestionStatusRepository
import com.skrymer.midgaard.repository.MarketHolidayRepository
import com.skrymer.midgaard.repository.QuoteRepository
import com.skrymer.midgaard.repository.SymbolRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import java.time.LocalDate
import kotlin.test.assertEquals

/**
 * Behaviour tests for `IngestionService.fetchAndSaveSupplementaryData` —
 * specifically the delisted-immutable rule, sector normalization, and drift
 * logging introduced by the sector-normalization PR.
 */
class IngestionServiceSectorWriteTest {
    @Test
    fun `active symbol with canonical sector gets updated to UPPERCASE`() {
        // Given: an active (delistedAt=null) symbol; EODHD returns proper-case
        val existing = activeSymbol("AAPL", sector = null, sectorSymbol = null)
        val repo = symbolRepoReturning(existing)
        val service = service(symbolRepository = repo, providerSector = "Technology")

        // When
        runBlocking { service.initialIngest("AAPL") }

        // Then: upserted with UPPERCASE TECHNOLOGY + XLK
        val captor = argumentCaptor<Symbol>()
        verify(repo).upsertSymbol(captor.capture())
        assertEquals("TECHNOLOGY", captor.firstValue.sector)
        assertEquals("XLK", captor.firstValue.sectorSymbol)
    }

    @Test
    fun `active symbol with provider variant Financials gets normalized to FINANCIAL SERVICES`() {
        // Given: an active symbol; EODHD returns the variant 'Financials'
        val existing = activeSymbol("XYZ", sector = null, sectorSymbol = null)
        val repo = symbolRepoReturning(existing)
        val service = service(symbolRepository = repo, providerSector = "Financials")

        // When
        runBlocking { service.initialIngest("XYZ") }

        // Then: variant maps to canonical FINANCIAL SERVICES + XLF
        val captor = argumentCaptor<Symbol>()
        verify(repo).upsertSymbol(captor.capture())
        assertEquals("FINANCIAL SERVICES", captor.firstValue.sector)
        assertEquals("XLF", captor.firstValue.sectorSymbol)
    }

    @Test
    fun `active symbol with EODHD Other does not overwrite existing canonical sector`() {
        // Given: an active symbol with a known-good sector; EODHD now returns 'Other'
        val existing = activeSymbol("XYZ", sector = "HEALTHCARE", sectorSymbol = "XLV")
        val repo = symbolRepoReturning(existing)
        val service = service(symbolRepository = repo, providerSector = "Other")

        // When
        runBlocking { service.initialIngest("XYZ") }

        // Then: defensive don't-downgrade — no upsert with sector mutation
        verify(repo, never()).upsertSymbol(any())
    }

    @Test
    fun `delisted symbol — sector update is SKIPPED entirely (immutability rule)`() {
        // Given: a delisted symbol with V6's correct sector; EODHD returns Healthcare
        // (a valid canonical name that would normally trigger an upsert)
        val existing = delistedSymbol("AAAB", sector = "FINANCIAL SERVICES", sectorSymbol = "XLF")
        val repo = symbolRepoReturning(existing)
        val service = service(symbolRepository = repo, providerSector = "Healthcare")

        // When
        runBlocking { service.initialIngest("AAAB") }

        // Then: the delisted-immutable guard prevents any sector mutation, even
        // for a canonical EODHD response. This is the AAAB regression test.
        verify(repo, never()).upsertSymbol(any())
    }

    @Test
    fun `delisted symbol with EODHD Other — sector update is SKIPPED`() {
        // Given: a delisted symbol; EODHD returns the typical 'Other' for delisted
        val existing = delistedSymbol("AAAB", sector = "FINANCIAL SERVICES", sectorSymbol = "XLF")
        val repo = symbolRepoReturning(existing)
        val service = service(symbolRepository = repo, providerSector = "Other")

        // When
        runBlocking { service.initialIngest("AAAB") }

        // Then: same outcome — no mutation; existing V6 baseline preserved
        verify(repo, never()).upsertSymbol(any())
    }

    @Test
    fun `delisted symbol with EODHD null sector — sector update is SKIPPED`() {
        // Given: a delisted symbol; EODHD returned no fundamentals (null)
        val existing = delistedSymbol("AAAB", sector = "FINANCIAL SERVICES", sectorSymbol = "XLF")
        val repo = symbolRepoReturning(existing)
        val service = service(symbolRepository = repo, providerSector = null)

        // When
        runBlocking { service.initialIngest("AAAB") }

        // Then: same outcome — no mutation
        verify(repo, never()).upsertSymbol(any())
    }

    @Test
    fun `active symbol with EODHD null sector — no upsert`() {
        // Given: an active symbol; provider returned null (no fundamentals available)
        val existing = activeSymbol("XYZ", sector = "TECHNOLOGY", sectorSymbol = "XLK")
        val repo = symbolRepoReturning(existing)
        val service = service(symbolRepository = repo, providerSector = null)

        // When
        runBlocking { service.initialIngest("XYZ") }

        // Then: nothing to write
        verify(repo, never()).upsertSymbol(any())
    }

    // ===== HELPERS =====

    private fun activeSymbol(
        symbol: String,
        sector: String?,
        sectorSymbol: String?,
    ) = Symbol(symbol = symbol, assetType = AssetType.STOCK, sector = sector, sectorSymbol = sectorSymbol, delistedAt = null)

    private fun delistedSymbol(
        symbol: String,
        sector: String?,
        sectorSymbol: String?,
    ) = Symbol(
        symbol = symbol,
        assetType = AssetType.STOCK,
        sector = sector,
        sectorSymbol = sectorSymbol,
        delistedAt = LocalDate.of(2003, 1, 29),
    )

    private fun symbolRepoReturning(symbol: Symbol): SymbolRepository =
        mock<SymbolRepository>().apply {
            stub { on { findBySymbol(eq(symbol.symbol)) } doReturn symbol }
        }

    @Suppress("LongParameterList", "LongMethod")
    private fun service(
        symbolRepository: SymbolRepository,
        providerSector: String?,
    ): IngestionService {
        val ohlcv =
            mock<OhlcvProvider>().apply {
                stub {
                    onBlocking { getDailyBars(any(), any(), any()) }.doReturn(
                        listOf(
                            RawBar(
                                symbol = "X",
                                date = LocalDate.of(2024, 1, 2),
                                open = 100.0,
                                high = 102.0,
                                low = 99.0,
                                close = 101.0,
                                volume = 1_000_000L,
                            ),
                        ),
                    )
                }
            }
        val companyInfo =
            mock<CompanyInfoProvider>().apply {
                stub {
                    onBlocking { getCompanyInfo(any()) }.doReturn(
                        if (providerSector == null) null else CompanyInfo(sector = providerSector, marketCap = null),
                    )
                }
            }
        val indicatorCalculator =
            mock<IndicatorCalculator> {
                on { calculateAllEMAs(any()) } doReturn emptyMap()
                on { calculateDonchianUpper(any(), any()) } doReturn emptyList()
                on { calculateATR(any(), any()) } doReturn emptyList()
                on { calculateADX(any(), any()) } doReturn emptyList()
            }
        return IngestionService(
            ohlcv = ohlcv,
            indicators =
                mock<IndicatorProvider>().apply {
                    stub {
                        onBlocking { getATR(any(), any()) }.doReturn(emptyMap())
                        onBlocking { getADX(any(), any()) }.doReturn(emptyMap())
                    }
                },
            earnings =
                mock<EarningsProvider>().apply {
                    stub { onBlocking { getEarnings(any()) }.doReturn(emptyList()) }
                },
            companyInfo = companyInfo,
            indicatorCalculator = indicatorCalculator,
            quoteRepository = mock<QuoteRepository>(),
            earningsRepository = mock(),
            symbolRepository = symbolRepository,
            ingestionStatusRepository = mock<IngestionStatusRepository>(),
            marketHolidayRepository =
                mock<MarketHolidayRepository>().apply {
                    stub { on { findHolidayDates(any()) } doReturn emptySet() }
                },
            dataIntegrityService = mock<DataIntegrityService>(),
            indicatorsMode = IndicatorsMode.LOCAL,
        )
    }
}
