package com.skrymer.midgaard.service

import com.skrymer.midgaard.integration.CompanyInfoProvider
import com.skrymer.midgaard.integration.EarningsProvider
import com.skrymer.midgaard.integration.IndicatorProvider
import com.skrymer.midgaard.integration.OhlcvProvider
import com.skrymer.midgaard.model.RawBar
import com.skrymer.midgaard.repository.MarketHolidayRepository
import com.skrymer.midgaard.repository.QuoteRepository
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

        // Then: OHLCV provider was asked for bars; fundamentals were also consulted
        verifyBlocking(fixture.ohlcv) { getDailyBars(eq("AAPL"), any(), any()) }
        verifyBlocking(fixture.earnings) { getEarnings(eq("AAPL")) }
        verifyBlocking(fixture.companyInfo) { getCompanyInfo(eq("AAPL")) }
    }

    @Test
    fun `daily update fetches OHLCV from the same configured provider as initial ingest`() {
        // Given: an existing symbol with a stored last-bar date so updateSymbol proceeds past the empty-DB short-circuit
        val quoteRepository =
            mock<QuoteRepository>().apply {
                stub { on { getLastBarDate(any()) } doReturn LocalDate.of(2024, 1, 1) }
            }
        val fixture = fixture(indicatorsMode = IndicatorsMode.LOCAL, quoteRepository = quoteRepository)

        // When
        runBlocking { fixture.service.updateSymbol("AAPL") }

        // Then: daily update flowed through the same `ohlcv` bean — no separate daily-update provider exists
        verifyBlocking(fixture.ohlcv) { getDailyBars(eq("AAPL"), any(), any()) }
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
    fun `initial ingest drops provider bars stamped to market holidays`() {
        // Given: provider returns three bars, one stamped to a known US holiday
        val tradingDay1 = LocalDate.of(2024, 1, 2)
        val holiday = LocalDate.of(2024, 1, 15) // MLK Day 2024
        val tradingDay2 = LocalDate.of(2024, 1, 16)
        val bars =
            listOf(
                sampleBar(tradingDay1),
                sampleBar(holiday),
                sampleBar(tradingDay2),
            )
        val quoteRepository = mock<QuoteRepository>()
        val fixture =
            fixture(
                indicatorsMode = IndicatorsMode.LOCAL,
                quoteRepository = quoteRepository,
                ohlcvBars = bars,
                holidays = setOf(holiday),
            )

        // When
        runBlocking { fixture.service.initialIngest("AAPL") }

        // Then: only the two trading-day bars reach `replaceForSymbol`
        argumentCaptor<List<com.skrymer.midgaard.model.Quote>>().apply {
            verify(quoteRepository).replaceForSymbol(eq("AAPL"), capture())
            val datesPersisted = firstValue.map { it.date }.toSet()
            org.junit.jupiter.api.Assertions
                .assertEquals(setOf(tradingDay1, tradingDay2), datesPersisted)
        }
    }

    @Test
    fun `initial ingest replaces history rather than upserting`() {
        // Given: provider returns a single trading-day bar. The contract this test
        // pins down is that initial ingest goes through `replaceForSymbol`, not
        // `upsertQuotes` — so any rows in the DB on dates the provider doesn't
        // re-emit (e.g. previously-stored holiday phantoms) get cleaned out.
        val tradingDay = LocalDate.of(2024, 1, 2)
        val quoteRepository = mock<QuoteRepository>()
        val fixture =
            fixture(
                indicatorsMode = IndicatorsMode.LOCAL,
                quoteRepository = quoteRepository,
                ohlcvBars = listOf(sampleBar(tradingDay)),
            )

        // When
        runBlocking { fixture.service.initialIngest("AAPL") }

        // Then: replaceForSymbol is the persistence call, NOT upsertQuotes
        verify(quoteRepository).replaceForSymbol(eq("AAPL"), any())
        verify(quoteRepository, never()).upsertQuotes(any())
    }

    @Test
    fun `daily update drops provider bars stamped to market holidays`() {
        // Given: existing symbol with a stored last bar so updateSymbol proceeds, fresh bars include a holiday
        val seedDay = LocalDate.of(2024, 1, 5)
        val holiday = LocalDate.of(2024, 1, 15)
        val tradingDay = LocalDate.of(2024, 1, 16)
        val freshBars = listOf(sampleBar(holiday), sampleBar(tradingDay))
        val quoteRepository =
            mock<QuoteRepository>().apply {
                stub {
                    on { getLastBarDate(any()) } doReturn seedDay
                    on { getLastNQuotes(any(), any()) } doReturn listOf(seedQuote(seedDay))
                    on { countBySymbol(any()) } doReturn 100
                }
            }
        val fixture =
            fixture(
                indicatorsMode = IndicatorsMode.LOCAL,
                quoteRepository = quoteRepository,
                ohlcvBars = freshBars,
                holidays = setOf(holiday),
            )

        // When
        runBlocking { fixture.service.updateSymbol("AAPL") }

        // Then: only the trading-day bar is upserted
        argumentCaptor<List<com.skrymer.midgaard.model.Quote>>().apply {
            verify(quoteRepository).upsertQuotes(capture())
            val datesPersisted = firstValue.map { it.date }.toSet()
            org.junit.jupiter.api.Assertions
                .assertEquals(setOf(tradingDay), datesPersisted)
        }
    }

    private fun sampleBar(date: LocalDate) =
        RawBar(
            symbol = "AAPL",
            date = date,
            open = 100.0,
            high = 102.0,
            low = 99.0,
            close = 101.0,
            volume = 1_000_000L,
        )

    private fun seedQuote(date: LocalDate) =
        com.skrymer.midgaard.model.Quote(
            symbol = "AAPL",
            date = date,
            open = java.math.BigDecimal("100"),
            high = java.math.BigDecimal("100"),
            low = java.math.BigDecimal("100"),
            close = java.math.BigDecimal("100"),
            volume = 1_000_000L,
            indicatorSource = com.skrymer.midgaard.model.IndicatorSource.CALCULATED,
        )

    private data class Fixture(
        val service: IngestionService,
        val ohlcv: OhlcvProvider,
        val indicators: IndicatorProvider,
        val earnings: EarningsProvider,
        val companyInfo: CompanyInfoProvider,
    )

    @Suppress("LongMethod")
    private fun fixture(
        indicatorsMode: IndicatorsMode,
        quoteRepository: QuoteRepository = mock(),
        ohlcvBars: List<RawBar> =
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
            ),
        holidays: Set<LocalDate> = emptySet(),
    ): Fixture {
        val ohlcv =
            mock<OhlcvProvider>().apply {
                stub { onBlocking { getDailyBars(any(), any(), any()) }.doReturn(ohlcvBars) }
            }
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
                indicators = indicators,
                earnings = earnings,
                companyInfo = companyInfo,
                indicatorCalculator = indicatorCalculator,
                quoteRepository = quoteRepository,
                earningsRepository = mock(),
                symbolRepository = mock(),
                ingestionStatusRepository = mock(),
                marketHolidayRepository =
                    mock<MarketHolidayRepository>().apply {
                        stub { on { findHolidayDates(any()) } doReturn holidays }
                    },
                indicatorsMode = indicatorsMode,
            )

        return Fixture(service, ohlcv, indicators, earnings, companyInfo)
    }
}
