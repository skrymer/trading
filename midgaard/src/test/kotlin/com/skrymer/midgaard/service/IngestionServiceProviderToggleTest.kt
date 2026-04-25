package com.skrymer.midgaard.service

import com.skrymer.midgaard.integration.CompanyInfoProvider
import com.skrymer.midgaard.integration.EarningsProvider
import com.skrymer.midgaard.integration.IndicatorProvider
import com.skrymer.midgaard.integration.OhlcvProvider
import com.skrymer.midgaard.model.RawBar
import com.skrymer.midgaard.repository.EarningsRepository
import com.skrymer.midgaard.repository.IngestionStatusRepository
import com.skrymer.midgaard.repository.QuoteRepository
import com.skrymer.midgaard.repository.SymbolRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verifyBlocking
import java.time.LocalDate

class IngestionServiceProviderToggleTest {
    @Test
    fun `default provider name routes initial ingest to AlphaVantage beans`() {
        // Given: a service constructed with the default `alphavantage` toggle
        val fixture = fixture(ingestProviderName = "alphavantage")

        // When: kicking off an initial ingest for a single symbol
        runBlocking { fixture.service.initialIngest("AAPL") }

        // Then: AlphaVantage providers were called, EODHD providers were not
        verifyBlocking(fixture.alphaVantageOhlcv) { getDailyBars(eq("AAPL"), any(), any()) }
        verifyBlocking(fixture.alphaVantageIndicators) { getATR(eq("AAPL"), any()) }
        verifyBlocking(fixture.alphaVantageIndicators) { getADX(eq("AAPL"), any()) }
        verifyBlocking(fixture.alphaVantageEarnings) { getEarnings(eq("AAPL")) }
        verifyBlocking(fixture.alphaVantageCompanyInfo) { getCompanyInfo(eq("AAPL")) }
        verifyBlocking(fixture.eodhdOhlcv, never()) { getDailyBars(any(), any(), any()) }
        verifyBlocking(fixture.eodhdIndicators, never()) { getATR(any(), any()) }
        verifyBlocking(fixture.eodhdEarnings, never()) { getEarnings(any()) }
    }

    @Test
    fun `eodhd toggle routes OHLCV to EODHD, computes indicators locally, and keeps fundamentals on AV`() {
        // Given: a service explicitly configured for `eodhd`. Per the access we have today,
        // OHLCV is the only thing that switches; ATR/ADX must be recomputed locally
        // (EODHD's /api/technical/ mishandles split events and is gated by tier on $29
        // EOD-only plans), and fundamentals stay on AlphaVantage because the same EODHD
        // tier doesn't include /api/fundamentals/.
        val fixture = fixture(ingestProviderName = "eodhd")

        // When
        runBlocking { fixture.service.initialIngest("AAPL") }

        // Then: OHLCV from EODHD, fundamentals from AV, and EODHD's indicator API never touched
        verifyBlocking(fixture.eodhdOhlcv) { getDailyBars(eq("AAPL"), any(), any()) }
        verifyBlocking(fixture.alphaVantageEarnings) { getEarnings(eq("AAPL")) }
        verifyBlocking(fixture.alphaVantageCompanyInfo) { getCompanyInfo(eq("AAPL")) }
        verifyBlocking(fixture.eodhdIndicators, never()) { getATR(any(), any()) }
        verifyBlocking(fixture.eodhdIndicators, never()) { getADX(any(), any()) }
        verifyBlocking(fixture.alphaVantageIndicators, never()) { getATR(any(), any()) }
        verifyBlocking(fixture.alphaVantageIndicators, never()) { getADX(any(), any()) }
        verifyBlocking(fixture.alphaVantageOhlcv, never()) { getDailyBars(any(), any(), any()) }
        verifyBlocking(fixture.eodhdEarnings, never()) { getEarnings(any()) }
        verifyBlocking(fixture.eodhdCompanyInfo, never()) { getCompanyInfo(any()) }

        // OHLCV permit acquired against eodhd; fundamentals against alphavantage
        verifyBlocking(fixture.rateLimiterService, atLeastOnce()) { acquirePermit(eq("eodhd")) }
        verifyBlocking(fixture.rateLimiterService, atLeastOnce()) { acquirePermit(eq("alphavantage")) }
    }

    @Test
    fun `unknown ingest provider value falls back to AlphaVantage routing`() {
        // Given: a misconfigured value — guard against typos / future provider names not yet wired
        val fixture = fixture(ingestProviderName = "definitely-not-real")

        // When
        runBlocking { fixture.service.initialIngest("AAPL") }

        // Then: defaults to AlphaVantage rather than refusing to ingest
        verifyBlocking(fixture.alphaVantageOhlcv) { getDailyBars(eq("AAPL"), any(), any()) }
        verifyBlocking(fixture.eodhdOhlcv, never()) { getDailyBars(any(), any(), any()) }
    }

