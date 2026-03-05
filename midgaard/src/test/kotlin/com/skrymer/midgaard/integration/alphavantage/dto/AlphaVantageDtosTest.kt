package com.skrymer.midgaard.integration.alphavantage.dto

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlphaVantageDtosTest {
    @Test
    fun `isEmptyQuote returns true for holiday bars with zero volume and flat OHLC`() {
        val data =
            DailyAdjustedData(
                open = "12.43",
                high = "12.43",
                low = "12.43",
                close = "12.43",
                adjustedClose = "12.43",
                volume = "0",
                dividendAmount = "0.0000",
                splitCoefficient = "1.0",
            )
        assertTrue(data.isEmptyQuote())
    }

    @Test
    fun `isEmptyQuote returns false for normal trading bars`() {
        val data =
            DailyAdjustedData(
                open = "12.38",
                high = "12.93",
                low = "12.32",
                close = "12.43",
                adjustedClose = "12.43",
                volume = "3737419",
                dividendAmount = "0.0000",
                splitCoefficient = "1.0",
            )
        assertFalse(data.isEmptyQuote())
    }

    @Test
    fun `isEmptyQuote returns false for zero volume with different OHLC`() {
        val data =
            DailyAdjustedData(
                open = "12.38",
                high = "12.50",
                low = "12.30",
                close = "12.43",
                adjustedClose = "12.43",
                volume = "0",
                dividendAmount = "0.0000",
                splitCoefficient = "1.0",
            )
        assertFalse(data.isEmptyQuote())
    }

    @Test
    fun `toRawBars filters out holiday bars`() {
        val response =
            buildTimeSeriesResponse(
                "2026-02-13" to tradingBar("12.38", "12.93", "12.32", "12.43", "3737419"),
                "2026-02-16" to holidayBar("12.43"),
                "2026-02-17" to tradingBar("12.27", "12.50", "11.96", "12.30", "3358350"),
            )

        val bars = response.toRawBars()

        assertEquals(2, bars.size)
        assertEquals("2026-02-13", bars[0].date.toString())
        assertEquals("2026-02-17", bars[1].date.toString())
    }

    private fun tradingBar(
        open: String,
        high: String,
        low: String,
        close: String,
        volume: String,
    ) = DailyAdjustedData(open, high, low, close, close, volume, "0.0000", "1.0")

    private fun holidayBar(price: String) = DailyAdjustedData(price, price, price, price, price, "0", "0.0000", "1.0")

    private fun buildTimeSeriesResponse(vararg entries: Pair<String, DailyAdjustedData>) =
        AlphaVantageTimeSeriesDailyAdjusted(
            metaData = AdjustedMetaData("Daily Adjusted", "PGY", "2026-02-17", "Compact", "US/Eastern"),
            timeSeriesDaily = mapOf(*entries),
        )
}
