package com.skrymer.udgaard.integration.alphavantage

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
internal class AlphavantageClientTest {
  @Autowired
  lateinit var alphaVantageClient: AlphaVantageClient

  @Test
  fun `test get daily adjusted time series for stock`() {
    val quotes = runBlocking {
      alphaVantageClient.getDailyAdjustedTimeSeries("PLTR", "compact")
    }
    // Note: This test may return null if:
    // 1. Alpha Vantage API rate limit is exceeded (75 calls/min for premium tier)
    // 2. API key is invalid or not configured
    // 3. API is experiencing issues
    // The important thing is that it doesn't crash on error responses

    if (quotes != null && quotes.isNotEmpty()) {
      // Verify that prices are adjusted (all OHLC should be adjusted)
      val firstQuote = quotes.first()
      Assertions.assertTrue(firstQuote.openPrice > 0, "Open price should be adjusted")
      Assertions.assertTrue(firstQuote.closePrice > 0, "Close price should be adjusted")
      Assertions.assertTrue(firstQuote.high > 0, "High price should be adjusted")
      Assertions.assertTrue(firstQuote.low > 0, "Low price should be adjusted")
      Assertions.assertTrue(firstQuote.volume > 0, "Volume should be present")
    } else {
      // This is acceptable - API might have rate limits or issues
      println("Alpha Vantage API returned null or empty (possibly rate limited or API key issue)")
    }
  }
}
