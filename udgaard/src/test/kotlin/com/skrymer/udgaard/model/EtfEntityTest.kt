package com.skrymer.udgaard.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class EtfEntityTest {
  @Test
  fun `getLatestQuote should return the most recent quote`() {
    val etf = createTestEtf()

    val latestQuote = etf.getLatestQuote()

    assertNotNull(latestQuote)
    assertEquals(LocalDate.of(2024, 11, 23), latestQuote?.date)
    assertEquals(595.0, latestQuote?.closePrice)
  }

  @Test
  fun `getLatestQuote should return null when no quotes exist`() {
    val etf =
      EtfEntity().apply {
        symbol = "SPY"
        name = "SPDR S&P 500 ETF Trust"
        quotes = mutableListOf()
      }

    assertNull(etf.getLatestQuote())
  }

  @Test
  fun `getQuoteByDate should return quote for specific date`() {
    val etf = createTestEtf()

    val quote = etf.getQuoteByDate(LocalDate.of(2024, 11, 22))

    assertNotNull(quote)
    assertEquals(590.0, quote?.closePrice)
  }

  @Test
  fun `getQuoteByDate should return null when date not found`() {
    val etf = createTestEtf()

    val quote = etf.getQuoteByDate(LocalDate.of(2024, 10, 1))

    assertNull(quote)
  }

  @Test
  fun `getBullishPercentage should return latest value when date is null`() {
    val etf = createTestEtf()

    val percentage = etf.getBullishPercentage()

    assertEquals(68.0, percentage, 0.001)
  }

  @Test
  fun `getBullishPercentage should return value for specific date`() {
    val etf = createTestEtf()

    val percentage = etf.getBullishPercentage(LocalDate.of(2024, 11, 22))

    assertEquals(65.0, percentage, 0.001)
  }

  @Test
  fun `getStocksInUptrend should return latest count when date is null`() {
    val etf = createTestEtf()

    val count = etf.getStocksInUptrend()

    assertEquals(340, count)
  }

  @Test
  fun `getStocksInDowntrend should return correct count`() {
    val etf = createTestEtf()

    val count = etf.getStocksInDowntrend()

    assertEquals(130, count)
  }

  @Test
  fun `getQuotesByDateRange should return quotes within range`() {
    val etf = createTestEtf()

    val quotes =
      etf.getQuotesByDateRange(
        LocalDate.of(2024, 11, 21),
        LocalDate.of(2024, 11, 22),
      )

    assertEquals(2, quotes.size)
    assertEquals(LocalDate.of(2024, 11, 21), quotes[0].date)
    assertEquals(LocalDate.of(2024, 11, 22), quotes[1].date)
  }

  @Test
  fun `getQuotesByDateRange should return empty list when no quotes in range`() {
    val etf = createTestEtf()

    val quotes =
      etf.getQuotesByDateRange(
        LocalDate.of(2024, 10, 1),
        LocalDate.of(2024, 10, 31),
      )

    assertTrue(quotes.isEmpty())
  }

  @Test
  fun `isInUptrend should return true when latest quote is in uptrend`() {
    val etf = createTestEtf()

    assertTrue(etf.isInUptrend())
  }

  @Test
  fun `isInUptrend should return false when no quotes exist`() {
    val etf =
      EtfEntity().apply {
        symbol = "SPY"
        quotes = mutableListOf()
      }

    assertFalse(etf.isInUptrend())
  }

  @Test
  fun `getTopHoldings should return holdings sorted by weight`() {
    val etf = createTestEtfWithHoldings()

    val topHoldings = etf.getTopHoldings(3)

    assertEquals(3, topHoldings.size)
    assertEquals("AAPL", topHoldings[0].stockSymbol)
    assertEquals(7.12, topHoldings[0].weight, 0.001)
    assertEquals("MSFT", topHoldings[1].stockSymbol)
    assertEquals(6.85, topHoldings[1].weight, 0.001)
    assertEquals("NVDA", topHoldings[2].stockSymbol)
    assertEquals(5.23, topHoldings[2].weight, 0.001)
  }

  @Test
  fun `getHoldingsInUptrend should return only holdings in uptrend`() {
    val etf = createTestEtfWithHoldings()

    val uptrendHoldings = etf.getHoldingsInUptrend()

    assertEquals(2, uptrendHoldings.size)
    assertTrue(uptrendHoldings.all { it.inUptrend })
    assertTrue(uptrendHoldings.any { it.stockSymbol == "AAPL" })
    assertTrue(uptrendHoldings.any { it.stockSymbol == "NVDA" })
  }

  @Test
  fun `getTotalWeightInUptrend should sum weights of holdings in uptrend`() {
    val etf = createTestEtfWithHoldings()

    val totalWeight = etf.getTotalWeightInUptrend()

    // AAPL (7.12) + NVDA (5.23) = 12.35
    assertEquals(12.35, totalWeight, 0.01)
  }

  @Test
  fun `toString should include symbol, name, and counts`() {
    val etf = createTestEtf()

    val str = etf.toString()

    assertTrue(str.contains("SPY"))
    assertTrue(str.contains("quotes=4"))
  }

  // Helper methods

  private fun createTestEtf(): EtfEntity =
    EtfEntity().apply {
      symbol = "SPY"
      name = "SPDR S&P 500 ETF Trust"
      description = "Tracks S&P 500 Index"
      quotes =
        mutableListOf(
          EtfQuote(
            date = LocalDate.of(2024, 11, 20),
            openPrice = 580.0,
            closePrice = 585.0,
            high = 586.0,
            low = 579.0,
            volume = 45000000,
            closePriceEMA5 = 582.0,
            closePriceEMA10 = 580.0,
            closePriceEMA20 = 575.0,
            closePriceEMA50 = 570.0,
            bullishPercentage = 63.0,
            stocksInUptrend = 315,
            stocksInDowntrend = 150,
            totalHoldings = 500,
          ),
          EtfQuote(
            date = LocalDate.of(2024, 11, 21),
            openPrice = 585.0,
            closePrice = 587.0,
            high = 588.0,
            low = 584.0,
            volume = 46000000,
            closePriceEMA5 = 585.0,
            closePriceEMA10 = 583.0,
            closePriceEMA20 = 578.0,
            closePriceEMA50 = 572.0,
            bullishPercentage = 64.0,
            stocksInUptrend = 320,
            stocksInDowntrend = 145,
            totalHoldings = 500,
          ),
          EtfQuote(
            date = LocalDate.of(2024, 11, 22),
            openPrice = 587.0,
            closePrice = 590.0,
            high = 591.0,
            low = 586.0,
            volume = 47000000,
            closePriceEMA5 = 588.0,
            closePriceEMA10 = 585.0,
            closePriceEMA20 = 580.0,
            closePriceEMA50 = 574.0,
            bullishPercentage = 65.0,
            stocksInUptrend = 325,
            stocksInDowntrend = 140,
            totalHoldings = 500,
          ),
          EtfQuote(
            date = LocalDate.of(2024, 11, 23),
            openPrice = 590.0,
            closePrice = 595.0,
            high = 596.0,
            low = 589.0,
            volume = 50000000,
            closePriceEMA5 = 592.0,
            closePriceEMA10 = 590.0,
            closePriceEMA20 = 585.0,
            closePriceEMA50 = 580.0,
            bullishPercentage = 68.0,
            stocksInUptrend = 340,
            stocksInDowntrend = 130,
            totalHoldings = 500,
          ),
        )
    }

  private fun createTestEtfWithHoldings(): EtfEntity =
    EtfEntity().apply {
      symbol = "SPY"
      name = "SPDR S&P 500 ETF Trust"
      holdings =
        mutableListOf(
          EtfHolding(
            stockSymbol = "AAPL",
            weight = 7.12,
            asOfDate = LocalDate.of(2024, 11, 15),
            inUptrend = true,
            trend = "UPTREND",
          ),
          EtfHolding(
            stockSymbol = "MSFT",
            weight = 6.85,
            asOfDate = LocalDate.of(2024, 11, 15),
            inUptrend = false,
            trend = "DOWNTREND",
          ),
          EtfHolding(
            stockSymbol = "NVDA",
            weight = 5.23,
            asOfDate = LocalDate.of(2024, 11, 15),
            inUptrend = true,
            trend = "UPTREND",
          ),
          EtfHolding(
            stockSymbol = "GOOGL",
            weight = 3.45,
            asOfDate = LocalDate.of(2024, 11, 15),
            inUptrend = false,
            trend = "NEUTRAL",
          ),
        )
    }
}
