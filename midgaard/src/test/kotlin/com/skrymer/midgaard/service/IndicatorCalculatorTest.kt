package com.skrymer.midgaard.service

import com.skrymer.midgaard.model.RawBar
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IndicatorCalculatorTest {
    private val calculator = IndicatorCalculator()

    private fun bars(highLows: List<Pair<Double, Double>>): List<RawBar> =
        highLows.mapIndexed { i, (high, low) ->
            RawBar(
                symbol = "TEST",
                date = LocalDate.of(2020, 1, 1).plusDays(i.toLong()),
                open = low,
                high = high,
                low = low,
                close = low,
                volume = 0L,
            )
        }

    @Test
    fun `calculateSMA returns the arithmetic mean of the trailing period closes`() {
        // Given a series of closes
        val closes = listOf(10.0, 11.0, 12.0, 13.0, 14.0)

        // When the 3-period SMA is calculated
        val sma = calculator.calculateSMA(closes, period = 3)

        // Then each filled value is the mean of the trailing 3 closes
        assertEquals(11.0, sma[2]) // (10+11+12)/3
        assertEquals(12.0, sma[3]) // (11+12+13)/3
        assertEquals(13.0, sma[4]) // (12+13+14)/3
    }

    @Test
    fun `calculateSMA returns null for bars before a full period of history exists`() {
        // Given a series of closes
        val closes = listOf(10.0, 11.0, 12.0, 13.0, 14.0)

        // When the 3-period SMA is calculated
        val sma = calculator.calculateSMA(closes, period = 3)

        // Then the first two bars are undefined (fewer than 3 prior closes)
        assertEquals(closes.size, sma.size)
        assertNull(sma[0])
        assertNull(sma[1])
    }

    @Test
    fun `calculate52WeekHigh returns the highest intraday high over the trailing lookback`() {
        // Given bars with varying intraday highs (high, low)
        val series = bars(listOf(10.0 to 8.0, 12.0 to 9.0, 11.0 to 7.0, 9.0 to 6.0, 15.0 to 10.0))

        // When the rolling high is calculated over a 3-bar lookback
        val high = calculator.calculate52WeekHigh(series, lookback = 3)

        // Then each filled value is the max high of the trailing 3 bars
        assertEquals(12.0, high[2]) // max(10,12,11)
        assertEquals(12.0, high[3]) // max(12,11,9)
        assertEquals(15.0, high[4]) // max(11,9,15)
    }

    @Test
    fun `calculate52WeekLow returns the lowest intraday low over the trailing lookback`() {
        // Given bars with varying intraday lows (high, low)
        val series = bars(listOf(10.0 to 8.0, 12.0 to 9.0, 11.0 to 7.0, 9.0 to 6.0, 15.0 to 10.0))

        // When the rolling low is calculated over a 3-bar lookback
        val low = calculator.calculate52WeekLow(series, lookback = 3)

        // Then each filled value is the min low of the trailing 3 bars
        assertEquals(7.0, low[2]) // min(8,9,7)
        assertEquals(6.0, low[3]) // min(9,7,6)
        assertEquals(6.0, low[4]) // min(7,6,10)
    }

    @Test
    fun `52-week high and low are null for a stock with less than a full year of history`() {
        // Given fewer bars than the default 252-day window
        val series = bars(List(100) { 10.0 to 8.0 })

        // When the 52-week channel is calculated at the default lookback
        val high = calculator.calculate52WeekHigh(series)
        val low = calculator.calculate52WeekLow(series)

        // Then every bar is undefined — the stock has no 52-week high or low yet
        assertEquals(100, high.size)
        assertEquals(100, low.size)
        assertNull(high.last())
        assertNull(low.last())
    }
}
