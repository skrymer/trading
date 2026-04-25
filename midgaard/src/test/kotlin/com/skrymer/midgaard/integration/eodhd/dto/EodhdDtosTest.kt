package com.skrymer.midgaard.integration.eodhd.dto

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EodhdDtosTest {
    @Test
    fun `toRawBars maps fields and filters by minDate`() {
        // Given: three bars, the first before the cutoff date
        val response =
            EodhdEodResponse(
                bars =
                    listOf(
                        bar("2024-12-30", close = 101.0),
                        bar("2025-01-02", close = 111.0, volume = 2_000_000),
                        bar("2025-01-03", close = 114.0, volume = 3_000_000),
                    ),
            )

        // When
        val bars = response.toRawBars("AAPL", LocalDate.of(2025, 1, 1))

        // Then: pre-cutoff bar dropped, remaining bars sorted ascending and mapped intact
        assertEquals(2, bars.size)
        assertEquals(LocalDate.of(2025, 1, 2), bars[0].date)
        assertEquals(LocalDate.of(2025, 1, 3), bars[1].date)
        assertEquals(111.0, bars[0].close)
        assertEquals(2_000_000L, bars[0].volume)
        assertEquals("AAPL", bars[0].symbol)
    }

    @Test
    fun `toRawBars uses adjusted_close for close and scales OHL by adjustment factor`() {
        // Given: a bar where adjusted_close differs from close (split or dividend).
        // close = 200, adjusted_close = 100 → factor 0.5 applied to OHL.
        val response =
            EodhdEodResponse(
                bars =
                    listOf(
                        bar(
                            date = "2025-01-02",
                            open = 200.0,
                            high = 210.0,
                            low = 195.0,
                            close = 200.0,
                            adjustedClose = 100.0,
                        ),
                    ),
            )

        // When
        val bars = response.toRawBars("TEST", LocalDate.of(2025, 1, 1))

        // Then: close == adjusted_close, OHL scaled by 0.5
        assertEquals(1, bars.size)
        assertEquals(100.0, bars[0].close)
        assertEquals(100.0, bars[0].open)
        assertEquals(105.0, bars[0].high)
        assertEquals(97.5, bars[0].low)
    }

    @Test
    fun `toRawBars skips bars with missing or non-positive close fields`() {
        // Given: a mix of malformed bars and one well-formed bar
        val response =
            EodhdEodResponse(
                bars =
                    listOf(
                        bar("2025-01-02", close = null),
                        bar("2025-01-03", adjustedClose = null),
                        bar("2025-01-04", close = 0.0, adjustedClose = 0.0),
                        bar("2025-01-05", close = 101.0),
                    ),
            )

        // When
        val bars = response.toRawBars("TEST", LocalDate.of(2025, 1, 1))

        // Then: only the fully-populated bar survives
        assertEquals(1, bars.size)
        assertEquals(LocalDate.of(2025, 1, 5), bars[0].date)
    }

    @Test
    fun `toRawBars skips bars with unparseable date strings`() {
        // Given: a malformed date string between two valid bars
        val response =
            EodhdEodResponse(
                bars =
                    listOf(
                        bar("2025-01-02"),
                        bar("not-a-date"),
                        bar("2025-01-03"),
                    ),
            )

        // When
        val bars = response.toRawBars("TEST", LocalDate.of(2025, 1, 1))

        // Then: the bad row is silently dropped
        assertEquals(2, bars.size)
    }

    @Test
    fun `isValid stays true on empty bars list because no-data is a valid response`() {
        // Given: an empty response — provider treats as "no data in range" and falls
        // through to a transformer that returns an empty list. Distinguishing this
        // from a malformed payload is hasError()'s job, not isValid()'s.
        val response = EodhdEodResponse(bars = emptyList())

        // When / Then
        assertTrue(response.isValid())
    }

    @Test
    fun `hasError reports the error message when present`() {
        // Given: an EODHD-style synthetic error response
        val response = EodhdEodResponse(bars = emptyList(), errorMessage = "Symbol not found")

        // When / Then
        assertTrue(response.hasError())
        assertEquals("Symbol not found", response.getErrorDescription())
    }

    @Test
    fun `toAtrMap parses rows and filters by minDate`() {
        // Given: three ATR rows with the first before the cutoff
        val response =
            EodhdAtrResponse(
                rows =
                    listOf(
                        EodhdAtrRowDto("2024-12-30", 1.10),
                        EodhdAtrRowDto("2025-01-02", 1.20),
                        EodhdAtrRowDto("2025-01-03", 1.30),
                    ),
            )

        // When
        val map = response.toAtrMap(LocalDate.of(2025, 1, 1))

        // Then: pre-cutoff value dropped, others mapped to date keys
        assertEquals(2, map.size)
        assertEquals(1.20, map[LocalDate.of(2025, 1, 2)])
        assertEquals(1.30, map[LocalDate.of(2025, 1, 3)])
    }

    @Test
    fun `toAtrMap drops rows with missing values or unparseable dates`() {
        // Given: a row with null atr and a row with an invalid date
        val response =
            EodhdAtrResponse(
                rows =
                    listOf(
                        EodhdAtrRowDto("2025-01-02", 1.20),
                        EodhdAtrRowDto("2025-01-03", null),
                        EodhdAtrRowDto("not-a-date", 1.40),
                    ),
            )

        // When
        val map = response.toAtrMap(LocalDate.of(2025, 1, 1))

        // Then: only the well-formed row survives
        assertEquals(1, map.size)
        assertEquals(1.20, map[LocalDate.of(2025, 1, 2)])
    }

    @Test
    fun `toAdxMap parses rows and filters by minDate`() {
        // Given: three ADX rows
        val response =
            EodhdAdxResponse(
                rows =
                    listOf(
                        EodhdAdxRowDto("2024-12-30", 18.0),
                        EodhdAdxRowDto("2025-01-02", 22.5),
                        EodhdAdxRowDto("2025-01-03", 24.1),
                    ),
            )

        // When
        val map = response.toAdxMap(LocalDate.of(2025, 1, 1))

        // Then
        assertEquals(2, map.size)
        assertEquals(22.5, map[LocalDate.of(2025, 1, 2)])
        assertEquals(24.1, map[LocalDate.of(2025, 1, 3)])
    }

    @Test
    fun `isValid stays true on empty technical responses for the same reason as EOD`() {
        // Given: empty atr and adx responses
        // When / Then
        assertTrue(EodhdAtrResponse(rows = emptyList()).isValid())
        assertTrue(EodhdAdxResponse(rows = emptyList()).isValid())
    }

    @Test
    fun `toCompanyInfo extracts sector and market cap from fundamentals payload`() {
        // Given: a fundamentals response with General + Highlights populated.
        // marketCapitalization is String? because some ADRs return it as a string;
        // the mapper parses to Long defensively.
        val response =
            EodhdFundamentalsResponse(
                general = EodhdGeneralSection(sector = "Technology", industry = "Consumer Electronics"),
                highlights = EodhdHighlightsSection(marketCapitalization = "3250000000000"),
            )

        // When
        val info = response.toCompanyInfo()

        // Then
        assertEquals("Technology", info.sector)
        assertEquals(3_250_000_000_000L, info.marketCap)
    }

    @Test
    fun `toCompanyInfo treats unparseable market cap string as null`() {
        // Given: an ADR-style payload where MarketCapitalization came back as garbage
        val response =
            EodhdFundamentalsResponse(
                general = EodhdGeneralSection(sector = "Technology"),
                highlights = EodhdHighlightsSection(marketCapitalization = "n/a"),
            )

        // When
        val info = response.toCompanyInfo()

        // Then: sector still surfaces, market cap drops to null rather than crashing
        assertEquals("Technology", info.sector)
        assertNull(info.marketCap)
    }

    @Test
    fun `toCompanyInfo returns nulls when sections are absent`() {
        // Given: empty fundamentals — both sections missing
        val response = EodhdFundamentalsResponse()

        // When
        val info = response.toCompanyInfo()

        // Then: both fields null, no NPE
        assertNull(info.sector)
        assertNull(info.marketCap)
    }

    @Test
    fun `toEarnings maps history entries sorted descending by fiscal date`() {
        // Given: three historical earnings reports
        val response =
            EodhdFundamentalsResponse(
                earnings =
                    EodhdEarningsSection(
                        history =
                            mapOf(
                                "2024-06-30" to earningEntry("2024-06-30", "2024-08-01", "1.40", "1.35", "0.05", "3.7", "AfterMarket"),
                                "2024-09-30" to earningEntry("2024-09-30", "2024-10-31", "1.64", "1.60", "0.04", "2.5", "AfterMarket"),
                                "2024-03-31" to earningEntry("2024-03-31", "2024-05-02", "1.53", "1.50", "0.03", "2.0", "AfterMarket"),
                            ),
                    ),
            )

        // When
        val earnings = response.toEarnings("AAPL")

        // Then: sorted newest-first, fields mapped
        assertEquals(3, earnings.size)
        assertEquals(LocalDate.of(2024, 9, 30), earnings[0].fiscalDateEnding)
        assertEquals(LocalDate.of(2024, 6, 30), earnings[1].fiscalDateEnding)
        assertEquals(LocalDate.of(2024, 3, 31), earnings[2].fiscalDateEnding)
        assertEquals("AAPL", earnings[0].symbol)
        assertEquals(BigDecimal("1.64"), earnings[0].reportedEps)
        assertEquals(BigDecimal("1.60"), earnings[0].estimatedEps)
        assertEquals(BigDecimal("0.04"), earnings[0].surprise)
        assertEquals(BigDecimal("2.5"), earnings[0].surprisePercentage)
        assertEquals("AfterMarket", earnings[0].reportTime)
        assertEquals(LocalDate.of(2024, 10, 31), earnings[0].reportedDate)
    }

    @Test
    fun `toEarnings returns empty list when earnings section is missing`() {
        // Given: a fundamentals response with no Earnings block
        val response = EodhdFundamentalsResponse()

        // When
        val earnings = response.toEarnings("AAPL")

        // Then
        assertTrue(earnings.isEmpty())
    }

    @Test
    fun `toEarning skips entries without a fiscal date`() {
        // Given: an earnings entry with null date — fiscalDateEnding is required
        val entry = earningEntry(date = null, reportDate = "2024-08-01", "1.40", "1.35", "0.05", "3.7", "AfterMarket")

        // When
        val earning = entry.toEarning("AAPL")

        // Then
        assertNull(earning)
    }

    @Test
    fun `isValid returns true when at least one fundamentals section is present`() {
        // Given: only General populated
        val response = EodhdFundamentalsResponse(general = EodhdGeneralSection(sector = "Tech"))

        // When / Then
        assertTrue(response.isValid())
    }

    @Test
    fun `isValid returns false when fundamentals payload is empty`() {
        // Given: no sections returned at all
        val response = EodhdFundamentalsResponse()

        // When / Then
        assertFalse(response.isValid())
    }

    @Suppress("LongParameterList")
    private fun earningEntry(
        date: String?,
        reportDate: String?,
        epsActual: String?,
        epsEstimate: String?,
        epsDifference: String?,
        surprisePercent: String?,
        beforeAfterMarket: String?,
    ): EodhdEarningEntry =
        EodhdEarningEntry(
            date = date,
            reportDate = reportDate,
            epsActual = epsActual?.let { BigDecimal(it) },
            epsEstimate = epsEstimate?.let { BigDecimal(it) },
            epsDifference = epsDifference?.let { BigDecimal(it) },
            surprisePercent = surprisePercent?.let { BigDecimal(it) },
            beforeAfterMarket = beforeAfterMarket,
        )

    @Suppress("LongParameterList")
    private fun bar(
        date: String,
        open: Double? = 100.0,
        high: Double? = 102.0,
        low: Double? = 99.0,
        close: Double? = 101.0,
        adjustedClose: Double? = close,
        volume: Long? = 1_000_000,
    ): EodhdBarDto =
        EodhdBarDto(
            date = date,
            open = open,
            high = high,
            low = low,
            close = close,
            adjustedClose = adjustedClose,
            volume = volume,
        )
}
