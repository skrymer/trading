package com.skrymer.udgaard.model.strategy

import com.skrymer.udgaard.model.StockQuote
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

class MarketRegimeFilterTest {

    @Test
    fun `should return true when all market regime conditions are met`() {
        val quote = StockQuote(
            date = LocalDate.of(2022, 6, 15),
            spyDaysAbove200SMA = 25,  // > 20 required
            spyEMA50 = 450.0,
            spyEMA200 = 440.0,  // 50 > 200 (golden cross maintained)
            marketAdvancingPercent = 65.0  // > 60 required
        )

        assertTrue(MarketRegimeFilter.isMarketRegimeFavorable(quote),
            "Market regime should be favorable when all conditions are met")
    }

    @Test
    fun `should return false when SPY not sustained above 200 SMA`() {
        val quote = StockQuote(
            date = LocalDate.of(2024, 3, 15),
            spyDaysAbove200SMA = 10,  // < 20 required (FAIL)
            spyEMA50 = 450.0,
            spyEMA200 = 440.0,
            marketAdvancingPercent = 65.0
        )

        assertFalse(MarketRegimeFilter.isMarketRegimeFavorable(quote),
            "Market regime should be unfavorable when SPY not sustained above 200 SMA")
    }

    @Test
    fun `should return false when golden cross not maintained`() {
        val quote = StockQuote(
            date = LocalDate.of(2024, 8, 1),
            spyDaysAbove200SMA = 25,
            spyEMA50 = 440.0,  // < 200 EMA (FAIL - death cross)
            spyEMA200 = 450.0,
            marketAdvancingPercent = 65.0
        )

        assertFalse(MarketRegimeFilter.isMarketRegimeFavorable(quote),
            "Market regime should be unfavorable when golden cross not maintained")
    }

    @Test
    fun `should return false when market breadth is weak`() {
        val quote = StockQuote(
            date = LocalDate.of(2025, 1, 15),
            spyDaysAbove200SMA = 25,
            spyEMA50 = 450.0,
            spyEMA200 = 440.0,
            marketAdvancingPercent = 45.0  // < 60 required (FAIL - weak breadth)
        )

        assertFalse(MarketRegimeFilter.isMarketRegimeFavorable(quote),
            "Market regime should be unfavorable when market breadth is weak")
    }

    @Test
    fun `should return false when multiple conditions fail`() {
        val quote = StockQuote(
            date = LocalDate.of(2024, 12, 1),
            spyDaysAbove200SMA = 5,    // FAIL
            spyEMA50 = 440.0,           // FAIL (death cross)
            spyEMA200 = 450.0,
            marketAdvancingPercent = 40.0  // FAIL
        )

        assertFalse(MarketRegimeFilter.isMarketRegimeFavorable(quote),
            "Market regime should be unfavorable when multiple conditions fail")
    }

    @Test
    fun `should return true when exactly at threshold values`() {
        val quote = StockQuote(
            date = LocalDate.of(2022, 3, 15),
            spyDaysAbove200SMA = 20,  // exactly at minimum
            spyEMA50 = 450.0,
            spyEMA200 = 440.0,
            marketAdvancingPercent = 60.0  // exactly at minimum
        )

        assertTrue(MarketRegimeFilter.isMarketRegimeFavorable(quote),
            "Market regime should be favorable when at exact threshold values")
    }

    @Test
    fun `should provide detailed market regime description`() {
        val quote = StockQuote(
            date = LocalDate.of(2025, 11, 1),
            spyDaysAbove200SMA = 25,
            spyEMA50 = 450.0,
            spyEMA200 = 440.0,
            marketAdvancingPercent = 65.0
        )

        val description = MarketRegimeFilter.getMarketRegimeDescription(quote)

        assertTrue(description.contains("FAVORABLE"), "Description should indicate favorable regime")
        assertTrue(description.contains("25"), "Description should include days above 200 SMA")
        assertTrue(description.contains("65.0%"), "Description should include market breadth percentage")
        assertTrue(description.contains("Yes"), "Description should confirm golden cross")
        assertTrue(description.contains("2025-11-01"), "Description should include the date")
    }

    @Test
    fun `should provide unfavorable description when conditions not met`() {
        val quote = StockQuote(
            date = LocalDate.of(2025, 1, 15),
            spyDaysAbove200SMA = 5,
            spyEMA50 = 440.0,
            spyEMA200 = 450.0,
            marketAdvancingPercent = 45.0
        )

        val description = MarketRegimeFilter.getMarketRegimeDescription(quote)

        assertTrue(description.contains("UNFAVORABLE"), "Description should indicate unfavorable regime")
        assertTrue(description.contains("No"), "Description should show no golden cross")
    }

    @Test
    fun `should handle edge case with zero values`() {
        val quote = StockQuote(
            date = LocalDate.of(2025, 1, 1),
            spyDaysAbove200SMA = 0,
            spyEMA50 = 0.0,
            spyEMA200 = 0.0,
            marketAdvancingPercent = 0.0
        )

        assertFalse(MarketRegimeFilter.isMarketRegimeFavorable(quote),
            "Market regime should be unfavorable with zero values")
    }

    @Test
    fun `should simulate 2022 bull market conditions`() {
        // 2022 had 4.45% edge - should pass regime filter
        val quote = StockQuote(
            date = LocalDate.of(2022, 4, 1),
            spyDaysAbove200SMA = 120,  // Strong sustained trend
            spyEMA50 = 450.0,
            spyEMA200 = 420.0,  // Clear golden cross
            marketAdvancingPercent = 75.0  // Strong breadth
        )

        assertTrue(MarketRegimeFilter.isMarketRegimeFavorable(quote),
            "2022 bull market conditions should pass regime filter")
    }

    @Test
    fun `should simulate 2024 choppy market conditions`() {
        // 2024 had 0.90% edge - should fail regime filter
        val quote = StockQuote(
            date = LocalDate.of(2024, 6, 15),
            spyDaysAbove200SMA = 8,   // Choppy, not sustained
            spyEMA50 = 445.0,
            spyEMA200 = 450.0,  // Death cross
            marketAdvancingPercent = 48.0  // Weak breadth
        )

        assertFalse(MarketRegimeFilter.isMarketRegimeFavorable(quote),
            "2024 choppy market conditions should fail regime filter")
    }
}