    private data class Fixture(
        val service: IngestionService,
        val alphaVantageOhlcv: OhlcvProvider,
        val eodhdOhlcv: OhlcvProvider,
        val alphaVantageIndicators: IndicatorProvider,
        val eodhdIndicators: IndicatorProvider,
        val alphaVantageEarnings: EarningsProvider,
        val eodhdEarnings: EarningsProvider,
        val alphaVantageCompanyInfo: CompanyInfoProvider,
        val eodhdCompanyInfo: CompanyInfoProvider,
        val rateLimiterService: RateLimiterService,
    )

    @Suppress("LongMethod")
    private fun fixture(ingestProviderName: String): Fixture {
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

        val alphaVantageOhlcv =
            mock<OhlcvProvider>().apply {
                stub { onBlocking { getDailyBars(any(), any(), any()) }.doReturn(sampleBars) }
            }
        val massiveOhlcv = mock<OhlcvProvider>()
        val eodhdOhlcv =
            mock<OhlcvProvider>().apply {
                stub { onBlocking { getDailyBars(any(), any(), any()) }.doReturn(sampleBars) }
            }
        val alphaVantageIndicators =
            mock<IndicatorProvider>().apply {
                stub {
                    onBlocking { getATR(any(), any()) }.doReturn(emptyMap())
                    onBlocking { getADX(any(), any()) }.doReturn(emptyMap())
                }
            }
        val eodhdIndicators =
            mock<IndicatorProvider>().apply {
                stub {
                    onBlocking { getATR(any(), any()) }.doReturn(emptyMap())
                    onBlocking { getADX(any(), any()) }.doReturn(emptyMap())
                }
            }
        val alphaVantageEarnings = mock<EarningsProvider>().apply { stub { onBlocking { getEarnings(any()) }.doReturn(emptyList()) } }
        val eodhdEarnings = mock<EarningsProvider>().apply { stub { onBlocking { getEarnings(any()) }.doReturn(emptyList()) } }
        val alphaVantageCompanyInfo = mock<CompanyInfoProvider>().apply { stub { onBlocking { getCompanyInfo(any()) }.doReturn(null) } }
        val eodhdCompanyInfo = mock<CompanyInfoProvider>().apply { stub { onBlocking { getCompanyInfo(any()) }.doReturn(null) } }

        val rateLimiterService = mock<RateLimiterService>()
        val indicatorCalculator =
            mock<IndicatorCalculator> {
                on { calculateAllEMAs(any()) } doReturn emptyMap()
                on { calculateDonchianUpper(any(), any()) } doReturn emptyList()
            }
        val quoteRepository = mock<QuoteRepository>()
        val earningsRepository = mock<EarningsRepository>()
        val symbolRepository = mock<SymbolRepository>()
        val ingestionStatusRepository = mock<IngestionStatusRepository>()

        val service =
            IngestionService(
                alphaVantageOhlcv = alphaVantageOhlcv,
                massiveOhlcv = massiveOhlcv,
                eodhdOhlcv = eodhdOhlcv,
                alphaVantageIndicators = alphaVantageIndicators,
                eodhdIndicators = eodhdIndicators,
                alphaVantageEarnings = alphaVantageEarnings,
                eodhdEarnings = eodhdEarnings,
                alphaVantageCompanyInfo = alphaVantageCompanyInfo,
                eodhdCompanyInfo = eodhdCompanyInfo,
                rateLimiterService = rateLimiterService,
                indicatorCalculator = indicatorCalculator,
                quoteRepository = quoteRepository,
                earningsRepository = earningsRepository,
                symbolRepository = symbolRepository,
                ingestionStatusRepository = ingestionStatusRepository,
                ingestProviderName = ingestProviderName,
            )

        return Fixture(
            service = service,
            alphaVantageOhlcv = alphaVantageOhlcv,
            eodhdOhlcv = eodhdOhlcv,
            alphaVantageIndicators = alphaVantageIndicators,
            eodhdIndicators = eodhdIndicators,
            alphaVantageEarnings = alphaVantageEarnings,
            eodhdEarnings = eodhdEarnings,
            alphaVantageCompanyInfo = alphaVantageCompanyInfo,
            eodhdCompanyInfo = eodhdCompanyInfo,
            rateLimiterService = rateLimiterService,
        )
    }
}
