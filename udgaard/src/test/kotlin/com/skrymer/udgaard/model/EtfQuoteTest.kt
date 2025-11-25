package com.skrymer.udgaard.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

class EtfQuoteTest {

    @Test
    fun `isInUptrend should return true when EMAs are properly aligned`() {
        val quote = EtfQuote(
            date = LocalDate.of(2024, 11, 23),
            openPrice = 590.0,
            closePrice = 595.0,
            high = 596.0,
            low = 589.0,
            volume = 50000000,
            closePriceEMA10 = 590.0,
            closePriceEMA20 = 585.0,
            closePriceEMA50 = 580.0
        )

        assertTrue(quote.isInUptrend(), "Should be in uptrend when EMA10 > EMA20 and close > EMA50")
    }

    @Test
    fun `isInUptrend should return false when EMAs are not aligned`() {
        val quote = EtfQuote(
            date = LocalDate.of(2024, 11, 23),
            openPrice = 590.0,
            closePrice = 575.0,  // Close below EMA50
            high = 596.0,
            low = 574.0,
            volume = 50000000,
            closePriceEMA10 = 585.0,  // EMA10 < EMA20
            closePriceEMA20 = 590.0,
            closePriceEMA50 = 595.0
        )

        assertFalse(quote.isInUptrend(), "Should not be in uptrend when EMA10 < EMA20")
    }

    @Test
    fun `isInUptrend should return false when close is below EMA50 even if EMA10 greater than EMA20`() {
        val quote = EtfQuote(
            date = LocalDate.of(2024, 11, 23),
            openPrice = 590.0,
            closePrice = 575.0,  // Close below EMA50
            high = 596.0,
            low = 574.0,
            volume = 50000000,
            closePriceEMA10 = 590.0,  // EMA10 > EMA20 (correct)
            closePriceEMA20 = 585.0,
            closePriceEMA50 = 580.0  // But close < EMA50
        )

        assertFalse(quote.isInUptrend(), "Should not be in uptrend when close price is below EMA50")
    }

    @Test
    fun `hasBuySignal should return true when last buy signal is after sell signal`() {
        val quote = EtfQuote(
            date = LocalDate.of(2024, 11, 23),
            openPrice = 590.0,
            closePrice = 595.0,
            high = 596.0,
            low = 589.0,
            volume = 50000000,
            lastBuySignal = LocalDate.of(2024, 11, 20),
            lastSellSignal = LocalDate.of(2024, 11, 15)
        )

        assertTrue(quote.hasBuySignal(), "Should have buy signal when buy is after sell")
    }

    @Test
    fun `hasBuySignal should return false when last buy signal is before sell signal`() {
        val quote = EtfQuote(
            date = LocalDate.of(2024, 11, 23),
            openPrice = 590.0,
            closePrice = 595.0,
            high = 596.0,
            low = 589.0,
            volume = 50000000,
            lastBuySignal = LocalDate.of(2024, 11, 10),
            lastSellSignal = LocalDate.of(2024, 11, 20)
        )

        assertFalse(quote.hasBuySignal(), "Should not have buy signal when buy is before sell")
    }

    @Test
    fun `hasBuySignal should return true when buy signal exists and no sell signal`() {
        val quote = EtfQuote(
            date = LocalDate.of(2024, 11, 23),
            openPrice = 590.0,
            closePrice = 595.0,
            high = 596.0,
            low = 589.0,
            volume = 50000000,
            lastBuySignal = LocalDate.of(2024, 11, 20),
            lastSellSignal = null
        )

        assertTrue(quote.hasBuySignal(), "Should have buy signal when no sell signal exists")
    }

    @Test
    fun `hasSellSignal should return true when last sell signal is after buy signal`() {
        val quote = EtfQuote(
            date = LocalDate.of(2024, 11, 23),
            openPrice = 590.0,
            closePrice = 585.0,
            high = 591.0,
            low = 584.0,
            volume = 50000000,
            lastBuySignal = LocalDate.of(2024, 11, 15),
            lastSellSignal = LocalDate.of(2024, 11, 22)
        )

        assertTrue(quote.hasSellSignal(), "Should have sell signal when sell is after buy")
    }

    @Test
    fun `hasSellSignal should return false when last sell signal is before buy signal`() {
        val quote = EtfQuote(
            date = LocalDate.of(2024, 11, 23),
            openPrice = 590.0,
            closePrice = 595.0,
            high = 596.0,
            low = 589.0,
            volume = 50000000,
            lastBuySignal = LocalDate.of(2024, 11, 20),
            lastSellSignal = LocalDate.of(2024, 11, 10)
        )

        assertFalse(quote.hasSellSignal(), "Should not have sell signal when sell is before buy")
    }

    @Test
    fun `bullishPercentage should match stocksInUptrend divided by totalHoldings`() {
        val quote = EtfQuote(
            date = LocalDate.of(2024, 11, 23),
            openPrice = 590.0,
            closePrice = 595.0,
            high = 596.0,
            low = 589.0,
            volume = 50000000,
            bullishPercentage = 67.5,
            stocksInUptrend = 270,
            stocksInDowntrend = 100,
            stocksInNeutral = 30,
            totalHoldings = 400
        )

        assertEquals(67.5, quote.bullishPercentage, 0.001)
        assertEquals(270, quote.stocksInUptrend)
        assertEquals(400, quote.totalHoldings)

        val calculatedPercentage = (quote.stocksInUptrend.toDouble() / quote.totalHoldings.toDouble()) * 100.0
        assertEquals(quote.bullishPercentage, calculatedPercentage, 0.1,
            "Bullish percentage should match calculated value")
    }

    @Test
    fun `default values should be zero for optional fields`() {
        val quote = EtfQuote(
            date = LocalDate.of(2024, 11, 23),
            openPrice = 590.0,
            closePrice = 595.0,
            high = 596.0,
            low = 589.0,
            volume = 50000000
        )

        assertEquals(0.0, quote.closePriceEMA5)
        assertEquals(0.0, quote.closePriceEMA10)
        assertEquals(0.0, quote.closePriceEMA20)
        assertEquals(0.0, quote.closePriceEMA50)
        assertEquals(0.0, quote.atr)
        assertEquals(0.0, quote.bullishPercentage)
        assertEquals(0, quote.stocksInUptrend)
        assertEquals(0, quote.stocksInDowntrend)
        assertEquals(0, quote.stocksInNeutral)
        assertEquals(0, quote.totalHoldings)
        assertNull(quote.lastBuySignal)
        assertNull(quote.lastSellSignal)
    }
}
