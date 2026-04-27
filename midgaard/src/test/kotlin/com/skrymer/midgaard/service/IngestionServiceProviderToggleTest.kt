package com.skrymer.midgaard.service

import com.skrymer.midgaard.integration.CompanyInfoProvider
import com.skrymer.midgaard.integration.EarningsProvider
import com.skrymer.midgaard.integration.IndicatorProvider
import com.skrymer.midgaard.integration.OhlcvProvider
import com.skrymer.midgaard.model.RawBar
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verifyBlocking
import java.time.LocalDate

/**
 * Behaviour tests for `IngestionService` independent of which provider backs
 * each interface. Provider selection is `ProviderConfiguration`'s concern;
 * this test verifies what the service does given some provider.
 */
class IngestionServiceProviderToggleTest {
    @Test
    fun `initial ingest fetches OHLCV from the configured ohlcv provider and falls through to fundamentals`() {
        // Given: an ingest service in default `LOCAL` indicator mode
        val fixture = fixture(indicatorsMode = IndicatorsMode.LOCAL)

        // When
        runBlocking { fixture.service.initialIngest("AAPL") }

        // Then: OHLCV provider was asked for bars; fundamentals + the daily-update path were not consulted
        verifyBlocking(fixture.ohlcv) { getDailyBars(eq("AAPL"), any(), any()) }
        verifyBlocking(fixture.dailyUpdateOhlcv, never()) { getDailyBars(any(), any(), any()) }
        verifyBlocking(fixture.earnings) { getEarnings(eq("AAPL")) }
        verifyBlocking(fixture.companyInfo) { getCompanyInfo(eq("AAPL")) }
    }

    @Test
    fun `local indicators mode skips the indicators provider`() {
        // Given: indicators mode set to `LOCAL` (default)
        val fixture = fixture(indicatorsMode = IndicatorsMode.LOCAL)

        // When
        runBlocking { fixture.service.initialIngest("AAPL") }

        // Then: ATR/ADX never fetched from the API; recomputation happens via IndicatorCalculator
        verifyBlocking(fixture.indicators, never()) { getATR(any(), any()) }
        verifyBlocking(fixture.indicators, never()) { getADX(any(), any()) }
    }

    @Test
    fun `api indicators mode calls the indicators provider`() {
        // Given: indicators mode set to `API`
        val fixture = fixture(indicatorsMode = IndicatorsMode.API)

        // When
        runBlocking { fixture.service.initialIngest("AAPL") }

        // Then: ATR + ADX both fetched from the indicator provider
        verifyBlocking(fixture.indicators) { getATR(eq("AAPL"), any()) }
        verifyBlocking(fixture.indicators) { getADX(eq("AAPL"), any()) }
    }

    @Test
    fun `skipSupplementary disables the earnings + company info fetch`() {
        // Given
        val fixture = fixture(indicatorsMode = IndicatorsMode.LOCAL)

        // When
        runBlocking { fixture.service.initialIngest("AAPL", skipSupplementary = true) }

        // Then
        verifyBlocking(fixture.earnings, never()) { getEarnings(any()) }
        verifyBlocking(fixture.companyInfo, never()) { getCompanyInfo(any()) }
    }

    private data class Fixture(
        val service: IngestionService,
        val ohlcv: OhlcvProvider,
        val dailyUpdateOhlcv: OhlcvProvider,
        val indicators: IndicatorProvider,
        val earnings: EarningsProvider,
        val companyInfo: CompanyInfoProvider,
    )

    @Suppress("LongMethod")
    private fun fixture(indicatorsMode: IndicatorsMode): Fixture {
        val sampleBars =
            listOf(
                RawBar(
                    symbol = "AAPL",
                    date = LocalDate.of(2024, 1, 2),
                    open = 100.0,
                    high = 102.0,
                    low = 99.0,
                    close = 101.0,
                    volume = 1_000_000L,
                ),
            )

        val ohlcv =
            mock<OhlcvProvider>().apply {
                stub { onBlocking { getDailyBars(any(), any(), any()) }.doReturn(sampleBars) }
            }
        val dailyUpdateOhlcv = mock<OhlcvProvider>()
        val indicators =
            mock<IndicatorProvider>().apply {
                stub {
                    onBlocking { getATR(any(), any()) }.doReturn(emptyMap())
                    onBlocking { getADX(any(), any()) }.doReturn(emptyMap())
                }
            }
        val earnings =
            mock<EarningsProvider>().apply { stub { onBlocking { getEarnings(any()) }.doReturn(emptyList()) } }
        val companyInfo =
            mock<CompanyInfoProvider>().apply { stub { onBlocking { getCompanyInfo(any()) }.doReturn(null) } }

        val indicatorCalculator =
            mock<IndicatorCalculator> {
                on { calculateAllEMAs(any()) } doReturn emptyMap()
                on { calculateDonchianUpper(any(), any()) } doReturn emptyList()
                on { calculateATR(any(), any()) } doReturn emptyList()
                on { calculateADX(any(), any()) } doReturn emptyList()
            }

        val service =
            IngestionService(
                ohlcv = ohlcv,
                dailyUpdateOhlcv = dailyUpdateOhlcv,
                indicators = indicators,
                earnings = earnings,
                companyInfo = companyInfo,
                indicatorCalculator = indicatorCalculator,
                quoteRepository = mock(),
                earningsRepository = mock(),
                symbolRepository = mock(),
                ingestionStatusRepository = mock(),
                indicatorsMode = indicatorsMode,
            )

        return Fixture(service, ohlcv, dailyUpdateOhlcv, indicators, earnings, companyInfo)
    }
}
