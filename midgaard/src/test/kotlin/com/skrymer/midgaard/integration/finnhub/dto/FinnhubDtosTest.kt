package com.skrymer.midgaard.integration.finnhub.dto

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FinnhubDtosTest {
    @Test
    fun `toLatestQuote converts valid response`() {
        val response =
            FinnhubQuoteResponse(
                currentPrice = 150.0,
                change = 2.0,
                changePercent = 1.35,
                highPrice = 152.0,
                lowPrice = 147.0,
                openPrice = 148.5,
                previousClose = 148.0,
                timestamp = 1711640400L,
            )

        val quote = response.toLatestQuote("AAPL")

        assertEquals("AAPL", quote.symbol)
        assertEquals(150.0, quote.price)
        assertEquals(148.0, quote.previousClose)
        assertEquals(2.0, quote.change)
        assertEquals(1.35, quote.changePercent)
        assertEquals(1711640400L, quote.timestamp)
    }

    @Test
    fun `toLatestQuote computes change when null`() {
        val response =
            FinnhubQuoteResponse(
                currentPrice = 150.0,
                change = null,
                changePercent = null,
                previousClose = 148.0,
                timestamp = 1711640400L,
            )

        val quote = response.toLatestQuote("AAPL")

        assertEquals(2.0, quote.change, 0.0001)
        assertEquals(0.0, quote.changePercent)
    }

    @Test
    fun `isValid returns true for valid response`() {
        val response = FinnhubQuoteResponse(currentPrice = 150.0, timestamp = 1711640400L)

        assertTrue(response.isValid())
    }

    @Test
    fun `isValid returns false when price is zero`() {
        val response = FinnhubQuoteResponse(currentPrice = 0.0, timestamp = 1711640400L)

        assertFalse(response.isValid())
    }

    @Test
    fun `isValid returns false when timestamp is zero`() {
        val response = FinnhubQuoteResponse(currentPrice = 150.0, timestamp = 0L)

        assertFalse(response.isValid())
    }
}
