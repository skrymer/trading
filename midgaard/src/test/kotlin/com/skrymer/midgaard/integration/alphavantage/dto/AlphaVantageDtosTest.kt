package com.skrymer.midgaard.integration.alphavantage.dto

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlphaVantageDtosTest {
    @Test
    fun `isEmptyQuote returns true for holiday bars with zero volume and flat OHLC`() {
        val data = DailyAdjustedData(
            open = "12.43",
            high = "12.43",
            low = "12.43",
            close = "12.43",
            adjustedClose = "12.43",
            volume = "0",
            dividendAmount = "0.0000",
            splitCoefficient = "1.0"
        )
        assertTrue(data.isEmptyQuote())
    }

    @Test
    fun `isEmptyQuote returns false for normal trading bars`() {
        val data = DailyAdjustedData(
            open = "12.38",
            high = "12.93",
            low = "12.32",
            close = "12.43",
            adjustedClose = "12.43",
            volume = "3737419",
            dividendAmount = "0.0000",
            splitCoefficient = "1.0"
        )
        assertFalse(data.isEmptyQuote())
    }

    @Test
    fun `isEmptyQuote returns false for zero volume with different OHLC`() {
        val data = DailyAdjustedData(
            open = "12.38",
            high = "12.50",
            low = "12.30",
            close = "12.43",
            adjustedClose = "12.43",
            volume = "0",
            dividendAmount = "0.0000",
            splitCoefficient = "1.0"
        )
        assertFalse(data.isEmptyQuote())
    }

    @Test
    fun `toRawBars filters out holiday bars`() {
        val response = AlphaVantageTimeSeriesDailyAdjusted(
            metaData = AdjustedMetaData(
                information = "Daily Adjusted",
                symbol = "PGY",
                lastRefreshed = "2026-02-17",
                outputSize = "Compact",
                timeZone = "US/Eastern"
            ),
            timeSeriesDaily = mapOf(
                "2026-02-13" to DailyAdjustedData(
                    open = "12.38", high = "12.93", low = "12.32",
                    close = "12.43", adjustedClose = "12.43",
                    volume = "3737419", dividendAmount = "0.0000", splitCoefficient = "1.0"
                ),
                "2026-02-16" to DailyAdjustedData(
                    open = "12.43", high = "12.43", low = "12.43",
                    close = "12.43", adjustedClose = "12.43",
                    volume = "0", dividendAmount = "0.0000", splitCoefficient = "1.0"
                ),
                "2026-02-17" to DailyAdjustedData(
                    open = "12.27", high = "12.50", low = "11.96",
                    close = "12.30", adjustedClose = "12.30",
                    volume = "3358350", dividendAmount = "0.0000", splitCoefficient = "1.0"
                )
            )
        )

        val bars = response.toRawBars()

        assertEquals(2, bars.size)
        assertEquals("2026-02-13", bars[0].date.toString())
        assertEquals("2026-02-17", bars[1].date.toString())
    }
}
